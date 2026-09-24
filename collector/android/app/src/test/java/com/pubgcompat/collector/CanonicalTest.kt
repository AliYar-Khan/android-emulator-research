/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Golden-hash test pinning the Kotlin canonicalizer to analysis/canonical.py.
 *
 * The fixture and expected hash were produced with analysis/canonical.py (see docs/methodology.md).
 * If this test fails, either side of the contract changed: update both sides together or not at
 * all.
 */
class CanonicalTest {

  private val fixture =
      """{"schema_version":"1.0","timestamp":"2026-01-02T03:04:05Z","fingerprint_sha256":"deadbeef","environment":{"label":"test"},"z":"tab\there \"q\" back\\slash é","a_number":42,"a_bool":true,"a_null":null,"list":[1,"two",false,null,{"nested":"object"}],"build":{"collector":"BuildCollector","timestamp":"2026-01-02T03:04:06Z","status":"available","errors":[],"warnings":[],"data":{"normalized":{"model":"Test Device","sdk_int":34}}}}"""

  @Test
  fun goldenHashMatchesPythonCanonicalizer() {
    val doc = Json.parseToJsonElement(fixture) as JsonObject
    assertEquals(
        "b88a907dc88c7178ee7bee93b02974fafc2e5969cc5c45af2a0ced0db50269c1",
        FingerprintHash.fingerprintSha256(doc),
    )
  }

  @Test
  fun canonicalSortsKeysAndEscapesControlCharacters() {
    val el = buildJsonObject {
      put("b", "x")
      put("a", "\"\\\n" + Char(0x01))
    }
    val expected = "{\"a\":\"\\\"\\\\\\n\\u0001\",\"b\":\"x\"}"
    assertEquals(expected, Canonical.canonicalJson(el))
  }

  @Test
  fun stripsVolatileKeysRecursively() {
    val doc = buildJsonObject {
      put("timestamp", "top")
      put("keep", "yes")
      put(
          "build",
          buildJsonObject {
            put("timestamp", "inner")
            put("status", "available")
          },
      )
      put(
          "list",
          buildJsonArray { add(buildJsonObject { put("timestamp", "in-array") }) },
      )
      put("fingerprint_sha256", "deadbeef")
    }
    val stripped = FingerprintHash.stripVolatile(doc) as JsonObject
    assertFalse("timestamp" in stripped)
    assertFalse("fingerprint_sha256" in stripped)
    val build = stripped["build"] as JsonObject
    assertFalse("timestamp" in build)
    assertEquals("\"available\"", build["status"].toString())
    val list = stripped["list"] as JsonArray
    assertFalse("timestamp" in (list[0] as JsonObject))
    assertEquals("\"yes\"", stripped["keep"].toString())
  }

  @Test
  fun hashIsDeterministicAcrossParses() {
    val a = FingerprintHash.fingerprintSha256(Json.parseToJsonElement(fixture) as JsonObject)
    val b = FingerprintHash.fingerprintSha256(Json.parseToJsonElement(fixture) as JsonObject)
    assertEquals(a, b)
  }
}
