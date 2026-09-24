/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parser for `/proc/net/dev`.
 *
 * Only interface names are extracted. The per-interface hardware (MAC) addresses in the raw file
 * are deliberately never surfaced: the fingerprint records which interfaces exist, not identifiers.
 */
object NetDevParser {

  private val IFACE_LINE = Regex("^\\s*([^:\\s]+):\\s+\\d+")

  /** Distinct interface names, sorted. Header lines never match. */
  fun parseInterfaceNames(text: String): List<String> {
    val out = sortedSetOf<String>()
    for (line in text.lineSequence()) {
      val match = IFACE_LINE.find(line) ?: continue
      out.add(match.groupValues[1])
    }
    return out.toList()
  }
}
