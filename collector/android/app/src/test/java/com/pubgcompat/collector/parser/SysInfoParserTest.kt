/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SysInfoParserTest {

  @Test
  fun parsesRangeList() {
    assertEquals(listOf(0, 1, 2, 3, 5), SysInfoParser.parseRangeList("0-3,5"))
    assertEquals(4, SysInfoParser.count("0-3"))
  }

  @Test
  fun deduplicatesAndSorts() {
    assertEquals(listOf(1, 2, 5), SysInfoParser.parseRangeList("5,1,2,1"))
    assertEquals(listOf(1, 2, 3), SysInfoParser.parseRangeList("3-1,1,2,3"))
  }

  @Test
  fun skipsMalformedTokens() {
    assertEquals(emptyList<Int>(), SysInfoParser.parseRangeList(""))
    assertEquals(emptyList<Int>(), SysInfoParser.parseRangeList("   "))
    assertEquals(emptyList<Int>(), SysInfoParser.parseRangeList("-1,999999"))
    assertEquals(listOf(7), SysInfoParser.parseRangeList("a,3-1,7,x,65536"))
  }

  @Test
  fun capsEntryCount() {
    val huge = (0..4500).joinToString(",")
    assertEquals(SysInfoParser.MAX_ENTRIES, SysInfoParser.parseRangeList(huge).size)
  }

  @Test
  fun acceptsBoundaryValues() {
    assertEquals(listOf(0, 65535), SysInfoParser.parseRangeList("0,65535"))
    val out = SysInfoParser.parseRangeList("0-65535")
    assertEquals(SysInfoParser.MAX_ENTRIES, out.size)
    assertTrue(out.first() == 0)
  }
}
