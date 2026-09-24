/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemFeatureParserTest {

  @Test
  fun normalizesNames() {
    val raw =
        listOf(
            "android.hardware.wifi",
            " android.hardware.wifi ",
            "",
            "   ",
            null,
            "android.hardware.touchscreen",
            "android.hardware.wifi",
        )
    assertEquals(
        listOf("android.hardware.touchscreen", "android.hardware.wifi"),
        SystemFeatureParser.normalizeNames(raw),
    )
    assertEquals(emptyList<String>(), SystemFeatureParser.normalizeNames(null))
  }

  @Test
  fun featureMatrixHasFixedOrderAndValues() {
    val out =
        SystemFeatureParser.featureMatrix(
            listOf("android.hardware.touchscreen", "android.software.leanback"),
        )
    assertEquals(28, out.size)
    assertEquals("touchscreen", out.keys.first())
    assertEquals("opengles_aep", out.keys.last())
    assertEquals(true, out["touchscreen"])
    assertEquals(true, out["leanback"])
    assertEquals(false, out["nfc"])
    assertEquals(false, out["gamepad"])
  }

  @Test
  fun formatsGlesVersion() {
    assertEquals("2.0", SystemFeatureParser.glesVersionString(0x00020000))
    assertEquals("3.2", SystemFeatureParser.glesVersionString(0x00030002))
    assertEquals("0.0", SystemFeatureParser.glesVersionString(0))
  }
}
