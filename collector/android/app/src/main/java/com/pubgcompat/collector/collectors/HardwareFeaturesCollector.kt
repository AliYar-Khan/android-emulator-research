/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.app.ActivityManager
import android.content.Context
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.parser.SystemFeatureParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Declared system features from the public `PackageManager` API plus the OpenGL ES version the
 * configuration reports. Both are read-only queries requiring no permission.
 */
class HardwareFeaturesCollector : FingerprintCollector {

  override val name: String = "HardwareFeaturesCollector"

  override fun collect(context: Context): CollectorResult {
    val featureNames =
        try {
          context.packageManager.systemAvailableFeatures.mapNotNull { it.name }
        } catch (e: SecurityException) {
          return CollectorResult(
              collector = name,
              timestamp = nowIso(),
              status = CollectorStatus.PERMISSION_DENIED,
              data = buildJsonObject {},
              errors = listOf("package manager denied feature enumeration: ${e.message}"),
          )
        }

    val glEsVersion =
        try {
          (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
              ?.deviceConfigurationInfo
              ?.reqGlEsVersion
        } catch (e: SecurityException) {
          null
        }

    val normalizedNames = SystemFeatureParser.normalizeNames(featureNames)
    val matrix = SystemFeatureParser.featureMatrix(normalizedNames)

    val normalized = buildJsonObject {
      put("feature_count", normalizedNames.size)
      put("features", JsonArray(normalizedNames.map { JsonPrimitive(it) }))
      glEsVersion?.let { put("gles_version", SystemFeatureParser.glesVersionString(it)) }
      put(
          "has",
          buildJsonObject {
            for ((key, present) in matrix) {
              put(key, present)
            }
          },
      )
    }

    val raw = buildJsonObject {
      put(
          "feature_names",
          JsonArray(featureNames.map { JsonPrimitive(it) }),
      )
      glEsVersion?.let { put("gles_version_raw", it) }
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
        warnings =
            if (glEsVersion == null) {
              listOf("ActivityManager.deviceConfigurationInfo unavailable")
            } else {
              emptyList()
            },
    )
  }
}
