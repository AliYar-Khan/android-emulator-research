/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.collectors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pubgcompat.collector.CollectorResult
import com.pubgcompat.collector.CollectorStatus
import com.pubgcompat.collector.FingerprintCollector
import com.pubgcompat.collector.nowIso
import com.pubgcompat.collector.parser.NetDevParser
import com.pubgcompat.collector.util.ReadOnlyFiles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Network reachability state from the public `ConnectivityManager` API (permission already declared
 * in the manifest) plus interface names from `/proc/net/dev`.
 *
 * Privacy: MAC addresses, IP addresses, and traffic counters in `/proc/net/dev` are deliberately
 * never recorded - only which interfaces exist. Network state is volatile (transports change with
 * connectivity); compare captures with `diff`.
 */
class NetworkCollector : FingerprintCollector {

  override val name: String = "NetworkCollector"

  override fun collect(context: Context): CollectorResult {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager == null) {
      return CollectorResult(
          collector = name,
          timestamp = nowIso(),
          status = CollectorStatus.UNAVAILABLE,
          data = buildJsonObject {},
          warnings = listOf("ConnectivityManager service is not available"),
      )
    }

    val warnings = mutableListOf<String>()

    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
    if (activeNetwork == null) {
      warnings.add("no active network")
    }

    val netdevText = ReadOnlyFiles.read("/proc/net/dev")
    if (netdevText == null) {
      warnings.add("/proc/net/dev is not readable on this environment")
    }
    val interfaceNames = NetDevParser.parseInterfaceNames(netdevText ?: "")

    val normalized = buildJsonObject {
      put("has_active_network", activeNetwork != null)
      put(
          "transports",
          buildJsonObject {
            put("wifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false)
            put(
                "cellular",
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false,
            )
            put(
                "ethernet",
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false,
            )
            put("vpn", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false)
            put(
                "bluetooth",
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ?: false,
            )
          },
      )
      put(
          "validated",
          capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false,
      )
      put(
          "not_metered",
          capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false,
      )
      put("is_active_network_metered", connectivityManager.isActiveNetworkMetered)
      put(
          "interface_names",
          JsonArray(interfaceNames.map { JsonPrimitive(it) }),
      )
    }

    val raw = buildJsonObject {
      put(
          "interface_names",
          JsonArray(interfaceNames.map { JsonPrimitive(it) }),
      )
      put("netdev_readable", netdevText != null)
      capabilities?.let { caps ->
        put(
            "transport_masks",
            buildJsonObject {
              put("wifi", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
              put("cellular", caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
              put("ethernet", caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
              put("vpn", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
              put("bluetooth", caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
            },
        )
      }
    }

    return CollectorResult(
        collector = name,
        timestamp = nowIso(),
        status = CollectorStatus.AVAILABLE,
        data =
            buildJsonObject {
              put("raw", raw)
              put("normalized", normalized)
            },
        warnings = warnings,
    )
  }
}
