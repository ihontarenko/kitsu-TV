package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.ui.components.typesInto
import org.jmouse.project.kitsu.tv.ui.components.QueryField
import org.jmouse.project.kitsu.tv.ui.components.LetterPad
import org.jmouse.project.kitsu.tv.ui.components.asNotice
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.data.api.NewNode
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.NodeCard
import org.jmouse.project.kitsu.tv.data.api.NodeSummary
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvListRow
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import androidx.compose.ui.draw.clip
import org.jmouse.project.kitsu.tv.ui.theme.DialogShape
import org.jmouse.project.kitsu.tv.ui.components.TvIconButton
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/*
  ==============================================================================
  Putting one title into collections, from the remote.

  ⚠️ «щоб з телевізора я теж міг додати фільм в колекцію». The batch from a
  person's filmography could file into a collection, but a single title — found
  at the film database and just added, or already in the library — had no way
  onto a shelf from the television at all.

  ⚠️ EACH ROW IS A REQUEST, NOT A DRAFT. Filing is idempotent and instant on the
  server; a Save button over a list of ticks would be a second, invisible state
  somebody walks away from with BACK. The row says what the server answered.

  ⚠️ The profile's own collections first, then the household's. A franchise the
  film database keeps in step is left out — filing into it by hand is the
  sync's business.
  ==============================================================================
 */

/** The button on a title's page, and the list it opens. */
@Composable
fun CollectionsButton(
    connection: ServerConnection,
    titleSlug: String,
    titleName: String,
    filedAtFirst: List<NodeCard>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var filedIn by remember(titleSlug) {
        mutableStateOf(filedAtFirst.filter { node -> node.kind == KIND_COLLECTION }.map { node -> node.slug }.toSet())
    }

    /* ⚠️ An icon, like Favourite beside it — the title row had no room for words on every button. The
       count went from the label into the accessible name, and the glyph wears the accent when the
       title is filed anywhere. */
    TvIconButton(
        onClick = { open = true },
        icon = Icons.Outlined.CollectionsBookmark,
        contentDescription = if (filedIn.isEmpty()) {
            t("tv.collections.button", "Collections")
        } else {
            t("tv.collections.buttonFiled", "Collections · in {count}", "count" to filedIn.size)
        },
        tint = if (filedIn.isEmpty()) null else Kitsu.accent,
        modifier = modifier,
    )

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            CollectionFilingPanel(
                connection = connection,
                titleSlug = titleSlug,
                titleName = titleName,
                filedIn = filedIn,
                onFiledInChanged = { changed -> filedIn = changed },
                onDone = { open = false },
            )
        }
    }
}

@Composable
private fun CollectionFilingPanel(
    connection: ServerConnection,
    titleSlug: String,
    titleName: String,
    filedIn: Set<String>,
    onFiledInChanged: (Set<String>) -> Unit,
    onDone: () -> Unit,
) {
    var collections by rememberLoaded<List<NodeSummary>>()
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    /** Non-null while a new collection is being named — the panel shows the pad instead of the list. */
    var naming by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(connection, reload) {
        collections = runCatching { connection.api.nodes(KIND_COLLECTION) }
            .fold({ found ->
                Loaded.Ready(found.filter { node -> !node.synced }
                                  .sortedWith(compareByDescending<NodeSummary> { node -> node.personal }.thenBy { node -> node.displayName }))
            }, { failure -> Loaded.Failed(failure.describeForScreen()) })
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(collections) {
        runCatching { first.requestFocus() }
    }

    fun toggle(collection: NodeSummary) {
        if (busy != null) {
            return
        }
        val filing = collection.slug !in filedIn
        busy = collection.slug
        problem = null
        scope.launch {
            runCatching {
                if (filing) {
                    connection.api.fileInto(collection.slug, titleSlug)
                } else {
                    connection.api.takeOutOf(collection.slug, titleSlug)
                }
            }
                .onSuccess { onFiledInChanged(if (filing) filedIn + collection.slug else filedIn - collection.slug) }
                .onFailure { failure -> problem = failure.describeForScreen() }
            busy = null
        }
    }

    /*
      ⚠️ MADE AND FILED IN ONE PRESS. Somebody naming a collection from a title's page wants the title
      in it; a second step to tick it would be the one they forget. The server decides whether this
      profile may make one — a refusal arrives in the server's own words.
     */
    fun createAndFile(name: String) {
        val wanted = name.trim()
        if (wanted.isEmpty() || busy != null) {
            return
        }
        busy = NEW_COLLECTION
        scope.launch {
            runCatching {
                val made = connection.api.createNode(NewNode(kindSlug = KIND_COLLECTION, name = wanted))
                connection.api.fileInto(made.slug, titleSlug)
                made
            }
                .onSuccess { made ->
                    onFiledInChanged(filedIn + made.slug)
                    naming = null
                    reload++
                }
                .onFailure { failure -> Notices.show(failure.asNotice(t("tv.collections.notMade", "Could not make the collection"))) }
            busy = null
        }
    }

    if (naming != null) {
        NewCollectionPad(
            titleName = titleName,
            name = naming.orEmpty(),
            working = busy == NEW_COLLECTION,
            onNameChange = { typed -> naming = typed },
            onCreate = { createAndFile(naming.orEmpty()) },
            onCancel = { naming = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .width(620.dp)
            .clip(DialogShape)
            .background(Kitsu.backgroundOverlay)
            .border(1.dp, Kitsu.borderStrong, DialogShape)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = t("tv.collections.putInto", "Put «{title}» into…", "title" to titleName), style = KitsuType.h2)
        problem?.let { refusal ->
            Text(text = refusal, style = KitsuType.body.copy(color = Kitsu.danger))
        }
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).revealsPageEdges(listState), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val current = collections) {
                Loaded.Pending -> item { Text(text = t("tv.collections.reading", "Reading the collections…"), style = KitsuType.meta) }
                is Loaded.Failed -> item { Text(text = current.problem, style = KitsuType.body.copy(color = Kitsu.danger)) }
                is Loaded.Ready -> {
                    /* ⚠️ Always first, and always there: with no collection yet it is the only row,
                       and it is what a person reaching this dialog came to do. */
                    item(key = NEW_COLLECTION) {
                        TvListRow(
                            onClick = { naming = "" },
                            enabled = busy == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (current.value.isEmpty()) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = null, tint = Kitsu.accentText)
                            Text(
                                text = t("tv.collections.new", "New collection…"),
                                style = KitsuType.body,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                        }
                    }
                    items(current.value, key = { node -> node.id }) { collection ->
                        val filed = collection.slug in filedIn
                        TvListRow(
                            onClick = { toggle(collection) },
                            marked = filed,
                            enabled = busy == null || busy == collection.slug,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (collection == current.value.first()) Modifier.focusRequester(first) else Modifier),
                        ) {
                            if (filed) {
                                Icon(imageVector = Icons.Outlined.Check, contentDescription = t("tv.collections.filedHere", "Filed here"), tint = Kitsu.ok)
                            }
                            Text(
                                text = collection.displayName,
                                style = KitsuType.body,
                                modifier = Modifier.weight(1f).padding(start = if (filed) 8.dp else 0.dp),
                            )
                            Text(
                                text = when {
                                    busy == collection.slug -> "…"
                                    collection.personal -> t("tv.collections.yours", "yours")
                                    else -> t("tv.collections.household", "household")
                                },
                                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                            )
                        }
                    }
                }
            }
        }
        TvButton(onClick = onDone, text = t("tv.common.done", "Done"), weight = ButtonWeight.Quiet)
    }
}

/** The key of the "New collection…" row, and what `busy` holds while one is being made. */
private const val NEW_COLLECTION = "new-collection"

/** Naming a new collection with the remote — the shared pad, and one button that makes it and files the title. */
@Composable
private fun NewCollectionPad(
    titleName: String,
    name: String,
    working: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstKey.requestFocus() }
    }
    Column(
        modifier = Modifier
            .width(620.dp)
            .clip(DialogShape)
            .background(Kitsu.backgroundOverlay)
            .border(1.dp, Kitsu.borderStrong, DialogShape)
            .padding(28.dp)
            .typesInto(name, onSubmit = onCreate, onQueryChange = onNameChange),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = t("tv.collections.newFor", "A new collection for «{title}»", "title" to titleName), style = KitsuType.h2)
        QueryField(query = name, placeholder = t("tv.collections.namePlaceholder", "Name the collection"))
        LetterPad(query = name, onQueryChange = onNameChange, firstKey = firstKey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton(
                onClick = onCreate,
                text = if (working) t("tv.collections.making", "Making it…") else t("tv.collections.create", "Create and add"),
                weight = ButtonWeight.Primary,
                icon = Icons.Outlined.Add,
                enabled = !working && name.isNotBlank(),
            )
            TvButton(onClick = onCancel, text = t("tv.common.back", "Back"), weight = ButtonWeight.Quiet, enabled = !working)
        }
    }
}
