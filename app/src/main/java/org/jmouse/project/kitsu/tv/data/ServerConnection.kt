package org.jmouse.project.kitsu.tv.data

import android.net.Uri
import org.jmouse.project.kitsu.tv.data.api.ApiClient
import org.jmouse.project.kitsu.tv.data.api.Credentials
import org.jmouse.project.kitsu.tv.data.api.KitsuApi
import okhttp3.OkHttpClient

/**
 * One server, and everything needed to talk to it: its address, the API bound
 * to it, the credentials the interceptor reads, and the two address builders a
 * picture and a stream need.
 */
class ServerConnection(
    val address: String,
    val displayName: String?,
    val credentials: Credentials = Credentials(),
    /** Called when any call is answered 401 — see `SessionLossInterceptor`. */
    onSessionLost: () -> Unit = {},
) {
    val httpClient: OkHttpClient = ApiClient.okHttp(credentials, onSessionLost)
    val api: KitsuApi = ApiClient.api(address, httpClient)

    /**
     * The client the PLAYER fetches bytes over — see {@code ApiClient.okHttpForMedia}.
     *
     * ⚠️ Deliberately not {@link #httpClient}. An hour of byte ranges has nothing in common with an
     * API call that answers in milliseconds, and the player used to run every range through the
     * API's interceptors and its thirty-second read timeout.
     */
    val mediaClient: OkHttpClient = ApiClient.okHttpForMedia()

    /**
     * A picture, wherever it currently lives — the same decision the web interface
     * makes in `catalog.ts#imageUrl`: a local copy wins, and the server serves it
     * openly at `/artwork`.
     *
     * ⚠️ NEVER THE PROVIDER'S ADDRESS. A picture the server has not copied yet is still
     * asked of the SERVER, at `/artwork/provider/{size}/{file}`: it downloads the picture,
     * keeps it, and serves it — so the television never reaches out to the film database,
     * and every picture it shows once is a picture the house keeps. Ivan, in as many words:
     * «не відображаємо медіа з лінками на провайдера ніколи».
     */
    fun imageUrl(path: String?, fileId: String?, size: ImageSize = ImageSize.THUMB): String? {
        if (!fileId.isNullOrBlank()) {
            return "${address}api/v1/artwork/$fileId"
        }
        if (path.isNullOrBlank()) {
            return null
        }
        /* ⚠️ ENCODED AS ONE SEGMENT. An IMDb picture travels as `imdb:MV5B…@.jpg`, and a `:` or an
           `@` left raw in a path is read differently by different stacks — encoded, it arrives as the
           single name the server matches against. */
        return "${address}api/v1/artwork/provider/${size.provider}/${Uri.encode(path.removePrefix("/"))}"
    }

    /**
     * An address the server handed over whole — a provider candidate's poster, say.
     *
     * ⚠️ The server answers these relative to itself (`/api/v1/artwork/provider/…`), because it
     * cannot know which of its addresses this television reached it by. Anything absolute is
     * passed through untouched.
     */
    fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        if (url.startsWith("/")) {
            return address.removeSuffix("/") + url
        }
        return url
    }

    /** A profile's face, from the open avatar route — or null, and the caller draws the letters. */
    fun avatarUrl(fileId: String?): String? {
        if (fileId.isNullOrBlank()) {
            return null
        }
        return "${address}api/v1/avatars/$fileId"
    }

    /**
     * The stream for a play ticket. A quality asks the server to re-encode on the
     * fly (no length, no seeking — a new `start` is a new request); none asks for
     * the file as it is, with byte ranges.
     */
    fun streamUrl(ticket: String, quality: Int? = null, startSeconds: Double = 0.0): String {
        val base = "${address}api/v1/stream/$ticket"
        if (quality == null) {
            return base
        }
        return "$base?quality=$quality&start=$startSeconds"
    }
}

enum class ImageSize(val provider: String) {
    THUMB("w342"),
    FULL("w780"),
    /* ⚠️ `original`, not w1280: the server's picture route offers exactly w342, w780 and original, and
       any other size is refused — a backdrop asked at w1280 would draw as nothing. A 1080p screen
       deserves the full picture anyway, and the server keeps the original whatever is asked. */
    BACKDROP("original"),
    ORIGINAL("original"),
}
