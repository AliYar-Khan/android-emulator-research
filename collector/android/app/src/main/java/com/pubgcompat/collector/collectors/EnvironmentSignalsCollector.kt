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
import com.pubgcompat.collector.parser.EnvironmentMarkerParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import com.pubgcompat.collector.util.SystemPropertiesReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Environment-signal probes: file-existence checks for known virtualization artifacts, an allowlist
 * of read-only `getprop` values, and substring markers searched across Build identity,
 * `/proc/cmdline`, `/proc/1/cgroup` and the property values.
 *
 * A probe returning `false` means "absent or unreadable" (this collector never needs root). Markers
 * are evidence, not verdicts: raw cgroup/cmdline/prop values are recorded alongside the flags so
 * every conclusion can be re-checked from the document alone.
 */
class EnvironmentSignalsCollector : FingerprintCollector {

  override val name: String = "EnvironmentSignalsCollector"

  private companion object {
    val PROBE_PATHS: List<String> =
        listOf(
            "/dev/qemu_pipe",
            "/dev/socket/qemud",
            "/dev/goldfish_pipe",
            "/dev/vhost-vsock",
            "/system/lib/libhoudini.so",
            "/system/lib64/libhoudini.so",
        )

    val PROP_KEYS: List<String> =
        listOf(
            "ro.kernel.qemu",
            "ro.kernel.qemu.gles",
            "ro.hardware",
            "ro.hardware.egl",
            "ro.boot.hardware",
            "ro.dalvik.vm.native.bridge",
            "ro.enable.native.bridge.exec",
            "ro.build.characteristics",
            "ro.build.waydroid.version",
        )
  }

  override fun collect(context: Context): CollectorResult {
    val warnings = mutableListOf<String>()

    val probes = LinkedHashMap<String, Boolean>()
    for (path in PROBE_PATHS) {
      probes[path] = ReadOnlyFiles.accessible(path)
    }

    val props = SystemPropertiesReader.readAll()
    if (props.isEmpty()) {
      warnings.add("getprop returned no properties")
    }
    val picked = SystemPropertiesReader.pick(props, PROP_KEYS)

    val cgroup = ReadOnlyFiles.read("/proc/1/cgroup")
    val cmdline = ReadOnlyFiles.read("/proc/cmdline")

    val markers =
        EnvironmentMarkerParser.detect(
            buildList {
              add(Build.FINGERPRINT)
              add(Build.HARDWARE)
              add(Build.PRODUCT)
              add(Build.DEVICE)
              add(Build.BOARD)
              add(cmdline)
              add(cgroup)
              addAll(picked.values)
            },
        )

    val normalized = buildJsonObject {
      put(
          "probes",
          buildJsonObject {
            for ((path, present) in probes) {
              put(path, present)
            }
          },
      )
      put(
          "props",
          buildJsonObject {
            for ((key, value) in picked) {
              put(key, value)
            }
          },
      )
      put(
          "markers",
          buildJsonObject {
            for ((marker, present) in markers) {
              put(marker, present)
            }
          },
      )
      put("cgroup_readable", cgroup != null)
      put("cmdline_readable", cmdline != null)
    }

    val raw = buildJsonObject {
      put("cgroup", cgroup)
      put("cmdline", cmdline)
      put(
          "prop_values",
          JsonArray(
              picked.map { (key, value) ->
                buildJsonObject {
                  put("name", key)
                  put("value", JsonPrimitive(value ?: "unavailable"))
                }
              }),
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
