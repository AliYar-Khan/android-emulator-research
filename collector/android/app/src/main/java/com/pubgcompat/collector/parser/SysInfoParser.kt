/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parser for `/sys` CPU/node topology range lists such as `0-3,5`.
 *
 * Malformed tokens are skipped; output is sorted and deduplicated. The result is capped at
 * [MAX_ENTRIES] so a pathological virtual file cannot exhaust memory.
 */
object SysInfoParser {

  const val MAX_ENTRIES = 4096
  private const val MAX_VALUE = 65535

  /** `"0-3,5"` -> `[0, 1, 2, 3, 5]`; blank/malformed input -> `[]`. */
  fun parseRangeList(text: String): List<Int> {
    val out = sortedSetOf<Int>()
    for (token in text.trim().split(',')) {
      if (out.size >= MAX_ENTRIES) break
      val part = token.trim()
      if (part.isEmpty()) continue
      val dash = part.indexOf('-')
      if (dash < 0) {
        val value = part.toIntOrNull() ?: continue
        if (value in 0..MAX_VALUE) out.add(value)
      } else {
        val start = part.substring(0, dash).trim().toIntOrNull() ?: continue
        val end = part.substring(dash + 1).trim().toIntOrNull() ?: continue
        if (start !in 0..MAX_VALUE || end !in 0..MAX_VALUE || start > end) continue
        for (value in start..end) {
          if (out.size >= MAX_ENTRIES) break
          out.add(value)
        }
      }
    }
    return out.toList()
  }

  /** Number of entries in a range list, e.g. `"0-3"` -> `4`. */
  fun count(text: String): Int = parseRangeList(text).size
}
