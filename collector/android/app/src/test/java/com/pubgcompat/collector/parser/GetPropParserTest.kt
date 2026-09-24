/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class GetPropParserTest {

  @Test
  fun parsesStandardLines() {
    val text =
        """
        [ro.product.model]: [Pixel 7]
        [ro.build.version.sdk]: [34]
        [persist.sys.locale]: [en-US]
        """
            .trimIndent()
    val out = GetPropParser.parse(text)
    assertEquals("Pixel 7", out["ro.product.model"])
    assertEquals("34", out["ro.build.version.sdk"])
    assertEquals("en-US", out["persist.sys.locale"])
    assertEquals(3, out.size)
  }

  @Test
  fun preservesFileOrder() {
    val text =
        """
        [z.prop]: [1]
        [a.prop]: [2]
        """
            .trimIndent()
    assertEquals(listOf("z.prop", "a.prop"), GetPropParser.parse(text).keys.toList())
  }

  @Test
  fun allowsEmptyValuesAndBracketsInValues() {
    val text =
        """
        [empty.prop]: []
        [bracket.prop]: [value] with ] inside]
        """
            .trimIndent()
    val out = GetPropParser.parse(text)
    assertEquals("", out["empty.prop"])
    assertEquals("value] with ] inside", out["bracket.prop"])
  }

  @Test
  fun skipsMalformedAndEmptyKeys() {
    val text =
        """
        not a getprop line
        []: [value]
        [key_without_brackets: [v]
        [good]: [ok]
        """
            .trimIndent()
    assertEquals(mapOf("good" to "ok"), GetPropParser.parse(text))
  }

  @Test
  fun toleratesBlankInput() {
    assertEquals(emptyMap<String, String>(), GetPropParser.parse(""))
    assertEquals(emptyMap<String, String>(), GetPropParser.parse("\n\n"))
  }
}
