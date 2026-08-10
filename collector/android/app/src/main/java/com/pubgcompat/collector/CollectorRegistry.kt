/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import com.pubgcompat.collector.collectors.AbiCollector
import com.pubgcompat.collector.collectors.BuildCollector
import com.pubgcompat.collector.collectors.CpuCollector
import com.pubgcompat.collector.collectors.DisplayCollector
import com.pubgcompat.collector.collectors.GpuCollector
import com.pubgcompat.collector.collectors.KernelCollector
import com.pubgcompat.collector.collectors.VulkanCollector

/**
 * Central registry of collectors, in deterministic document order matching
 * schema/fingerprint.schema.json.
 */
object CollectorRegistry {

  fun all(): LinkedHashMap<String, FingerprintCollector> =
      linkedMapOf(
          "build" to BuildCollector(),
          "abi" to AbiCollector(),
          "cpu" to CpuCollector(),
          "gpu" to GpuCollector(),
          "vulkan" to VulkanCollector(),
          "kernel" to KernelCollector(),
          "display" to DisplayCollector(),
      )
}
