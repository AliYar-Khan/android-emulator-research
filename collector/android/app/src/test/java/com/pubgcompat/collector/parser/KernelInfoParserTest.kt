package com.pubgcompat.collector.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KernelInfoParserTest {

    @Test
    fun parsesAndroidProcVersion() {
        val p = KernelInfoParser.parseProcVersion(
            "Linux version 5.15.74-android13-4-28052361 (build@host) " +
                "(gcc version 12.1.1 20220720 (GCC)) #1 SMP PREEMPT Thu Nov 24 01:08:07 UTC 2022",
        )
        assertEquals("5.15.74-android13-4-28052361", p.kernelRelease)
        assertEquals("5.15.74", p.kernelVersion)
    }

    @Test
    fun parsesGenericProcVersion() {
        val p = KernelInfoParser.parseProcVersion(
            "Linux version 6.1.0-16-amd64 (debian-kernel@lists.debian.org) " +
                "(gcc-12 (Debian 12.2.0-14) 12.2.0) #1 SMP PREEMPT_DYNAMIC Debian 6.1.67-1 (2023-12-12)",
        )
        assertEquals("6.1.0-16-amd64", p.kernelRelease)
        assertEquals("6.1.0", p.kernelVersion)
    }

    @Test
    fun parsesOsrelease() {
        val p = KernelInfoParser.parseOsrelease("5.15.74-android13-4-28052361")
        assertEquals("5.15.74-android13-4-28052361", p.kernelRelease)
        assertEquals("5.15.74", p.kernelVersion)
    }

    @Test
    fun leavesNonSemverReleaseAlone() {
        val p = KernelInfoParser.parseOsrelease("waydroid-container")
        assertEquals("waydroid-container", p.kernelVersion)
    }

    @Test
    fun toleratesBlankInput() {
        val p = KernelInfoParser.parseProcVersion("")
        assertNull(p.kernelRelease)
        assertNull(p.kernelVersion)
    }
}
