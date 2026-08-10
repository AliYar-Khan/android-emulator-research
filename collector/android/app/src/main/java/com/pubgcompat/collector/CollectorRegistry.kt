package com.pubgcompat.collector

import com.pubgcompat.collector.collectors.AbiCollector
import com.pubgcompat.collector.collectors.BuildCollector
import com.pubgcompat.collector.collectors.CpuCollector
import com.pubgcompat.collector.collectors.KernelCollector

/**
 * Central registry of collectors, in deterministic document order matching
 * schema/fingerprint.schema.json.
 */
object CollectorRegistry {

    fun all(): LinkedHashMap<String, FingerprintCollector> =
        linkedMapOf(
            "build" to BuildCollector(),
            "abi" to AbiCollector(),
            "cpu" to CpuCollector(),
            "kernel" to KernelCollector(),
        )
}
