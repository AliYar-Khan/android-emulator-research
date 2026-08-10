/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.integrity

/**
 * Extension point for future, legitimate Play Integrity research.
 *
 * This interface is intentionally left without a concrete implementation. Enabling a provider
 * requires an explicit, reviewed test integration (e.g. an app signed with a valid, provisioned
 * Play Integrity credential and a documented research protocol). We do not forge, modify, replay,
 * or bypass Integrity API responses.
 */
interface IntegrityProvider {
  fun isEnabled(): Boolean = false

  fun collect(): IntegrityResult
}

data class IntegrityResult(
    val status: String,
    val data: Map<String, Any?> = emptyMap(),
    val errors: List<String> = emptyList(),
)
