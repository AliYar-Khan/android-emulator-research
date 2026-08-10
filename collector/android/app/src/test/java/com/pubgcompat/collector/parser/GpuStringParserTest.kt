/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuStringParserTest {

  @Test
  fun parsesQualcommAdreno() {
    val p =
        GpuStringParser.parse(
            "Qualcomm",
            "Adreno (TM) 650",
            "OpenGL ES 3.2 V@16.0.0 (GIT@1d192c0, I5dc07f13a2) (Date:06/26/20)",
            "OpenGL ES GLSL ES 3.20",
        )
    assertEquals("qualcomm", p.vendor)
    assertEquals("3.2", p.glVersion)
    assertEquals("3.20", p.glslVersion)
    assertEquals(GpuStringParser.RendererCategory.HARDWARE, p.category)
  }

  @Test
  fun parsesArmMali() {
    val p =
        GpuStringParser.parse(
            "ARM",
            "Mali-G77",
            "OpenGL ES 3.2 v1.r32p0-01rel0",
            "OpenGL ES GLSL ES 3.20",
        )
    assertEquals("arm", p.vendor)
    assertEquals(GpuStringParser.RendererCategory.HARDWARE, p.category)
  }

  @Test
  fun detectsSwiftShaderSoftwareRenderer() {
    val p =
        GpuStringParser.parse(
            "Google",
            "Android Emulator OpenGL ES Translator (Google SwiftShader)",
            "OpenGL ES 3.2",
            "OpenGL ES GLSL ES 3.20",
        )
    assertEquals(GpuStringParser.RendererCategory.SOFTWARE, p.category)
  }

  @Test
  fun detectsVirglVirtualizedRenderer() {
    val p =
        GpuStringParser.parse(
            "Mesa",
            "virgl",
            "OpenGL ES 3.1 Mesa 22.3.6",
            "OpenGL ES GLSL ES 3.10",
        )
    assertEquals("virgl", p.vendor)
    assertEquals(GpuStringParser.RendererCategory.VIRTUALIZED, p.category)
  }

  @Test
  fun detectsLlvmpipe() {
    val p =
        GpuStringParser.parse(
            "Mesa",
            "llvmpipe (LLVM 14.0.0, 128 bits)",
            "OpenGL ES 3.1 Mesa 22.3.6",
            "OpenGL ES GLSL ES 3.10",
        )
    assertEquals("mesa_llvmpipe", p.vendor)
    assertEquals(GpuStringParser.RendererCategory.SOFTWARE, p.category)
  }

  @Test
  fun parsesEs20() {
    val p =
        GpuStringParser.parse(
            null,
            null,
            "OpenGL ES-CM 1.1",
            "OpenGL ES GLSL ES 1.00",
        )
    assertEquals("1.1", p.glVersion)
    assertEquals("1.00", p.glslVersion)
    assertEquals(GpuStringParser.RendererCategory.UNKNOWN, p.category)
  }

  @Test
  fun toleratesBlankInput() {
    val p = GpuStringParser.parse(null, null, null, null)
    assertNull(p.vendor)
    assertNull(p.glVersion)
    assertNull(p.glslVersion)
    assertEquals(GpuStringParser.RendererCategory.UNKNOWN, p.category)
  }
}
