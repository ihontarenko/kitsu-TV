package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.api.PageResponse
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.NodeSummary
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.TileWall
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.TvRailItem
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/** How a wall is ordered — the server's sort key, and its name on the chip, asked each time it is drawn. */
private enum class Sort(val key: String) {
    Added("added:desc"),
    Name("name"),
    Year("year:desc"),
    Rating("rating:desc");

    val label: String
        get() = when (this) {
            Added -> t("tv.catalog.sortAdded", "Added")
            Name -> t("tv.catalog.sortTitle", "Title")
            Year -> t("tv.catalog.sortYear", "Year")
            Rating -> t("tv.catalog.sortRating", "Rating")
        }
}

/** Two screenfuls of posters per request; the rest is read as the cursor nears the end. */
private const val WALL_PAGE_SIZE = 60

/** How many tiles short of the end of what is read the next page is asked for — about two rows. */
private const val WALL_PREFETCH_DISTANCE = 16

/**
 * What a wall has read so far, and how much the server says there is.
 *
 * ⚠️ THE COUNT ON THE HEADING IS [total], THE SERVER'S — never the number of posters read, which is
 * a page or two of it.
 */
private data class WallPages(val cards: List<TitleCard>, val total: Long, val pagesRead: Int, val pageCount: Int) {

    val complete: Boolean
        get() = pagesRead >= pageCount

    fun followedBy(next: PageResponse<TitleCard>): WallPages = copy(
        /* ⚠️ Distinct by id: a title added while the wall is read shifts a page by one, and the
           same poster twice is a duplicate key the grid refuses. */
        cards = (cards + next.content).distinctBy { card -> card.id },
        total = next.totalElements,
        pagesRead = pagesRead + 1,
        pageCount = next.totalPages,
    )

    companion object {
        fun first(page: PageResponse<TitleCard>) = WallPages(page.content, page.totalElements, 1, page.totalPages)
    }
}

private val sorts = Sort.entries

/**
 * The catalogue: a rail of genres down the left, a wall of posters on the
 * right, the sort above. LEFT from the first column reaches the rail, and the
 * chosen genre keeps its index tab so the eye can come back to it.
 */
@Composable
fun CatalogScreen(
    connection: ServerConnection,
    deck: DeckState,
    kind: String?,
    onTitle: (TitleCard) -> Unit,
    onAdd: (() -> Unit)? = null,
    onlyWithFiles: Boolean = false,
) {
    /* ⚠️ `kind` null is BOTH kinds — the Titles bay. One screen over one table, narrowed by a
       parameter, exactly as the browser's `/titles` is; a third copy of this wall for "everything"
       would drift from the two it copied. */
    val bay = when (kind) {
        "SERIES" -> Bay.Series
        "MOVIE" -> Bay.Films
        else -> Bay.Titles
    }
    val heading = when (bay) {
        Bay.Series -> t("tv.catalog.series", "Series")
        Bay.Films -> t("tv.catalog.films", "Films")
        else -> t("tv.catalog.titles", "Titles")
    }

    var genres by remember { mutableStateOf<List<NodeSummary>>(emptyList()) }
    var genre by remember { mutableStateOf<NodeSummary?>(null) }
    var sort by remember { mutableStateOf(sorts.first()) }
    var titles by rememberLoaded<WallPages>()
    var readingMore by remember { mutableStateOf(false) }
    var focusOnArrival by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    /* ⚠️ THE COUNTS ARE THE WALL'S OWN. Counted over everything filed, "Horror 2" sat beside a Series
       wall that opened on nothing — two horror series known, neither with a file. The server counts with
       the wall's filters, and a genre that would open on nothing is not offered at all. */
    LaunchedEffect(kind) {
        genres = runCatching {
            connection.api.nodes("genre", titleKind = kind, playable = if (onlyWithFiles) true else null)
                .filter { genre -> genre.titleCount > 0 }
        }.getOrDefault(emptyList())
    }

    /* ⚠️ FILMS AND SERIES ARE WHAT CAN BE WATCHED; TITLES IS EVERYTHING THE LIBRARY KNOWS. A film added
       from the provider with no copy on disk belongs under Titles and nowhere else — on the Films wall
       it is a poster that opens onto nothing to play. The SERVER narrows it (`playable`): the wall used
       to read one page of titles and drop those missing from a second list of works with files, which
       made every page short by a different amount and the count on the heading wrong. */
    suspend fun readPage(page: Int) = connection.api.titles(
        kind = kind,
        nodeId = genre?.id,
        sort = sort.key,
        playable = if (onlyWithFiles) true else null,
        page = page,
        size = WALL_PAGE_SIZE,
    )

    LaunchedEffect(kind, genre, sort) {
        titles = Loaded.Pending
        focusOnArrival = true
        titles = runCatching { readPage(0) }
            .fold({ first -> Loaded.Ready(WallPages.first(first)) }, { failure -> Loaded.Failed(failure.describeForScreen()) })
    }

    /** Asks for the next page while the cursor is still a few rows short of the end of what is read. */
    fun readMore() {
        val current = (titles as? Loaded.Ready)?.value ?: return
        if (current.complete || readingMore) {
            return
        }
        readingMore = true
        scope.launch {
            val next = runCatching { readPage(current.pagesRead) }.getOrNull()
            /* ⚠️ Only onto the wall it was asked for: a genre or a sort chosen while the page was out
               has replaced that wall, and appending to the new one would mix two orders. */
            if (next != null && (titles as? Loaded.Ready)?.value === current) {
                titles = Loaded.Ready(current.followedBy(next))
            }
            readingMore = false
        }
    }

    val firstTile = remember { FocusRequester() }
    val loaded = titles
    LaunchedEffect(loaded) {
        /* ⚠️ Once per wall, not per page: a page appended under the cursor must not send it home. */
        if (focusOnArrival && loaded is Loaded.Ready && loaded.value.cards.isNotEmpty()) {
            focusOnArrival = false
            runCatching { firstTile.requestFocus() }
        }
    }

    /* ⚠️ THE CHOSEN GENRE LENDS THE PAGE ITS GROUND, exactly as it does on the Genres shelf. Filtering
       to "Комедія" here and getting a plain black page, when choosing it from the shelf gave the room
       a picture, would make the same choice look like two different places. `All` clears it. */
    Screen(
        deck = deck,
        current = bay,
        backdrop = connection.imageUrl(genre?.imagePath, genre?.imageFileId),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp)) {
            /* ⚠️ ONE LINE OF HEAD. The eyebrow repeated the title — "SERIES" over "All series" — and the
               rail already says which section this is; the genre, when one is chosen, joins the title. */
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = heading, style = KitsuType.h1)
                    genre?.let { chosen ->
                        Text(text = chosen.displayName, style = KitsuType.h1.copy(color = Kitsu.foregroundMuted, fontWeight = FontWeight.Bold))
                    }
                    if (loaded is Loaded.Ready) {
                        Text(text = loaded.value.total.toString(), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                /* ⚠️ `focusGap` — these are chips a remote lands on; see the token. */
                /* ⚠️ Adding sits BESIDE the sort, on the wall it adds to: a title added from here lands on
                   this very wall, sorted by "Added" first. Absent, not disabled, for a profile that may not. */
                if (onAdd != null) {
                    /* ⚠️ A chip, the size of the sort chips beside it: a filled button here was the
                       loudest thing on a wall whose point is the titles. */
                    TvChip(
                        text = t("tv.catalog.add", "+ Add a title"),
                        pressed = false,
                        onClick = onAdd,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                    sorts.forEach { option ->
                        TvChip(text = option.label, onClick = { sort = option }, pressed = option == sort)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GenreRail(
                    genres = genres,
                    current = genre,
                    onChoose = { genre = it },
                    modifier = Modifier.width(144.dp).fillMaxHeight(),
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (loaded) {
                        Loaded.Pending -> Loading()
                        is Loaded.Failed -> Failed(loaded.problem) { genre = genre }
                        is Loaded.Ready -> {
                            val cards = loaded.value.cards
                            if (cards.isEmpty()) {
                                Text(
                                    text = when {
                                        !onlyWithFiles -> t("tv.catalog.empty", "Nothing here yet.")
                                        bay == Bay.Series -> t("tv.catalog.emptySeries", "No series with a file here yet — everything the library knows, with or without one, is under Titles.")
                                        else -> t("tv.catalog.emptyFilms", "No films with a file here yet — everything the library knows, with or without one, is under Titles.")
                                    },
                                    style = KitsuType.lead,
                                )
                            } else {
                                TileWall(minimumWidth = Kitsu.posterWidth) {
                                    itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                                        if (index >= cards.size - WALL_PREFETCH_DISTANCE) {
                                            LaunchedEffect(cards.size) {
                                                readMore()
                                            }
                                        }
                                        PosterTile(
                                            title = card.displayName,
                                            imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                                            onClick = { onTitle(card) },
                                            meta = card.metaLine(),
                                            year = card.year?.toString(),
                                            modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreRail(
    genres: List<NodeSummary>,
    current: NodeSummary?,
    onChoose: (NodeSummary?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = modifier.revealsPageEdges(listState), verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
        item {
            Eyebrow(t("tv.catalog.genre", "Genre"), muted = true, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        }
        item {
            TvRailItem(label = t("tv.catalog.all", "All"), count = null, current = current == null, onClick = { onChoose(null) })
        }
        items(genres, key = { it.id }) { genre ->
            TvRailItem(label = genre.displayName, count = genre.titleCount.toString(), current = current?.id == genre.id, onClick = { onChoose(genre) })
        }
    }
}
