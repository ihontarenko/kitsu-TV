package org.jmouse.project.kitsu.tv.update

import org.jmouse.project.kitsu.tv.text.t
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jmouse.project.kitsu.tv.BuildConfig
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.AppRelease
import okhttp3.Request

/**
 * This app, updating itself from the server it talks to.
 *
 * <h2>⚠️ THE SERVER DECIDES WHAT IS NEWER, BY versionCode</h2>
 *
 * The server reads each APK's own manifest; this compares that code with [BuildConfig.VERSION_CODE],
 * which the build derives from the same `version.properties`. Names are for people — "0.2.0" — and are
 * never compared: a code only ever grows, a name can be anything somebody typed.
 *
 * <h2>⚠️ STREAMED STRAIGHT INTO THE INSTALLER</h2>
 *
 * The APK is not written to a file first. It is copied from the response into a [PackageInstaller]
 * session as it arrives, so there is no temporary copy to clean up and no storage permission to ask
 * for; the session is the file.
 *
 * <h2>⚠️ THE PERSON CONFIRMS, ALWAYS</h2>
 *
 * An ordinary app cannot install silently. The first time, the television asks for "install unknown
 * apps" to be allowed for this app ([Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES]); every time, the
 * system shows its own confirmation — see [InstallResultReceiver]. The app closes and reopens as the
 * new version.
 */
object AppUpdater {

    /** Where an update stands, for the screen to show. */
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val serverVersion: String?) : State
        data class Available(val release: AppRelease) : State
        data class Downloading(val release: AppRelease, val fraction: Float) : State
        /** ⚠️ Carries the release, so coming back from the settings offers THAT install again. */
        data class NeedsPermission(val release: AppRelease) : State
        data object Installing : State
        data class Failed(val reason: String) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    fun report(next: State) {
        mutableState.value = next
    }

    /** Asks the server for its newest release and says whether it is newer than this build. */
    suspend fun check(connection: ServerConnection) {
        mutableState.value = State.Checking
        mutableState.value = runCatching { connection.api.releases() }.fold(
            onSuccess = { answer ->
                val latest = answer.latest
                if (latest != null && latest.versionCode > BuildConfig.VERSION_CODE) {
                    State.Available(latest)
                } else {
                    State.UpToDate(latest?.shownName)
                }
            },
            onFailure = { State.Failed(it.message ?: t("tv.common.noAnswer", "The server did not answer.")) },
        )
    }

    /**
     * Downloads [release] into an installer session and commits it.
     *
     * ⚠️ The permission is checked BEFORE the download. Downloading forty megabytes and then asking
     * whether it may be installed wastes the download whenever the answer is "not yet".
     */
    suspend fun install(context: Context, connection: ServerConnection, release: AppRelease) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            mutableState.value = State.NeedsPermission(release)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        mutableState.value = State.Downloading(release, 0f)
        val outcome = runCatching {
            withContext(Dispatchers.IO) { streamIntoSession(context, connection, release) }
        }
        outcome.onFailure { mutableState.value = State.Failed(it.message ?: t("tv.update.downloadFailed", "The download failed.")) }
        outcome.onSuccess { mutableState.value = State.Installing }
    }

    private fun streamIntoSession(context: Context, connection: ServerConnection, release: AppRelease) {
        val installer = context.packageManager.packageInstaller
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(release.sizeBytes)
        }
        val sessionId = installer.createSession(parameters)

        installer.openSession(sessionId).use { session ->
            val request = Request.Builder()
                .url("${connection.address}api/v1/releases/android-tv/${release.fileId}/apk")
                .build()

            connection.httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { t("tv.update.downloadRefused", "The server refused the download ({code}).", "code" to response.code) }
                val body = response.body ?: error(t("tv.update.nothingSent", "The server sent nothing."))
                val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes

                session.openWrite("kitsu-tv.apk", 0, total).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var copied = 0L
                        var lastReported = 0f
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) {
                                break
                            }
                            output.write(buffer, 0, read)
                            copied += read
                            val fraction = if (total > 0) copied.toFloat() / total else 0f
                            /* Reported in whole percents — a state per 64 KiB recomposes the screen
                               hundreds of times a second for no visible change. */
                            if (fraction - lastReported >= 0.01f) {
                                lastReported = fraction
                                mutableState.value = State.Downloading(release, fraction)
                            }
                        }
                    }
                    session.fsync(output)
                }
            }

            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                /* ⚠️ MUTABLE: the installer writes the result into this intent's extras, and an
                   immutable one would arrive at the receiver empty. */
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private const val BUFFER_BYTES = 64 * 1024
}
