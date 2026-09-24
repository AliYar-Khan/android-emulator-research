/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin detection of environment-marker substrings in observed text.
 *
 * A marker is evidence, not a verdict: `detect` returns which known substrings were found in the
 * combined (lowercased) observations. Absence of every marker never proves a physical device; the
 * raw evidence is recorded alongside the flags in the fingerprint.
 */
object EnvironmentMarkerParser {

  /** Fixed detection order; keys of the normalized marker map follow this list. */
  val MARKERS: List<String> =
      listOf(
          "qemu",
          "ranchu",
          "goldfish",
          "houdini",
          "waydroid",
          "anbox",
          "redroid",
          "virtualbox",
          "vbox",
          "genymotion",
          "lxc",
          "cuttlefish",
      )

  /** Fixed-order map of marker -> present in any of [texts] (case-insensitive). */
  fun detect(texts: Collection<String?>): Map<String, Boolean> {
    val haystack = texts.filterNotNull().joinToString("\n").lowercase()
    val out = LinkedHashMap<String, Boolean>()
    for (marker in MARKERS) {
      out[marker] = haystack.contains(marker)
    }
    return out
  }
}
