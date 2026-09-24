/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parsers for world-readable `/proc` files.
 *
 * Every parser tolerates blank, truncated and unexpected input: a malformed line is skipped, never
 * an exception. All returned collections are deterministically ordered so normalized fingerprint
 * output hashes stably.
 */
object ProcInfoParser {

  data class MeminfoEntry(val value: Long, val unit: String?)

  data class Loadavg(
      val load1: Double,
      val load5: Double,
      val load15: Double,
      val runnableTasks: Int,
      val totalTasks: Int,
      val lastPid: Int,
  )

  data class Partition(val name: String, val blocks: Long)

  data class MountSummary(val filesystemTypes: List<String>, val mountCount: Int)

  private val WHITESPACE = Regex("\\s+")

  private val SENSITIVE_KEY_MARKERS = listOf("serial", "password", "uuid")

  /**
   * Parses `/proc/meminfo` lines of the form `Key: 12345 kB` into key -> value+unit. Entries
   * without a unit (e.g. `HugePages_Total`) keep `unit = null`.
   */
  fun parseMeminfo(text: String): Map<String, MeminfoEntry> {
    val out = LinkedHashMap<String, MeminfoEntry>()
    for (line in text.lineSequence()) {
      val colon = line.indexOf(':')
      if (colon <= 0) continue
      val key = line.substring(0, colon).trim()
      val rest = line.substring(colon + 1).trim()
      if (key.isEmpty() || rest.isEmpty()) continue
      val parts = rest.split(WHITESPACE)
      val value = parts[0].toLongOrNull() ?: continue
      out[key] = MeminfoEntry(value, parts.getOrNull(1))
    }
    return out
  }

  /** Parses `/proc/loadavg`: `0.52 0.58 0.59 2/834 12345`. */
  fun parseLoadavg(text: String): Loadavg? {
    val parts = text.trim().split(WHITESPACE)
    if (parts.size < 5) return null
    val load1 = parts[0].toDoubleOrNull() ?: return null
    val load5 = parts[1].toDoubleOrNull() ?: return null
    val load15 = parts[2].toDoubleOrNull() ?: return null
    val runnable = parts[3].split('/')
    if (runnable.size != 2) return null
    val runnableTasks = runnable[0].toIntOrNull() ?: return null
    val totalTasks = runnable[1].toIntOrNull() ?: return null
    val lastPid = parts[4].toIntOrNull() ?: return null
    return Loadavg(load1, load5, load15, runnableTasks, totalTasks, lastPid)
  }

  /** Parses the first field of `/proc/uptime`: `123456.78 987654.32`. */
  fun parseUptimeSeconds(text: String): Double? =
      text.trim().split(WHITESPACE).firstOrNull()?.toDoubleOrNull()

  /** Splits a NUL-separated `/proc/cmdline` into non-blank tokens. */
  fun parseCmdlineTokens(text: String): List<String> =
      text.split(Char(0)).filter { it.isNotBlank() }

  /**
   * Extracts `key=value` pairs from cmdline tokens. Keys containing `serial`, `password` or `uuid`
   * (case-insensitive) are dropped: device identifiers must never enter the fingerprint.
   */
  fun parseKeyValues(tokens: List<String>, prefix: String? = null): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (token in tokens) {
      val eq = token.indexOf('=')
      if (eq <= 0) continue
      val key = token.substring(0, eq)
      if (prefix != null && !key.startsWith(prefix)) continue
      if (SENSITIVE_KEY_MARKERS.any { key.contains(it, ignoreCase = true) }) continue
      out[key] = token.substring(eq + 1)
    }
    return out
  }

  /**
   * Parses `/proc/partitions` (`major minor #blocks name` rows after the two header lines). Sorted
   * by name, then block count, for determinism.
   */
  fun parsePartitions(text: String): List<Partition> {
    val out = mutableListOf<Partition>()
    for (line in text.lineSequence()) {
      val parts = line.trim().split(WHITESPACE)
      if (parts.size < 4) continue
      if (parts[0] == "major") continue
      val blocks = parts[2].toLongOrNull() ?: continue
      val name = parts[3]
      if (name.isBlank()) continue
      out.add(Partition(name, blocks))
    }
    return out.sortedWith(compareBy({ it.name }, { it.blocks }))
  }

  /** Parses `/proc/mounts`: distinct filesystem types (sorted) and total mount count. */
  fun parseMountSummary(text: String): MountSummary {
    val types = sortedSetOf<String>()
    var count = 0
    for (line in text.lineSequence()) {
      val parts = line.trim().split(WHITESPACE)
      if (parts.size < 3) continue
      types.add(parts[2])
      count++
    }
    return MountSummary(types.toList(), count)
  }
}
