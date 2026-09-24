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
import com.pubgcompat.collector.Json
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.parser.ProcInfoParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Kernel-exposed process/system state from world-readable `/proc` files (no root).
 *
 * Raw file text is preserved verbatim per file; normalized fields are extracted by
 * [ProcInfoParser]. Dynamic values (uptime, load averages, free memory) are observations of the
 * moment of capture: the fingerprint hash covers them like any other field, so two captures of the
 * same environment differ here - compare captures with `diff`, not hash equality.
 */
class ProcCollector : FingerprintCollector {

  override val name: String = "ProcCollector"

  private data class Source(val path: String, val text: String?)

  override fun collect(context: Context): CollectorResult {
    val sources =
        listOf(
                "/proc/meminfo",
                "/proc/loadavg",
                "/proc/uptime",
                "/proc/cmdline",
                "/proc/partitions",
                "/proc/mounts")
            .map { Source(it, ReadOnlyFiles.read(it)) }

    val warnings =
        sources.mapNotNull { source ->
          if (source.text == null) "${source.path} is not readable on this environment" else null
        }

    val byPath = sources.associate { it.path to it.text }
    val meminfo = byPath["/proc/meminfo"]?.let(ProcInfoParser::parseMeminfo).orEmpty()
    val loadavg = byPath["/proc/loadavg"]?.let(ProcInfoParser::parseLoadavg)
    val uptime = byPath["/proc/uptime"]?.let(ProcInfoParser::parseUptimeSeconds)
    val cmdlineTokens = byPath["/proc/cmdline"]?.let(ProcInfoParser::parseCmdlineTokens).orEmpty()
    val partitions = byPath["/proc/partitions"]?.let(ProcInfoParser::parsePartitions).orEmpty()
    val mounts = byPath["/proc/mounts"]?.let(ProcInfoParser::parseMountSummary)

    val anyReadable = sources.any { it.text != null }

    val normalized = buildJsonObject {
      put("meminfo", meminfoNormalized(meminfo))
      loadavg?.let { load ->
        put(
            "loadavg",
            buildJsonObject {
              put("load1", Json.value(load.load1))
              put("load5", Json.value(load.load5))
              put("load15", Json.value(load.load15))
              put("runnable_tasks", load.runnableTasks)
              put("total_tasks", load.totalTasks)
              put("last_pid", load.lastPid)
            },
        )
      }
      uptime?.let { put("uptime_seconds", Json.value(it)) }
      put(
          "cmdline_tokens",
          JsonArray(cmdlineTokens.map { JsonPrimitive(it) }),
      )
      put(
          "androidboot",
          buildJsonObject {
            for ((key, value) in ProcInfoParser.parseKeyValues(cmdlineTokens, "androidboot.")) {
              put(key, value)
            }
          },
      )
      put(
          "partitions",
          JsonArray(
              partitions.map { partition ->
                buildJsonObject {
                  put("name", partition.name)
                  put("blocks", partition.blocks)
                }
              }),
      )
      mounts?.let { mount ->
        put(
            "mounts",
            buildJsonObject {
              put(
                  "filesystem_types",
                  JsonArray(mount.filesystemTypes.map { JsonPrimitive(it) }),
              )
              put("mount_count", mount.mountCount)
            },
        )
      }
    }

    val raw = buildJsonObject {
      for (source in sources) {
        put(source.path.removePrefix("/proc/").replace('/', '_'), source.text)
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

  private fun meminfoNormalized(meminfo: Map<String, ProcInfoParser.MeminfoEntry>): JsonObject {
    fun value(key: String): Long? = meminfo[key]?.value
    return buildJsonObject {
      value("MemTotal")?.let { put("total_memory_kb", it) }
      value("MemFree")?.let { put("free_memory_kb", it) }
      value("MemAvailable")?.let { put("available_memory_kb", it) }
      value("Buffers")?.let { put("buffers_kb", it) }
      value("Cached")?.let { put("cached_kb", it) }
      value("SwapTotal")?.let { put("swap_total_kb", it) }
      value("SwapFree")?.let { put("swap_free_kb", it) }
    }
  }
}
