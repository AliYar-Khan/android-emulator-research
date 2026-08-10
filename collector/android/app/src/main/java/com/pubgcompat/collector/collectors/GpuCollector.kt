/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.parser.GpuStringParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Creates a small offscreen GLES context and queries what the driver actually exposes:
 * vendor/renderer/version/GLSL strings, sorted extension list, and capability values. Runs on an
 * EGL surface of 1x1 pixels; no visible window is created and no rendering is performed.
 */
class GpuCollector : FingerprintCollector {

  override val name: String = "GpuCollector"

  companion object {
    /**
     * GL_MAX_TEXTURE_UNITS (0x84E2) from OpenGL ES 1.1; deprecated but still queryable on ES 2/3
     * contexts. Not exposed by the public android.opengl stubs.
     */
    private const val GL_MAX_TEXTURE_UNITS = 0x84E2
  }

  override fun collect(context: Context): CollectorResult {
    val session =
        GlSession.create()
            ?: return CollectorResult(
                collector = name,
                timestamp = nowIso(),
                status = CollectorStatus.UNAVAILABLE,
                data = buildJsonObject {},
                errors = listOf("could not create an EGL display/context"),
            )
    return try {
      val vendor = glString(GLES20.GL_VENDOR)
      val renderer = glString(GLES20.GL_RENDERER)
      val glVersion = glString(GLES20.GL_VERSION)
      val glslVersion = glString(GLES20.GL_SHADING_LANGUAGE_VERSION)
      val extensions =
          (glString(GLES20.GL_EXTENSIONS) ?: "")
              .split(' ')
              .filter { it.isNotBlank() }
              .distinct()
              .sorted()

      val major = GpuStringParser.parseGlVersion(glVersion)?.substringBefore('.')?.toIntOrNull()
      val supportsEs3 = major != null && major >= 3

      val caps = buildJsonObject {
        put("max_texture_size", JsonPrimitive(glInt(GLES20.GL_MAX_TEXTURE_SIZE)))
        put("max_texture_units", JsonPrimitive(glInt(GL_MAX_TEXTURE_UNITS)))
        put(
            "max_combined_texture_units",
            JsonPrimitive(glInt(GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)),
        )
        put("max_vertex_attribs", JsonPrimitive(glInt(GLES20.GL_MAX_VERTEX_ATTRIBS)))
        put(
            "max_vertex_uniform_vectors",
            JsonPrimitive(glInt(GLES20.GL_MAX_VERTEX_UNIFORM_VECTORS)),
        )
        put(
            "max_fragment_uniform_vectors",
            JsonPrimitive(glInt(GLES20.GL_MAX_FRAGMENT_UNIFORM_VECTORS)),
        )
        put(
            "max_viewport_dims",
            JsonArray(glInt2(GLES20.GL_MAX_VIEWPORT_DIMS).map { JsonPrimitive(it) }),
        )
        put(
            "max_samples",
            if (supportsEs3) {
              JsonPrimitive(glInt(GLES30.GL_MAX_SAMPLES))
            } else {
              JsonPrimitive("unavailable")
            },
        )
        put("context_is_es3", supportsEs3)
      }

      val parsed = GpuStringParser.parse(vendor, renderer, glVersion, glslVersion)

      val normalized = buildJsonObject {
        put("vendor", parsed.vendor)
        put("renderer", parsed.renderer)
        put("gl_version", parsed.glVersion)
        put("glsl_version", parsed.glslVersion)
        put("category", parsed.category.name.lowercase())
        put("extensions", JsonArray(extensions.map { JsonPrimitive(it) }))
        put("capabilities", caps)
      }
      val raw = buildJsonObject {
        put("gl_vendor", vendor)
        put("gl_renderer", renderer)
        put("gl_version", glVersion)
        put("glsl_version", glslVersion)
        put("gl_extensions", JsonArray(extensions.map { JsonPrimitive(it) }))
      }

      CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.AVAILABLE,
          data =
              buildJsonObject {
                put("raw", raw)
                put("normalized", normalized)
              },
      )
    } finally {
      session.release()
    }
  }

  private fun glString(name: Int): String? {
    val text = GLES20.glGetString(name)
    return text?.trim()?.takeIf { it.isNotEmpty() }
  }

  private fun glInt(name: Int): Int {
    val values = IntArray(1)
    GLES20.glGetIntegerv(name, values, 0)
    return values[0]
  }

  private fun glInt2(name: Int): List<Int> {
    val values = IntArray(2)
    GLES20.glGetIntegerv(name, values, 0)
    return listOf(values[0], values[1])
  }

  /** Owns an offscreen EGL display/context/surface; release() must be called. */
  private class GlSession(
      private val display: EGLDisplay,
      private val context: EGLContext,
      private val surface: EGLSurface,
  ) {
    fun release() {
      EGL14.eglMakeCurrent(
          display,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT,
      )
      EGL14.eglDestroySurface(display, surface)
      EGL14.eglDestroyContext(display, context)
      EGL14.eglTerminate(display)
    }

    companion object {
      /**
       * EGL_OPENGL_ES3_BIT_KHR (0x40). Not exposed by the public EGL14 stub, so defined here; value
       * is stable across Android versions.
       */
      private const val EGL_OPENGL_ES3_BIT = 0x40

      fun create(): GlSession? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

        val es3 = chooseConfig(display, EGL_OPENGL_ES3_BIT)
        val config =
            es3
                ?: chooseConfig(display, EGL14.EGL_OPENGL_ES2_BIT)
                ?: run {
                  EGL14.eglTerminate(display)
                  return null
                }

        val contextAttribs =
            if (es3 != null) {
              intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            } else {
              intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            }
        val context =
            EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
          EGL14.eglTerminate(display)
          return null
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
          EGL14.eglDestroyContext(display, context)
          EGL14.eglTerminate(display)
          return null
        }

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
          EGL14.eglDestroySurface(display, surface)
          EGL14.eglDestroyContext(display, context)
          EGL14.eglTerminate(display)
          return null
        }
        return GlSession(display, context, surface)
      }

      private fun chooseConfig(display: EGLDisplay, renderableType: Int): EGLConfig? {
        val attribs =
            intArrayOf(
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE,
                renderableType,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0)) {
          return null
        }
        return configs[0]
      }
    }
  }
}
