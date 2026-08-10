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
import com.pubgcompat.collector.parser.KernelInfoParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Kernel identity from `Build.VERSION` and the world-readable kernel files `/proc/version` and
 * `/proc/sys/kernel/osrelease`. Never requires root.
 */
class KernelCollector : FingerprintCollector {

  override val name: String = "KernelCollector"

  override fun collect(context: Context): CollectorResult {
    val procVersion = ReadOnlyFiles.read("/proc/version")
    val osrelease = ReadOnlyFiles.read("/proc/sys/kernel/osrelease")

    val fromProcVersion = procVersion?.let { KernelInfoParser.parseProcVersion(it) }
    val fromOsrelease = osrelease?.let { KernelInfoParser.parseOsrelease(it) }

    val normalized = buildJsonObject {
      put(
          "kernel_release",
          fromProcVersion?.kernelRelease ?: fromOsrelease?.kernelRelease,
      )
      put(
          "kernel_version",
          fromProcVersion?.kernelVersion ?: fromOsrelease?.kernelVersion,
      )
      put("build_release", Build.VERSION.RELEASE)
      put("build_sdk_int", Build.VERSION.SDK_INT)
      put("build_incremental", Build.VERSION.INCREMENTAL)
      put("build_fingerprint", Build.FINGERPRINT)
    }
    val raw = buildJsonObject {
      put("proc_version", procVersion)
      put("osrelease", osrelease)
    }

    val warnings = buildList {
      if (procVersion == null) add("/proc/version is not readable on this environment")
      if (osrelease == null) add("/proc/sys/kernel/osrelease is not readable on this environment")
    }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status =
            if (normalized["kernel_release"] != null) {
              CollectorStatus.AVAILABLE
            } else {
              CollectorStatus.UNAVAILABLE
            },
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }
}
