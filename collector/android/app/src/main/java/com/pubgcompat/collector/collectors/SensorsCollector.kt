/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.Json
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sensor inventory from the public `SensorManager` API. No permission is required to list sensors.
 * An empty list is a legitimate observation (common on emulators), not an error.
 *
 * Entries are sorted by (type, name, vendor) so the normalized list hashes deterministically.
 */
class SensorsCollector : FingerprintCollector {

  override val name: String = "SensorsCollector"

  private companion object {
    /** Default-sensor presence matrix; fixed order for stable normalized output. */
    val DEFAULT_SENSOR_TYPES: List<Pair<String, Int>> =
        listOf(
            "accelerometer" to Sensor.TYPE_ACCELEROMETER,
            "gyroscope" to Sensor.TYPE_GYROSCOPE,
            "magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
            "light" to Sensor.TYPE_LIGHT,
            "proximity" to Sensor.TYPE_PROXIMITY,
            "barometer" to Sensor.TYPE_PRESSURE,
            "step_counter" to Sensor.TYPE_STEP_COUNTER,
            "significant_motion" to Sensor.TYPE_SIGNIFICANT_MOTION,
        )
  }

  override fun collect(context: Context): CollectorResult {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    if (sensorManager == null) {
      return CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.UNAVAILABLE,
          data = buildJsonObject {},
          warnings = listOf("SensorManager service is not available"),
      )
    }

    val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
    val sorted = sensors.sortedWith(compareBy({ it.type }, { it.name }, { it.vendor }))

    val raw = buildJsonObject {
      put(
          "sensors",
          JsonArray(
              sensors.map { sensor ->
                buildJsonObject {
                  put("name", sensor.name)
                  put("vendor", sensor.vendor)
                  put("version", sensor.version)
                  put("type", sensor.type)
                  put("string_type", sensor.stringType)
                }
              }),
      )
    }

    val normalized = buildJsonObject {
      put("count", sensors.size)
      put(
          "sensors",
          JsonArray(sorted.map { sensor -> sensorEntry(sensor) }),
      )
      put("default_sensors", defaultSensors(sensorManager))
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
    )
  }

  private fun sensorEntry(sensor: Sensor): JsonObject = buildJsonObject {
    put("type", sensor.type)
    put("string_type", sensor.stringType)
    put("name", sensor.name)
    put("vendor", sensor.vendor)
    put("version", sensor.version)
    put("power", Json.value(sensor.power))
    put("max_range", Json.value(sensor.maximumRange))
    put("resolution", Json.value(sensor.resolution))
    put("min_delay", sensor.minDelay)
    put("max_delay", sensor.maxDelay)
    put("reporting_mode", sensor.reportingMode)
    put("is_wakeup", sensor.isWakeUpSensor)
  }

  private fun defaultSensors(sensorManager: SensorManager): JsonObject = buildJsonObject {
    for ((key, type) in DEFAULT_SENSOR_TYPES) {
      put(key, sensorManager.getDefaultSensor(type) != null)
    }
  }
}
