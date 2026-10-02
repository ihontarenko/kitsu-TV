package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.plural
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ImportBatch
import org.jmouse.project.kitsu.tv.data.api.ImportBatchItem
import org.jmouse.project.kitsu.tv.data.api.NodeSummary
import org.jmouse.project.kitsu.tv.data.api.ProviderWork
import org.jmouse.project.kitsu.tv.data.api.ProviderWorks
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.data.api.importBatchRequest
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.ProgressRule
import org.jmouse.project.kitsu.tv.ui.components.TileWall
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.TvListRow
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape


/*
  ==============================================================================
  One person's whole career at a film database, and taking it in at once.

  ⚠️ «пошукати в провайдера фільми до яких причетний стівен кінг, щоб додати всі
  фільми собі в колекцію». A career is not a list anybody wants whole: Stephen
  King is credited on 616 works, 536 of them "from his writing", and most of those
  are student shorts nobody has heard of. So two narrowings come first and are on
  by default — the one part that describes this person best, and only works enough
  people have rated — and "add all" means all of what is on the screen.
  ==============================================================================
 */

/** ⚠️ Below this many ratings a work is, in practice, a short film nobody in the house means. */
private const val WELL_KNOWN_VOTES = 100

/** The server refuses more than this in one batch. */
private const val BATCH_MAXIMUM = 200

private const val BATCH_POLL_MILLISECONDS = 1_500L

/** What pressing "Add all" leads through. */
private sealed interface AddingStep {
    data object Browsing : AddingStep
    data object ChoosingCollection : AddingStep
    data class Running(val batchId: String) : AddingStep
}

/**
 * @param source     the film database the person is known at — `TMDB`
 * @param externalId their identifier there
 * @param name       their name, carried from where they were found, for the header while loading
 */
@Composable
fun ProviderPersonScreen(
    connection: ServerConnection,
    deck: DeckState,
    source: String,
    externalId: String,
    name: String,
    onTitle: (TitleCard) -> Unit,
    onCollection: (slug: String) -> Unit,
) {
    var state by rememberLoaded<ProviderWorks>()
    var attempt by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf<String?>(null) }
    var wellKnownOnly by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf<AddingStep>(AddingStep.Browsing) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val importing = rememberProviderImport(
        connection = connection,
        onOpened = onTitle,
        onProblem = { refusal -> problem = refusal },
    )

    LaunchedEffect(source, externalId, attempt) {
        state = Loaded.Pending
        state = runCatching { connection.api.personWorks(externalId, providerLanguage(), source) }
            .fold({ works ->
                if (category == null) {
                    category = dominantCategory(works.works)
                }
                Loaded.Ready(works)
            }, { failure -> Loaded.Failed(failure.describeForScreen()) })
    }

    Screen(deck = deck, current = Bay.Titles) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(t("tv.career.eyebrow", "Everything by them at {source}", "source" to sourceMark(source)))
                Text(text = name, style = KitsuType.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            problem?.let { refusal ->
                Text(text = refusal, style = KitsuType.body.copy(color = Kitsu.danger))
            }

            when (val current = state) {
                Loaded.Pending -> Loading(t("tv.career.asking", "Asking {source} for everything they worked on…", "source" to sourceMark(source)))
                is Loaded.Failed -> Failed(current.problem) { attempt++ }
                is Loaded.Ready -> {
                    val eligible = current.value.works.filter { work -> !wellKnownOnly || (work.votes ?: 0) >= WELL_KNOWN_VOTES }
                    val visible = eligible.filter { work -> category == null || work.categories.contains(category) }

                    when (val adding = step) {
                        AddingStep.Browsing -> Browsing(
                            connection = connection,
                            works = current.value.works,
                            eligible = eligible,
                            visible = visible,
                            category = category,
                            onCategory = { chosen -> category = chosen },
                            wellKnownOnly = wellKnownOnly,
                            onWellKnownOnly = { wellKnownOnly = it },
                            importing = importing,
                            onAddAll = { step = AddingStep.ChoosingCollection },
                        )
                        AddingStep.ChoosingCollection -> CollectionChoice(
                            connection = connection,
                            count = visible.size,
                            onBack = { step = AddingStep.Browsing },
                            onChosen = { collectionSlug ->
                                problem = null
                                val request = importBatchRequest(current.value.source, providerLanguage(), collectionSlug,
                                                                 visible.map { work -> work.title })
                                step = AddingStep.Browsing
                                scope.launch {
                                    runCatching { connection.api.startImportBatch(request) }
                                        .onSuccess { batch -> step = AddingStep.Running(batch.id) }
                                        .onFailure { failure -> problem = failure.describeForScreen() }
                                }
                            },
                        )
                        is AddingStep.Running -> BatchProgress(
                            connection = connection,
                            batchId = adding.batchId,
                            onTitle = onTitle,
                            onCollection = onCollection,
                            onDone = {
                                step = AddingStep.Browsing
                                // ⚠️ Asked again, so every tile now says "In the library" where it is.
                                attempt++
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Browsing(
    connection: ServerConnection,
    works: List<ProviderWork>,
    eligible: List<ProviderWork>,
    visible: List<ProviderWork>,
    category: String?,
    onCategory: (String?) -> Unit,
    wellKnownOnly: Boolean,
    onWellKnownOnly: (Boolean) -> Unit,
    importing: ProviderImport,
    onAddAll: () -> Unit,
) {
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstChip.requestFocus() }
    }

    // ⚠️ Counted over what the well-known switch lets through, so a chip never promises forty and shows four.
    val counts = CATEGORY_ORDER.associateWith { key -> eligible.count { work -> work.categories.contains(key) } }
        .filterValues { count -> count > 0 }
    val missing = visible.count { work -> work.title.librarySlug == null }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            TvChip(
                text = t("tv.career.everything", "Everything"),
                pressed = category == null,
                count = eligible.size.toString(),
                onClick = { onCategory(null) },
                modifier = Modifier.focusRequester(firstChip),
            )
        }
        items(counts.keys.toList(), key = { key -> key }) { key ->
            TvChip(text = categoryLabel(key), pressed = category == key, count = counts[key].toString(), onClick = { onCategory(key) })
        }
        item {
            TvChip(
                text = if (wellKnownOnly) t("tv.career.wellKnown", "Well-known only") else t("tv.career.includingObscure", "Including obscure"),
                pressed = wellKnownOnly,
                onClick = { onWellKnownOnly(!wellKnownOnly) },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TvButton(
            onClick = onAddAll,
            text = t("tv.career.addAll", "Add all {count} to…", "count" to visible.size),
            weight = ButtonWeight.Primary,
            enabled = visible.isNotEmpty() && visible.size <= BATCH_MAXIMUM,
        )
        Text(
            text = when {
                visible.size > BATCH_MAXIMUM -> t("tv.career.tooMany", "At most {maximum} at once — narrow it with a part or the well-known switch.", "maximum" to BATCH_MAXIMUM)
                else -> t(
                    "tv.career.summary",
                    "{missing} · {works}",
                    "missing" to plural("tv.career.notInLibrary", missing, "{count} not in the library yet", "{count} not in the library yet"),
                    "works" to plural("tv.career.works", works.size, "{count} work in their whole career", "{count} works in their whole career"),
                )
            },
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
        )
    }

    if (visible.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, Kitsu.border, SurfaceShape).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = t("tv.career.nothing", "Nothing here with these switches."), style = KitsuType.lead.copy(color = Kitsu.foreground))
            Text(
                text = t("tv.career.nothingHint", "Turn off “Well-known only”, or choose another part — most of a long career is short films few people rated."),
                style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
            )
        }
        return
    }

    TileWall(minimumWidth = Kitsu.posterWidth) {
        items(visible, key = { work -> "work:${work.title.kind}:${work.title.externalId}" }) { work ->
            val alreadyHere = libraryCopyOf(work.title)
            PosterTile(
                title = work.title.title,
                imageUrl = connection.resolve(work.title.posterUrl),
                onClick = { importing.choose(work.title, alreadyHere) },
                meta = when {
                    alreadyHere != null -> t("tv.import.inLibrary", "In the library")
                    importing.adding == work.title.externalId -> t("tv.import.adding", "Adding…")
                    else -> partLabel(work, category)
                },
                year = work.title.year?.toString(),
            )
        }
    }
}

/**
 * Where the batch files what it takes in — a collection, or nowhere.
 *
 * ⚠️ The profile's own collections first: they are the ones a person means by "my collection".
 * A franchise the film database keeps in step is left out — filing into it by hand is the sync's
 * business, not a batch's.
 */
@Composable
private fun CollectionChoice(
    connection: ServerConnection,
    count: Int,
    onBack: () -> Unit,
    onChosen: (collectionSlug: String?) -> Unit,
) {
    var collections by rememberLoaded<List<NodeSummary>>()
    LaunchedEffect(connection) {
        collections = runCatching { connection.api.nodes(KIND_COLLECTION) }
            .fold({ found ->
                Loaded.Ready(found.filter { node -> !node.synced }
                                  .sortedWith(compareByDescending<NodeSummary> { node -> node.personal }.thenBy { node -> node.displayName }))
            }, { failure -> Loaded.Failed(failure.describeForScreen()) })
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
    }

    Text(text = t("tv.career.fileWhere", "Add {titles} — and file them where?", "titles" to Counted.titles(count)), style = KitsuType.h2)
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.width(640.dp).revealsPageEdges(listState), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            TvListRow(onClick = { onChosen(null) }, modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                Text(text = t("tv.career.noCollection", "No collection — just add them to the library"), style = KitsuType.body)
            }
        }
        when (val current = collections) {
            Loaded.Pending -> item { Text(text = t("tv.career.readingCollections", "Reading your collections…"), style = KitsuType.meta) }
            is Loaded.Failed -> item { Text(text = current.problem, style = KitsuType.body.copy(color = Kitsu.danger)) }
            is Loaded.Ready -> items(current.value, key = { node -> node.id }) { node ->
                TvListRow(onClick = { onChosen(node.slug) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = node.displayName, style = KitsuType.body, modifier = Modifier.weight(1f))
                    Text(
                        text = listOfNotNull(if (node.personal) t("tv.collections.yours", "yours") else t("tv.collections.household", "household"), Counted.titles(node.titleCount.toInt()))
                            .joinToString(" · "),
                        style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                    )
                }
            }
        }
        item {
            TvButton(onClick = onBack, text = t("tv.common.back", "Back"), weight = ButtonWeight.Quiet)
        }
    }
}

/**
 * A batch as it runs — the counts, and every title with where it got to.
 *
 * ⚠️ A FAILURE IS A NAMED ROW, never a smaller total: forty requested and thirty-eight added must
 * say which two and why, or the missing films are simply never noticed.
 */
@Composable
private fun BatchProgress(
    connection: ServerConnection,
    batchId: String,
    onTitle: (TitleCard) -> Unit,
    onCollection: (String) -> Unit,
    onDone: () -> Unit,
) {
    var batch by remember { mutableStateOf<ImportBatch?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(batchId) {
        while (true) {
            val read = runCatching { connection.api.importBatch(batchId) }
            read.onSuccess { found -> batch = found }
            read.onFailure { failure -> problem = failure.describeForScreen() }
            if (read.getOrNull()?.finished == true || read.isFailure) {
                break
            }
            delay(BATCH_POLL_MILLISECONDS)
        }
    }

    val current = batch
    if (current == null) {
        Text(text = problem ?: t("tv.career.starting", "Starting…"), style = KitsuType.lead)
        return
    }
    val settled = current.added + current.alreadyHere + current.failed

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (current.finished) {
                t("tv.common.done", "Done")
            } else {
                t("tv.career.addingOf", "Adding {count} of {total}…", "count" to settled + 1, "total" to current.total)
            },
            style = KitsuType.h2,
        )
        ProgressRule(fraction = if (current.total == 0) 1f else settled.toFloat() / current.total, modifier = Modifier.width(640.dp))
        Text(
            text = listOfNotNull(
                plural("tv.career.added", current.added, "{count} added", "{count} added"),
                plural("tv.career.alreadyHere", current.alreadyHere, "{count} already here", "{count} already here"),
                if (current.failed > 0) plural("tv.career.failed", current.failed, "{count} failed", "{count} failed") else null,
                current.collectionName?.let { collection -> t("tv.career.filedInto", "filed into «{collection}»", "collection" to collection) },
            ).joinToString("  ·  "),
            style = KitsuType.meta,
        )
        problem?.let { refusal -> Text(text = refusal, style = KitsuType.body.copy(color = Kitsu.danger)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current.finished) {
                TvButton(onClick = onDone, text = t("tv.career.backToWorks", "Back to the works"), weight = ButtonWeight.Primary)
                current.collectionSlug?.let { slug ->
                    TvButton(onClick = { onCollection(slug) }, text = t("tv.career.openCollection", "Open the collection"))
                }
            }
        }
    }

    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.width(720.dp).revealsPageEdges(listState), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(current.items, key = { item -> "${item.kind}:${item.externalId}" }) { item ->
            TvListRow(
                onClick = {
                    item.slug?.let { slug -> onTitle(TitleCard(id = slug, kind = item.kind, slug = slug, displayName = item.title ?: slug)) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title ?: item.externalId, style = KitsuType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.problem?.let { why ->
                        Text(text = why, style = KitsuType.caption.copy(color = Kitsu.danger), maxLines = 2)
                    }
                }
                Text(text = stateLabel(item), style = KitsuType.caption.copy(color = stateColour(item)))
            }
        }
    }
}

/** The order chips appear in — the parts people ask for first. */
private val CATEGORY_ORDER = listOf("SOURCE_MATERIAL", "WRITING", "DIRECTING", "PRODUCTION", "ACTING", "APPEARANCE", "CREW")

private fun categoryLabel(category: String): String = when (category) {
    "SOURCE_MATERIAL" -> t("tv.career.partSource", "From their work")
    "WRITING" -> t("tv.career.partWriting", "Wrote")
    "DIRECTING" -> t("tv.career.partDirecting", "Directed")
    "PRODUCTION" -> t("tv.career.partProduction", "Produced")
    "ACTING" -> t("tv.career.partActing", "Acted")
    "APPEARANCE" -> t("tv.career.partAppearance", "As themselves")
    "CREW" -> t("tv.career.partCrew", "Crew")
    else -> category
}

/**
 * The part that describes the person best — the one most of their work falls under — and never
 * "as themselves": a writer's many interviews are not what somebody browsing a writer came for.
 */
private fun dominantCategory(works: List<ProviderWork>): String? =
    CATEGORY_ORDER.filter { key -> key != "APPEARANCE" }
        .maxByOrNull { key -> works.count { work -> work.categories.contains(key) } }
        ?.takeIf { key -> works.any { work -> work.categories.contains(key) } }

/** What they did on this work, in the chosen part where there is one: "Novel", "Carl Stark". */
private fun partLabel(work: ProviderWork, category: String?): String {
    val parts = work.parts.filter { part -> category == null || part.category == category }.ifEmpty { work.parts }
    return parts.mapNotNull { part -> part.job ?: part.character }.distinct().take(2).joinToString(", ")
        .ifBlank { categoryLabel(work.categories.firstOrNull() ?: "") }
}

private fun stateLabel(item: ImportBatchItem): String = when (item.state) {
    "QUEUED" -> t("tv.career.stateQueued", "Waiting")
    "IMPORTING" -> t("tv.import.adding", "Adding…")
    "ADDED" -> t("tv.career.stateAdded", "Added")
    "ALREADY_HERE" -> t("tv.career.stateAlreadyHere", "Already here")
    "FAILED" -> t("tv.career.stateFailed", "Failed")
    else -> item.state
}

private fun stateColour(item: ImportBatchItem) = when (item.state) {
    "ADDED" -> Kitsu.ok
    "FAILED" -> Kitsu.danger
    "IMPORTING" -> Kitsu.accent
    else -> Kitsu.foregroundSubtle
}
