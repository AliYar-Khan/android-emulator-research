/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parser for `getprop` output.
 *
 * `getprop` prints one property per line as `[key]: [value]`. Values may be empty and may contain
 * `]`; only the first `]: [` boundary is used.
 */
object GetPropParser {

  private val LINE = Regex("^\\[([^]]+)]: \\[(.*)]$")

  /** Key -> value in file order. Malformed lines are skipped. */
  fun parse(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (line in text.lineSequence()) {
      val match = LINE.find(line.trim()) ?: continue
      val key = match.groupValues[1]
      if (key.isEmpty()) continue
      out[key] = match.groupValues[2]
    }
    return out
  }
}
