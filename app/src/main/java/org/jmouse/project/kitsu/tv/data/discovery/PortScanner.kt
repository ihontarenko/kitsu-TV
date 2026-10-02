package org.jmouse.project.kitsu.tv.data.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jmouse.project.kitsu.tv.data.Scheme
import org.jmouse.project.kitsu.tv.data.ServerAddress
import org.jmouse.project.kitsu.tv.data.api.ApiClient
import org.jmouse.project.kitsu.tv.data.api.InstallationResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** A Kitsu server answering on a port nobody told the television about. */
data class ScannedServer(val address: String, val port: Int, val name: String?, val version: String)

/** What a scan says as it goes. */
sealed interface ScanEvent {
    data class Checking(val checked: Int, val total: Int) : ScanEvent
    data class Found(val server: ScannedServer) : ScanEvent
    data class NothingFound(val checked: Int) : ScanEvent
    data object Unresolvable : ScanEvent
}

/**
 * Finds the port Kitsu listens on, on one host, when it is not the 8130 it usually is.
 *
 * ⚠️ RECOGNISED BY THE API, NOT BY AN OPEN PORT. A port that accepts a connection is a router's admin
 * page as often as it is a library. A port counts only when `GET /api/v1/installation` answers 200
 * WITH the `X-Kitsu-Server` header — the one thing only this product sends — and a body carrying an
 * installation id. Anything else is somebody else's server and is passed over.
 *
 * ⚠️ TWO PASSES. The likely ports first, which finds nearly every real installation in a second. Only
 * on a household address does it then sweep the rest (1024–65535) — sweeping a host on the internet is
 * what a scanner does, and a server would be right to treat it as one. See [ServerAddress.isPrivate].
 */
class PortScanner {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /* ⚠️ Its own pool, far wider than Dispatchers.IO's 64: almost every probe is a connection refused
       or dropped, a thread spent waiting on nothing. 256 at a time sweeps a household host in about a
       minute where 64 would take four. */
    private val probes = Dispatchers.IO.limitedParallelism(BATCH)

    fun scan(scheme: Scheme, host: String): Flow<ScanEvent> = flow {
        val resolved = runCatching { InetAddress.getByName(host) }.getOrNull()
        if (resolved == null) {
            emit(ScanEvent.Unresolvable)
            return@flow
        }

        val ports = portsFor(host)
        val connectTimeout = if (ServerAddress.isPrivate(host)) LAN_CONNECT_MILLIS else REMOTE_CONNECT_MILLIS
        var checked = 0

        for (batch in ports.chunked(BATCH)) {
            val found = coroutineScope {
                batch.map { port ->
                    async(probes) { probe(scheme, host, resolved, port, connectTimeout) }
                }.awaitAll()
            }.firstOrNull { it != null }

            checked += batch.size
            if (found != null) {
                emit(ScanEvent.Found(found))
                return@flow
            }
            emit(ScanEvent.Checking(checked, ports.size))
        }

        emit(ScanEvent.NothingFound(checked))
    }.flowOn(Dispatchers.IO)

    private fun portsFor(host: String): List<Int> {
        if (!ServerAddress.isPrivate(host)) {
            return LIKELY_PORTS
        }
        return LIKELY_PORTS + (1024..65535).filterNot { it in LIKELY_PORTS }
    }

    /** One port: is anything there, and if so, is it Kitsu. */
    private suspend fun probe(
        scheme: Scheme,
        host: String,
        resolved: InetAddress,
        port: Int,
        connectTimeout: Int,
    ): ScannedServer? {
        val open = withContext(probes) {
            runCatching {
                Socket().use { socket -> socket.connect(InetSocketAddress(resolved, port), connectTimeout) }
                true
            }.getOrDefault(false)
        }
        if (!open) {
            return null
        }
        return identify(scheme, host, port)
    }

    private fun identify(scheme: Scheme, host: String, port: Int): ScannedServer? {
        val address = "${scheme.prefix}$host:$port/"
        val request = Request.Builder().url("${address}api/v1/installation").get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val version = response.header(SERVER_HEADER)
                if (!response.isSuccessful || version.isNullOrBlank()) {
                    return@use null
                }
                val installation = ApiClient.json.decodeFromString(InstallationResponse.serializer(), response.body?.string().orEmpty())
                if (installation.id.isBlank()) {
                    return@use null
                }
                ScannedServer(address = address, port = port, name = installation.displayName, version = version)
            }
        }.getOrNull()
    }

    private companion object {
        /** What `InstallationController` sends and nothing else does. */
        const val SERVER_HEADER = "X-Kitsu-Server"

        const val BATCH = 256

        /* A refused port on the household network answers at once; a dropped one never does, so the
           wait is what a sweep costs per batch. Further away, a real server can take longer to accept. */
        const val LAN_CONNECT_MILLIS = 300
        const val REMOTE_CONNECT_MILLIS = 1500

        /** Kitsu's own, the proxy ports, and what people move a service to when theirs is taken. */
        val LIKELY_PORTS = listOf(8130, 443, 80, 8443, 8080, 8131, 8000, 8081, 8888, 9000, 9090, 5080)
    }
}
