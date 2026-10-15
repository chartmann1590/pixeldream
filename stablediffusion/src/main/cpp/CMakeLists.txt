cmake_minimum_required(VERSION 3.22.1)
project(pixeldream_diffusion)

set(SD_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../stable-diffusion.cpp")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_CUDA OFF CACHE BOOL "" FORCE)
set(SD_METAL OFF CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(SD_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# ---------------------------------------------------------------------------
# GPU acceleration (ggml Vulkan backend).
#
# The same library carries both the Vulkan and the CPU backend. Which one a model
# loads on is decided at runtime: the app sets PIXELDREAM_FORCE_CPU=1 before
# loading when the GPU is switched off or has failed on this phone, and the
# patch below makes stable-diffusion.cpp skip Vulkan entirely in that case
# (it never even creates a Vulkan instance, so a broken driver cannot crash
# the CPU path).
# ---------------------------------------------------------------------------
option(PIXELDREAM_GPU "Build the Vulkan GPU backend" ON)
if(PIXELDREAM_GPU)
    set(SD_VULKAN ON CACHE BOOL "" FORCE)

    # glslc ships with the NDK.
    if(NOT Vulkan_GLSLC_EXECUTABLE)
        file(GLOB _pd_glslc
            "${ANDROID_NDK}/shader-tools/*/glslc"
            "${ANDROID_NDK}/shader-tools/*/glslc.exe")
        if(_pd_glslc)
            list(GET _pd_glslc 0 _pd_glslc_first)
            set(Vulkan_GLSLC_EXECUTABLE "${_pd_glslc_first}" CACHE FILEPATH "glslc from the NDK" FORCE)
        endif()
    endif()
    message(STATUS "PixelDream: glslc = ${Vulkan_GLSLC_EXECUTABLE}")

    # The NDK sysroot has the C Vulkan headers only; ggml-vulkan needs vulkan.hpp
    # and current struct definitions, so use the Khronos headers. The library
    # itself (libvulkan.so) still comes from the NDK sysroot.
    if(NOT Vulkan_INCLUDE_DIR OR NOT EXISTS "${Vulkan_INCLUDE_DIR}/vulkan/vulkan.hpp")
        include(FetchContent)
        FetchContent_Declare(pd_vulkan_headers
            URL https://github.com/KhronosGroup/Vulkan-Headers/archive/refs/tags/v1.4.341.tar.gz)
        FetchContent_GetProperties(pd_vulkan_headers)
        if(NOT pd_vulkan_headers_POPULATED)
            FetchContent_Populate(pd_vulkan_headers)
        endif()
        set(Vulkan_INCLUDE_DIR "${pd_vulkan_headers_SOURCE_DIR}/include" CACHE PATH "Vulkan headers" FORCE)
    endif()
    message(STATUS "PixelDream: Vulkan headers = ${Vulkan_INCLUDE_DIR}")

    # Runtime CPU override (idempotent source patch).
    set(_pd_sd_cpp "${SD_ROOT}/src/stable-diffusion.cpp")
    file(READ "${_pd_sd_cpp}" _pd_sd_src)
    string(FIND "${_pd_sd_src}" "PIXELDREAM_FORCE_CPU" _pd_patched)
    if(_pd_patched EQUAL -1)
        set(_pd_old "const int device_count = ggml_backend_vk_get_device_count();")
        set(_pd_new "const char* pd_force_cpu = getenv(\"PIXELDREAM_FORCE_CPU\");\n        const int device_count = (pd_force_cpu != nullptr && pd_force_cpu[0] == '1') ? 0 : ggml_backend_vk_get_device_count();")
        string(FIND "${_pd_sd_src}" "${_pd_old}" _pd_at)
        if(_pd_at EQUAL -1)
            message(FATAL_ERROR "PixelDream: could not find the Vulkan device query in stable-diffusion.cpp; update the CPU override patch for this stable-diffusion.cpp version")
        endif()
        string(REPLACE "${_pd_old}" "${_pd_new}" _pd_sd_src "${_pd_sd_src}")
        file(WRITE "${_pd_sd_cpp}" "${_pd_sd_src}")
        message(STATUS "PixelDream: applied runtime CPU override to stable-diffusion.cpp")
    endif()
else()
    set(SD_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${SD_ROOT} sd_build)
add_library(pixeldream_diffusion SHARED StableDiffusionJni.cpp)
target_include_directories(pixeldream_diffusion PRIVATE ${SD_ROOT}/include)
if(PIXELDREAM_GPU)
    target_compile_definitions(pixeldream_diffusion PRIVATE PIXELDREAM_GPU=1)
endif()
target_compile_options(pixeldream_diffusion PRIVATE
    -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections -O3)
target_link_libraries(pixeldream_diffusion android log stable-diffusion ggml)
target_link_options(pixeldream_diffusion PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
