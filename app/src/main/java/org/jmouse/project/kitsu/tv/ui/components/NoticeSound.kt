package org.jmouse.project.kitsu.tv.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jmouse.project.kitsu.tv.data.api.KitsuApi
import java.io.File

/* ==========================================================================
   The sound a message makes.

   ⚠️ THE HOUSEHOLD CHOOSES IT ON THE SERVER, NOT ON THIS TELEVISION. `notices.sound` and
   `notices.soundName` are installation settings, so the browser and this client agree about
   whether the house makes a noise and which noise it is. A per-device preference would be the same
   decision taken twice, differently, in two rooms — and the room with the television is the one
   where getting it wrong is loudest.

   ⚠️ AND THE FILE IS THE SERVER'S TOO. Four sounds shipped in this APK and four more in the
   browser's bundle would make `notices.soundName` a word that meant two different noises, which is
   the one thing a shared setting exists to prevent.

   ⚠️ CACHED ON DISK AFTER THE FIRST PLAY. A television on a slow house network must not put a
   download between a message and its sound, and the file cannot change without the server being
   replaced.

   ⚠️ IT IS NEVER THE REASON ANYTHING FAILS. Every path here swallows what went wrong: a message
   that arrived is worth showing whether or not it could be heard, and a notice about a notice sound
   is worse than silence.
   ========================================================================== */

object NoticeSound {

    /** What the server last said. Off until it has said otherwise — see the browser's note on why. */
    private var on: Boolean = false
    private var name: String = "chime"

    /** Kept across plays: building one per message leaks players on a screen that is talking a lot. */
    private var player: MediaPlayer? = null

    /**
     * Takes the sound settings out of the client settings the shell already asks for.
     *
     * ⚠️ IT IS HANDED THE ANSWER RATHER THAN ASKING FOR ONE. The notice loop reads
     * `settings/client` to learn its pace; a second call for two more keys out of the same map
     * would be a second call for nothing.
     */
    fun read(values: Map<String, String>) {
        on = values["notices.sound"]?.trim().equals("true", ignoreCase = true)
        values["notices.soundName"]?.trim()?.takeIf { chosen -> chosen.isNotEmpty() }?.let { chosen ->
            name = chosen
        }
    }

    /** Makes the sound, if the household asked for one. */
    suspend fun play(context: Context, api: KitsuApi) {
        if (!on) {
            return
        }

        val file = runCatching { fetch(context, api, name) }.getOrNull() ?: return

        runCatching {
            withContext(Dispatchers.Main) {
                player?.release()
                player = MediaPlayer().apply {
                    /*
                      ⚠️ SONIFICATION, NOT MEDIA. On `USAGE_MEDIA` a television ducks or pauses what
                      is playing to make room for the chime — which is the opposite of what a nudge
                      should do to a film somebody is watching.
                     */
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(file.absolutePath)
                    setVolume(0.7f, 0.7f)
                    setOnCompletionListener { finished -> finished.release() }
                    prepare()
                    start()
                }
            }
        }
    }

    /** The file, downloaded once and kept in the cache directory under its own name. */
    private suspend fun fetch(context: Context, api: KitsuApi, sound: String): File =
        withContext(Dispatchers.IO) {
            val into = File(context.cacheDir, "notice-sounds").apply { mkdirs() }
            val file = File(into, "$sound.wav")

            if (file.exists() && file.length() > 0L) {
                return@withContext file
            }

            api.noticeSound(sound).byteStream().use { incoming ->
                file.outputStream().use { out -> incoming.copyTo(out) }
            }
            file
        }
}
