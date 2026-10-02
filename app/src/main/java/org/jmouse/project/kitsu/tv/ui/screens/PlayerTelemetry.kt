package org.jmouse.project.kitsu.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.trapsFocus
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.DialogShape
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/**
 * What the player is doing, live — the privileged panel behind the bug button.
 *
 * <h2>⚠️ NOT `PlayerFileInfo`, AND THE DIFFERENCE IS TENSE</h2>
 *
 * That one describes the FILE: what it is, what is in it, what the server decided about it. It is true
 * before a frame is drawn and does not change while somebody watches. This one describes the RUN —
 * which stream is being read right now, how deep the buffer is, what the decoder is chewing, how many
 * frames went on the floor, how many times the connection has been picked back up. It changes four
 * times a second, which is the whole point of it.
 *
 * <h2>⚠️ ONE READING, NOT A LOG</h2>
 *
 * It answers "what is happening", never "what happened". A scrolling log is unreadable at three metres
 * and cannot be scrolled with a D-pad anyway, so the panel is a fixed set of rows that change in place.
 *
 * <h2>⚠️ ITS OWN FILE BECAUSE `PlayerScreen` IS ALREADY LONG</h2>
 *
 * And because nothing here is playback: it reads the player and draws, and a change to it cannot break
 * a film. It takes the delivery as a STRING rather than the player screen's `Delivery` — that type is
 * file-private there, and this panel has no business knowing how a stream is chosen.
 */
data class PlaybackTelemetry(
    val delivery: String,
    val state: String,
    val loading: Boolean,
    val speed: Float,
    val positionMillis: Long,
    val durationMillis: Long,
    val bufferedAheadMillis: Long,
    val bufferedPercent: Int,
    val video: String?,
    val audio: String?,
    val videoSize: String?,
    val droppedFrames: Int?,
    val reconnects: Int,
    val lastErrorCode: String?,
    val source: String?,
)

/** One sample of the run, taken from the player as it stands. */
@OptIn(UnstableApi::class)
fun sampleTelemetry(
    player: ExoPlayer,
    delivery: String,
    reconnects: Int,
    lastErrorCode: String?,
): PlaybackTelemetry = PlaybackTelemetry(
    delivery = delivery,
    state = when (player.playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> if (player.playWhenReady) "PLAYING" else "PAUSED"
        Player.STATE_ENDED -> "ENDED"
        else -> "?"
    },
    loading = player.isLoading,
    speed = player.playbackParameters.speed,
    positionMillis = player.currentPosition,
    durationMillis = player.duration.takeIf { millis -> millis != C.TIME_UNSET } ?: 0L,
    bufferedAheadMillis = player.totalBufferedDuration,
    bufferedPercent = player.bufferedPercentage,
    video = player.videoFormat?.let { format -> describeFormat(format) },
    audio = player.audioFormat?.let { format -> describeFormat(format) },
    videoSize = player.videoSize.takeIf { size -> size.width > 0 }?.let { size -> "${size.width}×${size.height}" },
    droppedFrames = player.videoDecoderCounters?.droppedBufferCount,
    reconnects = reconnects,
    lastErrorCode = lastErrorCode,
    /* ⚠️ The ticket is IN the address, so this is also the answer to "which stream is being read" —
       the one question somebody opens this panel to settle. */
    source = player.currentMediaItem?.localConfiguration?.uri?.toString(),
)

/** A decoder format in one line: what it is, how big, how fast. */
@OptIn(UnstableApi::class)
private fun describeFormat(format: Format): String = listOfNotNull(
    format.sampleMimeType?.substringAfterLast("/"),
    format.codecs,
    format.width.takeIf { width -> width > 0 }?.let { width -> "${width}×${format.height}" },
    format.frameRate.takeIf { rate -> rate > 0f }?.let { rate -> "%.3f fps".format(rate) },
    format.channelCount.takeIf { channels -> channels > 0 }?.let { channels -> "${channels}ch" },
    format.sampleRate.takeIf { rate -> rate > 0 }?.let { rate -> "$rate Hz" },
    format.bitrate.takeIf { bits -> bits > 0 }?.let { bits -> "%.1f Mbit/s".format(bits / 1_000_000f) },
).joinToString(" · ")

@Composable
fun TelemetryDialog(reading: PlaybackTelemetry?) {
    Box(
        modifier = Modifier.fillMaxSize().background(Kitsu.scrim).trapsFocus(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(860.dp)
                .clip(DialogShape)
                .background(Kitsu.backgroundOverlay)
                .border(1.dp, Kitsu.borderStrong, DialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = t("tv.player.telemetryTitle", "What the player is doing"), style = KitsuType.h2)

            if (reading == null) {
                Text(
                    text = t("tv.player.telemetryWaiting", "Taking the first reading…"),
                    style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow(t("tv.player.telemetryStream", "Stream"), muted = true)
                    TelemetryFact(t("tv.player.telemetryDelivery", "Delivery"), reading.delivery)
                    TelemetryFact(t("tv.player.telemetryState", "State"), reading.state)
                    TelemetryFact(
                        t("tv.player.telemetryLoading", "Loading"),
                        if (reading.loading) t("tv.common.yes", "yes") else t("tv.common.no", "no"),
                    )
                    TelemetryFact(t("tv.player.telemetrySpeed", "Speed"), "%.2f×".format(reading.speed))
                    TelemetryFact(t("tv.player.telemetryReconnects", "Reconnects"), reading.reconnects.toString())
                    TelemetryFact(t("tv.player.telemetryLastError", "Last error"), reading.lastErrorCode ?: "—")
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow(t("tv.player.telemetryPicture", "Picture and sound"), muted = true)
                    TelemetryFact(t("tv.player.telemetryVideo", "Video"), reading.video ?: "—")
                    TelemetryFact(t("tv.player.telemetryAudio", "Audio"), reading.audio ?: "—")
                    TelemetryFact(t("tv.player.telemetrySurface", "Surface"), reading.videoSize ?: "—")
                    TelemetryFact(
                        t("tv.player.telemetryDropped", "Dropped frames"),
                        reading.droppedFrames?.toString() ?: "—",
                    )
                    /* ⚠️ The unit is translated too. It read a bare "s" beside Ukrainian labels, which
                       is the kind of seam that makes a screen look half-finished — and it is one
                       word, so there was never a reason for it. */
                    val seconds = t("tv.common.secondsShort", "s")
                    TelemetryFact(
                        t("tv.player.telemetryBuffer", "Buffer"),
                        "${reading.bufferedAheadMillis / 1000} $seconds · ${reading.bufferedPercent}%",
                    )
                    TelemetryFact(
                        t("tv.player.telemetryAt", "At"),
                        "${reading.positionMillis / 1000} $seconds / ${reading.durationMillis / 1000} $seconds",
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(t("tv.player.telemetrySource", "Reading from"), muted = true)
                /* ⚠️ Wrapped rather than ellipsised: the ticket is at the END of the address, and an
                   address cut short is exactly the half nobody needs. */
                Text(
                    text = reading.source ?: "—",
                    style = KitsuType.meta.copy(color = Kitsu.foreground, fontFamily = DataFamily),
                )
            }
        }
    }
}

@Composable
private fun TelemetryFact(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = KitsuType.meta.copy(color = Kitsu.foreground, fontFamily = DataFamily),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
