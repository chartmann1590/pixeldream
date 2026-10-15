#include <jni.h>
#include <android/log.h>
#include <android/set_abort_message.h>
#include <dlfcn.h>
#include <sys/system_properties.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <string>
#include "ggml.h"
#include "stable-diffusion.h"
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>

#define LOG_TAG "PixelDreamDiffusion"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Crash reporting. ggml prints its abort reason to stderr, which Android throws
// away, so a GPU failure used to show up as a bare SIGABRT. Route it to logcat
// and into the tombstone's "Abort message" line instead.
// ---------------------------------------------------------------------------
static void pd_ggml_abort(const char* message) {
    LOGF("ggml abort: %s", message ? message : "(no message)");
    android_set_abort_message(message ? message : "ggml abort");
}

static void pd_terminate() {
    std::string what = "std::terminate";
    if (auto current = std::current_exception()) {
        try {
            std::rethrow_exception(current);
        } catch (const std::exception& e) {
            what = std::string("uncaught exception: ") + e.what();
        } catch (...) {
            what = "uncaught non-standard exception";
        }
    }
    LOGF("%s", what.c_str());
    android_set_abort_message(what.c_str());
    std::abort();
}

static void install_crash_reporting() {
    static std::atomic<bool> installed{false};
    if (installed.exchange(true)) return;
    ggml_set_abort_callback(pd_ggml_abort);
    std::set_terminate(pd_terminate);
}

static std::string sysprop(const char* name) {
    char value[PROP_VALUE_MAX] = {0};
    const int length = __system_property_get(name, value);
    return length > 0 ? std::string(value, static_cast<size_t>(length)) : std::string();
}

// ---------------------------------------------------------------------------
// GPU tuning. Defaults for the GPU path: flash attention in the UNet (keeps the
// attention matrices small enough for mobile GPU buffer limits) and a tiled VAE
// decode. For testing on a phone, `adb shell setprop debug.pixeldream.vk <tokens>`
// overrides them; tokens are comma separated:
//   fa / nofa, tile / notile, vaecpu, clipcpu, conv,
//   e:NAME   sets GGML_VK_NAME=1 (e.g. e:DISABLE_COOPMAT),
//   alloc:N  sets GGML_VK_FORCE_MAX_ALLOCATION_SIZE to N MiB.
// The ggml Vulkan backend reads its environment once per process, so a change
// needs an app restart.
// ---------------------------------------------------------------------------
struct GpuOptions {
    bool flash_attn = true;
    bool vae_tiling = true;
    bool vae_on_cpu = false;
    bool clip_on_cpu = false;
    bool conv_direct = false;
};

static GpuOptions g_gpu_options;

static GpuOptions read_gpu_options() {
    GpuOptions options;
    const std::string tokens = sysprop("debug.pixeldream.vk");
    size_t start = 0;
    while (start < tokens.size()) {
        size_t end = tokens.find(',', start);
        if (end == std::string::npos) end = tokens.size();
        const std::string token = tokens.substr(start, end - start);
        start = end + 1;
        if (token.empty()) continue;
        if (token == "fa") options.flash_attn = true;
        else if (token == "nofa") options.flash_attn = false;
        else if (token == "tile") options.vae_tiling = true;
        else if (token == "notile") options.vae_tiling = false;
        else if (token == "vaecpu") options.vae_on_cpu = true;
        else if (token == "clipcpu") options.clip_on_cpu = true;
        else if (token == "conv") options.conv_direct = true;
        else if (token.rfind("e:", 0) == 0 && token.size() > 2) {
            const std::string name = "GGML_VK_" + token.substr(2);
            setenv(name.c_str(), "1", 1);
            LOGI("GPU test option: %s=1", name.c_str());
        } else if (token.rfind("alloc:", 0) == 0) {
            const unsigned long long mib = std::strtoull(token.c_str() + 6, nullptr, 10);
            if (mib > 0) {
                const std::string bytes = std::to_string(mib * 1024ULL * 1024ULL);
                setenv("GGML_VK_FORCE_MAX_ALLOCATION_SIZE", bytes.c_str(), 1);
                LOGI("GPU test option: GGML_VK_FORCE_MAX_ALLOCATION_SIZE=%s", bytes.c_str());
            }
        } else {
            LOGW("Unknown GPU test option '%s'", token.c_str());
        }
    }
    if (!tokens.empty()) LOGI("GPU test options: %s", tokens.c_str());
    return options;
}

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
    install_crash_reporting();
    const char* path = env->GetStringUTFChars(model_path, nullptr);
#ifdef PIXELDREAM_GPU
    // Read by stable-diffusion.cpp's backend selection (see CMakeLists.txt).
    setenv("PIXELDREAM_FORCE_CPU", use_gpu ? "0" : "1", 1);
#else
    (void) use_gpu;
#endif
    g_gpu_options = use_gpu ? read_gpu_options() : GpuOptions{};
    g_gpu_active.store(false);
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = path;
    params.n_threads = static_cast<int>(threads);
    params.vae_decode_only = true;
    params.enable_mmap = true;
    if (use_gpu) {
        params.diffusion_flash_attn = g_gpu_options.flash_attn;
        params.keep_vae_on_cpu = g_gpu_options.vae_on_cpu;
        params.keep_clip_on_cpu = g_gpu_options.clip_on_cpu;
        params.diffusion_conv_direct = g_gpu_options.conv_direct;
    }
    sd_set_log_callback(log_callback, nullptr);
    sd_ctx_t* context = nullptr;
    try {
        context = new_sd_ctx(&params);
    } catch (const std::exception& e) {
        LOGE("Model load threw: %s", e.what());
        context = nullptr;
    }
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
    if (g_gpu_active.load() && g_gpu_options.vae_tiling) {
        params.vae_tiling_params.enabled = true;
        params.vae_tiling_params.tile_size_x = 32;
        params.vae_tiling_params.tile_size_y = 32;
        params.vae_tiling_params.target_overlap = 0.5f;
    }
    sd_image_t* result = nullptr;
    try {
        result = generate_image(context, &params);
    } catch (const std::exception& e) {
        // A GPU driver error surfaces as a C++ exception from the Vulkan backend;
        // returning no image lets the app redo the picture on the CPU.
        LOGE("Generation threw: %s", e.what());
        result = nullptr;
    }

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

// Describes the phone's first Vulkan GPU as "vendorId:deviceId:name" (ids in hex),
// or returns null when there is none. Uses the system loader directly and never
// touches ggml, so it is cheap and cannot start a model load.
extern "C" JNIEXPORT jstring JNICALL
Java_com_hartmann_pixeldream_diffusion_StableDiffusion_gpuDeviceNative(JNIEnv* env, jobject) {
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return nullptr;
    std::string description;
    auto get_proc = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr"));
    if (get_proc) {
        auto create_instance = reinterpret_cast<PFN_vkCreateInstance>(get_proc(nullptr, "vkCreateInstance"));
        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "PixelDream";
        app.apiVersion = VK_API_VERSION_1_1;
        VkInstanceCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        info.pApplicationInfo = &app;
        VkInstance instance = VK_NULL_HANDLE;
        if (create_instance && create_instance(&info, nullptr, &instance) == VK_SUCCESS) {
            auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(get_proc(instance, "vkEnumeratePhysicalDevices"));
            auto properties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(get_proc(instance, "vkGetPhysicalDeviceProperties"));
            auto destroy = reinterpret_cast<PFN_vkDestroyInstance>(get_proc(instance, "vkDestroyInstance"));
            uint32_t count = 1;
            VkPhysicalDevice device = VK_NULL_HANDLE;
            VkResult listed = enumerate ? enumerate(instance, &count, &device) : VK_ERROR_INITIALIZATION_FAILED;
            if (properties && (listed == VK_SUCCESS || listed == VK_INCOMPLETE) && count > 0 && device != VK_NULL_HANDLE) {
                VkPhysicalDeviceProperties props{};
                properties(device, &props);
                char head[32];
                std::snprintf(head, sizeof(head), "%x:%x:", props.vendorID, props.deviceID);
                description = std::string(head) + props.deviceName;
            }
            if (destroy) destroy(instance, nullptr);
        }
    }
    dlclose(library);
    if (description.empty()) return nullptr;
    return env->NewStringUTF(description.c_str());
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
