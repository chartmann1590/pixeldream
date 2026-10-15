#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include "stable-diffusion.h"

#define LOG_TAG "PixelDreamDiffusion"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Set from stable-diffusion.cpp's own log lines while a model loads: true once it
// reports that the Vulkan device was picked, false if it fell back to the CPU.
static std::atomic<bool> g_gpu_active{false};

static void log_callback(sd_log_level_t level, const char* text, void*) {
    if (text == nullptr) return;
    if (std::strstr(text, "Vulkan: Using device") != nullptr) g_gpu_active.store(true);
    if (std::strstr(text, "Failed to initialize Vulkan backend") != nullptr) g_gpu_active.store(false);
    switch (level) {
        case SD_LOG_ERROR: LOGE("%s", text); break;
        case SD_LOG_WARN: LOGW("%s", text); break;
        case SD_LOG_INFO: LOGI("%s", text); break;
        default: break;
    }
}

struct ProgressBridge {
    JavaVM* vm;
    jobject callback;
    jmethodID invoke;
};

static void progress_callback(int step, int steps, float, void* data) {
    auto* bridge = static_cast<ProgressBridge*>(data);
    if (!bridge || !bridge->callback || !bridge->invoke) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (bridge->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (bridge->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    env->CallVoidMethod(bridge->callback, bridge->invoke, step, steps);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) bridge->vm->DetachCurrentThread();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_loadModelNative(
        JNIEnv* env, jobject, jstring model_path, jint threads, jboolean use_gpu) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
#ifdef PIXELDREAM_GPU
    // Read by stable-diffusion.cpp's backend selection (see CMakeLists.txt).
    setenv("PIXELDREAM_FORCE_CPU", use_gpu ? "0" : "1", 1);
#else
    (void) use_gpu;
#endif
    g_gpu_active.store(false);
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = path;
    params.n_threads = static_cast<int>(threads);
    params.vae_decode_only = true;
    params.enable_mmap = true;
    sd_set_log_callback(log_callback, nullptr);
    sd_ctx_t* context = new_sd_ctx(&params);
    env->ReleaseStringUTFChars(model_path, path);
    if (context == nullptr) g_gpu_active.store(false);
    LOGI("Model load %s (requested %s, running on %s)",
         context ? "ok" : "failed", use_gpu ? "GPU" : "CPU", g_gpu_active.load() ? "GPU" : "CPU");
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_generateImageNative(
        JNIEnv* env, jobject, jlong handle, jstring prompt, jstring negative_prompt,
        jint width, jint height, jint steps, jfloat cfg_scale, jlong seed, jobject progress) {
    auto* context = reinterpret_cast<sd_ctx_t*>(handle);
    if (!context) return nullptr;

    const char* prompt_text = env->GetStringUTFChars(prompt, nullptr);
    const char* negative_text = env->GetStringUTFChars(negative_prompt, nullptr);
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    jclass callback_class = env->GetObjectClass(progress);
    jmethodID progress_method = callback_class == nullptr
        ? nullptr
        : env->GetMethodID(callback_class, "onProgress", "(II)V");
    if (progress_method == nullptr && env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("Progress callback method was unavailable; generation will continue without progress events");
    }
    ProgressBridge bridge{
        vm,
        env->NewGlobalRef(progress),
        progress_method
    };
    if (callback_class != nullptr) env->DeleteLocalRef(callback_class);
    sd_set_progress_callback(progress_callback, &bridge);

    sd_img_gen_params_t params;
    sd_img_gen_params_init(&params);
    params.prompt = prompt_text;
    params.negative_prompt = negative_text;
    params.width = width;
    params.height = height;
    params.seed = seed;
    params.batch_count = 1;
    params.sample_params.sample_steps = steps;
    params.sample_params.guidance.txt_cfg = cfg_scale;
    params.sample_params.sample_method = EULER_A_SAMPLE_METHOD;
    sd_image_t* result = generate_image(context, &params);

    sd_set_progress_callback(nullptr, nullptr);
    env->DeleteGlobalRef(bridge.callback);
    env->ReleaseStringUTFChars(prompt, prompt_text);
    env->ReleaseStringUTFChars(negative_prompt, negative_text);
    if (!result || !result->data) {
        if (result) std::free(result);
        return nullptr;
    }
    const int size = static_cast<int>(result->width * result->height * result->channel);
    jbyteArray bytes = env->NewByteArray(size);
    env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(result->data));
    std::free(result->data);
    std::free(result);
    return bytes;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_isGpuActiveNative(JNIEnv*, jobject) {
    return g_gpu_active.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_isGpuBuildNative(JNIEnv*, jobject) {
#ifdef PIXELDREAM_GPU
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_freeContextNative(
        JNIEnv*, jobject, jlong handle) {
    if (handle) free_sd_ctx(reinterpret_cast<sd_ctx_t*>(handle));
}
