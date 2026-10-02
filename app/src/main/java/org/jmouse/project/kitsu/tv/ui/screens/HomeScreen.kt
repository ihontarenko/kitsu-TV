package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jmouse.project.kitsu.tv.data.ImageSize
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.NodeSummary
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.TitleDetail
import org.jmouse.project.kitsu.tv.data.api.Watched
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.MetaLine
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.ProgressRule
import org.jmouse.project.kitsu.tv.ui.components.Shelf
import org.jmouse.project.kitsu.tv.ui.components.TextTile
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.kindLabel
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/** Everything the home screen shows, fetched together. */
data class HomeContent(
    val continueWatching: List<Watched>,
    val latest: List<TitleCard>,
    val genres: List<NodeSummary>,
    val favourites: List<TitleCard>,
    /** The hero: the thing being watched, with its backdrop; or the newest arrival. */
    val hero: TitleDetail?,
    val heroWatched: Watched?,
)

/**
 * Home: the hero is what was being watched, with one button — carry on. Under
 * it, the shelves: continue, latest arrivals, genres, favourites.
 */
@Composable
fun HomeScreen(
    connection: ServerConnection,
    deck: DeckState,
    onTitle: (TitleCard) -> Unit,
    onPlay: (playKey: String) -> Unit,
    onGenre: (NodeSummary) -> Unit,
) {
    var state by rememberLoaded<HomeContent>()
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = Loaded.Pending
        val loaded = runCatching {
            coroutineScope {
                val api = connection.api
                val continueWatching = async { api.progress() }
                val latest = async { api.titles(sort = "added:desc", size = 24) }
                val genres = async { runCatching { api.nodes("genre") }.getOrDefault(emptyList()) }
                val favourites = async { runCatching { api.favourites() }.getOrDefault(emptyList()) }

                val watching = continueWatching.await().filter { !it.finished && it.title != null }
                val arrivals = latest.await().content
                val heroWatched = watching.firstOrNull()
                val heroSlug = heroWatched?.title?.slug ?: arrivals.firstOrNull()?.slug
                val hero = heroSlug?.let { runCatching { api.title(it) }.getOrNull() }

                HomeContent(
                    continueWatching = watching,
                    latest = arrivals,
                    genres = genres.await(),
                    favourites = favourites.await(),
                    hero = hero,
                    heroWatched = heroWatched,
                )
            }
        }
        state = loaded.fold(
            onSuccess = { Loaded.Ready(it) },
            onFailure = { Loaded.Failed(it.describeForScreen()) },
        )
    }

    Screen(deck = deck, current = Bay.Home) {
        when (val current = state) {
            Loaded.Pending -> Loading(t("tv.home.opening", "Opening the library…"))
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> HomeBody(connection, current.value, onTitle, onPlay, onGenre)
        }
    }
}

@Composable
private fun HomeBody(
    connection: ServerConnection,
    content: HomeContent,
    onTitle: (TitleCard) -> Unit,
    onPlay: (String) -> Unit,
    onGenre: (NodeSummary) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    val pageState = rememberLazyListState()
    LazyColumn(
        state = pageState,
        modifier = Modifier.fillMaxSize().revealsPageEdges(pageState),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Hero(connection, content, onPlay, onTitle, firstFocus)
        }

        if (content.continueWatching.isNotEmpty()) {
            item {
                Shelf(title = t("tv.home.continue", "Continue watching"), note = t("tv.home.continueNote", "Picks up where any screen left off")) {
                    items(content.continueWatching.size, key = { content.continueWatching[it].playKey }) { index ->
                        val watched = content.continueWatching[index]
                        val title = watched.title ?: return@items
                        val progress = watched.durationSeconds?.takeIf { it > 0 }?.let { watched.positionSeconds.toFloat() / it }
                        /* A poster, not a still: a card carries no backdrop, and a poster cropped
                           to 16:9 shows a chin and a logo. The episode is named under it instead. */
                        PosterTile(
                            title = title.displayName,
                            imageUrl = connection.imageUrl(title.posterPath, title.posterFileId),
                            onClick = { onPlay(watched.playKey) },
                            meta = listOfNotNull(episodeBadge(watched.playKey), formatLeft(watched.positionSeconds, watched.durationSeconds)).joinToString("  ·  "),
                            year = title.year?.toString(),
                            progress = progress,
                        )
                    }
                }
            }
        }

        item {
            Shelf(title = t("tv.home.latest", "Latest arrivals"), note = t("tv.home.latestNote", "Newest first, by when it was taken into the library"), count = Counted.titles(content.latest.size)) {
                items(content.latest.size, key = { content.latest[it].id }) { index ->
                    val card = content.latest[index]
                    PosterTile(
                        title = card.displayName,
                        imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                        onClick = { onTitle(card) },
                        meta = card.metaLine(),
                        year = card.year?.toString(),
                    )
                }
            }
        }

        if (content.genres.isNotEmpty()) {
            item {
                Shelf(title = t("tv.home.genres", "Browse by genre"), count = Counted.genres(content.genres.size)) {
                    items(content.genres.size, key = { content.genres[it].id }) { index ->
                        val genre = content.genres[index]
                        TextTile(
                            title = genre.displayName,
                            meta = Counted.titles(genre.titleCount.toInt()),
                            onClick = { onGenre(genre) },
                        )
                    }
                }
            }
        }

        if (content.favourites.isNotEmpty()) {
            item {
                Shelf(title = t("tv.home.favourites", "Favourites"), note = t("tv.home.favouritesNote", "In the order they were kept")) {
                    items(content.favourites.size, key = { content.favourites[it].id }) { index ->
                        val card = content.favourites[index]
                        PosterTile(
                            title = card.displayName,
                            imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                            onClick = { onTitle(card) },
                            meta = card.metaLine(),
                            year = card.year?.toString(),
                        )
                    }
                }
            }
        }
    }
}

/** `severance-2022:s01e04` → `S1 · E4`, for a still that is an episode (see the server's EpisodeKey). */
fun episodeBadge(playKey: String): String? {
    val match = EPISODE_KEY.matchEntire(playKey) ?: return null
    val season = match.groupValues[1].toInt()
    val episode = match.groupValues[2].toInt()
    return t("tv.episode.badge", "S{season} · E{episode}", "season" to season, "episode" to episode)
}

/**
 * The same episode as a code — `S01E03` — for a label that has no room for words.
 *
 * ⚠️ NOT TRANSLATED, on purpose. The code is the one form every language already reads the same,
 * which is why a button can carry it: "Сезон 1 · серія 3" beside a verb and a time ran the play
 * button half across the screen.
 */
fun episodeCode(playKey: String): String? {
    val match = EPISODE_KEY.matchEntire(playKey) ?: return null
    val season = match.groupValues[1].toInt()
    val episode = match.groupValues[2].toInt()
    return "S%02dE%02d".format(season, episode)
}

private val EPISODE_KEY = Regex("^[a-z0-9-]+:s([0-9]{1,3})e([0-9]{1,4})$")

@Composable
private fun Hero(
    connection: ServerConnection,
    content: HomeContent,
    onPlay: (String) -> Unit,
    onTitle: (TitleCard) -> Unit,
    firstFocus: FocusRequester,
) {
    val hero = content.hero
    if (hero == null) {
        EmptyLibrary()
        return
    }
    val watched = content.heroWatched
    val backdrop = connection.imageUrl(hero.backdropPath, hero.backdropFileId, ImageSize.BACKDROP)
        ?: connection.imageUrl(hero.posterPath, hero.posterFileId, ImageSize.FULL)
    val progress = watched?.durationSeconds?.takeIf { it > 0 }?.let { watched.positionSeconds.toFloat() / it }
    val card = TitleCard(hero.id, hero.kind, hero.slug, hero.displayName, hero.year, hero.rating, hero.voteCount, hero.posterPath, hero.posterFileId)

    /* Taller when there is a bar and a timecode to show under the meta line. */
    /* ⚠️ 240–280, not 300–340: the first lane has to be on screen whole under the hero, or the
       page opens with half a row of posters and the reason to press DOWN hidden. */
    Box(modifier = Modifier.fillMaxWidth().height(if (watched != null) 280.dp else 240.dp)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Kitsu.background))
                .background(Brush.horizontalGradient(0f to Kitsu.background.copy(alpha = 0.88f), 0.45f to Kitsu.background.copy(alpha = 0.4f), 0.75f to Color.Transparent)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Kitsu.gutter, vertical = 24.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eyebrow(
                listOfNotNull(
                    if (watched != null) t("tv.home.continue", "Continue watching") else t("tv.home.justArrived", "Just arrived"),
                    kindLabel(hero.kind),
                    hero.year?.toString(),
                ).joinToString(" · "),
            )
            Text(text = hero.displayName, style = KitsuType.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetaLine(formatRuntime(hero.runtimeMinutes), hero.rating?.let { formatRating(it) })
                if (hero.available) {
                    TvBadge(text = t("tv.title.onDisk", "On disk"), tone = BadgeTone.Ok)
                }
            }
            if (watched != null && progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(320.dp)) {
                    ProgressRule(fraction = progress, height = 4.dp, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = t(
                            "tv.home.position",
                            "{position} of {duration} · {left}",
                            "position" to formatSeconds(watched.positionSeconds),
                            "duration" to formatSeconds(watched.durationSeconds ?: 0),
                            "left" to formatLeft(watched.positionSeconds, watched.durationSeconds),
                        ),
                        style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (watched != null) {
                    TvButton(
                        onClick = { onPlay(watched.playKey) },
                        text = t("tv.title.resume", "Resume"),
                        hint = formatSeconds(watched.positionSeconds),
                        weight = ButtonWeight.Primary,
                        icon = Icons.Outlined.PlayArrow,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton(onClick = { onTitle(card) }, text = t("tv.home.details", "Details"), icon = Icons.Outlined.Info)
                } else {
                    TvButton(
                        onClick = { onTitle(card) },
                        text = t("tv.home.open", "Open"),
                        weight = ButtonWeight.Primary,
                        icon = Icons.Outlined.Info,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = t("tv.home.empty", "The library is empty."), style = KitsuType.h1)
        Text(text = t("tv.home.emptyLead", "Add a folder on the server and let it scan; films appear here as they are identified."), style = KitsuType.lead)
    }
}
