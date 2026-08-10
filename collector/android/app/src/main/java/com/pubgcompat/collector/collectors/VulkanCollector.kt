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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Vulkan enumeration via the read-only native shim. Absence of Vulkan is a legitimate observation:
 * status unavailable / failed_to_initialize is recorded, never treated as an error.
 */
class VulkanCollector : FingerprintCollector {

  override val name: String = "VulkanCollector"

  override fun collect(context: Context): CollectorResult {
    val apiVersion =
        try {
          VulkanShim.apiVersion()
        } catch (e: UnsatisfiedLinkError) {
          return result("unavailable", null, listOf("vulkan_shim failed to load: ${e.message}"))
        }

    if (apiVersion == null) {
      return result("unavailable", null, emptyList())
    }

    val layers = safeList { VulkanShim.instanceLayers() }
    val extensions = safeList { VulkanShim.instanceExtensions() }
    val devices = safeList { VulkanShim.physicalDevices() }

    if (devices.isEmpty()) {
      return result(
          "failed_to_initialize",
          apiVersion,
          listOf("instance created but no physical devices enumerated"),
          layers,
          extensions,
      )
    }

    val deviceList =
        devices.map { device ->
          val fields = device.split('|')
          val deviceExtensions =
              runCatching { VulkanShim.deviceExtensions(devices.indexOf(device)) }.getOrNull()
                  ?: emptyArray()
          val queueFamilies =
              runCatching { VulkanShim.queueFamilies(devices.indexOf(device)) }.getOrNull()
                  ?: emptyArray()

          buildJsonObject {
            put("name", fields.getOrNull(0))
            put("vendor_id", fields.getOrNull(1)?.toIntOrNull())
            put("device_id", fields.getOrNull(2)?.toIntOrNull())
            put("driver_version_raw", fields.getOrNull(3)?.toIntOrNull())
            put("api_version", fields.getOrNull(4))
            put("device_type", fields.getOrNull(5))
            put("driver_version", fields.getOrNull(6))
            put(
                "device_extensions",
                JsonArray(
                    deviceExtensions.map { ext ->
                      val parts = ext.split(':')
                      buildJsonObject {
                        put("name", parts.getOrNull(0))
                        put("spec_version", parts.getOrNull(1)?.toIntOrNull())
                      }
                    }),
            )
            put(
                "queue_families",
                JsonArray(
                    queueFamilies.map { family ->
                      val parts = family.split('|')
                      buildJsonObject {
                        put("queue_count", parts.getOrNull(0)?.toIntOrNull())
                        put("flags_raw", parts.getOrNull(1)?.toIntOrNull())
                        put(
                            "flags",
                            JsonArray(
                                decodeQueueFlags(parts.getOrNull(1)?.toIntOrNull()).map {
                                  JsonPrimitive(it)
                                },
                            ),
                        )
                        put("timestamp_valid_bits", parts.getOrNull(2)?.toIntOrNull())
                        put(
                            "min_image_transfer_granularity",
                            JsonArray(
                                listOf(3, 4, 5).map { idx ->
                                  parts.getOrNull(idx)?.toIntOrNull()?.let { JsonPrimitive(it) }
                                      ?: JsonPrimitive(0)
                                },
                            ),
                        )
                      }
                    }),
            )
          }
        }

    return result("available", apiVersion, emptyList(), layers, extensions, deviceList)
  }

  private fun safeList(block: () -> Array<String>?): List<String> =
      try {
        block()?.toList() ?: emptyList()
      } catch (_: Throwable) {
        emptyList()
      }

  private fun decodeQueueFlags(flags: Int?): List<String> {
    if (flags == null) return emptyList()
    val names =
        listOf(
            0x1 to "graphics",
            0x2 to "compute",
            0x4 to "transfer",
            0x8 to "sparse_binding",
            0x10 to "protected",
        )
    return names.filter { (bit, _) -> flags and bit != 0 }.map { it.second }
  }

  private fun result(
      status: String,
      apiVersion: String?,
      errors: List<String>,
      layers: List<String> = emptyList(),
      extensions: List<String> = emptyList(),
      devices: List<JsonObject> = emptyList(),
  ): CollectorResult {
    val data = buildJsonObject {
      put("vulkan_status", status)
      put("api_version", apiVersion)
      put(
          "instance_layers",
          JsonArray(
              layers.map { layer ->
                val parts = layer.split(':')
                buildJsonObject {
                  put("name", parts.getOrNull(0))
                  put("implementation_version", parts.getOrNull(1)?.toIntOrNull())
                  put("spec_version", parts.getOrNull(2))
                }
              }),
      )
      put(
          "instance_extensions",
          JsonArray(
              extensions.map { ext ->
                val parts = ext.split(':')
                buildJsonObject {
                  put("name", parts.getOrNull(0))
                  put("spec_version", parts.getOrNull(1)?.toIntOrNull())
                }
              }),
      )
      put("physical_devices", JsonArray(devices))
    }
    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status =
            when (status) {
              "available" -> CollectorStatus.AVAILABLE
              "unavailable" -> CollectorStatus.UNAVAILABLE
              else -> CollectorStatus.UNAVAILABLE
            },
        data =
            buildJsonObject {
              put("raw", buildJsonObject { put("vulkan_status", status) })
              put("normalized", data)
            },
        errors = errors,
    )
  }
}
