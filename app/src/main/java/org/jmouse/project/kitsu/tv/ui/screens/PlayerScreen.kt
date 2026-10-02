package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.Texts
import org.jmouse.project.kitsu.tv.ui.components.trapsFocus
import org.jmouse.project.kitsu.tv.ui.components.NoticeHost
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.jmouse.project.kitsu.tv.data.ImageSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.compose.material.icons.outlined.StopCircle
import retrofit2.HttpException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.Playable
import org.jmouse.project.kitsu.tv.data.api.PlaybackSource
import org.jmouse.project.kitsu.tv.data.api.Position
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.BrandLockup
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.theme.DialogShape
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.components.focusRing
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvIconButton
import org.jmouse.project.kitsu.tv.ui.components.indexTab
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

private const val CHROME_TIMEOUT_MILLIS = 2600L
private const val SEEK_STEP_MILLIS = 10_000L
private const val PROGRESS_EVERY_MILLIS = 5000L
private const val UP_NEXT_SECONDS = 8
private const val MAX_RECONNECTS = 3

/**
 * The statuses that mean "not yours any more" rather than "not reachable right now".
 *
 * ⚠️ 404 is among them on purpose: the server answers a film closed to this profile exactly as it
 * answers one that does not exist, so that a refusal does not confirm the film is here.
 */
private val REFUSED_STATUSES = setOf(401, 403, 404, 410)

/**
 * Where a play begins: where it was left, unless that is the end.
 *
 * ⚠️ The closing stretch counts as the end — the same tolerance the player uses to decide a stream
 * really ended. Resuming in the credits of something already watched is resuming nothing.
 */
private fun resumePointMillis(playable: Playable): Long {
    val runtime = playable.durationSeconds ?: 0
    val at = playable.resumeAtSeconds
    val nearTheEnd = runtime > 0 && runtime - at <= END_TOLERANCE_MILLIS / 1000
    return if (playable.finished || nearTheEnd) 0L else at * 1000L
}

/** Why playback was stopped from outside this screen. */
/**
 * Why playback stopped on its own.
 *
 * ⚠️ `CopyUnreachable` IS NOT `AccessClosed`. A re-encode of a copy on a drive the media service
 * cannot see answered 404, and the screen said "no longer open to this profile" about a film the
 * profile was allowed to watch. The server answers 422 `drive-unreachable` for it now.
 */
enum class Stopped { ByConsole, AccessClosed, CopyUnreachable }

/** What the server answers when THIS copy cannot be re-encoded — see `Stopped.CopyUnreachable`. */
private const val COPY_UNREACHABLE_STATUS = 422
private const val SEEK_FLASH_MILLIS = 1400L

/** How close to the known runtime a stream has to stop to count as the end of the film. */
private const val END_TOLERANCE_MILLIS = 45_000L

private val UP_NEXT_HEIGHT = 144.dp

/** What the last run of quiet seeks added up to, and when the last one landed. */
private data class SeekFlash(val seconds: Int, val stamp: Long)

/**
 * How the file is reaching the screen. Direct play is the file as it is, with
 * seeking by byte range; a transcode is a fresh encode from `start`, with no
 * length and no seeking — a seek there is a new request.
 */
private sealed interface Delivery {
    data object Direct : Delivery
    data class Transcode(val quality: Int, val startSeconds: Double) : Delivery
}

/**
 * The player: the picture is the screen. Two thin bands of chrome rise from its
 * edges on any press and fold away after 2.6 s of nothing; the playback
 * decision stays in the corner while they are up, because it is the first
 * question when the picture stutters.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    connection: ServerConnection,
    playKey: String,
    /** The copy that played the episode before this one — this one comes from the same folder. */
    sameAs: String? = null,
    onLeave: () -> Unit,
    /** ⚠️ With the copy that is playing, so the next episode stays in its folder (MH-302). */
    onNext: (playKey: String, sameAs: String?) -> Unit,
) {
    val context = LocalContext.current
    var playable by rememberLoaded<Playable>()
    var attempt by remember { mutableIntStateOf(0) }

    /* ⚠️ WHICH COPY, where a work has several — null is the server's default, the biggest. Chosen in
       the playback sheet; switching asks the server again for that copy's ticket and tracks. */
    var source by remember(playKey) { mutableStateOf<String?>(null) }

    /* ⚠️ WHERE THE FILM WAS when the copy was switched. The server's resume point is only as fresh as
       the last progress write, so the live position is carried across instead. */
    var carriedMillis by remember(playKey) { mutableStateOf<Long?>(null) }

    LaunchedEffect(playKey, attempt, source) {
        playable = Loaded.Pending
        playable = runCatching { connection.api.playable(playKey, source, sameAs) }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    val player = remember(connection) {
        /* ⚠️ THE MEDIA CLIENT, NOT THE API'S. A film is an hour of byte ranges; the API's client is
           built for calls that answer in milliseconds, and every range was going through its
           logging interceptor, its authorization header and its thirty-second read timeout. See
           `ApiClient.okHttpForMedia`. */
        val dataSourceFactory = OkHttpDataSource.Factory(connection.mediaClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            /*
              ⚠️ A DEEPER BUFFER THAN THE DEFAULT, because this is a household file server rather
              than a content network. The default holds about fifty seconds and re-fills from two and
              a half; a 4.5 Mbit remux over house Wi-Fi with a PC at the other end has minutes where
              it cannot keep that up, and a renderer that runs dry drops frames rather than pausing —
              which is what makes a stall look like a decode problem instead of a supply one.

              ⚠️ Four minutes rather than "as much as possible": the buffer is heap on a television
              with very little of it, and a set-top box that runs out of memory is not slow, it is
              gone.
             */
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(120_000, 240_000, 2_500, 5_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build()
            .apply {
                playWhenReady = true

                /* ⚠️ THE WAKE LOCK THE MANIFEST HAS ALWAYS ASKED FOR AND NOTHING EVER TOOK.
                   `android.permission.WAKE_LOCK` is declared and, until now, was used by no line in
                   the application — a permission nobody exercises. This is what exercises it: the
                   film is a network stream, so the radio has to stay up as well as the CPU, and a
                   device that dozes mid-film stalls on the next byte range rather than pausing. */
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    /* ⚠️ WHY PLAYBACK STOPPED, where something outside this screen stopped it — the console, or a
       rule closing what was playing. Keyed on the attempt, so "Play again" starts clean. */
    var stopped by remember(playKey, attempt, source) { mutableStateOf<Stopped?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = playable) {
            Loaded.Pending -> Loading(t("tv.player.asking", "Asking the server for the file…"))
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> {
                val why = stopped
                if (why != null) {
                    /* ⚠️ The best OTHER copy, offered where this one cannot be re-encoded — biggest
                       first, as the server's own default chooses. */
                    val otherCopy = current.value.sources
                        .filter { it.assetId != current.value.assetId }
                        .maxByOrNull { (it.height ?: 0) * 1_000_000_000L + it.sizeBytes / 1_000 }
                    PlaybackStopped(
                        why,
                        onReplay = { attempt++ },
                        onLeave = onLeave,
                        onOtherCopy = otherCopy?.let { copy -> { source = copy.assetId } },
                    )
                } else {
                    Playing(
                        connection,
                        player,
                        current.value,
                        onLeave,
                        onNext = { next -> onNext(next, current.value.assetId) },
                        startAtMillis = carriedMillis,
                        onSource = { assetId, atMillis ->
                            carriedMillis = atMillis
                            source = assetId
                        },
                        onStopped = { reason ->
                            /* ⚠️ STOP, NOT PAUSE. `stop()` closes the open range request and drops the
                           buffer; a pause would keep minutes of film one button away — exactly what
                           the console asked not to happen. The item is kept, so the position the
                           screen writes on its way out is still the real one. */
                            player.stop()
                            stopped = reason
                        },
                    )
                }
            }
        }
        /* ⚠️ The player is drawn without the page scaffold, so it carries the notice host itself —
           a rule greeting playback ("enjoy the film") speaks exactly while the player is open. */
        NoticeHost()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Playing(
    connection: ServerConnection,
    player: ExoPlayer,
    playable: Playable,
    onLeave: () -> Unit,
    onNext: (String) -> Unit,
    /** Where to start instead of the server's resume point — the position carried across a copy switch. */
    startAtMillis: Long?,
    /** Another copy was chosen in the playback sheet — with where the film is now. */
    onSource: (assetId: String, atMillis: Long) -> Unit,
    onStopped: (Stopped) -> Unit,
) {
    var delivery by remember(playable.playKey) { mutableStateOf<Delivery>(Delivery.Direct) }
    var chromeShown by remember { mutableStateOf(true) }
    var lastPress by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var positionMillis by remember { mutableLongStateOf(0L) }
    var durationMillis by remember { mutableLongStateOf((playable.durationSeconds ?: 0) * 1000L) }
    var bufferedMillis by remember { mutableLongStateOf(0L) }
    var problem by remember { mutableStateOf<String?>(null) }

    /* ⚠️ THE CODE IS KEPT, IT IS JUST NO LONGER THE MESSAGE. `errorCodeName` is what somebody needs
       when they come to ask why, and it is the one thing a sentence cannot replace — so it is drawn
       small and below rather than thrown away. */
    var problemCode by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf<Tracks>(Tracks.EMPTY) }
    var upNextIn by remember { mutableIntStateOf(UP_NEXT_SECONDS) }

    /* ⚠️ AUTOPLAY IS THE PROFILE'S TO DECIDE — `playback.autoplayNext`, which the browser has
       offered as a switch for a long time and nothing honoured (MH-288). Unknown until asked, and
       unknown counts as on: that is the declared default.

       ⚠️ AND `playback.upNextAtPercent` RIDES THE SAME READ — how far through the card appears, read
       here so there is one request for both. Out of range or unreadable falls back to the default
       rather than suppressing the card. */
    var autoplay by remember { mutableStateOf(true) }
    var upNextAtPercent by remember { mutableIntStateOf(UP_NEXT_AT_PERCENT_DEFAULT) }
    LaunchedEffect(connection) {
        val preferences = runCatching { connection.api.preferences() }.getOrNull()

        autoplay = preferences
            ?.firstOrNull { preference -> preference.key == AUTOPLAY_NEXT_PREFERENCE }
            ?.let { preference -> (preference.value ?: preference.defaultTo) != "false" }
            ?: true

        upNextAtPercent = preferences
            ?.firstOrNull { preference -> preference.key == UP_NEXT_AT_PERCENT_PREFERENCE }
            ?.let { preference -> (preference.value ?: preference.defaultTo)?.toIntOrNull() }
            ?: UP_NEXT_AT_PERCENT_DEFAULT
    }

    /* ⚠️ The countdown stops the moment somebody reaches for the card. Moving to "Watch again" while
       the count ran on started the next episode under their thumb. */
    var upNextHeld by remember(playable.playKey) { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }

    /* ⚠️ THE DEBUG PANEL STOOD HERE and went on 2026-10-01: its live figures — state, reconnects, the
       last error — now sit in «About this file», which every viewer can open, rather than behind a
       bug icon only an administrator saw. */
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var reconnects by remember(playable.playKey) { mutableIntStateOf(0) }

    /** The position in the FILM: for a transcode the player counts from `start`. */
    fun filmPositionMillis(): Long {
        val offset = (delivery as? Delivery.Transcode)?.startSeconds?.times(1000)?.toLong() ?: 0L
        return offset + player.currentPosition.coerceAtLeast(0)
    }

    fun load(target: Delivery, resumeMillis: Long) {
        delivery = target
        val url = when (target) {
            Delivery.Direct -> connection.streamUrl(playable.ticket)
            is Delivery.Transcode -> connection.streamUrl(playable.ticket, target.quality, target.startSeconds)
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (target is Delivery.Direct && resumeMillis > 0) {
            player.seekTo(resumeMillis)
        }
        player.play()
    }

    /**
     * Changing how the same film is delivered, without losing the place.
     *
     * ⚠️ THE POSITION IS CARRIED ACROSS, and it has to be computed before the switch. For a
     * transcode the player counts from the encode's own start, so `player.currentPosition` alone is
     * the offset INTO THIS ENCODE, not into the film — reading it after `delivery` has changed
     * gives a number measured against the wrong origin, and the film restarts some minutes early or
     * late. `filmPositionMillis()` is the only figure that means the same thing either side.
     *
     * ⚠️ A DIRECT PLAY RESUMES BY SEEKING; a transcode resumes by being ASKED FOR from that second.
     * There is no seeking within a fragmented stream the server is producing as it goes, which is
     * the whole reason these two paths differ.
     */
    fun deliverAs(quality: Int?) {
        val at = filmPositionMillis()
        if (quality == null) {
            load(Delivery.Direct, at)
        } else {
            load(Delivery.Transcode(quality, at / 1000.0), 0)
        }
    }

    /** Seeking: a byte range for a direct play; a new request from `seconds` for a transcode. */
    fun seekTo(targetMillis: Long) {
        val clamped = targetMillis.coerceIn(0, if (durationMillis > 0) durationMillis else Long.MAX_VALUE)
        when (val current = delivery) {
            /* ⚠️ A file the player cannot seek in — a Matroska with no cue index, most often — ignores
               seekTo without a word, and the scrubber moved while the picture did not. The server CAN
               start an encode at any second, so that is where such a seek goes. */
            Delivery.Direct -> if (player.isCurrentMediaItemSeekable || playable.qualities.isEmpty()) {
                player.seekTo(clamped)
            } else {
                load(Delivery.Transcode(playable.qualities.max(), clamped / 1000.0), 0)
            }
            is Delivery.Transcode -> load(Delivery.Transcode(current.quality, clamped / 1000.0), 0)
        }
    }

    /**
     * A seek from the remote while the film is playing with nothing on screen.
     *
     * ⚠️ IT DOES NOT RAISE THE CHROME. It used to, and the next LEFT or RIGHT then moved the cursor
     * across the control row instead of seeking again — so "back thirty seconds" became one seek and
     * two trips through the buttons. A small flash says what happened, and repeated presses add up in
     * it ("+30 s") the way a person counts them.
     */
    fun quietSeek(deltaMillis: Long) {
        seekTo(filmPositionMillis() + deltaMillis)
        val now = System.currentTimeMillis()
        val previous = seekFlash
        val stepSeconds = (deltaMillis / 1000).toInt()
        val carried = if (previous != null && now - previous.stamp < SEEK_FLASH_MILLIS && (previous.seconds > 0) == (stepSeconds > 0)) previous.seconds else 0
        seekFlash = SeekFlash(seconds = carried + stepSeconds, stamp = now)
    }

    suspend fun report(finished: Boolean = false) {
        val seconds = (filmPositionMillis() / 1000).toInt()
        val duration = (durationMillis / 1000).toInt().takeIf { it > 0 } ?: playable.durationSeconds
        runCatching { connection.api.recordProgress(playable.playKey, Position(if (finished) duration ?: seconds else seconds, duration)) }
            /* ⚠️ THE ANSWER IS READ NOW. This client ignored it, so the console's "Stop playback"
               left the television playing on from its buffer while the web player stopped (MH-282). */
            .onSuccess { answer ->
                if (answer.halted) {
                    onStopped(Stopped.ByConsole)
                }
            }
            /* ⚠️ And a refusal of the report itself is a rule closing what is playing — the film,
               its folder, this profile. A network blip is not: only a status that says "not yours". */
            .onFailure { failure ->
                if (failure is HttpException && failure.code() in REFUSED_STATUSES) {
                    onStopped(Stopped.AccessClosed)
                }
            }
    }

    /*
      First load: the file as it is, from where it was left.

      ⚠️ BUT NEVER FROM THE END. A finished episode is reported with its position AT its duration, and
      this seeked straight there: the player opened on the last two seconds, ended, and offered the
      next episode — every time, so an episode that ended while nobody was looking could not be
      watched at all (MH-284). Finished, or inside the closing stretch, starts from the beginning —
      the same answer the browser gives with `finished`.
     */
    /* ⚠️ Keyed on the COPY too: switching copies is the same work under another ticket, and the
       picture has to be the new file's, from where the old one was. */
    LaunchedEffect(playable.playKey, playable.assetId) {
        load(Delivery.Direct, startAtMillis ?: resumePointMillis(playable))
    }

    /* What the player says. A decoder that cannot play the file is the one failure
       with an answer: ask the server to re-encode at the best quality it offers. */
    DisposableEffect(player, playable.playKey) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    reconnects = 0
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    /*
                      ⚠️ A STREAM THAT STOPS IS NOT AN EPISODE THAT ENDED. A re-encode that the server
                      cut short — several seeks in a row each start a new one — reaches the player as a
                      clean end of stream, and this used to take it at its word: the episode was marked
                      watched and the next one started three minutes into the last. Only an end within
                      reach of the known runtime is the end; anything earlier is asked for again from
                      where it stopped, like a dropped connection.
                     */
                    val at = filmPositionMillis()
                    val runtime = durationMillis
                    if (runtime > 0 && runtime - at > END_TOLERANCE_MILLIS && reconnects < MAX_RECONNECTS) {
                        reconnects++
                        when (val current = delivery) {
                            Delivery.Direct -> load(Delivery.Direct, at)
                            is Delivery.Transcode -> load(Delivery.Transcode(current.quality, at / 1000.0), 0)
                        }
                    } else {
                        ended = true
                        /* The controls come up at the end only when nothing follows — with a next
                           episode the up-next card IS the end screen, and the control row under it
                           was two sets of buttons on top of each other. */
                        chromeShown = playable.nextPlayKey == null
                    }
                }
                if (playbackState == Player.STATE_READY && delivery is Delivery.Direct && player.duration > 0) {
                    durationMillis = player.duration
                }
            }

            override fun onTracksChanged(changed: Tracks) {
                tracks = changed
            }

            override fun onPlayerError(error: PlaybackException) {
                /* ⚠️ A TICKET THAT WAS WITHDRAWN IS NOT A CONNECTION THAT DROPPED. Closing access —
                   or the console stopping this device — withdraws the tickets, and the next range
                   request is refused. Reconnecting with the same ticket only asks to be refused
                   again, several times, before ending on a sentence about the network. */
                val refusedWith = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (refusedWith == COPY_UNREACHABLE_STATUS) {
                    onStopped(Stopped.CopyUnreachable)
                    return
                }
                if (refusedWith != null && refusedWith in REFUSED_STATUSES) {
                    onStopped(Stopped.AccessClosed)
                    return
                }
                /* ⚠️ A CONNECTION THAT DROPPED IS NOT A DECODER THAT REFUSED. Every error used to fall
                   back to a re-encode, so a network hiccup a minute in turned a perfectly playable
                   file into a lower-quality transcode. Network and server errors (the 2xxx codes) are
                   answered by asking for the same thing again from where it stopped — a few times —
                   and only a file this television cannot decode is sent to the encoder. */
                val network = error.errorCode in 2000..2999
                if (network && reconnects < MAX_RECONNECTS) {
                    reconnects++
                    val at = filmPositionMillis()
                    when (val current = delivery) {
                        Delivery.Direct -> load(Delivery.Direct, at)
                        is Delivery.Transcode -> load(Delivery.Transcode(current.quality, at / 1000.0), 0)
                    }
                    return
                }
                val best = playable.qualities.maxOrNull()
                if (!network && delivery is Delivery.Direct && best != null) {
                    val at = filmPositionMillis() / 1000.0
                    load(Delivery.Transcode(best, at), 0)
                } else {
                    problem = playbackSentence(error)
                    problemCode = error.errorCodeName.removePrefix("ERROR_CODE_")
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    /* The clock: position, buffering, and the chrome's own timeout. */
    LaunchedEffect(player) {
        while (true) {
            positionMillis = filmPositionMillis()
            bufferedMillis = ((delivery as? Delivery.Transcode)?.startSeconds?.times(1000)?.toLong() ?: 0L) + player.bufferedPosition

            if (chromeShown && isPlaying && !ended && !tracksOpen && !infoOpen && System.currentTimeMillis() - lastPress > CHROME_TIMEOUT_MILLIS) {
                chromeShown = false
            }
            delay(250)
        }
    }

    /* Where we are, told to the server every five seconds while playing — the
       same key the browser reads, so both pick up the same place. */
    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_EVERY_MILLIS)
            if (isPlaying) {
                report()
            }
        }
    }

    /* ⚠️ THE CARD MAY COME UP BEFORE THE END — `playback.upNextAtPercent`. The position is the FILM's
       (`positionMillis` is `filmPositionMillis()`, so a transcode's offset is already in it), measured
       against the whole. 100, or a length not yet known, means it waits for the real end. */
    val nearEnd = durationMillis > 0 && upNextAtPercent in 1..99 &&
        positionMillis >= durationMillis / 100 * upNextAtPercent
    val offerNext = (ended || nearEnd) && playable.nextPlayKey != null

    /* At the real end: say so. ⚠️ Finished is the END, never the threshold — a card at 95% has not
       been watched through, and the leaving report (onDispose) records the position for the server to
       judge against `library.watchedAtPercent`. */
    LaunchedEffect(ended) {
        if (ended) {
            report(finished = true)
        }
    }

    /* Offer the next episode with a count, once it is time — at the end, or at the threshold. */
    LaunchedEffect(offerNext) {
        if (offerNext) {
            /* ⚠️ With autoplay off the card waits, exactly as if somebody had reached for it. */
            upNextHeld = !autoplay
            upNextIn = UP_NEXT_SECONDS
            while (upNextIn > 0 && !upNextHeld) {
                delay(1000)
                upNextIn--
            }
            if (!upNextHeld) {
                playable.nextPlayKey?.let { next -> onNext(next) }
            }
        }
    }

    /*
      ⚠️ HOLDING THE SCREEN AWAKE MOVED TO `MainActivity`, and this is the note that used to do it.

      A television measures idleness by INPUT, and watching a film is the one thing somebody does
      with a remote they never touch — so after the system's timeout the daydream took the screen
      while the film went on playing behind it. `WAKE_LOCK` in the manifest does not do this: it is
      about the CPU, and the screen is a separate claim that a window makes for itself.

      This screen used to make that claim on its own view and tie it to `isPlaying`, on the reasoning
      that a film left paused is a television nobody is watching. The reasoning was sound and the
      SCOPE was wrong: idleness took the panel everywhere ELSE in the application too — on a series
      screen somebody was reading, on the shelf they were choosing from — and this set answers a
      daydream with a music channel, which reads as Kitsu having done something.

      So the claim is now the window's, held for as long as Kitsu is in front and released the
      instant it is not. A per-view flag under a window that already has it could only ever be a
      no-op with a comment arguing the opposite.
     */

    /* Leaving: the position is kept. */
    DisposableEffect(Unit) {
        onDispose {
            val seconds = (filmPositionMillis() / 1000).toInt()
            val duration = (durationMillis / 1000).toInt().takeIf { it > 0 } ?: playable.durationSeconds
            if (!ended && seconds > 0) {
                Thread {
                    runCatching {
                        kotlinx.coroutines.runBlocking {
                            connection.api.recordProgress(playable.playKey, Position(seconds, duration))
                        }
                    }
                }.start()
            }
        }
    }

    val controlsFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }

    fun wake() {
        lastPress = System.currentTimeMillis()
        chromeShown = true
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        wake()
    }

    /* ⚠️ `ended` goes back to false as well. Seeking to zero after the end played the episode again
       under the "Up next" card, which stayed because nothing said the end was over. */
    fun startOver() {
        ended = false
        upNextHeld = false
        seekTo(0)
        player.play()
        wake()
    }

    /* ⚠️ THE END CARD KEEPS ITS FOCUS. The chrome folds away when an episode ends with another after
       it, and folding away used to pull focus back to the surface — out from under the card. */
    val upNextShown = offerNext

    LaunchedEffect(chromeShown, upNextShown) {
        if (chromeShown) {
            runCatching { controlsFocus.requestFocus() }
        } else if (!upNextShown) {
            runCatching { surfaceFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    event.key == Key.MediaPlayPause -> { togglePlay(); true }
                    event.key == Key.MediaPlay -> { player.play(); wake(); true }
                    event.key == Key.MediaPause -> { player.pause(); wake(); true }
                    event.key == Key.MediaFastForward && !chromeShown -> { quietSeek(SEEK_STEP_MILLIS); true }
                    event.key == Key.MediaRewind && !chromeShown -> { quietSeek(-SEEK_STEP_MILLIS); true }
                    event.key == Key.MediaFastForward -> { seekTo(filmPositionMillis() + SEEK_STEP_MILLIS); wake(); true }
                    event.key == Key.MediaRewind -> { seekTo(filmPositionMillis() - SEEK_STEP_MILLIS); wake(); true }
                    event.key == Key.MediaNext && playable.nextPlayKey != null -> { onNext(playable.nextPlayKey); true }
                    event.key == Key.MediaPrevious && playable.previousPlayKey != null -> { onNext(playable.previousPlayKey); true }
                    event.key == Key.Info -> { infoOpen = !infoOpen; wake(); true }
                    event.key == Key.Back || event.key == Key.Escape -> {
                        if (infoOpen) {
                            infoOpen = false
                            runCatching { controlsFocus.requestFocus() }
                            true
                        } else if (tracksOpen) {
                            tracksOpen = false
                            runCatching { controlsFocus.requestFocus() }
                            true
                        } else if (chromeShown && isPlaying && !ended) {
                            chromeShown = false
                            true
                        } else {
                            onLeave()
                            true
                        }
                    }
                    /* ⚠️ THE CARD'S OWN KEYS. With the chrome folded, every D-pad press below was taken
                       by the player — OK toggled play, Left and Right seeked into the last seconds —
                       and the "Up next" card could never be reached (MH-284). */
                    upNextShown -> {
                        upNextHeld = true
                        false
                    }
                    !chromeShown -> {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> { togglePlay(); true }
                            Key.DirectionLeft -> { quietSeek(-SEEK_STEP_MILLIS); true }
                            Key.DirectionRight -> { quietSeek(SEEK_STEP_MILLIS); true }
                            else -> { wake(); true }
                        }
                    }
                    else -> {
                        lastPress = System.currentTimeMillis()
                        false
                    }
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        /* An invisible thing to hold the focus while the chrome is folded, so the
           key events above have somewhere to arrive. */
        Box(modifier = Modifier.size(1.dp).focusRequester(surfaceFocus).focusable())

        if (chromeShown) {
            Chrome(
                playable = playable,
                delivery = delivery,
                isPlaying = isPlaying,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                bufferedMillis = bufferedMillis,
                tracks = tracks,
                problem = problem,
                problemCode = problemCode,
                controlsFocus = controlsFocus,
                onTogglePlay = ::togglePlay,
                onSeek = { delta -> seekTo(filmPositionMillis() + delta); wake() },
                onTracks = { tracksOpen = true; wake() },
                onInfo = { infoOpen = true; wake() },
                onRestart = { startOver() },
                onNext = playable.nextPlayKey?.let { next -> { onNext(next) } },
                /* ⚠️ The same way across as "Next": the player's route is replaced, not stacked, so Back
                   still leaves to where the programme was opened from. */
                onPrevious = playable.previousPlayKey?.let { previous -> { onNext(previous) } },
                onAnyFocus = { lastPress = System.currentTimeMillis() },
            )
        }

        if (tracksOpen) {
            PlaybackDialog(
                playable = playable,
                delivery = delivery,
                tracks = tracks,
                onQuality = { quality -> deliverAs(quality); tracksOpen = false; wake() },
                onSource = { assetId ->
                    tracksOpen = false
                    if (assetId != playable.assetId) {
                        onSource(assetId, filmPositionMillis())
                    }
                },
                onAudio = { group -> chooseAudio(player, group) },
                onSubtitles = { group -> chooseSubtitles(player, group) },
            )
        }

        if (infoOpen) {
            FileInfoDialog(
                playable = playable,
                deliveryLabel = when (val current = delivery) {
                    Delivery.Direct -> t("tv.player.directPlayDetail", "Direct play — the file as it is")
                    is Delivery.Transcode -> t("tv.player.reencodedTo", "Re-encoded to {quality}p by the server", "quality" to current.quality)
                },
                player = player,
                reconnects = reconnects,
                lastErrorCode = problemCode,
            )
        }

        val flash = seekFlash
        if (flash != null && !chromeShown) {
            SeekFlashBadge(
                flash = flash,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                modifier = Modifier.align(Alignment.Center),
            )
            ThinProgress(
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        LaunchedEffect(flash?.stamp) {
            if (flash != null) {
                delay(SEEK_FLASH_MILLIS)
                if (seekFlash?.stamp == flash.stamp) {
                    seekFlash = null
                }
            }
        }

        // ⚠️ offerNext, NOT ended — the card has to be on screen while the threshold countdown runs,
        // not only at the very end. Keyed on ended it auto-advanced at 95% with no popup at all. The
        // local keeps the smart-cast that offerNext (a separate boolean) cannot give the compiler.
        val upNextKey = playable.nextPlayKey
        if (offerNext && upNextKey != null) {
            UpNext(
                connection = connection,
                playKey = upNextKey,
                name = playable.nextDisplayName ?: t("tv.player.nextEpisode", "Next episode"),
                seconds = upNextIn,
                held = upNextHeld,
                onPlay = { onNext(upNextKey) },
                onAgain = { startOver() },
                onNotNow = onLeave,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = Kitsu.gutter, vertical = 40.dp),
            )
        }
    }
}

@Composable
private fun Chrome(
    playable: Playable,
    delivery: Delivery,
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    bufferedMillis: Long,
    tracks: Tracks,
    problem: String?,
    /** ⚠️ The machine's name for the fault, shown small under the sentence — see `playbackSentence`. */
    problemCode: String?,
    controlsFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onTracks: () -> Unit,
    onInfo: () -> Unit,
    onRestart: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onAnyFocus: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        /* Top band: what is playing, and how. */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color(0xE6040710), 0.6f to Color(0x99040710), 1f to Color.Transparent))
                .padding(horizontal = Kitsu.gutter, vertical = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            /* ⚠️ THE MARK RIDES WITH THE CHROME, top left, and folds away with it. Burned into the
               corner for the whole film it would be a logo over somebody else's picture all evening;
               shown while the bands are up it says whose machine this is at the one moment somebody
               is looking at the controls rather than at the film. */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrandLockup(markSize = 14.dp, tint = Kitsu.foreground, muted = Kitsu.foregroundMuted, modifier = Modifier.padding(bottom = 4.dp))
                val badge = episodeBadge(playable.playKey)
                Eyebrow(if (badge != null) t("tv.player.seriesBadge", "Series · {episode}", "episode" to badge) else t("tv.kind.film", "Film"))
                Text(text = playable.displayName, style = KitsuType.h2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (delivery) {
                    Delivery.Direct -> TvBadge(text = t("tv.player.directPlay", "Direct play"), tone = BadgeTone.Ok)
                    is Delivery.Transcode -> TvBadge(text = t("tv.player.transcode", "Transcode · {quality}p", "quality" to delivery.quality), tone = BadgeTone.Warn)
                }
                /* The container as the file's own extension: the server's `container` is the
                   probe's format list ("mov,mp4,m4a,3gp,3g2,mj2"), which is not a word for a screen. */
                val extension = playable.fileName?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 }?.uppercase()
                Text(
                    text = listOfNotNull(
                        resolution(playable.width, playable.height),
                        playable.videoCodec?.uppercase(),
                        extension,
                        copyPosition(playable),
                    ).joinToString(" · "),
                    style = KitsuType.meta,
                )
            }
        }

        /*
          Paused.

          ⚠️ NO MARK HERE — it lives in the top-left corner and nowhere else in the player. It was
          here as well, and two marks on one screen read as a logo stamped over a still frame.

          ⚠️ This box is the ONLY chrome that stays when the bands fold away, because a paused
          television with nothing on it reads as a television that has crashed.

          ⚠️ IT WORE NO SHAPE AT ALL UNTIL 2026-09-28 — a square-cornered slab of scrim with a
          hairline round it, wider than it was tall because its padding was 28 by 20, and a near-white
          glyph in the middle. Every one of those is against the product's own language: Kitsu has
          four radii and this is a SURFACE (8dp); a panel is square only in the Harbour set this
          design replaced; and the accent is the colour that marks state, which is exactly what a
          pause is. Over a bright frame the old slab read as a rendering fault rather than as a
          control.

          ⚠️ THE BACKGROUND IS CLIPPED, NOT JUST SHAPED. `background(shape)` alone rounds the fill
          while the border is drawn on the raw box, so the corners show a square hairline over a round
          fill — the two have to be given the same shape, and clipping is what makes it true of
          anything drawn inside as well.

          ⚠️ A BOX, NOT A COLUMN. It holds one glyph; the column's `spacedBy` spaced nothing and was
          an invitation to add a second thing to the one piece of chrome that must stay unreadably
          simple at three metres.
         */
        if (!isPlaying && problem == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(SurfaceShape)
                    .background(Kitsu.scrim)
                    .border(1.dp, Kitsu.borderStrong, SurfaceShape)
                    .padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Pause,
                    contentDescription = null,
                    tint = Kitsu.accentText,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        /*
          The failure, said properly.

          ⚠️ IT WAS A BADGE AND A CODE ON BARE BLACK. The whole of it read "Cannot play" over
          `io bad http status` — a machine's word for the fault, in the middle of a screen with no
          surface under it, which is neither the product's design nor a sentence anybody can act on.
          The code is still here, at the size a code deserves.

          ⚠️ A SURFACE, BECAUSE THE PICTURE BEHIND IT IS UNKNOWABLE. A film that failed at minute
          forty leaves its last frame on the screen, and text laid straight over it is legible or not
          depending on what was showing — the same reason the player scrims its bands.
         */
        if (problem != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .clip(DialogShape)
                    .background(Kitsu.backgroundOverlay)
                    .border(1.dp, Kitsu.borderStrong, DialogShape)
                    .padding(horizontal = 32.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                /* ⚠️ Icon AND heading AND sentence: the tone is carried by three things, not by the
                   colour alone, which a television washes out and some people cannot separate. */
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Kitsu.danger,
                    modifier = Modifier.size(36.dp),
                )
                Text(text = t("tv.player.cannotPlay", "Cannot play"), style = KitsuType.h2)
                Text(
                    text = problem,
                    style = KitsuType.lead.copy(color = Kitsu.foregroundMuted),
                    textAlign = TextAlign.Center,
                )
                if (problemCode != null) {
                    Text(
                        text = problemCode,
                        style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        /* Bottom band: the seek bar, the controls, the hints. */
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color(0xCC040710), 1f to Color(0xF0040710)))
                .padding(horizontal = Kitsu.gutter)
                .padding(top = 48.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = formatSeconds((positionMillis / 1000).toInt()), style = KitsuType.lead.copy(fontFamily = DataFamily, color = Kitsu.foreground))
                SeekBar(
                    position = positionMillis,
                    duration = durationMillis,
                    buffered = bufferedMillis,
                    modifier = Modifier.weight(1f),
                    onSeek = onSeek,
                    onFocus = onAnyFocus,
                )
                val left = if (durationMillis > 0) "−" + formatSeconds(((durationMillis - positionMillis) / 1000).coerceAtLeast(0).toInt()) else t("tv.player.live", "live")
                val endsAt = if (durationMillis > 0) {
                    val ends = java.time.LocalTime.now().plusSeconds((durationMillis - positionMillis) / 1000)
                    " · " + t("tv.player.endsAt", "ends {time}", "time" to "%02d:%02d".format(ends.hour, ends.minute))
                } else ""
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = left, style = KitsuType.lead.copy(fontFamily = DataFamily, color = Kitsu.foreground))
                    Text(text = endsAt, style = KitsuType.meta)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                /* ⚠️ EPISODE NAV LIVES ON THE LEFT NOW, AND ICON-ONLY (MH-311). The two arrows carried
                   the episode number as a label and sat on the right of the bar; Ivan asked for the
                   arrows alone, grouped at the left ahead of the within-episode transport. Only where
                   the server named one — a "Previous" before the first episode is a control that can
                   only fail — so each stays behind its own guard. */
                if (onPrevious != null) {
                    TvIconButton(onClick = onPrevious, icon = Icons.Outlined.SkipPrevious, contentDescription = t("tv.player.previous", "Previous"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })
                }
                if (onNext != null) {
                    TvIconButton(onClick = onNext, icon = Icons.Outlined.SkipNext, contentDescription = t("tv.player.next", "Next"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })
                }
                TvIconButton(onClick = onRestart, icon = Icons.Outlined.RestartAlt, contentDescription = t("tv.player.fromStart", "From the start"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })
                TvIconButton(onClick = { onSeek(-SEEK_STEP_MILLIS) }, icon = Icons.Outlined.Replay10, contentDescription = t("tv.player.back10", "Back 10 seconds"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })
                TvButton(
                    onClick = onTogglePlay,
                    text = if (isPlaying) t("tv.player.pause", "Pause") else t("tv.title.play", "Play"),
                    icon = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    weight = ButtonWeight.Primary,
                    modifier = Modifier.focusRequester(controlsFocus).onFocusChanged { if (it.isFocused) onAnyFocus() },
                )
                TvIconButton(onClick = { onSeek(SEEK_STEP_MILLIS) }, icon = Icons.Outlined.Forward10, contentDescription = t("tv.player.forward10", "Forward 10 seconds"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })

                Spacer(modifier = Modifier.weight(1f))

                /* ⚠️ ONE CONTROL FOR THE ONE SHEET. Quality, audio and subtitles were three buttons that all
                   opened the same dialog, so three places to press did one thing and the row was mostly
                   labels. What is playing now is said in the top band and inside the sheet itself. */
                TvIconButton(
                    onClick = onTracks,
                    icon = Icons.Outlined.Tune,
                    contentDescription = t("tv.player.tracks", "Quality, audio and subtitles"),
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() },
                )
                /* ⚠️ A BUTTON WITH WORDS, ONLY WHERE THERE IS A CHOICE. Two copies of one film went
                   unnoticed while the choice sat inside the sliders sheet; this says there is one and
                   which copy is playing, and opens the same sheet with the copies at the top. */
                copyPosition(playable)?.let { position ->
                    TvButton(
                        onClick = onTracks,
                        text = position,
                        icon = Icons.Outlined.Layers,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() },
                    )
                }
                TvIconButton(onClick = onInfo, icon = Icons.Outlined.Info, contentDescription = t("tv.player.aboutFile", "About this file"), modifier = Modifier.onFocusChanged { if (it.isFocused) onAnyFocus() })
            }

        }
    }
}


/**
 * The seek bar is one control: in hand it wears the ring 6dp out, and LEFT/RIGHT
 * step it while it is focused — the D-pad's natural gesture for "scrub".
 */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit,
    onFocus: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(20.dp)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocus()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-SEEK_STEP_MILLIS); true }
                    Key.DirectionRight -> { onSeek(SEEK_STEP_MILLIS); true }
                    else -> false
                }
            }
            .focusable()
            /* ⚠️ The ring is shaped like a control, as every other focusable thing's is. Square here
               was the one ring in the product drawn with corners the design does not have. */
            .then(if (focused) Modifier.border(Kitsu.focusRingWidth, Kitsu.accentText, ControlShape) else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Kitsu.foreground.copy(alpha = 0.26f)))
        Box(modifier = Modifier.fillMaxWidth(bufferedFraction).height(4.dp).background(Kitsu.foreground.copy(alpha = 0.18f)))
        Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(Kitsu.progress))
        Box(
            modifier = Modifier
                .padding(start = 0.dp)
                .fillMaxWidth(fraction)
                .height(12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            /* ⚠️ Shaped like the ring above it and like every other small object in the product.
               A square handle on a bar whose focus ring is rounded is the same mismatch that note
               describes, one element further in. */
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Kitsu.progress, ChipShape)
                    .border(2.dp, Kitsu.foreground, ChipShape),
            )
        }
    }
}

/**
 * The seek, said in the middle of the picture for a moment: which way, how far in all, and where that
 * lands. ⚠️ Not the chrome — see `quietSeek`.
 */
@Composable
private fun SeekFlashBadge(flash: SeekFlash, positionMillis: Long, durationMillis: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(SurfaceShape)
            .background(Kitsu.scrim)
            .border(1.dp, Kitsu.borderStrong, SurfaceShape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = if (flash.seconds >= 0) Icons.Outlined.FastForward else Icons.Outlined.FastRewind,
            contentDescription = null,
            tint = Kitsu.accentText,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = (if (flash.seconds >= 0) "+" else "−") + formatSeconds(kotlin.math.abs(flash.seconds)),
            style = KitsuType.h1.copy(fontFamily = DataFamily),
        )
        Text(
            text = formatSeconds((positionMillis / 1000).toInt()) +
                (if (durationMillis > 0) " / " + formatSeconds((durationMillis / 1000).toInt()) else ""),
            style = KitsuType.meta.copy(color = Kitsu.foregroundMuted),
        )
    }
}

/** A hairline of progress along the bottom edge, shown with the flash so the jump has a place. */
@Composable
private fun ThinProgress(positionMillis: Long, durationMillis: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    Box(modifier = modifier.fillMaxWidth().height(4.dp).background(Kitsu.foreground.copy(alpha = 0.2f))) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(Kitsu.progress))
    }
}

/**
 * The next episode, offered as the credits end: its own frame, which episode it is, and a count that
 * drains as a bar rather than as a number somebody has to read.
 *
 * ⚠️ THE FRAME IS ASKED FOR HERE. The server's answer to `/play` names the next episode but carries
 * no picture of it, so the season list is fetched once, when this card appears, and the still is
 * read out of it. No still, no hole: the card simply has no picture.
 */
@Composable
private fun UpNext(
    connection: ServerConnection,
    playKey: String,
    name: String,
    seconds: Int,
    held: Boolean,
    onPlay: () -> Unit,
    onAgain: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberInitialFocus()
    var still by remember(playKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(playKey) {
        val parsed = Regex("^(.+):s(\\d+)e(\\d+)$").matchEntire(playKey) ?: return@LaunchedEffect
        val (seriesSlug, seasonNumber, episodeNumber) = parsed.destructured
        val episode = runCatching { connection.api.seasons(seriesSlug) }.getOrNull()
            ?.firstOrNull { it.number == seasonNumber.toInt() }
            ?.episodes?.firstOrNull { it.number == episodeNumber.toInt() }
        still = connection.imageUrl(episode?.stillPath, episode?.stillFileId, ImageSize.FULL)
    }

    /* "S01E03 · Bad Tradecraft" — the badge and the name are two things on this card. */
    val badge = episodeBadge(playKey)
    val title = name.substringAfter(" · ", name)

    /* ⚠️ A STATED HEIGHT. The text column holds a flexible spacer to push the buttons down, and inside
       a row with no height of its own that spacer took the whole screen — the card ran from the top
       of the picture to the bottom. The frame's 16:9 at this height is what the row is measured by. */
    Row(
        modifier = modifier
            .width(600.dp)
            .height(UP_NEXT_HEIGHT)
            /* ⚠️ Clipped, and that is what rounds the STILL as well: the frame sits flush in the
               corner, so a shaped fill alone would leave the picture squaring off the card. */
            .clip(SurfaceShape)
            .background(Kitsu.backgroundOverlay)
            .border(1.dp, Kitsu.borderStrong, SurfaceShape),
    ) {
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Kitsu.backgroundSunken)) {
            if (still != null) {
                AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            /* The count, as a bar across the foot of the frame: full when the card appears, empty when
               the episode starts. */
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(seconds.toFloat() / UP_NEXT_SECONDS)
                    .height(4.dp)
                    .background(Kitsu.progress),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Eyebrow(listOfNotNull(t("tv.series.upNext", "Up next"), badge).joinToString(" · "), muted = true)
            Text(
                text = title,
                style = KitsuType.h2,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            /* ⚠️ Once held, the count says so rather than freezing on a number that no longer means anything. */
            Text(
                text = if (held) t("tv.player.upNextHeld", "Waiting for you") else t("tv.player.startsIn", "Starts in {seconds} s", "seconds" to seconds),
                style = KitsuType.meta.copy(color = Kitsu.accentText),
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                /* ⚠️ ONE WORD EACH (MH-313). "Переглянути знову" wrapped to two crooked lines on the
                   card at three metres; the icon already says what the verb did, so the label is the
                   bare adverb — «Зараз» / «Знову», "Now" / "Again". */
                TvButton(onClick = onPlay, text = t("tv.player.playNow", "Now"), weight = ButtonWeight.Primary, icon = Icons.Outlined.PlayArrow, modifier = Modifier.focusRequester(focus))
                /* ⚠️ The episode that just ended, from its beginning — the way out of an episode that
                   ended while nobody was watching. */
                TvButton(onClick = onAgain, text = t("tv.player.watchAgain", "Again"), icon = Icons.Outlined.RestartAlt)
                TvButton(onClick = onNotNow, text = t("tv.player.notNow", "Not now"))
            }
        }
    }
}

/* ── Tracks ───────────────────────────────────────────────────────────────── */

private fun groupsOf(tracks: Tracks, type: Int): List<Tracks.Group> = tracks.groups.filter { it.type == type && it.isSupported }

private fun trackName(group: Tracks.Group): String {
    val format = group.getTrackFormat(0)
    val language = format.language?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(Texts.locale) }?.takeIf { it.isNotBlank() && it != format.language }
        ?: format.language?.uppercase()
    val channels = when (format.channelCount) {
        6 -> "5.1"
        8 -> "7.1"
        2 -> "2.0"
        else -> null
    }
    return listOfNotNull(language ?: format.label, channels).joinToString(" ").ifBlank { format.label ?: t("tv.player.track", "Track") }
}

private fun selectedName(tracks: Tracks, type: Int): String? {
    val selected = groupsOf(tracks, type).firstOrNull { it.isSelected } ?: return null
    return trackName(selected)
}

/** The codec as a person names it: the MIME subtype's marketing name, not the RFC 6381 tag. */
private fun codecLabel(format: androidx.media3.common.Format): String? {
    val mime = format.sampleMimeType ?: return format.codecs?.substringBefore('.')?.uppercase()
    return when (mime) {
        androidx.media3.common.MimeTypes.AUDIO_AAC -> "AAC"
        androidx.media3.common.MimeTypes.AUDIO_AC3 -> "AC-3"
        androidx.media3.common.MimeTypes.AUDIO_E_AC3, androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        androidx.media3.common.MimeTypes.AUDIO_DTS, androidx.media3.common.MimeTypes.AUDIO_DTS_HD -> "DTS"
        androidx.media3.common.MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        androidx.media3.common.MimeTypes.AUDIO_OPUS -> "Opus"
        androidx.media3.common.MimeTypes.AUDIO_VORBIS -> "Vorbis"
        androidx.media3.common.MimeTypes.AUDIO_FLAC -> "FLAC"
        androidx.media3.common.MimeTypes.AUDIO_MPEG -> "MP3"
        else -> mime.substringAfterLast('/').uppercase()
    }
}

/** One audio group chosen outright. */
private fun chooseAudio(player: ExoPlayer, group: Tracks.Group) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .build()
}

/** One subtitle group, or none. */
private fun chooseSubtitles(player: ExoPlayer, group: Tracks.Group?) {
    val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
    if (group == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
    }
    player.trackSelectionParameters = builder.build()
}

/**
 * Everything about HOW this is playing, in one sheet: quality, audio, subtitles.
 *
 * <h2>⚠️ ONE DIALOG, NOT THREE</h2>
 *
 * It held audio and subtitles, and quality was about to become a second sheet with its own button.
 * They are the same question asked three ways — "change how this plays" — and a remote only reaches
 * what is on screen: three sheets means three trips out of the film. Three columns is one trip.
 *
 * <h2>⚠️ WHAT THE FILE AND THE SERVER OFFER IS WHAT IS LISTED</h2>
 *
 * A subtitle the file does not carry is not a row, and neither is a quality the server did not
 * offer — `playable.qualities` is the server's own list. Original is always there, because the file
 * always exists; whether this television can DECODE it is a different question, and the one the
 * player answers by falling back on its own.
 */
@Composable
private fun PlaybackDialog(
    playable: Playable,
    delivery: Delivery,
    tracks: Tracks,
    onQuality: (Int?) -> Unit,
    onSource: (String) -> Unit,
    onAudio: (Tracks.Group) -> Unit,
    onSubtitles: (Tracks.Group?) -> Unit,
) {
    val audio = groupsOf(tracks, C.TRACK_TYPE_AUDIO)
    val subtitles = groupsOf(tracks, C.TRACK_TYPE_TEXT)

    /* ⚠️ `rememberInitialFocus`, NOT `runCatching { requestFocus() }`. The modifier is not attached
       on the first composition, so the request throws and — wrapped in runCatching — threw away the
       cursor silently: the dialog opened with nothing selected and the remote did nothing. */
    val first = rememberInitialFocus()

    Box(
        modifier = Modifier.fillMaxSize().background(Kitsu.scrim).trapsFocus(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(860.dp)
                /* ⚠️ `DialogShape`, not `SurfaceShape` — this is the widest radius in the set and it
                   is reserved for the thing that takes the whole screen's attention. A dialog wearing
                   a panel's radius reads as a panel that has drifted into the middle. */
                .clip(DialogShape)
                .background(Kitsu.backgroundOverlay)
                .border(1.dp, Kitsu.borderStrong, DialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = t("tv.player.howThisPlays", "How this plays"), style = KitsuType.h2)

            /* ⚠️ THE COPY FIRST, AND ONLY WHERE THERE IS A CHOICE. Which file plays decides what the
               three columns below can even offer — its qualities, its tracks — so it sits above them,
               full width, rather than as a fourth column a 960 dp screen has no room for. */
            if (playable.sources.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                    Eyebrow(t("tv.player.source", "Copy"), muted = true, modifier = Modifier.padding(bottom = 4.dp))
                    playable.sources.forEach { copy ->
                        TrackRow(
                            name = copyLabel(copy),
                            detail = listOfNotNull(copy.rootName, copy.folder?.takeIf { it.isNotBlank() })
                                .joinToString(" / "),
                            current = copy.assetId == playable.assetId,
                            onClick = { onSource(copy.assetId) },
                            // ⚠️ The cursor starts on the copy playing: this sheet was opened to choose one.
                            modifier = if (copy.assetId == playable.assetId) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                    Eyebrow(t("tv.player.quality", "Quality"), muted = true, modifier = Modifier.padding(bottom = 4.dp))

                    /* ⚠️ ORIGINAL IS NAMED FOR WHAT IT IS, not "Auto". It is the file on the disk,
                       untouched — the best this library holds — and calling it automatic would
                       suggest something is choosing, when the only thing that ever changes it is a
                       decoder that refuses. */
                    TrackRow(
                        name = t("tv.player.original", "Original"),
                        detail = resolution(playable.width, playable.height) ?: playable.videoCodec?.uppercase(),
                        current = delivery is Delivery.Direct,
                        onClick = { onQuality(null) },
                        modifier = if (playable.sources.size > 1) Modifier else Modifier.focusRequester(first),
                    )
                    playable.qualities.sortedDescending().forEach { quality ->
                        TrackRow(
                            name = "${quality}p",
                            detail = t("tv.player.reencoded", "re-encoded"),
                            current = (delivery as? Delivery.Transcode)?.quality == quality,
                            onClick = { onQuality(quality) },
                        )
                    }
                    if (playable.qualities.isEmpty()) {
                        Text(
                            text = t("tv.player.noOtherQuality", "The server offers no other quality for this file."),
                            style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                    Eyebrow(t("tv.player.audio", "Audio"), muted = true, modifier = Modifier.padding(bottom = 4.dp))
                    if (audio.isEmpty()) {
                        Text(text = t("tv.player.oneTrack", "One track"), style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
                    }
                    audio.forEach { group ->
                        TrackRow(
                            name = trackName(group),
                            detail = codecLabel(group.getTrackFormat(0)),
                            current = group.isSelected,
                            onClick = { onAudio(group) },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                    Eyebrow(t("tv.player.subtitles", "Subtitles"), muted = true, modifier = Modifier.padding(bottom = 4.dp))
                    TrackRow(
                        name = t("tv.administration.off", "Off"),
                        detail = null,
                        current = subtitles.none { it.isSelected },
                        onClick = { onSubtitles(null) },
                    )
                    subtitles.forEach { group ->
                        TrackRow(
                            name = trackName(group),
                            detail = group.getTrackFormat(0).sampleMimeType?.substringAfterLast('/')?.uppercase(),
                            current = group.isSelected,
                            onClick = { onSubtitles(group) },
                        )
                    }
                }
            }

            /* ⚠️ Said once, here, rather than left for somebody to discover: changing the quality
               reloads the stream and the film carries on from this second. Without the sentence the
               half-second of black reads as a fault. */
            Text(
                text = t("tv.player.qualityRestarts", "Changing the quality restarts the stream from where you are."),
                style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
            )

        }
    }
}

@Composable
private fun TrackRow(name: String, detail: String?, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Kitsu.backgroundElevated else Color.Transparent,
            focusedContainerColor = Kitsu.backgroundElevated,
            contentColor = if (current) Kitsu.foreground else Kitsu.foregroundMuted,
            focusedContentColor = Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (current) Modifier.indexTab() else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = KitsuType.body.copy(fontWeight = FontWeight.SemiBold))
            if (detail != null) {
                Text(text = detail, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
            }
        }
    }
}

/**
 * A playback failure in words, for the person in front of the television.
 *
 * <h2>⚠️ NOT `errorCodeName`, WHICH IS WHAT THIS SCREEN USED TO SHOW</h2>
 *
 * The player printed the code with its prefix stripped and its underscores turned into spaces, so a
 * refused HTTP response reached a sofa as `io bad http status`. That is a correct name for the fault
 * and tells somebody holding a remote nothing at all — least of all whether the thing to do is wait,
 * rescan, or go and look at the server.
 *
 * <h2>⚠️ EVERY SENTENCE NAMES WHAT TO DO, or says plainly that there is nothing to do</h2>
 *
 * A message that only restates the failure spends the one screen somebody reads and leaves them where
 * they were. Where the answer is genuinely "this file cannot play here", it says that, because a
 * suggestion that will not work is worse than none.
 *
 * ⚠️ The code itself is still shown, small, under the sentence — it is what makes a report actionable
 * later, and it is the half a sentence cannot carry.
 */
private fun playbackSentence(error: PlaybackException): String = when (error.errorCode) {

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
        t("tv.player.problemRefused",
          "The server answered, but not with the film. Its ticket may have expired — go back and press play again.")

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        t("tv.player.problemGone",
          "The file is no longer where the library expects it. Rescan the folder on the server.")

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        t("tv.player.problemRefusedPermission",
          "This television is not allowed to read this file.")

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        t("tv.player.problemUnreachable",
          "The server stopped answering. Check that the computer is awake and on the same network.")

    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
        t("tv.player.problemCleartext",
          "This television refused a plain HTTP connection to the server.")

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
        t("tv.player.problemContainer",
          "The file is damaged, or is not the kind of file it claims to be.")

    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        t("tv.player.problemDecoder",
          "This television cannot decode this file, and re-encoding it did not help either.")

    else ->
        t("tv.player.problemUnknown",
          "Something stopped the film and the player did not say what.")
}

/**
 * Playback stopped by somebody else — the household's console, or a rule closing what was playing.
 *
 * ⚠️ A STATE THE PRODUCT MEANT, NOT AN ERROR. The same surface as the player's own failure notice
 * (scrimmed, ruled, centred) because the last frame of a film is still behind it, but it says what
 * happened and offers what can be done next: ask again — which the server allows, since resolving a
 * work afresh lifts the console's order — or go back.
 *
 * ⚠️ FOCUS LANDS ON "PLAY AGAIN". The control row that held focus is gone with the player; a screen
 * with nothing focused is a remote that does nothing.
 */
@Composable
private fun PlaybackStopped(
    why: Stopped,
    onReplay: () -> Unit,
    onLeave: () -> Unit,
    /** Plays another copy of the same work — offered when this copy is the problem. */
    onOtherCopy: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(DialogShape)
                .background(Kitsu.backgroundOverlay)
                .border(1.dp, Kitsu.borderStrong, DialogShape)
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.StopCircle,
                contentDescription = null,
                tint = Kitsu.foregroundMuted,
                modifier = Modifier.size(36.dp),
            )
            Text(text = t("tv.player.stoppedHeading", "Playback stopped"), style = KitsuType.h2)
            Text(
                text = when (why) {
                    Stopped.ByConsole ->
                        t("tv.player.stoppedFromConsole", "Somebody stopped it from this household's console.")
                    Stopped.AccessClosed ->
                        t("tv.player.stoppedAccessClosed", "This is no longer open to this profile.")
                    Stopped.CopyUnreachable ->
                        t(
                            "tv.player.stoppedCopyUnreachable",
                            "This television cannot play this copy as it is, and its drive is out of the media service's reach for re-encoding.",
                        )
                },
                style = KitsuType.lead.copy(color = Kitsu.foregroundMuted),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (why == Stopped.CopyUnreachable && onOtherCopy != null) {
                    TvButton(
                        onClick = onOtherCopy,
                        text = t("tv.player.playOtherCopy", "Play another copy"),
                        weight = ButtonWeight.Primary,
                        icon = Icons.Outlined.RestartAlt,
                        modifier = Modifier.focusRequester(first),
                    )
                    TvButton(onClick = onReplay, text = t("tv.player.playAgain", "Play again"))
                } else {
                    TvButton(
                        onClick = onReplay,
                        text = t("tv.player.playAgain", "Play again"),
                        weight = ButtonWeight.Primary,
                        icon = Icons.Outlined.RestartAlt,
                        modifier = Modifier.focusRequester(first),
                    )
                }
                TvButton(onClick = onLeave, text = t("tv.player.back", "Back"))
            }
        }
    }
}

/**
 * One copy of a work, as every copy chooser names it: `1080p [9.1 GB] : Movie.Name.2019.mkv`.
 *
 * ⚠️ THE SAME FORMAT AS THE WEB'S `copyLabel` (FE/src/lib/media-file.ts): the picture first because it
 * is what people choose by, the weight because it tells two 1080p rips apart, the file as the proof.
 */
internal fun copyLabel(copy: PlaybackSource): String {
    // ⚠️ The CLASS, not the raw height: a 1920×1040 scope rip is what people call 1080p.
    val quality = resolution(null, copy.height)?.let { "$it " } ?: ""
    return "${quality}[${formatBytes(copy.sizeBytes)}] : ${copy.fileName}"
}

/** «Copy 1 of 2», or null where there is only one — then there is nothing to say. */
@Composable
private fun copyPosition(playable: Playable): String? {
    val at = playable.sources.indexOfFirst { it.assetId == playable.assetId }
    if (playable.sources.size < 2 || at < 0) {
        return null
    }
    return t("tv.player.copyPosition", "Copy {at}/{of}", "at" to at + 1, "of" to playable.sources.size)
}
