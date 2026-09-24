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
import com.pubgcompat.collector.collectors.EnvironmentSignalsCollector
import com.pubgcompat.collector.collectors.GpuCollector
import com.pubgcompat.collector.collectors.HardwareFeaturesCollector
import com.pubgcompat.collector.collectors.KernelCollector
import com.pubgcompat.collector.collectors.NetworkCollector
import com.pubgcompat.collector.collectors.ProcCollector
import com.pubgcompat.collector.collectors.SecurityCollector
import com.pubgcompat.collector.collectors.SensorsCollector
import com.pubgcompat.collector.collectors.StorageCollector
import com.pubgcompat.collector.collectors.SysCollector
import com.pubgcompat.collector.collectors.TelephonyCollector
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
          "proc" to ProcCollector(),
          "sys" to SysCollector(),
          "hardware_features" to HardwareFeaturesCollector(),
          "sensors" to SensorsCollector(),
          "display" to DisplayCollector(),
          "network" to NetworkCollector(),
          "telephony" to TelephonyCollector(),
          "storage" to StorageCollector(),
          "security" to SecurityCollector(),
          "environment_signals" to EnvironmentSignalsCollector(),
      )
}
