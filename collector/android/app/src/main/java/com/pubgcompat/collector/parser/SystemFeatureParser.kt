/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin normalization of `PackageManager.getSystemAvailableFeatures()` output.
 *
 * The canonical subset in [CANONICAL_FEATURES] is a fixed, ordered list of (matrix key, Android
 * feature name) pairs so the normalized `has` object has a stable key order across environments.
 */
object SystemFeatureParser {

  private val CANONICAL_FEATURES: List<Pair<String, String>> =
      listOf(
          "touchscreen" to "android.hardware.touchscreen",
          "wifi" to "android.hardware.wifi",
          "wifi_direct" to "android.hardware.wifi.direct",
          "bluetooth" to "android.hardware.bluetooth",
          "bluetooth_le" to "android.hardware.bluetooth_le",
          "camera" to "android.hardware.camera.any",
          "camera_autofocus" to "android.hardware.camera.autofocus",
          "microphone" to "android.hardware.microphone",
          "accelerometer" to "android.hardware.sensor.accelerometer",
          "gyroscope" to "android.hardware.sensor.gyroscope",
          "compass" to "android.hardware.sensor.compass",
          "barometer" to "android.hardware.sensor.barometer",
          "light" to "android.hardware.sensor.light",
          "proximity" to "android.hardware.sensor.proximity",
          "location" to "android.hardware.location",
          "gps" to "android.hardware.location.gps",
          "nfc" to "android.hardware.nfc",
          "usb_host" to "android.hardware.usb.host",
          "ethernet" to "android.hardware.ethernet",
          "fingerprint" to "android.hardware.fingerprint",
          "face" to "android.hardware.biometrics.face",
          "telephony" to "android.hardware.telephony",
          "leanback" to "android.software.leanback",
          "live_tv" to "android.software.live_tv",
          "vr" to "android.hardware.vr.high_performance",
          "gamepad" to "android.hardware.gamepad",
          "low_ram" to "android.hardware.low_ram",
          "opengles_aep" to "android.hardware.opengles.aep",
      )

  /** Deduplicated, trimmed, sorted feature names. Nulls and blanks are dropped. */
  fun normalizeNames(raw: Collection<String?>?): List<String> {
    if (raw == null) return emptyList()
    return raw.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
  }

  /** Fixed-order map of canonical matrix key -> whether the feature is declared present. */
  fun featureMatrix(names: List<String>): Map<String, Boolean> {
    val present = names.toSet()
    val out = LinkedHashMap<String, Boolean>()
    for ((key, feature) in CANONICAL_FEATURES) {
      out[key] = feature in present
    }
    return out
  }

  /**
   * Formats a `ConfigurationInfo.glEsVersion` int as `major.minor` (high 16 bits / low 16 bits),
   * e.g. `0x00020000` -> `"2.0"`, `0x00030002` -> `"3.2"`.
   */
  fun glesVersionString(version: Int): String {
    val major = version ushr 16
    val minor = version and 0xFFFF
    return "$major.$minor"
  }
}
