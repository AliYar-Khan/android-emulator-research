/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.util.ReadOnlyFiles
import com.pubgcompat.collector.util.SystemPropertiesReader
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Security-relevant environment state observable without any permission: SELinux enforce mode,
 * build signing tags, whether this collector runs debuggable, and an allowlist of read-only
 * `getprop` values related to verified boot / debugging.
 *
 * Observations only - nothing is measured about other applications, accounts, or attestation.
 */
class SecurityCollector : FingerprintCollector {

  override val name: String = "SecurityCollector"

  private companion object {
    val PROP_KEYS: List<String> =
        listOf(
            "ro.debuggable",
            "ro.secure",
            "ro.adb.secure",
            "ro.boot.verifiedbootstate",
            "ro.boot.flash.locked",
        )
  }

  override fun collect(context: Context): CollectorResult {
    val warnings = mutableListOf<String>()

    val selinuxRaw = ReadOnlyFiles.read("/sys/fs/selinux/enforce")
    if (selinuxRaw == null) {
      warnings.add("/sys/fs/selinux/enforce is not readable on this environment")
    }
    val selinuxState =
        when (selinuxRaw?.trim()) {
          "1" -> "enforcing"
          "0" -> "permissive"
          null -> null
          else -> selinuxRaw.trim()
        }

    val props = SystemPropertiesReader.readAll()
    if (props.isEmpty()) {
      warnings.add("getprop returned no properties")
    }
    val picked = SystemPropertiesReader.pick(props, PROP_KEYS)

    val buildTags = Build.TAGS
    val testKeys = buildTags?.contains("test-keys", ignoreCase = true) == true
    val collectorDebuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val normalized = buildJsonObject {
      put("selinux_enforce", selinuxState)
      put("selinux_readable", selinuxRaw != null)
      put("test_keys", testKeys)
      put("collector_debuggable", collectorDebuggable)
      put("build_tags", buildTags)
      put(
          "props",
          buildJsonObject {
            for ((key, value) in picked) {
              put(key, value)
            }
          },
      )
    }

    val raw = buildJsonObject {
      put("selinux_raw", selinuxRaw)
      put("build_tags", buildTags)
      put(
          "props",
          buildJsonObject {
            for ((key, value) in picked) {
              put(key, JsonPrimitive(value ?: "unavailable"))
            }
          },
      )
    }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = CollectorStatus.AVAILABLE,
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }
}
