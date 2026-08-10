# Vulkan shim: loads libvulkan at runtime via dlopen and exposes a minimal,
# read-only enumeration surface to Kotlin. No modification of the device,
# no writes, no privileged operations.

cmake_minimum_required(VERSION 3.22.1)
project(vulkan_shim LANGUAGES CXX)

add_library(vulkan_shim SHARED vulkan_shim.cpp)

target_compile_features(vulkan_shim PRIVATE cxx_std_17)

find_path(VULKAN_INCLUDE_DIR vulkan/vulkan.h
    PATHS ${ANDROID_NDK}/toolchains/llvm/prebuilt/*/sysroot/usr/include)
target_include_directories(vulkan_shim PRIVATE ${VULKAN_INCLUDE_DIR})
