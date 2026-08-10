package com.pubgcompat.collector.collectors

import android.content.Context
import android.os.Build
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Build/device identity from the public [android.os.Build] API, plus system
 * properties reachable through legitimate application-visible mechanisms
 * ([System.getProperty]). Properties are recorded (name, value) and never
 * modified.
 */
class BuildCollector : FingerprintCollector {

    override val name: String = "BuildCollector"

    override fun collect(context: Context): CollectorResult {
        val normalized = buildJsonObject {
            put("brand", Build.BRAND)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("hardware", Build.HARDWARE)
            put("board", Build.BOARD)
            put("display", Build.DISPLAY)
            put("fingerprint", Build.FINGERPRINT)
            put("host", Build.HOST)
            put("id", Build.ID)
            put("tags", Build.TAGS)
            put("type", Build.TYPE)
            put("user", Build.USER)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("security_patch", Build.VERSION.SECURITY_PATCH)
            put("base_os", Build.VERSION.BASE_OS)
            put("incremental", Build.VERSION.INCREMENTAL)
        }

        val raw = buildJsonObject {
            put("properties", JsonArray(SYSTEM_PROPERTIES.map { (key, value) ->
                buildJsonObject {
                    put("name", key)
                    put("value", value)
                }
            }))
        }

        val warnings = SYSTEM_PROPERTIES
            .mapNotNull { (key, value) -> if (value == null) "system property '$key' unavailable" else null }

        return CollectorResult(
            collector = name,
            timestamp = nowIso(),
            status = CollectorStatus.AVAILABLE,
            data = buildJsonObject {
                put("raw", raw)
                put("normalized", normalized)
            },
            warnings = warnings,
        )
    }

    private companion object {
        /** Allowlist of system properties readable without special permission. */
        val SYSTEM_PROPERTIES: List<Pair<String, String?>> =
            listOf(
                "java.version" to safeProperty("java.version"),
                "java.vm.name" to safeProperty("java.vm.name"),
                "java.vm.version" to safeProperty("java.vm.version"),
                "java.vm.vendor" to safeProperty("java.vm.vendor"),
                "java.specification.version" to safeProperty("java.specification.version"),
                "os.name" to safeProperty("os.name"),
                "os.version" to safeProperty("os.version"),
                "os.arch" to safeProperty("os.arch"),
                "user.language" to safeProperty("user.language"),
            )

        fun safeProperty(key: String): String? =
            try {
                System.getProperty(key)
            } catch (_: SecurityException) {
                null
            }
    }
}
