/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.os.Build
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Supported ABIs from [android.os.Build] plus runtime architecture information exposed through the
 * standard Java API surface.
 */
class AbiCollector : FingerprintCollector {

  override val name: String = "AbiCollector"

  override fun collect(context: Context): CollectorResult {
    val normalized = buildJsonObject {
      put("supported_abis", JsonArray(Build.SUPPORTED_ABIS.map { JsonPrimitive(it) }))
      put("supported_32_bit_abis", JsonArray(Build.SUPPORTED_32_BIT_ABIS.map { JsonPrimitive(it) }))
      put("supported_64_bit_abis", JsonArray(Build.SUPPORTED_64_BIT_ABIS.map { JsonPrimitive(it) }))
      put("runtime_os_arch", System.getProperty("os.arch"))
      put("runtime_java_vm", System.getProperty("java.vm.name"))
      put("runtime_java_vm_version", System.getProperty("java.vm.version"))
    }
    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = CollectorStatus.AVAILABLE,
        data = buildJsonObject { put("normalized", normalized) },
    )
  }
}
