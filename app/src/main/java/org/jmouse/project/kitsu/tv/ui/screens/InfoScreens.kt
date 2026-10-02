package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.formatDate
import org.jmouse.project.kitsu.tv.text.formatDateTime
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.plural
import org.jmouse.project.kitsu.tv.text.formatDecimal
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.BuildConfig
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.CreditEntry
import org.jmouse.project.kitsu.tv.data.api.ServerDescription
import org.jmouse.project.kitsu.tv.data.api.ServerLibrary
import org.jmouse.project.kitsu.tv.data.api.Session
import org.jmouse.project.kitsu.tv.data.api.TitleDetail
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.PersonTile
import org.jmouse.project.kitsu.tv.ui.components.Shelf
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.focusRing
import org.jmouse.project.kitsu.tv.ui.components.kindLabel
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/*
  ==============================================================================
  The screens that are read rather than browsed. The profile page, which used
  to be the third, is in `ProfilePage.kt` now that it is a menu.

  ⚠️ NONE OF THEM IS A BAY, and that is deliberate. The deck holds seven already,
  which is what a 1080p bar takes; these are reached from the thing they are
  about — a title's own About, and the profile tile that was already in the
  corner. A screen reached from its subject needs no door of its own.

  ⚠️ AND THEY SHARE ONE VOCABULARY — `Section`, `Fact`, `Paragraph` — because
  three pages of facts written three ways become three layouts within a month.
  It is the same argument as `ShelfScreen`'s, applied to prose instead of tiles.
  ==============================================================================
 */

/* ── The shared vocabulary ────────────────────────────────────────────────── */

/**
 * A titled block of facts.
 *
 * <p>⚠️ The heading is a heading, not an eyebrow. An eyebrow labels the thing BELOW it in the same
 * breath — "GENRE · Дитячий" — while these are separate subjects a person scans between, and the
 * difference has to be legible from three metres without colour doing the work.
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = KitsuType.h2,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/**
 * One label and one value.
 *
 * <p>⚠️ ABSENT RATHER THAN EMPTY. A fact with no value is not drawn at all — a column of labels
 * followed by nothing reads as a broken screen, and "—" beside eleven labels reads as a broken
 * server. What is not known is simply not claimed.
 *
 * <p>⚠️ The value is MONO. Everything on these screens is compared to something — an address to
 * another address, a version to a version — and proportional digits make that harder for no gain.
 */
@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            modifier = Modifier.width(180.dp),
        )
        Text(
            text = value,
            style = KitsuType.meta.copy(color = Kitsu.foreground),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A paragraph at reading size, with no line limit — this is the screen that exists to hold it. */
@Composable
private fun Paragraph(text: String?) {
    if (text.isNullOrBlank()) {
        return
    }
    Text(
        text = text,
        style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
        modifier = Modifier.fillMaxWidth(0.72f),
    )
}

/**
 * A block of reading that can hold focus — so a television can scroll to it.
 *
 * <p>⚠️ IT DOES NOTHING WHEN PRESSED, and that is the point: it exists so the remote has somewhere
 * to stand above the shelves. A sunken ground and a ruled edge make it a surface at rest; the focus
 * ring says where the cursor is, the same ring every control draws.
 */
@Composable
private fun ReadingBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(SurfaceShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Kitsu.backgroundSunken,
            focusedContainerColor = Kitsu.backgroundSunken,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Kitsu.border), shape = SurfaceShape),
            focusedBorder = focusRing(shape = SurfaceShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) { content() }
    }
}

/* ── About a title ────────────────────────────────────────────────────────── */

/**
 * Everything the catalogue knows about one work — and nothing that plays it.
 *
 * <h2>⚠️ IT EXISTS BECAUSE THE TITLE SCREEN CANNOT HOLD IT</h2>
 *
 * That screen is a decision: artwork, a sentence, and a button. Its summary is clipped to three
 * lines and its cast is a single shelf, because somebody standing in front of it has already
 * decided to watch something. This one is for the other visit — reading about a film without
 * watching it, the way the browser lets somebody — so nothing here is truncated and nothing here
 * plays.
 *
 * ⚠️ **The credits are grouped by department**, not listed. A flat run of forty names is a wall;
 * "Directing · Writing · Cast · Sound" is four short lists somebody can skip between with one press.
 */
@Composable
fun AboutTitleScreen(
    connection: ServerConnection,
    deck: DeckState,
    slug: String,
    onPerson: (String) -> Unit,
) {
    var state by rememberLoaded<Pair<TitleDetail, List<CreditEntry>>>()
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(slug, attempt) {
        state = Loaded.Pending
        state = runCatching {
            val detail = connection.api.title(slug)
            val credits = runCatching { connection.api.credits(slug) }.getOrDefault(emptyList())
            detail to credits
        }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    Screen(deck = deck, current = null) {
        when (val current = state) {
            Loaded.Pending -> Loading()
            is Loaded.Failed -> Failed(current.problem) { attempt += 1 }
            is Loaded.Ready ->
                AboutTitleBody(connection, current.value.first, current.value.second, onPerson)
        }
    }
}

@Composable
private fun AboutTitleBody(
    connection: ServerConnection,
    detail: TitleDetail,
    credits: List<CreditEntry>,
    onPerson: (String) -> Unit,
) {
    val first = rememberInitialFocus(detail.slug)

    /*
      ⚠️ GROUPED IN THE ORDER A CREDIT ROLL USES, not alphabetically and not by size. Cast first
      because it is what somebody came for; the departments follow in the order they are usually
      read. An unknown department falls to the end rather than inventing a place for itself.
     */
    val cast = credits.filter { it.kind.equals("CAST", ignoreCase = true) }
    val crew = credits.filterNot { it.kind.equals("CAST", ignoreCase = true) }
    val departments = crew.groupBy { it.department?.takeIf(String::isNotBlank) ?: "Crew" }
        .toList()
        .sortedBy { (name, _) -> DEPARTMENT_ORDER.indexOf(name).takeIf { it >= 0 } ?: DEPARTMENT_ORDER.size }

    val pageState = rememberLazyListState()
    LazyColumn(
        state = pageState,
        modifier = Modifier.fillMaxSize().revealsPageEdges(pageState),
        contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        /*
          ⚠️ THE TOP OF THE PAGE IS SOMETHING TO STAND ON. A television scrolls by focus, and nothing
          above the cast could hold it — so pressing Up from the first shelf could never bring the
          title, the summary or the facts back into view, and the page stopped short of its own top.
          These two blocks take focus (and draw the ring when they have it); the page opens on the
          first, at the top, with the summary in reach before anybody touches the remote.
         */
        item {
            ReadingBlock(modifier = Modifier.padding(horizontal = Kitsu.gutter).focusRequester(first)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(listOfNotNull(kindLabel(detail.kind), detail.year?.toString()).joinToString(" · "))
                    Text(text = detail.displayName, style = KitsuType.h1)
                    if (!detail.tagline.isNullOrBlank()) {
                        Text(
                            text = detail.tagline,
                            style = KitsuType.lead.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                    if (!detail.summary.isNullOrBlank()) {
                        Box(modifier = Modifier.padding(top = 8.dp)) { Paragraph(detail.summary) }
                    }
                }
            }
        }

        item {
            ReadingBlock(modifier = Modifier.padding(horizontal = Kitsu.gutter)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Section(t("tv.about.work", "The work")) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Fact(t("tv.about.sortName", "Sort name"), detail.sortName)
                            Fact(t("tv.title.originalLanguage", "Original language"), detail.originalLanguage)
                            Fact(t("tv.title.released", "Released"), formatDate(detail.releasedOn))
                            Fact(t("tv.about.ended", "Ended"), detail.endedYear?.toString())
                            Fact(t("tv.series.status", "Status"), detail.status)
                            Fact(t("tv.about.runtime", "Runtime"), formatRuntime(detail.runtimeMinutes))
                            Fact(
                                t("tv.catalog.sortRating", "Rating"),
                                detail.rating?.let { rating ->
                                    val votes = detail.voteCount
                                    if (votes != null && votes > 0) {
                                        formatDecimal(rating) + " · " + plural("tv.about.votes", votes, "{count} vote", "{count} votes")
                                    } else {
                                        formatDecimal(rating)
                                    }
                                },
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Section(t("tv.person.inLibrary", "In this library")) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Fact(t("tv.title.added", "Added"), formatDateTime(detail.addedAt))
                            Fact(t("tv.about.address", "Address"), detail.slug)
                            for ((kind, nodes) in detail.nodes.groupBy { it.kind }) {
                                Fact(
                                    kind?.let { known -> kindLabel(known) } ?: t("tv.about.filedUnder", "Filed under"),
                                    nodes.joinToString(" · ") { it.displayName },
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                Shelf(title = t("tv.about.cast", "Cast"), note = Counted.credited(cast.size)) {
                    items(cast.size, key = { cast[it].personId + cast[it].sortOrder }) { index ->
                        val credit = cast[index]
                        PersonTile(
                            name = credit.personName,
                            role = credit.characterName,
                            imageUrl = connection.imageUrl(credit.portraitPath, credit.portraitFileId),
                            onClick = { onPerson(credit.personId) },
                        )
                    }
                }
            }
        }

        for ((department, people) in departments) {
            item {
                Shelf(title = department, note = Counted.credited(people.size)) {
                    items(people.size, key = { people[it].personId + people[it].sortOrder }) { index ->
                        val credit = people[index]
                        PersonTile(
                            name = credit.personName,
                            role = credit.job,
                            imageUrl = connection.imageUrl(credit.portraitPath, credit.portraitFileId),
                            onClick = { onPerson(credit.personId) },
                        )
                    }
                }
            }
        }

        if (credits.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter)) {
                    Text(
                        text = t("tv.about.noCredits", "No credits have been fetched for this title yet."),
                        style = KitsuType.lead,
                    )
                }
            }
        }
    }
}

/** ⚠️ The order a credit roll uses. Anything not named here falls to the end, in its own name. */
private val DEPARTMENT_ORDER = listOf(
    "Directing", "Writing", "Production", "Camera", "Editing",
    "Art", "Costume & Make-Up", "Sound", "Visual Effects", "Crew",
)

/* ── The server, and what this build is ───────────────────────────────────── */

/**
 * Every library the server runs on, grouped by family — read from the server's own classpath.
 *
 * ⚠️ A sub-page rather than a block on the server screen: a hundred and forty lines is a list somebody
 * opens on purpose, and on the screen above it would push everything else off the bottom.
 */
@Composable
fun LibrariesScreen(connection: ServerConnection, deck: DeckState) {
    val context = LocalContext.current
    var state by rememberLoaded<ServerDescription>()
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(connection.address, attempt) {
        state = Loaded.Pending
        state = runCatching { connection.api.server() }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    /* ⚠️ TWO LISTS, NEVER ONE. What this television app is built from and what the server runs on are
       different machines with different answers to "which version of X", and one list of both would
       make every question about a library ambiguous. The app's own list was written into its assets
       at build time — see `AppLibrariesTask` in the build file. */
    val appLibraries = remember { readAppLibraries(context) }
    var showingServer by remember { mutableStateOf(false) }
    val first = rememberInitialFocus()

    Screen(deck = deck, current = null) {
        val serverLibraries = (state as? Loaded.Ready)?.value?.libraries.orEmpty()
        val shown = if (showingServer) serverLibraries else appLibraries
        val families = librariesByFamily(shown)
        val pageState = rememberLazyListState()
        LazyColumn(
            state = pageState,
            modifier = Modifier.fillMaxSize().revealsPageEdges(pageState),
            contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Kitsu.gutter).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(t("tv.profile.server", "Server and build"))
                        Text(text = t("tv.libraries.heading", "Libraries"), style = KitsuType.h1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
                        TvChip(
                            text = t("tv.libraries.thisApp", "This app"),
                            count = appLibraries.size.toString(),
                            pressed = !showingServer,
                            onClick = { showingServer = false },
                            modifier = Modifier.focusRequester(first),
                        )
                        TvChip(
                            text = t("tv.libraries.theServer", "The server"),
                            count = serverLibraries.size.takeIf { it > 0 }?.toString(),
                            pressed = showingServer,
                            onClick = { showingServer = true },
                        )
                    }
                    Text(
                        text = if (showingServer) {
                            when (val current = state) {
                                Loaded.Pending -> t("tv.libraries.asking", "Asking the server…")
                                is Loaded.Failed -> current.problem
                                is Loaded.Ready -> t("tv.libraries.serverJars", "{jars}, read off the running server's classpath.", "jars" to Counted.jars(serverLibraries.size))
                            }
                        } else {
                            plural(
                                "tv.libraries.appLibraries",
                                appLibraries.size,
                                "{count} library this television app was built with — read from its own build, not typed.",
                                "{count} libraries this television app was built with — read from its own build, not typed.",
                            )
                        },
                        style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                    )
                }
            }
                    families.forEachIndexed { familyIndex, (family, members) ->
                        item(key = "family-$family") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Kitsu.gutter)
                                    .padding(top = if (familyIndex == 0) 0.dp else 24.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(text = family, style = KitsuType.h2)
                                Text(
                                    text = Counted.jars(members.size),
                                    style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }
                        val pairs = members.chunked(2)
                        items(pairs.size, key = { "$family-$it" }) { pairIndex ->
                            LibraryRow(
                                pair = pairs[pairIndex],
                                family = family,
                            )
                        }
                    }
                }
    }
}

/**
 * Families with a few jars of their own keep their name; the singletons go together at the end.
 *
 * ⚠️ Forty-eight headings for a hundred and forty jars read as a list of headings. A family that is
 * one jar — `HikariCP`, `jmdns` — is not a family, and a heading over it is noise.
 */
private fun librariesByFamily(libraries: List<ServerLibrary>): List<Pair<String, List<ServerLibrary>>> {
    val grouped = libraries.groupBy { it.family }
    val (families, loose) = grouped.entries.partition { it.value.size >= FAMILY_THRESHOLD }
    val named = families
        .sortedWith(compareByDescending<Map.Entry<String, List<ServerLibrary>>> { it.value.size }.thenBy { it.key })
        .map { it.key to it.value.sortedBy { library -> library.name } }
    val rest = loose.flatMap { it.value }.sortedBy { it.name.lowercase() }
    return if (rest.isEmpty()) named else named + (t("tv.libraries.everythingElse", "everything else") to rest)
}

private const val FAMILY_THRESHOLD = 3

/**
 * Two libraries to a line, name left and version right in tabular figures, on alternating ground.
 *
 * ⚠️ FOCUSABLE, and pressing it does nothing. A D-pad scrolls a list by moving the cursor through it;
 * a page of plain text is a page the remote cannot move at all.
 */
@Composable
private fun LibraryRow(pair: List<ServerLibrary>, family: String, modifier: Modifier = Modifier) {
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Kitsu.backgroundElevated,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().drawBehind {
                drawLine(Kitsu.border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            pair.forEach { library -> LibraryCell(library, family, Modifier.weight(1f)) }
            if (pair.size == 1) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryCell(library: ServerLibrary, family: String, modifier: Modifier) {
    /* The family is the heading above, so it is said once there and dimmed here: "spring-" thirty-six
       times in full ink is what made the list unreadable. */
    val prefix = "$family-"
    val (lead, rest) = if (library.name.startsWith(prefix)) prefix to library.name.removePrefix(prefix) else "" to library.name
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = Kitsu.foregroundSubtle))
                append(lead)
                pop()
                append(rest)
            },
            style = KitsuType.body.copy(color = Kitsu.foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = library.version ?: "",
            style = KitsuType.meta.copy(color = Kitsu.foregroundMuted),
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * The app's own libraries, from the list its build wrote into the assets: `group:name:version` lines.
 *
 * ⚠️ The family is the group's first two parts — `androidx.compose`, `com.squareup` — because an
 * Android dependency's group is where its family lives; a jar's name alone ("ui", "core") says nothing.
 */
private fun readAppLibraries(context: android.content.Context): List<ServerLibrary> =
    runCatching {
        context.assets.open("app-libraries.txt").bufferedReader().readLines()
            .mapNotNull { line ->
                val parts = line.trim().split(":")
                if (parts.size != 3) {
                    null
                } else {
                    ServerLibrary(name = parts[1], version = parts[2], family = parts[0].split(".").take(2).joinToString("."))
                }
            }
    }.getOrDefault(emptyList())

internal fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> t("tv.time.daysHours", "{days} d {hours} h", "days" to days, "hours" to hours)
        hours > 0 -> t("tv.time.hoursMinutes", "{hours} h {minutes} min", "hours" to hours, "minutes" to minutes)
        else -> t("tv.time.minutes", "{minutes} min", "minutes" to minutes)
    }
}

/**
 * Which of the formats that decide direct play this device can decode at all.
 *
 * ⚠️ The one fact that answers "why is this being re-encoded" before anybody asks the server: a
 * television without an HEVC decoder transcodes every HEVC file, however fast the network is.
 */
internal fun decodableFormats(): String {
    val wanted = linkedMapOf(
        "video/avc" to "H.264",
        "video/hevc" to "HEVC",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/av01" to "AV1",
        "audio/ac3" to "AC-3",
        "audio/eac3" to "E-AC-3",
        "audio/vnd.dts" to "DTS",
    )
    val decoders = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
    return wanted.filter { (mime, _) ->
        decoders.any { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
    }.values.joinToString(" · ").ifBlank { t("tv.server.noneReported", "none reported") }
}
