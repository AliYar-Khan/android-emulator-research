package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parser for `/proc/version` and `/proc/sys/kernel/osrelease`.
 *
 * `/proc/version` has the form:
 *   "Linux version 5.15.74-android13-4-... (build@host) (gcc ...) #1 SMP ..."
 * The kernel release is the token after "Linux version"; kernel_version is
 * the release with any trailing "-...-abc1234"-style suffix stripped so
 * different builds of the same upstream version compare cleanly.
 */
object KernelInfoParser {

    data class ParsedKernelInfo(
        val kernelRelease: String?,
        val kernelVersion: String?,
    )

    fun parseProcVersion(raw: String): ParsedKernelInfo {
        if (raw.isBlank()) return ParsedKernelInfo(null, null)
        val trimmed = raw.trim()
        val release = trimmed
            .removePrefix("Linux version ")
            .substringBefore(' ')
            .ifBlank { null }
        return ParsedKernelInfo(
            kernelRelease = release,
            kernelVersion = release?.let(::normalizeVersion),
        )
    }

    fun parseOsrelease(raw: String): ParsedKernelInfo {
        val release = raw.trim().ifBlank { null }
        return ParsedKernelInfo(
            kernelRelease = release,
            kernelVersion = release?.let(::normalizeVersion),
        )
    }

    /**
     * "5.15.74-android13-4-28052361" -> "5.15.74" (upstream portion only).
     * Non-semver strings (e.g. "waydroid-container") are returned as-is.
     */
    fun normalizeVersion(release: String): String? {
        val core = release.substringBefore('-').trim()
        if (core.isEmpty() || !core.all { it.isDigit() || it == '.' }) return release
        val parts = core.split('.')
        if (parts.size >= 2 && parts.take(2).all { it.all(Char::isDigit) }) {
            return core
        }
        return release
    }
}
