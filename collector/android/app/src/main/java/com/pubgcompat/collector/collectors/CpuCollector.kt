/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.parser.CpuInfoParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * CPU characteristics from `/proc/cpuinfo` (world-readable, no root). Raw text is preserved;
 * normalized fields are extracted by a format-agnostic parser that tolerates ARM, x86 and
 * virtualized layouts.
 */
class CpuCollector : FingerprintCollector {

  override val name: String = "CpuCollector"

  override fun collect(context: Context): CollectorResult {
    val rawText = ReadOnlyFiles.read("/proc/cpuinfo")
    if (rawText == null) {
      return CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.UNAVAILABLE,
          data = buildJsonObject { put("normalized", buildJsonObject {}) },
          warnings = listOf("/proc/cpuinfo is not readable on this environment"),
      )
    }
    val parsed = CpuInfoParser.parse(rawText)
    val normalized = buildJsonObject {
      put("architecture", parsed.architecture)
      put("model", parsed.model)
      put("processor", parsed.processor)
      put("cpu_implementer", parsed.cpuImplementer)
      put("cpu_variant", parsed.cpuVariant)
      put("cpu_part", parsed.cpuPart)
      put("vendor_id", parsed.vendorId)
      put(
          "features",
          JsonArray(parsed.features.map { JsonPrimitive(it) }),
      )
      put("core_count", parsed.coreCount)
    }
    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = CollectorStatus.AVAILABLE,
        data =
            buildJsonObject {
              put("raw", buildJsonObject { put("cpuinfo", rawText) })
              put("normalized", normalized)
            },
    )
  }
}
