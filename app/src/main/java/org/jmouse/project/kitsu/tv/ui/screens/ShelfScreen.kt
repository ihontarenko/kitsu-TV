package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.NodeSummary
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.NodeButton
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.TileWall
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/*
  ==============================================================================
  The shelves the television could not reach.

  ⚠️ THREE SCREENS THAT ARE ONE SCREEN. Collections, genres and the household's
  kept titles are the same page twice over — a heading, a count, a wall of
  tiles — and writing them three times is how three pages drift into three
  layouts. What differs is only WHAT IS FETCHED and WHAT A TILE IS, so those are
  the two things passed in; everything else is written once, here.

  ⚠️ AND THE DRILL-DOWN IS A ROUTE, NOT A STATE. Choosing a genre could have
  swapped the content of this screen and kept a boolean for "am I inside one".
  Making it an address instead means BACK already works, the deck's bay stays
  lit, and nothing here has to think about the remote's back button at all —
  which is the sort of thing that is only ever got wrong by hand.
  ==============================================================================
 */

/**
 * A wall of titles under a heading.
 *
 * <p>⚠️ The first tile takes focus once something arrives. A television that opens a page with the
 * cursor nowhere is a television that needs a press before the D-pad does anything, and the press
 * is invisible: nothing on screen says where the cursor would land.
 */
@Composable
private fun TitleWall(
    connection: ServerConnection,
    deck: DeckState,
    bay: Bay,
    eyebrow: String,
    heading: String,
    counting: (Int) -> String,
    empty: String,
    load: suspend () -> List<TitleCard>,
    onTitle: (TitleCard) -> Unit,
    reloadKey: Any? = Unit,
    backdrop: String? = null,
    /**
     * Drawn between the masthead and the wall — the places INSIDE this one, where there are any.
     *
     * ⚠️ A SLOT RATHER THAN A SECOND SCREEN. A genre's shelf and a collection's shelf differ by one
     * row of tiles and nothing else; forking them would leave two copies of the loading, failure,
     * empty and first-focus behaviour to keep in step, which is exactly the drift this file's own
     * "three screens that are one screen" note is about.
     *
     * ⚠️ IT IS ABOVE THE WALL, NOT BESIDE IT. On a D-pad the row of sub-genres has to be somewhere
     * UP from the posters, because up-and-down is the only way out of a grid that scrolls.
     */
    lead: (@Composable () -> Unit)? = null,
) {
    var titles by rememberLoaded<List<TitleCard>>()
    var attempt by remember { androidx.compose.runtime.mutableStateOf(0) }

    LaunchedEffect(reloadKey, attempt) {
        titles = Loaded.Pending
        titles = runCatching { load() }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    val firstTile = remember { FocusRequester() }
    val loaded = titles
    LaunchedEffect(loaded) {
        if (loaded is Loaded.Ready && loaded.value.isNotEmpty()) {
            runCatching { firstTile.requestFocus() }
        }
    }

    Screen(deck = deck, current = bay, backdrop = backdrop) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp)) {
            Masthead(
                eyebrow = eyebrow,
                heading = heading,
                count = (loaded as? Loaded.Ready)?.value?.size,
                counting = counting,
            )

            if (lead != null) {
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    lead()
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
                when (loaded) {
                    Loaded.Pending -> Loading()
                    is Loaded.Failed -> Failed(loaded.problem) { attempt += 1 }
                    is Loaded.Ready ->
                        if (loaded.value.isEmpty()) {
                            Text(text = empty, style = KitsuType.lead)
                        } else {
                            TileWall(minimumWidth = Kitsu.posterWidth) {
                                items(loaded.value, key = { it.id }) { card ->
                                    PosterTile(
                                        title = card.displayName,
                                        imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                                        onClick = { onTitle(card) },
                                        meta = card.metaLine(),
                                        year = card.year?.toString(),
                                        modifier = if (card == loaded.value.first()) {
                                            Modifier.focusRequester(firstTile)
                                        } else {
                                            Modifier
                                        },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * The head of a shelf: what this is, what it is called, and how much of it there is.
 *
 * <p>⚠️ The count is beside the heading rather than under it, in the quiet ink. It is the one number
 * somebody wants before they start moving, and a line of its own would give it the weight of a
 * second title.
 */
@Composable
private fun Masthead(eyebrow: String, heading: String, count: Int?, counting: (Int) -> String) {
    /* ⚠️ ONE LINE: the heading, then what kind of place it is and how much is in it, quietly. The
       eyebrow above it cost a line on every shelf and mostly repeated what the rail already says. */
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = heading, style = KitsuType.h1)
        val aside = listOfNotNull(eyebrow.ifBlank { null }, count?.let { counting(it) }).joinToString(" · ")
        if (aside.isNotEmpty()) {
            Text(text = aside, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/**
 * What this profile keeps — the kept titles as a wall, reached from the Collections bay.
 *
 * ⚠️ NOT A BAY OF ITS OWN ANY MORE. Favourites are a collection on the server — one per profile —
 * so on this television they sit where every other collection sits, first on the shelf, and the bay
 * that stays lit while reading them is Collections.
 */
@Composable
fun FavouritesScreen(
    connection: ServerConnection,
    deck: DeckState,
    onTitle: (TitleCard) -> Unit,
) {
    TitleWall(
        connection = connection,
        deck = deck,
        bay = Bay.Collections,
        eyebrow = t("tv.shelf.favouritesEyebrow", "Collection · yours"),
        heading = favouritesName(),
        counting = Counted::titles,
        empty = t("tv.shelf.favouritesEmpty", "Nothing kept yet. Open a title and press Keep."),
        load = { connection.api.favourites() },
        onTitle = onTitle,
    )
}

/** What the profile's own set of kept titles is called until the server names it otherwise. */
private fun favouritesName(): String = t("tv.shelf.favourites", "Favourites")

/**
 * One tile on a wall of places: what it is called, what the line under it says, and where it leads.
 *
 * ⚠️ A PLACE IS PASSED IN ALREADY DESCRIBED. A genre, a collection, a folder and the favourites all
 * draw as the same `NodeButton`; what differs is the second line and the destination. Deciding both
 * where the wall is built — rather than inside it with a `when` over kinds — keeps the wall one piece
 * of layout that knows nothing about what it is showing.
 */
data class ShelfEntry(
    val key: String,
    val name: String,
    val detail: String?,
    val icon: String?,
    val picture: String?,
    val onOpen: () -> Unit,
)

/**
 * A wall of places under a heading — genres, collections, the collections in one folder.
 *
 * <p>⚠️ A PLACE IS A TEXT TILE, NOT A POSTER. A genre has no artwork of its own, and a collection's
 * is whatever the first film in it happens to be; picking one and showing it as the collection's
 * face is a lie that looks convincing. The name and the line under it are what somebody chooses by.
 */
@Composable
private fun NodeWall(
    deck: DeckState,
    bay: Bay,
    eyebrow: String,
    heading: String,
    counting: (Int) -> String,
    empty: String,
    reloadKey: Any?,
    load: suspend () -> List<ShelfEntry>,
) {
    var entries by rememberLoaded<List<ShelfEntry>>()
    var attempt by remember { androidx.compose.runtime.mutableStateOf(0) }

    LaunchedEffect(reloadKey, attempt) {
        entries = Loaded.Pending
        entries = runCatching { load() }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    val firstTile = remember { FocusRequester() }
    val loaded = entries
    LaunchedEffect(loaded) {
        if (loaded is Loaded.Ready && loaded.value.isNotEmpty()) {
            runCatching { firstTile.requestFocus() }
        }
    }

    /*
      ⚠️ THE PICTURE BELONGS TO THE SCREEN, NOT TO THE BUTTON — see `NodeButton`. Whichever place the
      cursor is on lends the page its ground, so moving along the row changes the room.

      ⚠️ Held HERE rather than inside the grid, because the grid is recycled: a LazyVerticalGrid
      disposes the items that scroll out, and state kept in one of them would be lost and re-created
      as the cursor travelled.
     */
    var backdrop by remember(reloadKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Screen(deck = deck, current = bay, backdrop = backdrop) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp)) {
            Masthead(
                eyebrow = eyebrow,
                heading = heading,
                count = (loaded as? Loaded.Ready)?.value?.size,
                counting = counting,
            )

            Box(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
                when (loaded) {
                    Loaded.Pending -> Loading()
                    is Loaded.Failed -> Failed(loaded.problem) { attempt += 1 }
                    is Loaded.Ready ->
                        if (loaded.value.isEmpty()) {
                            Text(text = empty, style = KitsuType.lead)
                        } else {
                            /*
                              ⚠️ NARROWER TRACKS THAN A POSTER WALL, because these are controls.
                              A genre is chosen by its name; giving it a poster's footprint gave
                              it a poster's emptiness — see `NodeButton`.
                             */
                            TileWall(minimumWidth = 260.dp, rowGap = 10.dp) {
                                items(loaded.value, key = { it.key }) { entry ->
                                    NodeButton(
                                        name = entry.name,
                                        count = entry.detail,
                                        icon = entry.icon,
                                        onFocused = { backdrop = entry.picture },
                                        onClick = entry.onOpen,
                                        modifier = if (entry == loaded.value.first()) {
                                            Modifier.focusRequester(firstTile)
                                        } else {
                                            Modifier
                                        },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * A place as a tile whose second line counts its titles — a genre, or a collection.
 *
 * @param subGenres how many places are filed INSIDE this one; zero for a place that holds none
 */
private fun ServerConnection.titledEntry(
    node: NodeSummary,
    subGenres: Int = 0,
    onOpen: () -> Unit,
): ShelfEntry {
    /* ⚠️ Said, quietly, where a set re-fills itself: a franchise the provider knows gets the films it
       names on the next import, which is not a set somebody curates by taking things out. */
    val titles = Counted.titles(node.titleCount.toInt())
    val detail = when {
        node.synced -> t("tv.shelf.franchise", "{titles} · franchise", "titles" to titles)
        /* ⚠️ THE COUNT OF TITLES IS THE GENRE'S OWN, and the shelf behind the tile will hold more —
           it also carries everything filed in the sub-genres. Saying how many there are is what
           keeps the two numbers from looking like one of them is wrong. */
        subGenres > 0 -> t(
            "tv.shelf.withSubGenres",
            "{titles} · {count} inside",
            "titles" to titles,
            "count" to subGenres.toString(),
        )
        else -> titles
    }
    return ShelfEntry(
        key = node.id,
        name = node.displayName,
        detail = detail,
        icon = node.icon,
        picture = imageUrl(node.imagePath, node.imageFileId),
        onOpen = onOpen,
    )
}

/**
 * Every place of one kind — the genres.
 *
 * <p>⚠️ A GENRE SAYS WHAT IS INSIDE IT, and that is two requests rather than one. The sub-genres are
 * a whole kind fetched once and matched to their parents here; asking per genre would be a request
 * per tile. A genre with none reads exactly as it did before.
 *
 * <p>⚠️ THE SECOND REQUEST MAY FAIL WITHOUT FAILING THE SCREEN. The wall is the genres; how many
 * shelves are inside each is a detail on a line, and losing it is not worth losing the way in.
 */
@Composable
fun NodeShelfScreen(
    connection: ServerConnection,
    deck: DeckState,
    bay: Bay,
    kind: String,
    eyebrow: String,
    heading: String,
    counting: (Int) -> String,
    empty: String,
    onNode: (NodeSummary) -> Unit,
) {
    NodeWall(
        deck = deck,
        bay = bay,
        eyebrow = eyebrow,
        heading = heading,
        counting = counting,
        empty = empty,
        reloadKey = kind,
        load = {
            coroutineScope {
                val places = async { connection.api.nodes(kind) }
                val inside = async {
                    if (kind != KIND_GENRE) {
                        emptyList()
                    } else {
                        runCatching { connection.api.nodes(KIND_SUB_GENRE) }.getOrDefault(emptyList())
                    }
                }

                val branches = inside.await().groupBy { subGenre -> subGenre.parentId }

                places.await().map { node ->
                    connection.titledEntry(node, branches[node.id].orEmpty().size) { onNode(node) }
                }
            }
        },
    )
}

/**
 * The Collections bay: what this profile keeps, its folders, and every collection not in one.
 *
 * <h2>⚠️ ONE BAY FOR TWO THINGS THAT WERE ALWAYS ONE</h2>
 *
 * Favourites are a collection on the server — the profile's own, made on the first heart — so a bay
 * of their own beside Collections said there were two kinds of set when there is one. They lead the
 * wall because they are the set a person reaches for most.
 *
 * <h2>⚠️ A FOLDER HOLDS COLLECTIONS, SO ITS CONTENTS ARE NOT REPEATED HERE</h2>
 *
 * A collection whose parent is one of this profile's folders is drawn inside that folder only.
 * Drawing it twice would make the folder pointless, and a profile with six Bond films filed into
 * "Spies" would see the set at both addresses.
 */
@Composable
fun CollectionsScreen(
    connection: ServerConnection,
    deck: DeckState,
    onFavourites: () -> Unit,
    onGenres: () -> Unit,
    onFolder: (NodeSummary) -> Unit,
    onCollection: (NodeSummary) -> Unit,
) {
    NodeWall(
        deck = deck,
        bay = Bay.Collections,
        eyebrow = "",
        heading = t("tv.shelf.collections", "Collections"),
        counting = Counted::places,
        empty = t("tv.shelf.collectionsEmpty", "No collections yet."),
        reloadKey = Unit,
        load = {
            coroutineScope {
                val kept = async { connection.api.favourites() }
                val keptNode = async { connection.api.nodes(KIND_FAVOURITES).firstOrNull() }
                val folders = async { connection.api.nodes(KIND_FOLDER) }
                val collections = async { connection.api.nodes(KIND_COLLECTION) }
                val genres = async { connection.api.nodes(KIND_GENRE) }

                val folderIds = folders.await().map { folder -> folder.id }.toSet()
                val favourites = keptNode.await()

                buildList {
                    /* ⚠️ Always first and always there: a profile that has kept nothing yet still has
                       somewhere to look, and the server makes the node itself on the first heart. */
                    add(
                        ShelfEntry(
                            key = "favourites",
                            name = favourites?.displayName ?: favouritesName(),
                            detail = t("tv.shelf.yours", "{titles} · yours", "titles" to Counted.titles(kept.await().size)),
                            icon = favourites?.icon ?: "collection",
                            picture = connection.imageUrl(favourites?.imagePath, favourites?.imageFileId),
                            onOpen = onFavourites,
                        ),
                    )
                    /* ⚠️ GENRES ARE ONE ENTRY HERE, NOT A BAY. A genre is another way a title is
                       grouped, so it sits beside the sets a person made, and the deck keeps one
                       place for "how things are grouped" instead of two. */
                    add(
                        ShelfEntry(
                            key = "genres",
                            name = t("tv.genres.heading", "Genres"),
                            detail = t("tv.shelf.fromMetadata", "{genres} · from the metadata", "genres" to Counted.genres(genres.await().size)),
                            icon = "tag",
                            picture = null,
                            onOpen = onGenres,
                        ),
                    )
                    folders.await().forEach { folder ->
                        val inside = collections.await().count { collection -> collection.parentId == folder.id }
                        add(
                            ShelfEntry(
                                key = folder.id,
                                name = folder.displayName,
                                detail = t("tv.shelf.folderDetail", "{collections} · folder", "collections" to Counted.collections(inside)),
                                icon = folder.icon ?: "folder",
                                picture = connection.imageUrl(folder.imagePath, folder.imageFileId),
                                onOpen = { onFolder(folder) },
                            ),
                        )
                    }
                    collections.await()
                        .filter { collection -> collection.parentId !in folderIds }
                        .forEach { collection -> add(connection.titledEntry(collection) { onCollection(collection) }) }
                }
            }
        },
    )
}

/** The collections one of this profile's folders holds. */
@Composable
fun FolderScreen(
    connection: ServerConnection,
    deck: DeckState,
    slug: String,
    onCollection: (NodeSummary) -> Unit,
) {
    var folder by remember { androidx.compose.runtime.mutableStateOf<NodeSummary?>(null) }

    LaunchedEffect(slug) {
        folder = runCatching { connection.api.nodes(KIND_FOLDER).firstOrNull { it.slug == slug } }.getOrNull()
    }

    val resolved = folder

    NodeWall(
        deck = deck,
        bay = Bay.Collections,
        eyebrow = t("tv.shelf.folder", "Folder"),
        heading = resolved?.displayName ?: "…",
        counting = Counted::collections,
        empty = t("tv.shelf.folderEmpty", "This folder holds no collections yet. They are moved into it in the browser."),
        reloadKey = resolved?.id,
        load = {
            val current = resolved
            if (current == null) {
                emptyList()
            } else {
                connection.api.nodes(KIND_COLLECTION)
                    .filter { collection -> collection.parentId == current.id }
                    .map { collection -> connection.titledEntry(collection) { onCollection(collection) } }
            }
        },
    )
}

/** The kinds the Collections bay reads, as the server spells them. */
const val KIND_COLLECTION = "collection"
const val KIND_FOLDER = "collection-folder"
private const val KIND_GENRE = "genre"
private const val KIND_FAVOURITES = "favourites"

/**
 * A sub-genre — "Stephen King" inside Horror.
 *
 * <h2>⚠️ IT IS A `folder`, AND THE DIFFERENCE FROM `collection` IS WHO SEES IT</h2>
 *
 * <p>A collection is PROFILE-SCOPED: the server gives a new one to the profile that made it, so a
 * shelf built as a collection under Horror would exist for exactly one face in the household. A
 * sub-genre is the household's by definition, and `folder` is the kind that is not profile-scoped,
 * allows filing by hand, nests without a depth limit, and carries no external source — which is
 * what tells a metadata re-sync to leave it alone while it rewrites the genres around it.
 *
 * <h2>⚠️ NOTHING NEW IN THE TREE, AND NOTHING NEW ON THE SERVER</h2>
 *
 * <p>The taxonomy has always allowed this: no rule constrains a parent's kind, and the depth check
 * counts ancestors of the SAME kind, so a folder under a genre is depth 1 and legal. The browser
 * grew the screen for it first; this client simply could not SEE them, which is why every genre
 * looked flat here and had a different number of titles in it than the same genre in the browser.
 */
const val KIND_SUB_GENRE = "folder"

/**
 * What is filed under one node.
 *
 * <p>⚠️ THE NODE IS RESOLVED BY SLUG RATHER THAN CARRIED IN THE ADDRESS. The address could have held
 * the id and the display name as well and saved a request — at the cost of an address that stops
 * being true the moment somebody renames a genre in the browser. The list being resolved against is
 * a handful of rows, and it is the same request the shelf above already made.
 *
 * <p>⚠️ And the two kinds are fetched differently on purpose: a genre is a FILTER over the
 * catalogue, a collection is an ORDERED list somebody arranged. Asking for a collection by
 * `nodeId` would return its titles sorted by whatever the catalogue sorts by, which quietly throws
 * away the only thing that made it a collection.
 *
 * <h2>⚠️ A GENRE'S SHELF IS THE GENRE **AND EVERYTHING UNDER IT**</h2>
 *
 * <p>A film filed only into "Stephen King" is a horror film, and a Horror shelf that did not list it
 * would be a shelf that disagrees with the tree somebody built in the browser. See
 * {@link #KIND_SUB_GENRE} for what those places are.
 *
 * <p>⚠️ AND THE SUBTREE IS ASKED FOR ONE NODE AT A TIME, which is not laziness. {@code nodeId}
 * matches DIRECT memberships only — the listing route takes one node and has no "under here"
 * operator, and the browser works around the same gap by spelling the branch out as a jMQ
 * expression over slugs. There is no such parameter on this client's route, so the branch is spelled
 * out as requests instead: one per place, in parallel, merged by identifier. A genre holds a handful
 * of sub-genres, so this is a handful of requests and not a page-per-row.
 */
@Composable
fun NodeTitlesScreen(
    connection: ServerConnection,
    deck: DeckState,
    bay: Bay,
    kind: String,
    slug: String,
    eyebrow: String,
    onTitle: (TitleCard) -> Unit,
    /** Opening a sub-genre. Null where the kind cannot hold one — a collection. */
    onNode: ((NodeSummary) -> Unit)? = null,
) {
    var node by remember { androidx.compose.runtime.mutableStateOf<NodeSummary?>(null) }
    var subGenres by remember { androidx.compose.runtime.mutableStateOf<List<NodeSummary>>(emptyList()) }

    LaunchedEffect(kind, slug) {
        val resolved = runCatching { connection.api.nodes(kind).firstOrNull { it.slug == slug } }.getOrNull()
        node = resolved

        /* ⚠️ ASKED FOR THE WHOLE KIND AND NARROWED HERE, not asked per genre. The listing route
           answers one kind at a time; a request per genre would be a request per tile on the index
           the cursor just came from. A household has tens of these, not thousands.

           ⚠️ A FAILURE LEAVES THE LIST EMPTY RATHER THAN FAILING THE SCREEN. A genre with no
           sub-genres and a genre whose sub-genres could not be read look the same, and that is the
           right trade: the titles are what the screen is for. */
        subGenres = if (resolved == null || kind != KIND_GENRE) {
            emptyList()
        } else {
            runCatching { connection.api.nodes(KIND_SUB_GENRE) }
                .getOrDefault(emptyList())
                .filter { candidate -> candidate.parentId == resolved.id }
        }
    }

    val resolved = node
    val branch = subGenres

    TitleWall(
        connection = connection,
        deck = deck,
        bay = bay,
        eyebrow = eyebrow,
        heading = resolved?.displayName ?: "…",
        counting = Counted::titles,
        empty = t("tv.shelf.nodeEmpty", "Nothing is filed here."),
        /* ⚠️ The place's own picture follows you INTO it. Choosing "Комедія" and then losing the
           ground that said so would read as having arrived somewhere else. */
        backdrop = connection.imageUrl(resolved?.imagePath, resolved?.imageFileId),
        /* ⚠️ THE BRANCH IS PART OF THE KEY. The sub-genres arrive in the same effect as the node but
           the wall must reload when they do, or a genre opens showing only what is filed directly
           in it and silently gains rows nobody asked for a moment later. */
        reloadKey = resolved?.id to branch.map { it.id },
        load = {
            val current = resolved
            when {
                /* ⚠️ Not an error, and not an empty shelf either — the node list has simply not
                   come back yet, and `reloadKey` fires this again the moment it does. */
                current == null -> emptyList()
                kind == KIND_COLLECTION -> connection.api.curated(current.slug)
                else -> titlesUnder(connection, current, branch)
            }
        },
        onTitle = onTitle,
        lead = if (branch.isEmpty() || onNode == null) {
            null
        } else {
            {
                SubGenreLane(
                    connection = connection,
                    subGenres = branch,
                    onNode = onNode,
                )
            }
        },
    )
}

/**
 * Everything filed in a place or anywhere under it, once.
 *
 * <p>⚠️ MERGED BY IDENTIFIER, BECAUSE A TITLE SITS IN SEVERAL PLACES AT ONCE. A film filed into both
 * Horror and "Stephen King" comes back from two of these requests, and a shelf that showed it twice
 * would also count it twice in the heading — which is the number a person uses to decide whether the
 * shelf is worth walking.
 *
 * <p>⚠️ THE ORDER IS RE-ESTABLISHED AFTER THE MERGE. Each request is sorted by name on the server,
 * but concatenating several sorted lists gives a list that is not sorted; a shelf where the alphabet
 * restarts partway down reads as a fault.
 */
private suspend fun titlesUnder(
    connection: ServerConnection,
    place: NodeSummary,
    branch: List<NodeSummary>,
): List<TitleCard> = coroutineScope {
    val everywhere = (listOf(place) + branch).map { node ->
        async {
            runCatching {
                connection.api.titles(nodeId = node.id, sort = "name", size = 200).content
            }.getOrDefault(emptyList())
        }
    }

    everywhere.awaitAll()
        .flatten()
        .distinctBy { card -> card.id }
        .sortedBy { card -> card.displayName.lowercase() }
}

/**
 * The places inside this one, as a row above the wall.
 *
 * <h2>⚠️ A ROW, NOT A SECOND WALL</h2>
 *
 * <p>A genre's sub-genres are a way to NARROW what is already on screen, not a separate index — the
 * titles below already include everything in them. Given a wall of their own they would read as
 * "the genre is empty, look in here instead", which is the opposite of what they mean.
 *
 * <h2>⚠️ IT SCROLLS SIDEWAYS AND THE PAGE DOES NOT MOVE</h2>
 *
 * <p>That is the same rule every other row in this client follows — see `Lane`. A row that pushed
 * the poster wall down the screen as the cursor travelled along it would make the thing a person is
 * actually looking at move while they are looking at it.
 */
@Composable
private fun SubGenreLane(
    connection: ServerConnection,
    subGenres: List<NodeSummary>,
    onNode: (NodeSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = t("tv.genre.subGenres", "Inside this genre"),
            style = KitsuType.eyebrow,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
            items(subGenres, key = { it.id }) { subGenre ->
                NodeButton(
                    name = subGenre.displayName,
                    count = Counted.titles(subGenre.titleCount.toInt()),
                    icon = subGenre.icon,
                    onFocused = {},
                    onClick = { onNode(subGenre) },
                    modifier = Modifier.width(240.dp),
                )
            }
        }
    }
}
