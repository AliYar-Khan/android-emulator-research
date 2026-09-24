/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentMarkerParserTest {

  @Test
  fun detectsMarkersCaseInsensitively() {
    val out = EnvironmentMarkerParser.detect(listOf("ranchu", "Build: GOLDFISH Board"))
    assertTrue(out["ranchu"]!!)
    assertTrue(out["goldfish"]!!)
    assertFalse(out["qemu"]!!)
    assertFalse(out["waydroid"]!!)
  }

  @Test
  fun fixedKeyOrderMatchesMarkers() {
    val out = EnvironmentMarkerParser.detect(listOf("qemu"))
    assertEquals(EnvironmentMarkerParser.MARKERS, out.keys.toList())
  }

  @Test
  fun cleanTextYieldsAllFalse() {
    val out = EnvironmentMarkerParser.detect(listOf("Samsung SM-G991B", "One UI 6.0"))
    assertTrue(out.values.all { !it })
  }

  @Test
  fun toleratesNullsAndEmpty() {
    val out = EnvironmentMarkerParser.detect(listOf(null, null))
    assertEquals(12, out.size)
    assertTrue(out.values.all { !it })
    assertEquals(12, EnvironmentMarkerParser.detect(emptyList()).size)
  }
}
