/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

/**
 * JNI entry points into the read-only Vulkan enumeration shim (src/main/cpp/vulkan_shim.cpp). All
 * methods return null when the loader or the requested data is unavailable; none of them perform
 * writes or require privileges.
 */
object VulkanShim {

  init {
    System.loadLibrary("vulkan_shim")
  }

  external fun apiVersion(): String?

  external fun instanceLayers(): Array<String>?

  external fun instanceExtensions(): Array<String>?

  external fun physicalDevices(): Array<String>?

  external fun deviceExtensions(deviceIndex: Int): Array<String>?

  external fun queueFamilies(deviceIndex: Int): Array<String>?
}
