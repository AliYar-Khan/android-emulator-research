/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deterministic canonicalization of fingerprint JSON.
 *
 * Contract (shared with analysis/canonical.py - do not change one side only):
 * 1. Object keys are sorted by UTF-8 byte order.
 * 2. Separators are compact: `{"a":1,"b":[1,2]}` - no whitespace anywhere.
 * 3. Strings use JSON escaping with `\b \f \n \r \t` shortcuts and `\uXXXX` for other control
 *    characters; no escaping of forward slashes; non-ASCII characters are emitted verbatim (UTF-8).
 * 4. Numbers are emitted as-is. Fingerprint data only ever contains integers, strings, booleans and
 *    null in canonical positions (floats are rendered via [Json.value] as "%g" strings on the
 *    device).
 * 5. Arrays preserve order and contain comma-separated canonical elements.
 */
object Canonical {

  fun canonicalJson(element: JsonElement): String {
    val out = StringBuilder()
    write(element, out)
    return out.toString()
  }

  fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
  }

  private fun write(element: JsonElement, out: StringBuilder) {
    when (element) {
      is JsonNull -> out.append("null")
      is JsonPrimitive -> {
        if (element.isString) {
          writeString(element.content, out)
        } else {
          when (element.content) {
            "true" -> out.append("true")
            "false" -> out.append("false")
            else -> out.append(element.content)
          }
        }
      }
      is JsonArray -> {
        out.append('[')
        element.forEachIndexed { i, e ->
          if (i > 0) out.append(',')
          write(e, out)
        }
        out.append(']')
      }
      is JsonObject -> {
        out.append('{')
        val keys = element.keys.sorted()
        keys.forEachIndexed { i, key ->
          if (i > 0) out.append(',')
          writeString(key, out)
          out.append(':')
          write(element.getValue(key), out)
        }
        out.append('}')
      }
    }
  }

  private fun writeString(s: String, out: StringBuilder) {
    out.append('"')
    for (c in s) {
      when (c) {
        '"' -> out.append("\\\"")
        '\\' -> out.append("\\\\")
        '\b' -> out.append("\\b")
        '\u000C' -> out.append("\\f")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\t' -> out.append("\\t")
        else -> {
          if (c.code < 0x20) {
            out.append("\\u")
            out.append(String.format(Locale.ROOT, "%04x", c.code))
          } else {
            out.append(c)
          }
        }
      }
    }
    out.append('"')
  }
}

/**
 * Canonicalizes a full fingerprint document and hashes it.
 *
 * Volatile fields are excluded so the hash is deterministic per environment: every key named
 * "timestamp" (top-level and per-collector), and the fingerprint_sha256 field itself.
 */
object FingerprintHash {

  fun fingerprintSha256(document: JsonObject): String {
    val stable = stripVolatile(document)
    val canonical = Canonical.canonicalJson(stable)
    return Canonical.sha256Hex(canonical.toByteArray(Charsets.UTF_8))
  }

  fun stripVolatile(element: JsonElement): JsonElement =
      when (element) {
        is JsonObject ->
            JsonObject(
                element
                    .filterKeys { it != "timestamp" && it != "fingerprint_sha256" }
                    .mapValues { (_, v) -> stripVolatile(v) },
            )
        is JsonArray -> JsonArray(element.map { stripVolatile(it) })
        else -> element
      }
}
