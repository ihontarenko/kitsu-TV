package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.Texts
import androidx.annotation.OptIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jmouse.project.kitsu.tv.data.api.Playable
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import androidx.compose.ui.draw.clip
import org.jmouse.project.kitsu.tv.ui.theme.DialogShape
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/**
 * What is playing, from both ends: the FILE as the server read it, and the STREAM as this television
 * is decoding it right now.
 *
 * <h2>⚠️ TWO COLUMNS BECAUSE THEY ANSWER DIFFERENT QUESTIONS</h2>
 *
 * "What is this file" is asked out of curiosity and is the same every time. "Why does it stutter" is
 * asked when something is wrong, and the answer is only ever in the live column — the decoder in use,
 * how much is buffered ahead, how many frames were dropped. Mixed into one list, the figure that
 * explains a stutter sits between the file size and the container.
 *
 * <h2>⚠️ THE LIVE COLUMN IS READ EVERY SECOND</h2>
 *
 * The buffer and the dropped frames are the point of it, and a snapshot taken when the sheet opened
 * would show a healthy buffer through the very stall somebody opened it to diagnose.
 *
 * @param deliveryLabel how the file is going out — as it is, or re-encoded and at what height
 * @param reconnects    how many times this run asked for the stream again after it stopped short
 * @param lastErrorCode the player's last failure, for the person reporting a stutter
 *
 * <h2>⚠️ IT ABSORBED THE DEBUG PANEL (2026-10-01)</h2>
 *
 * The state, the reconnects and the last error sat behind a bug icon only an administrator saw. They
 * are the same question this sheet answers — "why does it stutter" — so they live here now, in the
 * live column, and the second sheet with its own button is gone.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun FileInfoDialog(
    playable: Playable,
    deliveryLabel: String,
    player: ExoPlayer,
    reconnects: Int,
    lastErrorCode: String?,
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Kitsu.scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(860.dp)
                /* ⚠️ `DialogShape`, the same as the tracks panel it sits beside — the two open from
                   neighbouring buttons on one row, and a difference of radius between them is read
                   as one of them being broken. */
                .clip(DialogShape)
                .background(Kitsu.backgroundOverlay)
                .border(1.dp, Kitsu.borderStrong, DialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(t("tv.player.aboutFile", "About this file"), muted = true)
                Text(text = playable.fileName ?: playable.displayName, style = KitsuType.h2, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(t("tv.fileInfo.theFile", "The file"), muted = true, modifier = Modifier.padding(bottom = 4.dp))
                    val averageBitrate = playable.durationSeconds?.takeIf { it > 0 }?.let { playable.sizeBytes * 8 / it }
                    /* ⚠️ WHICH COPY, where there are several — the same words the chooser uses. */
                    val copyAt = playable.sources.indexOfFirst { it.assetId == playable.assetId }
                    if (playable.sources.size > 1 && copyAt >= 0) {
                        InfoLine(
                            t("tv.fileInfo.copy", "Copy"),
                            t("tv.fileInfo.copyOf", "{at} of {of}", "at" to copyAt + 1, "of" to playable.sources.size),
                        )
                    }
                    playable.sources.getOrNull(copyAt)?.let { copy ->
                        InfoLine(
                            t("tv.fileInfo.where", "Where"),
                            listOfNotNull(copy.rootName, copy.folder?.takeIf { it.isNotBlank() }).joinToString(" / "),
                        )
                    }
                    InfoLine(t("tv.file.size", "Size"), formatBytes(playable.sizeBytes))
                    InfoLine(t("tv.fileInfo.duration", "Duration"), playable.durationSeconds?.let { formatSeconds(it) })
                    InfoLine(t("tv.file.container", "Container"), containerOf(playable.fileName))
                    InfoLine(
                        t("tv.file.video", "Video"),
                        listOfNotNull(
                            playable.videoCodec?.uppercase(),
                            if (playable.width != null && playable.height != null) "${playable.width}×${playable.height}" else null,
                            resolution(playable.width, playable.height),
                        ).joinToString(" · ").ifBlank { null },
                    )
                    InfoLine(t("tv.fileInfo.averageBitrate", "Average bitrate"), formatBitrate(averageBitrate))
                    playable.tracks.filter { it.kind == "AUDIO" }.forEachIndexed { index, track ->
                        InfoLine(
                            if (index == 0) t("tv.player.audio", "Audio") else "",
                            listOfNotNull(languageName(track.language), track.codec?.uppercase(), channelsName(track.channels), track.title)
                                .joinToString(" · "),
                        )
                    }
                    playable.tracks.filter { it.kind == "SUBTITLE" }.forEachIndexed { index, track ->
                        InfoLine(
                            if (index == 0) t("tv.player.subtitles", "Subtitles") else "",
                            listOfNotNull(
                                languageName(track.language),
                                track.title,
                                t("tv.fileInfo.forced", "forced").takeIf { track.forced },
                            ).joinToString(" · "),
                        )
                    }
                }
                /* ⚠️ Keyed on the tick so the live figures are re-read every second. */
                key(tick) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow(t("tv.fileInfo.rightNow", "Right now"), muted = true, modifier = Modifier.padding(bottom = 4.dp))
                        InfoLine(t("tv.fileInfo.delivery", "Delivery"), deliveryLabel)
                        InfoLine(
                            t("tv.fileInfo.state", "State"),
                            when (player.playbackState) {
                                Player.STATE_BUFFERING -> t("tv.fileInfo.buffering", "buffering")
                                Player.STATE_ENDED -> t("tv.fileInfo.ended", "ended")
                                Player.STATE_IDLE -> t("tv.fileInfo.idle", "idle")
                                else -> if (player.playWhenReady) t("tv.fileInfo.playing", "playing") else t("tv.fileInfo.paused", "paused")
                            },
                        )
                        InfoLine(
                            t("tv.fileInfo.decodingVideo", "Decoding video"),
                            player.videoFormat?.let { format ->
                                listOfNotNull(
                                    format.sampleMimeType?.substringAfter('/')?.uppercase(),
                                    if (format.width > 0) "${format.width}×${format.height}" else null,
                                    format.frameRate.takeIf { it > 0 }?.let { rate -> formatFrameRate(rate.toDouble()) },
                                ).joinToString(" · ")
                            },
                        )
                        InfoLine(
                            t("tv.fileInfo.decodingAudio", "Decoding audio"),
                            player.audioFormat?.let { format ->
                                listOfNotNull(
                                    audioCodecName(format),
                                    channelsName(format.channelCount.takeIf { it > 0 }),
                                    format.sampleRate.takeIf { it > 0 }?.let { rate -> t("tv.unit.kilohertz", "{value} kHz", "value" to rate / 1000) },
                                ).joinToString(" · ")
                            },
                        )
                        InfoLine(
                            t("tv.fileInfo.canSeek", "Can seek"),
                            if (player.isCurrentMediaItemSeekable) t("tv.fileInfo.yes", "yes") else t("tv.fileInfo.seekRestarts", "no — a seek restarts the stream"),
                        )
                        val ahead = ((player.bufferedPosition - player.currentPosition) / 1000).coerceAtLeast(0)
                        InfoLine(t("tv.fileInfo.bufferedAhead", "Buffered ahead"), t("tv.unit.seconds", "{value} s", "value" to ahead))
                        InfoLine(
                            t("tv.fileInfo.reconnects", "Reconnects"),
                            reconnects.takeIf { it > 0 }?.toString(),
                        )
                        InfoLine(t("tv.fileInfo.lastError", "Last error"), lastErrorCode)
                        InfoLine(
                            t("tv.fileInfo.droppedFrames", "Dropped frames"),
                            player.videoDecoderCounters?.let { counters ->
                                t(
                                    "tv.series.ofTotal",
                                    "{count} of {total}",
                                    "count" to counters.droppedBufferCount,
                                    "total" to counters.renderedOutputBufferCount + counters.droppedBufferCount,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = label, style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle), modifier = Modifier.width(112.dp))
        Text(text = value, style = KitsuType.meta.copy(color = Kitsu.foreground), maxLines = 2)
    }
}

private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") {
        return null
    }
    val name = java.util.Locale.forLanguageTag(code).getDisplayLanguage(Texts.locale)
    return name.takeIf { it.isNotBlank() && it != code } ?: code.uppercase()
}

private fun channelsName(channels: Int?): String? = when (channels) {
    null -> null
    1 -> t("tv.fileInfo.mono", "mono")
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> t("tv.fileInfo.channels", "{count} ch", "count" to channels)
}

private fun audioCodecName(format: Format): String? = when (format.sampleMimeType) {
    null -> null
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC-3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_RAW -> t("tv.fileInfo.pcmDecoded", "PCM (decoded)")
    else -> format.sampleMimeType?.substringAfterLast('/')?.uppercase()
}
