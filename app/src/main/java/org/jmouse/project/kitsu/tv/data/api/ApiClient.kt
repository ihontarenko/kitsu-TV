package org.jmouse.project.kitsu.tv.data.api

import kotlinx.serialization.json.Json
import org.jmouse.project.kitsu.tv.text.t
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * What this television holds to speak to the server: the token the pairing
 * handed over, and the profile the person chose. Both are read on every request
 * by [AuthorizationInterceptor], so changing them here changes the next call
 * without rebuilding anything.
 */
class Credentials {
    @Volatile var deviceToken: String? = null
    @Volatile var profileId: String? = null

    /**
     * Which television this is — `android:<ANDROID_ID>`, sent as `X-Kitsu-Device-Fingerprint`.
     *
     * ⚠️ A HINT, NOT A CREDENTIAL. The server keeps its hash on the device row so a television
     * paired twice reads as one on the Devices screen; nothing is authorized by it.
     */
    @Volatile var fingerprint: String? = null
}

/**
 * `Authorization: Bearer <token>` and `X-Kitsu-Profile` — the two headers the
 * server's `DeviceTokenFilter` reads. Absent when there is nothing to send, so
 * the open routes (installation, pairing, session) work before pairing.
 */
class AuthorizationInterceptor(private val credentials: Credentials) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        credentials.deviceToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        credentials.profileId?.let { profile ->
            builder.header("X-Kitsu-Profile", profile)
        }
        credentials.fingerprint?.let { fingerprint ->
            builder.header("X-Kitsu-Device-Fingerprint", fingerprint)
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Notices, on ANY request, that the server no longer recognises this television.
 *
 * ⚠️ WHY AN INTERCEPTOR AND NOT A CHECK PER SCREEN. A device revoked on the Devices page, an account
 * signed out, a profile removed — the server answers the next call with 401, whichever screen made
 * it. Handled per screen, each one drew its own "HTTP 401" in the middle of a working-looking
 * library, and nothing sent the television back to the door it now has to go through. One place
 * that hears every answer is the only place that can say "the session is gone" for all of them.
 *
 * ⚠️ IT ONLY SIGNALS; the gate decides. What a 401 means — pair again, sign in again, pick a
 * profile — is the session's answer, not this interceptor's, so it asks nothing and changes nothing.
 *
 * ⚠️ THE OPEN ROUTES ARE IGNORED. `installation`, `pairing/…` and `auth/…` are what the gate itself
 * calls to find out where it stands; a 401 from one of those reporting "session lost" would send
 * the gate to ask the same route again, for ever.
 */
class SessionLossInterceptor(private val onSessionLost: () -> Unit) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == HTTP_UNAUTHORIZED && !isGateRoute(chain.request().url.encodedPath)) {
            onSessionLost()
        }
        return response
    }

    private fun isGateRoute(path: String): Boolean {
        val route = path.substringAfter("/api/v1/", missingDelimiterValue = path)
        return GATE_ROUTES.any { gate -> route.startsWith(gate) }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        val GATE_ROUTES = listOf("installation", "pairing", "auth/")
    }
}

/**
 * One Retrofit per server address. The address changes only when the person
 * picks another server, so a client is built per connection and kept.
 */
object ApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // A default value is still a value the server wants: `kind = "ANDROID_TV"` was silently
        // dropped from the pairing request until this was set, and the device paired as "unknown kind".
        encodeDefaults = true
        explicitNulls = false
    }

    fun okHttp(credentials: Credentials, onSessionLost: () -> Unit = {}): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthorizationInterceptor(credentials))
            .addInterceptor(SessionLossInterceptor(onSessionLost))
            .addInterceptor(logging)
            .build()
    }

    /**
     * A client for MEDIA BYTES, kept apart from the one the API talks over.
     *
     * ⚠️ A FILM IS NOT A REQUEST, IT IS AN HOUR OF THEM. The player was handed the API's own client,
     * which is built for calls that answer in milliseconds: a 30-second read timeout, a logging
     * interceptor and two application interceptors, all of which then ran on every byte range
     * ExoPlayer asked for — hundreds per film. None of that is what a long stream wants.
     *
     * ⚠️ NO AUTHORIZATION INTERCEPTOR, and that is not an oversight. Media is fetched on a TICKET in
     * the address — see `PlayTickets` on the server — so a bearer header on a byte range is a
     * credential sent where nothing reads it, on every chunk.
     *
     * ⚠️ NO LOGGING INTERCEPTOR. At `BASIC` it is one line per request, and a film is hundreds of
     * requests; the log it fills is the one somebody reads to find out why playback went wrong.
     *
     * ⚠️ A LONGER READ TIMEOUT, because the number means something different here. On an API call
     * thirty seconds of silence is a server that has gone; on a stream it is a buffer that is full
     * and a connection legitimately idle between ranges, and killing it makes ExoPlayer re-open the
     * range it was already holding.
     */
    fun okHttpForMedia(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            /* ⚠️ No call timeout at all: a call here IS the film. */
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun api(baseUrl: String, client: OkHttpClient): KitsuApi {
        return Retrofit.Builder()
            .baseUrl(normalise(baseUrl) + "api/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KitsuApi::class.java)
    }

    /**
     * `192.168.0.104` → `http://192.168.0.104:8130/`. A person typing an address
     * on a remote should not have to type the scheme or the default port.
     *
     * ⚠️ A TYPED SCHEME KEEPS ITS OWN DEFAULT PORT. A server published from home sits behind a
     * proxy on 80 or 443, and `http://jmouse.org` padded to `:8130` would ask the proxy's port for
     * the application's — a refused connection that looks like the server being down. Only a bare
     * `host` is taken to be Kitsu itself, on 8130.
     */
    fun normalise(address: String): String {
        var url = address.trim()
        val schemeTyped = url.startsWith("http://") || url.startsWith("https://")
        if (!schemeTyped) {
            url = "http://$url"
        }
        val afterScheme = url.substringAfter("://")
        val hostAndPath = afterScheme.substringBefore("/")
        if (!hostAndPath.contains(":") && !schemeTyped) {
            url = url.replaceFirst(hostAndPath, "$hostAndPath:$DEFAULT_PORT")
        }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    /**
     * Whether what was typed can be connected to at all — checked BEFORE a request, so a slip on the
     * pad reads as "that is not an address" instead of a five-second timeout and a socket error.
     *
     * ⚠️ A host must be an IP, `localhost`, or a name with a dot in it. `jmouse` alone parses as a
     * perfectly valid URL and resolves nowhere; `jmouse.org` is what was meant.
     */
    fun isUsableAddress(address: String): Boolean {
        if (address.isBlank() || address.any { it.isWhitespace() }) {
            return false
        }
        val url = normalise(address).toHttpUrlOrNull() ?: return false
        val host = url.host
        return host == "localhost" || host.contains('.') || host.contains(':')
    }

    const val DEFAULT_PORT = 8130
}

/** The server's ProblemDetail read out of a failed call, or the bare status when there is none. */
fun Throwable.describeForScreen(): String {
    if (this is HttpException) {
        /* ⚠️ A 429 in the television's own language: the server's sentence is English, and this one
           reaches a screen in another household — the parents' — where "wait a minute" has to be read. */
        if (code() == 429) {
            return t("tv.problem.tooManyRequests", "Too many requests from this address. Wait a minute and try again.")
        }
        val body = response()?.errorBody()?.string()
        if (!body.isNullOrBlank()) {
            runCatching { ApiClient.json.decodeFromString(Problem.serializer(), body) }
                .getOrNull()
                ?.let { problem ->
                    return problem.detail ?: problem.title ?: "HTTP ${code()}"
                }
        }
        return "HTTP ${code()}"
    }
    return message ?: javaClass.simpleName
}
