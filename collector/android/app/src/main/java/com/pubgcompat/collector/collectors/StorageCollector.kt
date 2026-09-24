/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.os.StatFs
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * App-visible storage volumes via `StatFs` (no permission for the app's own directories).
 *
 * Free/available byte counts are observations of capture time and fluctuate between runs; compare
 * captures with `diff`. Total capacity is stable per environment.
 */
class StorageCollector : FingerprintCollector {

  override val name: String = "StorageCollector"

  private data class Target(val role: String, val path: String)

  override fun collect(context: Context): CollectorResult {
    val targets = mutableListOf<Target>()
    context.filesDir?.let { targets.add(Target("internal", it.path)) }
    context.cacheDir?.let { targets.add(Target("cache", it.path)) }
    val externalDirs = context.getExternalFilesDirs(null) ?: emptyArray()
    externalDirs.filterNotNull().forEachIndexed { index, dir ->
      targets.add(Target("external_$index", dir.path))
    }

    val warnings = mutableListOf<String>()

    data class Measured(val target: Target, val stat: StatFs)

    val measured =
        targets.mapNotNull { target ->
          try {
            Measured(target, StatFs(target.path))
          } catch (e: IllegalArgumentException) {
            warnings.add("path '${target.path}' is not stat-able: ${e.message}")
            null
          }
        }

    if (measured.isEmpty()) {
      warnings.add("no measurable storage volume")
    }

    val normalized = buildJsonObject {
      put(
          "volumes",
          JsonArray(
              measured.map { entry ->
                buildJsonObject {
                  put("role", entry.target.role)
                  put("path", entry.target.path)
                  put("total_bytes", entry.stat.totalBytes)
                  put("free_bytes", entry.stat.freeBytes)
                  put("available_bytes", entry.stat.availableBytes)
                }
              }),
      )
    }

    val raw = buildJsonObject {
      put(
          "volumes",
          JsonArray(
              measured.map { entry ->
                buildJsonObject {
                  put("role", entry.target.role)
                  put("path", entry.target.path)
                  put("total_blocks", entry.stat.blockCount.toLong())
                  put("free_blocks", entry.stat.freeBlocks.toLong())
                  put("available_blocks", entry.stat.availableBlocks.toLong())
                  put("block_size", entry.stat.blockSize.toLong())
                }
              }),
      )
    }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status =
            if (measured.isNotEmpty()) CollectorStatus.AVAILABLE else CollectorStatus.UNAVAILABLE,
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }
}
