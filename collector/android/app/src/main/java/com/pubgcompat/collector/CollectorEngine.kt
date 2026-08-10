/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Runs every collector independently and assembles the fingerprint document.
 *
 * A failing collector never aborts the run: it is recorded with status COLLECTION_ERROR and
 * collection continues.
 *
 * Document shape (mirrors schema/fingerprint.schema.json):
 *
 * { "schema_version": "1.0", "collector_version": "0.1.0", "timestamp": "...", "environment": {
 * "label": "waydroid" }, "build": { "collector": "...", "timestamp": "...", "status": "...",
 * "errors": [], "warnings": [], "data": { ... } }, ...one section per collector...
 * "fingerprint_sha256": "..." }
 */
class CollectorEngine(
    private val context: Context,
    private val environmentLabel: String,
    private val collectors: LinkedHashMap<String, FingerprintCollector>,
) {

  companion object {
    const val SCHEMA_VERSION = "1.0"
    const val COLLECTOR_VERSION = "0.1.0"
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /** Section key order matches schema/fingerprint.schema.json. */
    val SECTION_ORDER: List<String> =
        listOf(
            "build",
            "abi",
            "cpu",
            "gpu",
            "vulkan",
            "kernel",
            "proc",
            "sys",
            "hardware_features",
            "sensors",
            "display",
            "network",
            "telephony",
            "storage",
            "security",
            "environment_signals",
        )
  }

  /** Runs all collectors. Returns (document, fingerprint_sha256). */
  suspend fun run(): Pair<JsonObject, String> {
    val results = LinkedHashMap<String, CollectorResult>()
    for ((key, collector) in collectors) {
      results[key] = runOne(collector)
    }
    val document = buildJsonObject {
      put("schema_version", SCHEMA_VERSION)
      put("collector_version", COLLECTOR_VERSION)
      put("timestamp", nowIso())
      put("environment", buildJsonObject { put("label", environmentLabel) })
      for ((key, result) in results) {
        put(key, resultToJson(result))
      }
      put("collector_status_summary", buildStatusSummary(results))
    }
    val hash = FingerprintHash.fingerprintSha256(document)
    return JsonObject(document.toMap() + ("fingerprint_sha256" to JsonPrimitive(hash))) to hash
  }

  private suspend fun runOne(collector: FingerprintCollector): CollectorResult {
    val start = nowIso()
    return try {
      withTimeout(DEFAULT_TIMEOUT_MS) { withContext(Dispatchers.IO) { collector.collect(context) } }
    } catch (e: TimeoutCancellationException) {
      CollectorResult(
          collector = collector.name,
          timestamp = start,
          status = CollectorStatus.COLLECTION_ERROR,
          data = JsonObject(emptyMap()),
          errors = listOf("collector exceeded ${DEFAULT_TIMEOUT_MS}ms timeout"),
      )
    } catch (e: Throwable) {
      CollectorResult(
          collector = collector.name,
          timestamp = start,
          status = CollectorStatus.COLLECTION_ERROR,
          data = JsonObject(emptyMap()),
          errors = listOf("${e.javaClass.simpleName}: ${e.message.orEmpty()}"),
      )
    }
  }

  private fun resultToJson(result: CollectorResult): JsonObject = buildJsonObject {
    put("collector", result.collector)
    put("timestamp", result.timestamp)
    put("status", result.status.name.lowercase())
    put("errors", JsonArray(result.errors.map { JsonPrimitive(it) }))
    put("warnings", JsonArray(result.warnings.map { JsonPrimitive(it) }))
    put("data", result.data)
  }

  private fun buildStatusSummary(results: Map<String, CollectorResult>): JsonObject =
      buildJsonObject {
        for (key in SECTION_ORDER) {
          val result = results[key] ?: continue
          put(key, result.status.name.lowercase())
        }
      }
}
