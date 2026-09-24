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
import com.pubgcompat.collector.parser.SysInfoParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * CPU/node topology from world-readable `/sys` files, plus DMI identity where the environment
 * exposes it (x86 virtual machines usually do, ARM devices rarely do).
 */
class SysCollector : FingerprintCollector {

  override val name: String = "SysCollector"

  private data class Source(val path: String, val text: String?)

  override fun collect(context: Context): CollectorResult {
    val sources =
        listOf(
                "/sys/devices/system/cpu/possible",
                "/sys/devices/system/cpu/present",
                "/sys/devices/system/cpu/online",
                "/sys/devices/system/node/possible",
                "/sys/devices/virtual/dmi/id/sys_vendor",
                "/sys/devices/virtual/dmi/id/product_name",
                "/sys/devices/virtual/dmi/id/board_vendor",
            )
            .map { Source(it, ReadOnlyFiles.read(it)) }

    fun path(suffix: String): String? = sources.firstOrNull { it.path.endsWith(suffix) }?.text

    val cpuPossible = path("/cpu/possible")
    val cpuPresent = path("/cpu/present")
    val cpuOnline = path("/cpu/online")
    val nodePossible = path("/node/possible")

    val warnings =
        sources.mapNotNull { source ->
          if (source.text == null) "${source.path} is not readable on this environment" else null
        }
    val anyReadable = sources.any { it.text != null }

    val normalized = buildJsonObject {
      put("cpu_possible", cpuPossible)
      put("cpu_present", cpuPresent)
      put("cpu_online", cpuOnline)
      put("cpu_possible_count", SysInfoParser.count(cpuPossible ?: ""))
      put("cpu_present_count", SysInfoParser.count(cpuPresent ?: ""))
      put("cpu_online_count", SysInfoParser.count(cpuOnline ?: ""))
      put("node_possible", nodePossible)
      put(
          "dmi",
          buildJsonObject {
            put("sys_vendor", path("/id/sys_vendor"))
            put("product_name", path("/id/product_name"))
            put("board_vendor", path("/id/board_vendor"))
          },
      )
    }

    val raw = buildJsonObject {
      for (source in sources) {
        put(source.path, source.text)
      }
    }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = if (anyReadable) CollectorStatus.AVAILABLE else CollectorStatus.UNAVAILABLE,
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }
}
