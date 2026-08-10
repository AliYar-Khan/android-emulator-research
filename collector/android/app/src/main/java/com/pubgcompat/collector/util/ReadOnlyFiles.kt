/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.util

import java.io.File
import java.io.IOException

/**
 * Read-only access to world-readable kernel files.
 *
 * Rules: never follows into unreadable locations, never writes, caps file size so a pathological
 * virtual file cannot exhaust memory. Returns null (not an exception) when a path is absent,
 * unreadable, or oversized.
 */
object ReadOnlyFiles {

  const val DEFAULT_MAX_BYTES = 1 shl 20

  fun read(path: String, maxBytes: Int = DEFAULT_MAX_BYTES): String? {
    if (maxBytes <= 0) return null
    return try {
      val file = File(path)
      if (!file.exists() || !file.isFile) return null
      val size = file.length()
      if (size > maxBytes) return null
      val bytes = file.readBytes()
      if (bytes.size > maxBytes) return null
      String(bytes, Charsets.UTF_8)
    } catch (_: IOException) {
      null
    } catch (_: SecurityException) {
      null
    }
  }

  fun accessible(path: String): Boolean = read(path, maxBytes = 1) != null
}
