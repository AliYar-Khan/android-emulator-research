package com.pubgcompat.collector

/**
 * Central registry of collectors, in deterministic document order.
 * Collectors are added here as they are implemented.
 */
object CollectorRegistry {

    private val collectors = LinkedHashMap<String, FingerprintCollector>()

    fun register(sectionKey: String, collector: FingerprintCollector) {
        collectors[sectionKey] = collector
    }

    fun all(): LinkedHashMap<String, FingerprintCollector> = LinkedHashMap(collectors)
}
