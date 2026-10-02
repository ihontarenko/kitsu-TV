package org.jmouse.project.kitsu.tv.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A server seen on the network.
 *
 * @param id      what the TXT record's `id` says, if anything — the thing a television is
 *                meant to remember instead of an address that changes with the router
 * @param address `http://host:port/`, ready to hand to the API client
 */
data class DiscoveredServer(
    val id: String?,
    val name: String,
    val host: String,
    val port: Int,
    val version: String?,
) {
    val address: String get() = "http://$host:$port/"
}

/**
 * Listens for `_kitsu._tcp` over mDNS/DNS-SD (ARCHITECTURE.md, "Пошук сервера без
 * введення IP") and emits every server as it resolves.
 *
 * ⚠️ mDNS does not cross subnets, and a guest network that isolates its clients
 * hides the server entirely. The manual address stays on the connect screen for
 * exactly that reason — this flow finding nothing is a fact about the network,
 * not a fault in the app.
 */
class ServerDiscovery(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discover(): Flow<DiscoveredServer> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed to start: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed to stop: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.startsWith(SERVICE_TYPE_PREFIX)) {
                    return
                }
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "could not resolve ${serviceInfo.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        val attributes = serviceInfo.attributes
                        trySend(
                            DiscoveredServer(
                                id = attributes["id"]?.decodeToString(),
                                name = attributes["name"]?.decodeToString() ?: serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                                version = attributes["version"]?.decodeToString(),
                            )
                        )
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private companion object {
        const val TAG = "ServerDiscovery"
        const val SERVICE_TYPE = "_kitsu._tcp."
        const val SERVICE_TYPE_PREFIX = "_kitsu._tcp"
    }
}
