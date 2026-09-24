/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Telephony surface observable without any dangerous permission.
 *
 * Only permission-free `TelephonyManager` getters are queried; `READ_PHONE_STATE` and friends are
 * deliberately never requested (least privilege - the absence of a permission is itself an
 * observation). Fields whose getter throws `SecurityException` are recorded as denied.
 */
class TelephonyCollector : FingerprintCollector {

  override val name: String = "TelephonyCollector"

  private data class Attempt(val value: String?, val code: Int?, val denied: Boolean)

  override fun collect(context: Context): CollectorResult {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    if (telephonyManager == null) {
      return CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.UNAVAILABLE,
          data = buildJsonObject {},
          warnings = listOf("TelephonyManager service is not available"),
      )
    }

    val warnings = mutableListOf<String>()

    fun attempt(field: String, code: () -> Int, text: (Int) -> String): Attempt =
        try {
          val value = code()
          Attempt(text(value), value, false)
        } catch (e: SecurityException) {
          warnings.add("telephony field '$field' denied: ${e.message}")
          Attempt(null, null, true)
        }

    fun attemptString(field: String, read: () -> String?): Attempt =
        try {
          Attempt(read()?.ifEmpty { null }, null, false)
        } catch (e: SecurityException) {
          warnings.add("telephony field '$field' denied: ${e.message}")
          Attempt(null, null, true)
        }

    val phoneType = attempt("phone_type", { telephonyManager.phoneType }, ::phoneTypeName)
    val simState = attempt("sim_state", { telephonyManager.simState }, ::simStateName)
    val networkOperator = attemptString("network_operator") { telephonyManager.networkOperator }
    val networkOperatorName =
        attemptString("network_operator_name") { telephonyManager.networkOperatorName }
    val simOperator = attemptString("sim_operator") { telephonyManager.simOperator }
    val simOperatorName = attemptString("sim_operator_name") { telephonyManager.simOperatorName }
    val networkCountryIso =
        attemptString("network_country_iso") { telephonyManager.networkCountryIso }
    val simCountryIso = attemptString("sim_country_iso") { telephonyManager.simCountryIso }

    val attempts =
        listOf(
            phoneType,
            simState,
            networkOperator,
            networkOperatorName,
            simOperator,
            simOperatorName,
            networkCountryIso,
            simCountryIso,
        )
    val anyValue = attempts.any { it.value != null }
    val allDenied = attempts.all { it.denied }

    val hasTelephonyFeature =
        try {
          context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        } catch (e: SecurityException) {
          false
        }

    val normalized = buildJsonObject {
      put("has_telephony_feature", hasTelephonyFeature)
      put("phone_type", phoneType.value)
      put("sim_state", simState.value)
      put("network_operator", networkOperator.value)
      put("network_operator_name", networkOperatorName.value)
      put("sim_operator", simOperator.value)
      put("sim_operator_name", simOperatorName.value)
      put("network_country_iso", networkCountryIso.value)
      put("sim_country_iso", simCountryIso.value)
    }

    val raw = buildJsonObject {
      put("phone_type_code", phoneType.code)
      put("sim_state_code", simState.code)
      put("network_operator", networkOperator.value)
      put("network_operator_name", networkOperatorName.value)
      put("sim_operator", simOperator.value)
      put("sim_operator_name", simOperatorName.value)
      put("network_country_iso", networkCountryIso.value)
      put("sim_country_iso", simCountryIso.value)
    }

    val status =
        when {
          anyValue -> CollectorStatus.AVAILABLE
          allDenied -> CollectorStatus.PERMISSION_DENIED
          else -> CollectorStatus.UNAVAILABLE
        }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = status,
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }

  private fun phoneTypeName(type: Int): String =
      when (type) {
        TelephonyManager.PHONE_TYPE_NONE -> "none"
        TelephonyManager.PHONE_TYPE_GSM -> "gsm"
        TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
        TelephonyManager.PHONE_TYPE_SIP -> "sip"
        else -> "type_$type"
      }

  private fun simStateName(state: Int): String =
      when (state) {
        TelephonyManager.SIM_STATE_UNKNOWN -> "unknown"
        TelephonyManager.SIM_STATE_ABSENT -> "absent"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network_locked"
        TelephonyManager.SIM_STATE_READY -> "ready"
        TelephonyManager.SIM_STATE_NOT_READY -> "not_ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "permanently_disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "card_io_error"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "card_restricted"
        else -> "state_$state"
      }
}
