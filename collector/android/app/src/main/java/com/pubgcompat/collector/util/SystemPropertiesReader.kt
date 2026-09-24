/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.util

import com.pubgcompat.collector.parser.GetPropParser
import java.util.concurrent.TimeUnit

/**
 * Read-only access to system properties via the `getprop` binary.
 *
 * Rules: never sets or edits a property, never uses hidden `SystemProperties` APIs, always times
 * out, and returns an empty map (not an exception) when `getprop` is absent or fails. Callers must
 * treat returned values as observations, never as truth.
 */
object SystemPropertiesReader {

  private const val TIMEOUT_MS = 2000L

  /** Parses the full `getprop` dump. Key filtering/allowlisting is the caller's responsibility. */
  fun readAll(): Map<String, String> {
    return try {
      val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
      val output = StringBuilder()
      val reader = Thread {
        try {
          output.append(process.inputStream.readBytes().toString(Charsets.UTF_8))
        } catch (_: Exception) {
          // partial output; parsed below with whatever arrived
        }
      }
      reader.start()
      val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
      if (!finished) {
        process.destroyForcibly()
        reader.join(TIMEOUT_MS)
        return emptyMap()
      }
      reader.join(TIMEOUT_MS)
      GetPropParser.parse(output.toString())
    } catch (_: Exception) {
      emptyMap()
    }
  }

  /** Fixed-order projection of [keys] from [props]; missing keys map to null. */
  fun pick(props: Map<String, String>, keys: List<String>): Map<String, String?> {
    val out = LinkedHashMap<String, String?>()
    for (key in keys) {
      out[key] = props[key]
    }
    return out
  }
}
