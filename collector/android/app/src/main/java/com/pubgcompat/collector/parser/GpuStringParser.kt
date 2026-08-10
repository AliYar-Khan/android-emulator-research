/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parsing/normalization of OpenGL strings reported by the driver. Never hard-codes
 * expectations about what a GPU "should" be; it only classifies what the environment exposes.
 */
object GpuStringParser {

  enum class RendererCategory {
    HARDWARE,
    SOFTWARE,
    VIRTUALIZED,
    UNKNOWN
  }

  data class ParsedGpu(
      val vendor: String?,
      val renderer: String?,
      val glVersion: String?,
      val glslVersion: String?,
      val category: RendererCategory,
  )

  private val VENDOR_KEYWORDS =
      listOf(
          // Virtualized first: a Mesa/VirGL combo must classify as virgl,
          // and "virgl" is a substring that "mesa" would otherwise swallow.
          "virgl" to "virgl",
          "virtio" to "virtio_gpu",
          "swiftshader" to "swiftshader",
          "llvmpipe" to "mesa_llvmpipe",
          "softpipe" to "mesa_softpipe",
          "lavapipe" to "mesa_lavapipe",
          "angle" to "angle",
          "mesa" to "mesa",
          "qualcomm" to "qualcomm",
          "adreno" to "qualcomm",
          "arm" to "arm",
          "mali" to "arm",
          "imgtec" to "imagination",
          "imagination" to "imagination",
          "powervr" to "imagination",
          "nvidia" to "nvidia",
          "amd" to "amd",
          "ati" to "amd",
          "intel" to "intel",
          "google" to "google",
      )

  fun parse(
      vendor: String?,
      renderer: String?,
      glVersion: String?,
      glslVersion: String?,
  ): ParsedGpu {
    val classified = classify(vendor, renderer)
    return ParsedGpu(
        vendor = classified.vendor,
        renderer = renderer?.trim()?.takeIf { it.isNotEmpty() },
        glVersion = parseGlVersion(glVersion),
        glslVersion = parseGlslVersion(glslVersion),
        category = classified.category,
    )
  }

  /** "OpenGL ES 3.2 v1.r20p0 ..." -> "3.2"; "OpenGL ES-CM 1.1 ..." -> "1.1". */
  fun parseGlVersion(version: String?): String? {
    val text = version?.trim() ?: return null
    val match = Regex("(\\d+\\.\\d+)").find(text) ?: return null
    return match.groupValues[1]
  }

  /** "OpenGL ES GLSL ES 3.20" -> "3.20". */
  fun parseGlslVersion(version: String?): String? {
    val text = version?.trim() ?: return null
    val match = Regex("(\\d+\\.\\d+)").find(text) ?: return null
    return match.groupValues[1]
  }

  private fun classify(vendor: String?, renderer: String?): ClassifiedGpu {
    val haystack = "${vendor.orEmpty()} ${renderer.orEmpty()}".lowercase()
    for ((keyword, normalized) in VENDOR_KEYWORDS) {
      if (keyword in haystack) {
        val category =
            when (normalized) {
              "swiftshader",
              "mesa_llvmpipe",
              "mesa_softpipe",
              "mesa_lavapipe",
              "mesa",
              "angle",
              "google", -> RendererCategory.SOFTWARE
              "virgl",
              "virtio_gpu", -> RendererCategory.VIRTUALIZED
              else -> RendererCategory.HARDWARE
            }
        return ClassifiedGpu(normalized, category)
      }
    }
    return ClassifiedGpu(null, RendererCategory.UNKNOWN)
  }

  private data class ClassifiedGpu(val vendor: String?, val category: RendererCategory)
}
