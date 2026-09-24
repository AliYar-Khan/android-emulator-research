/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class NetDevParserTest {

  private val sample =
      """
      Inter-|   Receive                                                |  Transmit
       face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
          lo: 4294967295   12345    0    0    0     0          0         0  4294967295   12345    0    0    0     0       0          0
        eth0:  9876543    5432    0    0    0     0          0         0    1234567    4321    0    0    0     0       0          0
       wlan0:  1112223     321    0    0    0     0          0         0     222333     111    0    0    0     0       0          0
       dummy0:        0       0    0    0    0     0          0         0         0       0    0    0    0     0       0          0
      """
          .trimIndent()

  @Test
  fun extractsDistinctSortedInterfaceNames() {
    assertEquals(
        listOf("dummy0", "eth0", "lo", "wlan0"),
        NetDevParser.parseInterfaceNames(sample),
    )
  }

  @Test
  fun headerLinesNeverMatch() {
    val names = NetDevParser.parseInterfaceNames(sample)
    for (name in listOf("face", "Inter-", "bytes")) {
      assertEquals(false, name in names)
    }
  }

  @Test
  fun ignoresLinesWithoutCounters() {
    val text =
        """
        eth0:
        eth1: notanumber
        """
            .trimIndent()
    assertEquals(emptyList<String>(), NetDevParser.parseInterfaceNames(text))
  }

  @Test
  fun toleratesBlankInput() {
    assertEquals(emptyList<String>(), NetDevParser.parseInterfaceNames(""))
  }
}
