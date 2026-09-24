/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcInfoParserTest {

  @Test
  fun parsesMeminfoWithAndWithoutUnit() {
    val text =
        """
        MemTotal:       16384000 kB
        HugePages_Total:       0
        Corrupt Line
        """
            .trimIndent()
    val out = ProcInfoParser.parseMeminfo(text)
    assertEquals(ProcInfoParser.MeminfoEntry(16384000L, "kB"), out["MemTotal"])
    assertEquals(ProcInfoParser.MeminfoEntry(0L, null), out["HugePages_Total"])
    assertEquals(2, out.size)
  }

  @Test
  fun meminfoSkipsValuelessAndBlankLines() {
    val text =
        """
        :

        MemFree: notanumber kB
        SwapTotal:    1024 kB
        """
            .trimIndent()
    val out = ProcInfoParser.parseMeminfo(text)
    assertEquals(setOf("SwapTotal"), out.keys)
  }

  @Test
  fun parsesLoadavg() {
    val p = ProcInfoParser.parseLoadavg("0.52 0.58 0.59 2/834 12345")
    assertEquals(0.52, p!!.load1, 1e-9)
    assertEquals(0.58, p.load5, 1e-9)
    assertEquals(0.59, p.load15, 1e-9)
    assertEquals(2, p.runnableTasks)
    assertEquals(834, p.totalTasks)
    assertEquals(12345, p.lastPid)
  }

  @Test
  fun loadavgRejectsMalformedInput() {
    assertNull(ProcInfoParser.parseLoadavg(""))
    assertNull(ProcInfoParser.parseLoadavg("0.52 0.58 0.59"))
    assertNull(ProcInfoParser.parseLoadavg("0.52 0.58 0.59 two/834 12345"))
    assertNull(ProcInfoParser.parseLoadavg("0.52 0.58 0.59 2/834 notapid"))
  }

  @Test
  fun parsesUptimeSeconds() {
    assertEquals(123456.78, ProcInfoParser.parseUptimeSeconds("123456.78 987654.32")!!, 1e-9)
    assertNull(ProcInfoParser.parseUptimeSeconds("   "))
    assertNull(ProcInfoParser.parseUptimeSeconds("garbage"))
  }

  @Test
  fun splitsCmdlineTokens() {
    val tokens =
        ProcInfoParser.parseCmdlineTokens("console=tty0\u0000androidboot.hardware=ranchu\u0000")
    assertEquals(listOf("console=tty0", "androidboot.hardware=ranchu"), tokens)
    assertEquals(emptyList<String>(), ProcInfoParser.parseCmdlineTokens(""))
  }

  @Test
  fun keyValuesDropSensitiveKeys() {
    val tokens =
        listOf(
            "console=tty0",
            "androidboot.serialno=ABC123",
            "BOOT_ID=xyz",
            "password=hunter2",
            "androidboot.uuid=1234",
            "noequals",
            "=novalue",
        )
    val out = ProcInfoParser.parseKeyValues(tokens)
    assertEquals(mapOf("console" to "tty0", "BOOT_ID" to "xyz"), out)
  }

  @Test
  fun keyValuesHonorPrefix() {
    val tokens = listOf("androidboot.hardware=ranchu", "console=tty0")
    val out = ProcInfoParser.parseKeyValues(tokens, prefix = "androidboot.")
    assertEquals(mapOf("androidboot.hardware" to "ranchu"), out)
  }

  @Test
  fun parsesPartitionsSortedByNameThenBlocks() {
    val text =
        """
        major minor  #blocks  name

           8        1  156290976 sda1
           8        0  156290976 sda
           7        0     995328 loop0
        """
            .trimIndent()
    val out = ProcInfoParser.parsePartitions(text)
    assertEquals(
        listOf(
            ProcInfoParser.Partition("loop0", 995328L),
            ProcInfoParser.Partition("sda", 156290976L),
            ProcInfoParser.Partition("sda1", 156290976L),
        ),
        out,
    )
  }

  @Test
  fun parsesMountSummary() {
    val text =
        """
        /dev/block/sda4 /data ext4 rw,nodev,noatime 0 0
        tmpfs /dev tmpfs rw,seclabel,relatime 0 0
        /dev/block/sda4 /system ext4 ro,seclabel,relatime 0 0
        broken line
        """
            .trimIndent()
    val summary = ProcInfoParser.parseMountSummary(text)
    assertEquals(listOf("ext4", "tmpfs"), summary.filesystemTypes)
    assertEquals(3, summary.mountCount)
  }
}
