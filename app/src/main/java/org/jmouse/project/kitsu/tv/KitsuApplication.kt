package org.jmouse.project.kitsu.tv

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.jmouse.project.kitsu.tv.data.DeviceStore
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ApiClient
import org.jmouse.project.kitsu.tv.data.discovery.ServerDiscovery
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The application's one container. No injection framework: three long-lived
 * objects and a current connection, reachable from any screen through
 * [KitsuApplication.container].
 */
class AppContainer(context: Context) {

    val store = DeviceStore(context)
    val discovery = ServerDiscovery(context)

    /** Which television this is, worked out once — see [deviceFingerprint]. */
    private val fingerprint: String? = deviceFingerprint(context)

    private val current = MutableStateFlow<ServerConnection?>(null)

    /** The server this television is talking to, or null before one is chosen. */
    val connection: StateFlow<ServerConnection?> = current

    fun requireConnection(): ServerConnection {
        return current.value ?: error("No server connected")
    }

    /**
     * Binds to a server. The address is normalised (`192.168.0.104` becomes
     * `http://192.168.0.104:8130/`), and any credentials remembered for it are
     * put on the new connection so the next call is already signed.
     */
    /**
     * The server stopped recognising this television — a 401 from any request past the gate.
     *
     * ⚠️ A SIGNAL, NOT A STATE: the session model listens and asks the server where it now stands.
     * One slot and dropped overflow, because ten screens failing at once are one event, not ten.
     */
    private val lost = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sessionLost: SharedFlow<Unit> = lost

    fun connect(address: String, displayName: String?, token: String?, profileId: String?): ServerConnection {
        val connection = ServerConnection(
            address = ApiClient.normalise(address),
            displayName = displayName,
            onSessionLost = { lost.tryEmit(Unit) },
        )
        connection.credentials.deviceToken = token
        connection.credentials.profileId = profileId
        connection.credentials.fingerprint = fingerprint
        current.value = connection
        return connection
    }

    /**
     * What this television is, across every pairing of it — see `Credentials.fingerprint`.
     *
     * ⚠️ `ANDROID_ID`, which is exactly the documented use: stable for this app on this device until a
     * factory reset, and different for every app signing key, so it identifies nothing outside Kitsu.
     */
    @SuppressLint("HardwareIds")
    private fun deviceFingerprint(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { "android:$it" }

    fun disconnect() {
        current.value = null
    }

    /**
     * Which film database a provider search goes to, as last chosen — `TMDB`, `IMDB`, or null for
     * "the first one available".
     *
     * ⚠️ HELD HERE, not in a screen: Add title and the provider row in Search are two doors onto the
     * same choice, and a person who picked IMDb on one should find it picked on the other. For the
     * session only — which database to ask is a decision about this search, not a preference.
     */
    val providerSource = MutableStateFlow<String?>(null)
}

class KitsuApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Posters are looked at again and again; keep them, on disk as well as in memory. */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.2).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(120)
            .build()
    }
}

val Context.kitsuContainer: AppContainer
    get() = (applicationContext as KitsuApplication).container
