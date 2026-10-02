package org.jmouse.project.kitsu.tv.ui.screens

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.text.formatDateTime
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.plural
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import org.jmouse.project.kitsu.tv.data.api.filesOfWork
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jmouse.project.kitsu.tv.data.ImageSize
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.CreditEntry
import org.jmouse.project.kitsu.tv.data.api.EpisodeView
import org.jmouse.project.kitsu.tv.data.api.MediaAssetResponse
import org.jmouse.project.kitsu.tv.data.api.SeasonView
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.TitleDetail
import org.jmouse.project.kitsu.tv.data.api.Watched
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.data.api.episodePlayKey
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.MetaLine
import org.jmouse.project.kitsu.tv.ui.components.ProgressRule
import org.jmouse.project.kitsu.tv.ui.components.RowScrolling
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.focusRing
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/**
 * A series as this television sees it: the catalogue's seasons and episodes, and — beside them, from
 * the file domain — which episodes are on the disk and where somebody got to.
 *
 * ⚠️ `files` IS NULL WHEN THE DISK COULD NOT BE ASKED, and an empty list when it was asked and holds
 * nothing. The two must not be confused: a failed request drawn as "nothing on disk" would disable
 * every episode of a series sitting on the disk.
 */
data class SeriesContent(
    val detail: TitleDetail,
    val seasons: List<SeasonView>,
    val credits: List<CreditEntry>,
    val files: List<MediaAssetResponse>?,
    val watched: Map<String, Watched>,
    val similar: List<TitleCard>,
    val kept: Boolean,
) {
    private val filesByKey: Map<String, MediaAssetResponse>? =
        files?.mapNotNull { file -> file.cover?.playKey?.let { key -> key to file } }?.toMap()

    fun keyOf(season: SeasonView, episode: EpisodeView): String =
        episode.playKey ?: episodePlayKey(detail.slug, season.number, episode.number)

    /** ⚠️ Null when unknown — see the class note. */
    fun fileOf(season: SeasonView, episode: EpisodeView): MediaAssetResponse? = filesByKey?.get(keyOf(season, episode))

    fun available(season: SeasonView, episode: EpisodeView): Boolean? =
        if (filesByKey == null) episode.available else filesByKey.containsKey(keyOf(season, episode))

    fun watchedOf(season: SeasonView, episode: EpisodeView): Watched? = watched[keyOf(season, episode)]
}

/**
 * A series: the same page a film gets — backdrop, poster, the name, what it is, the buttons, cast,
 * more like this, facts — with seasons and episodes where a film has nothing, because an episode is
 * the thing somebody actually chooses.
 *
 * Episodes are TILES with their own frame, in a row per season: on a television an episode is picked
 * by what it looks like and where it sits in the run, and a list of names read downwards is the one
 * layout a remote is worst at.
 */
@Composable
fun SeriesScreen(
    connection: ServerConnection,
    deck: DeckState,
    slug: String,
    onPlay: (playKey: String) -> Unit,
    onTitle: (TitleCard) -> Unit,
    onPerson: (personId: String) -> Unit,
    onAbout: (slug: String) -> Unit,
) {
    var state by rememberLoaded<SeriesContent>()
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(slug, attempt) {
        /* ⚠️ A REFRESH KEEPS THE PAGE. "Start over" and every other change on this page re-read it, and
           going back to Loading first unmounted the whole body — the page reopened at its top with
           focus on Play, which read as being thrown back to the title. Only a first load or a retry
           after a failure shows the waiting state; a refresh swaps the content under the cursor. */
        if (state !is Loaded.Ready) {
            state = Loaded.Pending
        }
        state = runCatching {
            coroutineScope {
                val api = connection.api
                val detail = api.title(slug)
                val seasons = async { api.seasons(slug) }
                val credits = async { runCatching { api.credits(slug) }.getOrDefault(emptyList()) }
                val files = async { runCatching { api.filesOfWork(slug) }.getOrNull() }
                val progress = async { runCatching { api.progress(all = true) }.getOrDefault(emptyList()) }
                val favourites = async { runCatching { api.favourites() }.getOrDefault(emptyList()) }
                val similar = async { api.similarTo(detail) }
                SeriesContent(
                    detail = detail,
                    seasons = seasons.await().sortedBy { season -> if (season.number == 0) Int.MAX_VALUE else season.number },
                    credits = credits.await(),
                    files = files.await(),
                    watched = progress.await().associateBy { it.playKey },
                    similar = similar.await(),
                    kept = favourites.await().any { it.slug == slug },
                )
            }
        }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    Screen(deck = deck, current = Bay.Series) {
        when (val current = state) {
            Loaded.Pending -> Loading()
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> SeriesBody(connection, current.value, onPlay, onTitle, onPerson, onAbout, onChanged = { attempt++ })
        }
    }
}

/** An episode together with the season it is listed under — the season carries its number. */
private data class Placed(val season: SeasonView, val episode: EpisodeView)

@Composable
private fun SeriesBody(
    connection: ServerConnection,
    content: SeriesContent,
    onPlay: (String) -> Unit,
    onTitle: (TitleCard) -> Unit,
    onPerson: (String) -> Unit,
    onAbout: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val detail = content.detail
    val seasons = content.seasons
    val everything = seasons.flatMap { season -> season.episodes.sortedBy { it.number }.map { Placed(season, it) } }

    /*
      The next thing to watch: the episode somebody is in the middle of, else the first unfinished one
      after the last one finished, else the first on the disk.

      ⚠️ `!= false`, because null is "nobody asked the disk" rather than "no file".
     */
    val playable = everything.filter { content.available(it.season, it.episode) != false }
    val started = playable.firstOrNull { placed ->
        val watched = content.watchedOf(placed.season, placed.episode)
        watched != null && !watched.finished && watched.positionSeconds > 0
    }
    val lastFinished = everything.indexOfLast { content.watchedOf(it.season, it.episode)?.finished == true }
    val next = started
        ?: playable.firstOrNull { everything.indexOf(it) > lastFinished && content.watchedOf(it.season, it.episode)?.finished != true }
        ?: playable.firstOrNull()

    var chosen by remember(detail.slug) { mutableStateOf(next?.season ?: seasons.firstOrNull()) }
    val firstFocus = rememberInitialFocus(detail.slug)

    val onDisk = everything.count { content.available(it.season, it.episode) == true }
    val best = content.files?.maxByOrNull { (it.height ?: 0) * 10_000L + (it.width ?: 0) }

    val pageState = rememberLazyListState()
    LazyColumn(
        state = pageState,
        modifier = Modifier.fillMaxSize().revealsPageEdges(pageState),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            TitleHero(connection, detail) {
                val creators = content.credits
                    .filter { it.job.equals("Creator", ignoreCase = true) }
                    .joinToString(", ") { it.personName }
                    .ifBlank { null }
                Eyebrow(listOfNotNull(t("tv.kind.series", "Series"), runOf(detail), statusOf(detail.status), creators).joinToString(" · "))
                TitleName(detail)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaLine(
                        detail.rating?.let { formatRating(it) },
                        listOf(Counted.seasons(seasons.count { it.number > 0 }), Counted.episodes(everything.size)).joinToString(" · "),
                        best?.pictureLine(),
                    )
                    when {
                        content.files == null -> Unit
                        onDisk == 0 -> TvBadge(text = t("tv.series.noFiles", "No files"), tone = BadgeTone.Muted)
                        onDisk == everything.size -> TvBadge(text = t("tv.series.allOnDisk", "All on disk"), tone = BadgeTone.Ok)
                        else -> TvBadge(text = t("tv.series.someOnDisk", "{count} of {total} on disk", "count" to onDisk, "total" to everything.size), tone = BadgeTone.Ok)
                    }
                }
                TitleSummary(detail)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (next != null) {
                        val key = content.keyOf(next.season, next.episode)
                        val watched = content.watchedOf(next.season, next.episode)
                        val resuming = watched != null && !watched.finished && watched.positionSeconds > 0
                        val runtime = next.episode.runtimeMinutes?.times(60) ?: content.fileOf(next.season, next.episode)?.durationSeconds
                        TvButton(
                            onClick = { onPlay(key) },
                            text = "${if (resuming) t("tv.title.resume", "Resume") else t("tv.title.play", "Play")} ${episodeCode(key) ?: ""}".trim(),
                            hint = if (resuming) formatRemaining(watched!!.positionSeconds, runtime) else formatRuntime(next.episode.runtimeMinutes),
                            weight = ButtonWeight.Primary,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                        KeepButton(connection, detail.slug, content.kept)
                    } else {
                        KeepButton(connection, detail.slug, content.kept, modifier = Modifier.focusRequester(firstFocus))
                    }
                    /* ⚠️ The WHOLE programme, asked twice — see `StartOverButton`. */
                    /* ⚠️ ALWAYS DRAWN, not only while there is progress: the button vanishing under the cursor the moment it
                           worked left focus nowhere, and the page jumped to find somewhere to put it. */
                    StartOverButton(connection, detail.slug, askTwice = true, onForgotten = onChanged)
                    /* Onto a shelf of the profile's own, or the household's — a title found at the film
                       database and just added arrives here, so this is where it gets filed. */
                    CollectionsButton(connection, detail.slug, detail.displayName, detail.nodes)
                    TvButton(onClick = { onAbout(detail.slug) }, text = t("tv.title.about", "About"), icon = Icons.Outlined.Info)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter),
                /* ⚠️ `focusGap`, not a number — a focus ring is drawn OUTSIDE the chip, so two chips
                   closer than twice its footprint draw over each other. */
                horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                seasons.forEach { season ->
                    TvChip(
                        text = season.displayName ?: if (season.number == 0) t("tv.series.specials", "Specials") else t("tv.series.season", "Season {number}", "number" to season.number),
                        count = season.episodes.size.toString(),
                        pressed = season.id == chosen?.id,
                        onClick = { chosen = season },
                    )
                }
            }
        }

        chosen?.let { season ->
            item(key = "episodes-${season.id}") {
                SeasonRow(connection, content, season, next?.episode?.id, onPlay, onChanged)
            }
        }

        item {
            CastShelf(connection, content.credits, onPerson)
        }

        item {
            SimilarShelf(connection, content.similar, onTitle)
        }

        item {
            SeriesFacts(content, onDisk, everything.size)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeasonRow(
    connection: ServerConnection,
    content: SeriesContent,
    season: SeasonView,
    nextEpisodeId: String?,
    onPlay: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val episodes = season.episodes.sortedBy { it.number }
    val scope = rememberCoroutineScope()
    val missing = episodes.count { content.available(season, it) == false }
    /* The row opens on the episode that is next, not on episode one of a season half watched. */
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = episodes.indexOfFirst { it.id == nextEpisodeId }.coerceAtLeast(0),
    )
    val fallback = connection.imageUrl(content.detail.backdropPath, content.detail.backdropFileId, ImageSize.FULL)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = season.displayName ?: t("tv.series.season", "Season {number}", "number" to season.number), style = KitsuType.h2)
            Text(
                text = listOfNotNull(season.airedYear?.toString(), Counted.episodes(episodes.size)).joinToString(" · "),
                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Box(modifier = Modifier.weight(1f))
            /* ⚠️ This season only, asked twice. One episode is a long press on its tile. */
            StartOverButton(connection, content.detail.slug, season = season.number, askTwice = true, onForgotten = onChanged)
            if (missing > 0) {
                Text(
                    text = plural("tv.series.notOnDisk", missing, "{count} not on the disk yet", "{count} not on the disk yet"),
                    style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        RowScrolling {
            LazyRow(
                state = listState,
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = Kitsu.gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(Kitsu.tileGap),
            ) {
                /* ⚠️ Keyed on the ROW's id, not on the play key — a derived key collapses two
                   episodes with the same numbers in a malformed import into one tile. */
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { _, episode ->
                    EpisodeTile(
                        connection = connection,
                        episode = episode,
                        fallbackImage = fallback,
                        available = content.available(season, episode),
                        watched = content.watchedOf(season, episode),
                        file = content.fileOf(season, episode),
                        next = episode.id == nextEpisodeId,
                        onPlay = { onPlay(content.keyOf(season, episode)) },
                        /* ⚠️ LONG PRESS FORGETS THIS ONE EPISODE — the way out of an episode that ended
                           while nobody was watching (MH-284). Only where there is something to forget. */
                        onStartOver = content.watchedOf(season, episode)?.let {
                            {
                                scope.launch {
                                    runCatching { connection.api.forgetProgress(content.detail.slug, season.number, episode.number) }
                                    onChanged()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private val EpisodeTileWidth = 256.dp

/**
 * One episode: its own frame, the number over it, how far in, and — under it — the name and the
 * facts. An episode with no file is drawn dimmed, says so, and cannot be pressed.
 */
@Composable
private fun EpisodeTile(
    connection: ServerConnection,
    episode: EpisodeView,
    fallbackImage: String?,
    available: Boolean?,
    watched: Watched?,
    file: MediaAssetResponse?,
    next: Boolean,
    onPlay: () -> Unit,
    onStartOver: (() -> Unit)?,
) {
    val present = available != false
    val runtimeSeconds = episode.runtimeMinutes?.times(60) ?: file?.durationSeconds
    val fraction = when {
        watched?.finished == true -> 1f
        watched != null && runtimeSeconds != null && runtimeSeconds > 0 -> watched.positionSeconds.toFloat() / runtimeSeconds
        else -> null
    }
    val still = connection.imageUrl(episode.stillPath, episode.stillFileId, ImageSize.FULL)

    Column(modifier = Modifier.width(EpisodeTileWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onPlay,
            onLongClick = onStartOver,
            enabled = present,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(ControlShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Kitsu.backgroundElevated,
                focusedContainerColor = Kitsu.backgroundElevated,
                disabledContainerColor = Kitsu.backgroundSunken,
            ),
            border = ClickableSurfaceDefaults.border(
                /* The next episode wears the accent at rest, so it is found before the cursor gets there. */
                border = Border(BorderStroke(if (next) 2.dp else 1.dp, if (next) Kitsu.accent else Kitsu.border), shape = ControlShape),
                focusedBorder = focusRing(),
                disabledBorder = Border(BorderStroke(1.dp, Kitsu.border), shape = ControlShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
        ) {
            Box(modifier = Modifier.fillMaxSize().alpha(if (present) 1f else 0.4f)) {
                when {
                    still != null -> AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    /* ⚠️ No frame of its own: the show's picture, pushed well back, with the number
                       large over it — a stand-in that says what it is rather than an empty grey box. */
                    fallbackImage != null -> AsyncImage(
                        model = fallbackImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.28f),
                    )
                }
                if (still == null) {
                    Text(
                        text = episode.number.toString(),
                        style = KitsuType.hero.copy(fontFamily = DataFamily, color = Kitsu.foreground.copy(alpha = 0.7f)),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Kitsu.scrim)),
                )
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Kitsu.scrim)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "E%02d".format(episode.number),
                        style = KitsuType.meta.copy(fontSize = 12.sp, color = Kitsu.foreground),
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    when {
                        !present -> TvBadge(text = t("tv.title.noFile", "No file"), tone = BadgeTone.Muted)
                        watched?.finished == true -> TvBadge(text = t("tv.series.watched", "Watched"), tone = BadgeTone.Ok)
                        next -> TvBadge(text = t("tv.series.upNext", "Up next"), tone = BadgeTone.Warn)
                    }
                }
                formatRuntime(runtimeSeconds?.div(60))?.let { runtime ->
                    Text(
                        text = runtime,
                        style = KitsuType.meta.copy(fontSize = 12.sp, color = Kitsu.foreground),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                if (fraction != null && fraction > 0f) {
                    ProgressRule(fraction = fraction, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = episode.displayName ?: t("tv.series.episode", "Episode {number}", "number" to episode.number),
                style = KitsuType.body.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (present) Kitsu.foreground else Kitsu.foregroundSubtle,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val left = watched?.takeIf { !it.finished && it.positionSeconds > 0 }?.let { formatLeft(it.positionSeconds, runtimeSeconds) }
            Text(
                text = listOfNotNull(episode.airedOn, left, file?.let { resolution(it.width, it.height) }).joinToString(" · "),
                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeriesFacts(content: SeriesContent, onDisk: Int, total: Int) {
    val detail = content.detail
    val files = content.files.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Kitsu.gutter)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        FactBlock(t("tv.series.aboutSeries", "About the series"), modifier = Modifier.weight(1f)) {
            FactLine(t("tv.series.runs", "Runs"), runOf(detail))
            FactLine(t("tv.series.status", "Status"), statusOf(detail.status))
            FactLine(t("tv.title.originalLanguage", "Original language"), detail.originalLanguage)
            FactLine(t("tv.series.seasons", "Seasons"), content.seasons.count { it.number > 0 }.toString())
            FactLine(t("tv.series.episodes", "Episodes"), total.toString())
            FactLine(t("tv.title.added", "Added"), formatDateTime(detail.addedAt))
            val genres = detail.nodes.filter { it.kind == "genre" }
            if (genres.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    genres.forEach { genre -> TvChip(text = genre.displayName, onClick = {}) }
                }
            }
        }
        FactBlock(t("tv.title.onTheDisk", "On the disk"), modifier = Modifier.weight(1f)) {
            if (files.isEmpty()) {
                Text(
                    text = if (content.files == null) {
                        t("tv.series.filesUnknown", "The server did not say which files it holds.")
                    } else {
                        t("tv.series.noEpisodeFile", "No episode of this series has a file yet.")
                    },
                    style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                )
            } else {
                FactLine(t("tv.series.episodes", "Episodes"), t("tv.series.ofTotal", "{count} of {total}", "count" to onDisk, "total" to total))
                FactLine(t("tv.file.size", "Size"), formatBytes(files.sumOf { it.sizeBytes }))
                FactLine(t("tv.file.runningTime", "Running time"), files.sumOf { it.durationSeconds ?: 0 }.takeIf { it > 0 }?.let { formatSeconds(it) })
                FactLine(t("tv.file.video", "Video"), files.mapNotNull { it.pictureLine() }.distinct().joinToString(", ").ifBlank { null })
                FactLine(t("tv.file.container", "Container"), files.mapNotNull { containerOf(it.fileName) }.distinct().joinToString(", ").ifBlank { null })
                FactLine(t("tv.file.folder", "Folder"), files.firstOrNull()?.relativePath?.substringBefore('/'))
            }
        }
    }
}

/** `2022 – 2026`, `2022 –` for a show still running, or the one year. */
private fun runOf(detail: TitleDetail): String? {
    val start = detail.year ?: return null
    return when {
        detail.endedYear != null && detail.endedYear != start && detail.status?.uppercase() != "RETURNING" -> "$start – ${detail.endedYear}"
        detail.status?.uppercase() == "RETURNING" || detail.status?.uppercase() == "IN_PRODUCTION" -> "$start –"
        else -> start.toString()
    }
}

private fun statusOf(status: String?): String? = when (status?.uppercase()) {
    null -> null
    "RETURNING" -> t("tv.series.statusReturning", "Returning")
    "ENDED" -> t("tv.series.statusEnded", "Ended")
    "CANCELED", "CANCELLED" -> t("tv.series.statusCancelled", "Cancelled")
    "IN_PRODUCTION" -> t("tv.series.statusInProduction", "In production")
    "PLANNED" -> t("tv.series.statusPlanned", "Planned")
    "UNKNOWN" -> null
    else -> status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
