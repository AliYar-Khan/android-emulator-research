package com.pubgcompat.collector.parser

/**
 * Pure-Kotlin parser for `/proc/cpuinfo` text.
 *
 * Handles the ARM layout ("CPU implementer", "CPU architecture",
 * "Features") and the x86 layout ("processor : 0", "model name", "flags"),
 * including virtualized variants that expose the `hypervisor` flag.
 * Absent fields are null; parsing never throws. The raw text is preserved by
 * the caller.
 */
object CpuInfoParser {

    data class ParsedCpuInfo(
        val architecture: String?,
        val model: String?,
        val processor: String?,
        val cpuImplementer: String?,
        val cpuVariant: String?,
        val cpuPart: String?,
        val features: List<String>,
        val coreCount: Int?,
        val vendorId: String?,
    )

    fun parse(raw: String): ParsedCpuInfo {
        if (raw.isBlank()) {
            return ParsedCpuInfo(null, null, null, null, null, null, emptyList(), null, null)
        }
        val lines = raw.lines()
        val blocks = splitBlocks(lines)

        var architecture: String? = null
        var model: String? = null
        var processor: String? = null
        var cpuImplementer: String? = null
        var cpuVariant: String? = null
        var cpuPart: String? = null
        var vendorId: String? = null
        val featureSets = mutableListOf<String>()
        var processorKeyLines = 0
        val processorLineValues = mutableListOf<String>()

        for (line in lines) {
            val (key, value) = splitKeyValue(line) ?: continue
            when (key) {
                "CPU architecture" -> architecture = value
                "model name" -> model = value
                "Processor" -> processor = value
                "CPU implementer" -> cpuImplementer = value
                "CPU variant" -> cpuVariant = value
                "CPU part" -> cpuPart = value
                "vendor_id" -> vendorId = value
                "Features", "flags" -> featureSets.add(value)
                "processor" -> {
                    processorKeyLines++
                    processorLineValues.add(value)
                }
            }
        }

        val features = featureSets
            .flatMap { it.split(Regex("\\s+")).filter { f -> f.isNotBlank() } }
            .distinct()
            .sorted()

        val coreCount: Int? =
            if (processorKeyLines > 1) {
                processorKeyLines
            } else if (blocks.size > 1 && blocks.all { block -> block.any { it.startsWith("processor") } }) {
                blocks.size
            } else {
                processorLineValues.mapNotNull { it.toIntOrNull() }.maxOrNull()?.plus(1)
            }

        val inferredArchitecture =
            architecture ?: inferArchitecture(model, processor, vendorId, features)

        return ParsedCpuInfo(
            architecture = inferredArchitecture,
            model = model,
            processor = processor,
            cpuImplementer = cpuImplementer,
            cpuVariant = cpuVariant,
            cpuPart = cpuPart,
            features = features,
            coreCount = coreCount,
            vendorId = vendorId,
        )
    }

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx < 0) return null
        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        if (key.isEmpty()) return null
        return key to value
    }

    private fun splitBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            if (line.isBlank()) {
                current = null
            } else {
                val block = current ?: mutableListOf<String>().also {
                    blocks.add(it)
                    current = it
                }
                block.add(line)
            }
        }
        return blocks
    }

    private fun inferArchitecture(
        model: String?,
        processor: String?,
        vendorId: String?,
        features: List<String>,
    ): String? {
        val modelName = model ?: processor
        if (modelName != null) {
            val lower = modelName.lowercase()
            when {
                "x86_64" in lower || "amd64" in lower -> return "x86_64"
                "aarch64" in lower || "armv8" in lower -> return "aarch64"
                "x86" in lower || "i686" in lower || "i386" in lower -> return "x86"
                "arm" in lower || "aarch32" in lower -> return "arm"
                "riscv" in lower -> return "riscv"
            }
        }
        if (vendorId != null) {
            val knownX86Vendors =
                listOf("GenuineIntel", "AuthenticAMD", "HygonGenuine", "CentaurHauls", "VIA")
            if (knownX86Vendors.any { it.equals(vendorId, ignoreCase = true) }) {
                return if ("lm" in features) "x86_64" else "x86"
            }
        }
        return vendorId
    }
}
