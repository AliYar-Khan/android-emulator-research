/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Lifecycle state of a single collector run.
 *
 * `AVAILABLE` - the collector ran and produced observations. `UNAVAILABLE` - the capability is
 * absent on this environment (e.g. no Vulkan, no sensors). This is a legitimate observation, NOT an
 * error and NOT the same as `false`. `PERMISSION_DENIED`- Android withheld information the
 * collector asked for. `COLLECTION_ERROR` - the collector crashed or timed out. `UNKNOWN` - the
 * collector could not determine anything.
 */
enum class CollectorStatus {
  AVAILABLE,
  UNAVAILABLE,
  PERMISSION_DENIED,
  COLLECTION_ERROR,
  UNKNOWN,
}

/** Result of one collector run. `data` is an ordered JSON object. */
class CollectorResult(
    val collector: String,
    val timestamp: String,
    val status: CollectorStatus,
    val data: JsonObject,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Common interface implemented by every fingerprint collector. */
interface FingerprintCollector {
  val name: String

  fun collect(context: Context): CollectorResult
}

/** Ordered-JSON building helpers shared by all collectors. */
object Json {

  fun obj(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

  fun value(v: Any?): JsonElement =
      when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Float -> JsonPrimitive(finiteToString(v.toDouble()))
        is Double -> JsonPrimitive(finiteToString(v))
        else -> JsonPrimitive(v.toString())
      }

  /**
   * Canonical numeric rendering used everywhere a float would otherwise appear: 9 significant
   * digits, matching Python's "%.9g" formatting so the on-device canonical hash agrees with the
   * host-side hash. Integers are left untouched; non-finite values never enter fingerprint data.
   */
  private fun finiteToString(v: Double): String =
      if (v.isFinite()) {
        String.format(java.util.Locale.ROOT, "%.9g", v)
      } else {
        "null"
      }
}

fun nowIso(): String =
    // java.time.Instant would require API 26 (or core desugaring); SimpleDateFormat
    // is API 1 and yields RFC 3339 UTC with millisecond precision.
    java.text
        .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date())
