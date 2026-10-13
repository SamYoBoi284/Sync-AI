cmake_minimum_required(VERSION 3.22.1)
project(syncai_native)

include(FetchContent)

# Android NDK ships the Vulkan C headers, but not the Vulkan-Hpp C++ wrapper
# required by llama.cpp's Vulkan backend. Fetch the official Khronos headers
# and add them only to the Vulkan backend's include path.
FetchContent_Declare(
    vulkan_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG v1.3.290
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(vulkan_headers)
if(NOT vulkan_headers_POPULATED)
    FetchContent_Populate(vulkan_headers)
endif()

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "Enable experimental Vulkan GPU backend" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 4a89937
    GIT_SHALLOW TRUE
    # Keep this for clean FetchContent checkouts. The compatibility script is
    # also run after MakeAvailable below because cached source trees may skip
    # PATCH_COMMAND on subsequent Gradle/CMake configure runs.
    PATCH_COMMAND python3 "${CMAKE_CURRENT_SOURCE_DIR}/patches/patch_llama_vulkan_android.py" "<SOURCE_DIR>"
)
FetchContent_MakeAvailable(llama)

# Always apply/verify the patch after FetchContent_MakeAvailable. Gradle's
# native-build cache can retain _deps/llama-src and skip PATCH_COMMAND, which
# otherwise leaves raw Vulkan 1.1 symbols in the object file and breaks linking.
execute_process(
    COMMAND python3 "${CMAKE_CURRENT_SOURCE_DIR}/patches/patch_llama_vulkan_android.py" "${llama_SOURCE_DIR}"
    RESULT_VARIABLE llama_android_patch_result
    COMMAND_ECHO STDOUT
)
if(NOT llama_android_patch_result EQUAL 0)
    message(FATAL_ERROR "Failed to apply/verify Android Vulkan dynamic-dispatch patch")
endif()

# llama.cpp's Vulkan backend also includes the SPIRV-Headers C++ declarations.
# The host package installed by CI satisfies find_package(), but its include
# directory is not propagated to ggml-vulkan during the Android cross-build.
FetchContent_Declare(
    spirv_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
    GIT_TAG vulkan-sdk-1.3.290.0
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(spirv_headers)
if(NOT spirv_headers_POPULATED)
    FetchContent_Populate(spirv_headers)
endif()

if(TARGET ggml-vulkan)
    target_include_directories(ggml-vulkan PRIVATE
        "${vulkan_headers_SOURCE_DIR}/include"
        "${spirv_headers_SOURCE_DIR}/include"
    )
endif()

add_library(syncai_native SHARED syncai_native.cpp)

target_include_directories(syncai_native PRIVATE
    ${llama_SOURCE_DIR}/include
)

target_link_libraries(syncai_native
    llama
    log
)
