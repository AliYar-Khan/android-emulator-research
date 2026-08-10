/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.Json
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Display characteristics via the public DisplayManager / Display APIs. Units: resolution in
 * pixels, density in dpi (integer), refresh rate as canonical numeric string. Color modes require
 * the hidden ColorManager API and are deliberately not queried; HDR support uses the public
 * Display.getHdrCapabilities() / isHdr() surface.
 */
@Suppress("DEPRECATION")
class DisplayCollector : FingerprintCollector {

  override val name: String = "DisplayCollector"

  override fun collect(context: Context): CollectorResult {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    if (display == null) {
      return CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.UNAVAILABLE,
          data = buildJsonObject {},
          warnings = listOf("no default display available"),
      )
    }

    val metrics = context.resources.displayMetrics
    val size = displaySize(display)
    val mode = display.mode
    val supportedModes = display.supportedModes

    val hdr = buildJsonObject {
      val caps = display.hdrCapabilities
      val types = caps.supportedHdrTypes.map { JsonPrimitive(hdrTypeName(it)) }
      val supported =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.isHdr
          } else {
            types.isNotEmpty()
          }
      put("hdr_supported", supported)
      put("supported_types", JsonArray(types))
    }

    val normalized = buildJsonObject {
      put("resolution", "${metrics.widthPixels}x${metrics.heightPixels}")
      put("density_dpi", metrics.densityDpi)
      put("refresh_rate", Json.value(mode.refreshRate))
      put(
          "supported_refresh_rates",
          JsonArray(
              supportedModes.map { it.refreshRate }.distinct().sorted().map { Json.value(it) },
          ),
      )
      put("hdr", hdr)
      put(
          "color_modes",
          buildJsonObject {
            put("available", false)
            put("reason", "color mode API is not public (hidden ColorManager)")
          },
      )
    }

    val raw = buildJsonObject {
      put("size", JsonArray(listOf(JsonPrimitive(size.first), JsonPrimitive(size.second))))
      put("density_dpi", metrics.densityDpi)
      put("xdpi", Json.value(metrics.xdpi))
      put("ydpi", Json.value(metrics.ydpi))
      put("mode_id", mode.modeId)
      put(
          "supported_modes",
          JsonArray(
              supportedModes.map { m ->
                buildJsonObject {
                  put("mode_id", m.modeId)
                  put("width", m.physicalWidth)
                  put("height", m.physicalHeight)
                  put("refresh_rate", Json.value(m.refreshRate))
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
        warnings = listOf("color mode enumeration unavailable: hidden ColorManager API"),
    )
  }

  private fun displaySize(display: Display): Pair<Int, Int> {
    val size = Point()
    display.getSize(size)
    return size.x to size.y
  }

  private fun hdrTypeName(type: Int): String =
      when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "dolby_vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "hdr10"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "hdr10_plus"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "hlg"
        else -> "type_$type"
      }
}
