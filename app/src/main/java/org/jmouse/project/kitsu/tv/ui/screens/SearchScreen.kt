package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.components.NoticeTone
import org.jmouse.project.kitsu.tv.ui.components.Notice
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ProviderResult
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.KindMark
import org.jmouse.project.kitsu.tv.ui.components.LetterPad
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.QueryField
import org.jmouse.project.kitsu.tv.ui.components.TileWall
import org.jmouse.project.kitsu.tv.ui.components.typesInto
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape


/**
 * Search: the letter grid lives on the left where the thumb rests; results fill
 * in on the right as letters land. A remote with letters (or a keyboard) types
 * straight into the query too, so the grid is never in the way.
 */
@Composable
fun SearchScreen(
    connection: ServerConnection,
    deck: DeckState,
    onTitle: (TitleCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by rememberLoaded<List<TitleCard>>()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = Loaded.Ready(emptyList())
            return@LaunchedEffect
        }
        delay(250)
        results = runCatching { connection.api.titles(query = query, size = 48).content }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    /*
      ⚠️ THE SECOND HALF OF THE BROWSER'S "ADD", ON THE REMOTE. The library answers what this house
      already has; the provider answers what it could have. Offered only to a profile that may add
      (`title:write`) — asked once, as a hint for drawing; the import route refuses on its own.
     */
    val permission = rememberAddingPermission(connection)
    val mayAdd = permission == AddingPermission.Allowed
    val sources = rememberProviderSources(connection, mayAdd)
    val source = sources.chosen
    /** Whether the provider shelf belongs on screen at all — the picker must be reachable even when
        the chosen source found nothing, or there is no way to try the other one. */
    val asksProvider = mayAdd && query.trim().length >= PROVIDER_QUERY_MINIMUM
    /** A profile that may not add is TOLD so where the film database would be — see AddingRefused. */
    val explainsRefusal = permission == AddingPermission.Refused && query.trim().length >= PROVIDER_QUERY_MINIMUM

    var candidates by remember { mutableStateOf<List<ProviderResult>>(emptyList()) }
    var candidatesProblem by remember { mutableStateOf<String?>(null) }
    /** True while a provider search is out — so an empty shelf is not reported as "nothing found" early. */
    var askingProvider by remember { mutableStateOf(false) }
    val importing = rememberProviderImport(
        connection = connection,
        onOpened = onTitle,
        onProblem = { problem -> candidatesProblem = problem },
    )

    // ⚠️ Keyed on the SOURCE as well: choosing another film database asks again for the same words.
    LaunchedEffect(query, mayAdd, source?.source) {
        candidates = emptyList()
        candidatesProblem = null
        askingProvider = false
        if (!mayAdd || query.trim().length < PROVIDER_QUERY_MINIMUM) {
            return@LaunchedEffect
        }
        askingProvider = true
        delay(PROVIDER_DEBOUNCE_MILLISECONDS)
        runCatching { connection.api.searchProvider(query.trim(), providerLanguage(), source?.source) }
            .onSuccess { found -> candidates = found }
            .onFailure { failure -> candidatesProblem = failure.describeForScreen() }
        askingProvider = false
    }

    val firstKey = remember { FocusRequester() }
    /* ⚠️ Submitting hands focus to the results beside the pad: they already arrive as letters
       land, so "search" means "I am done typing, take me to what was found". */
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = {
        /* ⚠️ Only into results that exist — with none, "right" carried the cursor into the deck. */
        val hasResults = (results as? Loaded.Ready)?.value?.isNotEmpty() == true || candidates.isNotEmpty()
        if (!hasResults || !focusManager.moveFocus(FocusDirection.Right)) {
            Notices.show(
                Notice(
                    title = t("tv.search.heading", "Search"),
                    lead = t("tv.search.nothingMatchesQuery", "Nothing in the library matches “{query}”.", "query" to query.trim()),
                    tone = NoticeTone.Information,
                ),
            )
        }
    }
    LaunchedEffect(Unit) {
        runCatching { firstKey.requestFocus() }
    }

    Screen(deck = deck, current = Bay.Search) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Kitsu.gutter, vertical = 16.dp)
                .typesInto(query, onSubmit = submit) { query = it },
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(modifier = Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                QueryField(query = query, placeholder = t("tv.search.placeholder", "Type a few letters"))
                LetterPad(query = query, onQueryChange = { query = it }, firstKey = firstKey, onSubmit = submit)
                Text(
                    text = t("tv.search.hint", "Names, in either language. Results arrive as you type."),
                    style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = results) {
                    Loaded.Pending -> Text(text = t("tv.search.looking", "Looking…"), style = KitsuType.meta)
                    is Loaded.Failed -> Text(text = current.problem, style = KitsuType.lead.copy(color = Kitsu.danger))
                    is Loaded.Ready -> {
                        if (query.isBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().border(1.dp, Kitsu.border, SurfaceShape).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(text = t("tv.search.typeLetters", "Type a few letters."), style = KitsuType.lead.copy(color = Kitsu.foreground))
                                Text(text = t("tv.search.typeLettersHint", "Three are usually enough. The whole library is searched — films and series alike."), style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = t("tv.search.matchesFor", "Matches for"), style = KitsuType.h2)
                                Text(text = query, style = KitsuType.h2.copy(color = Kitsu.accentText))
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = current.value.size.toString(), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
                            }
                            if (current.value.isEmpty() && !asksProvider && !explainsRefusal) {
                                Text(text = t("tv.search.nothingMatches", "Nothing in the library matches."), style = KitsuType.lead)
                            } else {
                                /*
                                  ⚠️ ONE GRID, TWO SHELVES. Two grids stacked in a column would be two
                                  scroll containers, and DOWN from the last library row would stop at
                                  the edge of the first instead of travelling into the provider's.
                                 */
                                TileWall(minimumWidth = Kitsu.posterWidth) {
                                    if (current.value.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(text = t("tv.search.nothingMatches", "Nothing in the library matches."), style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
                                        }
                                    }
                                    items(current.value, key = { it.id }) { card ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            PosterTile(
                                                title = card.displayName,
                                                imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                                                onClick = { onTitle(card) },
                                                meta = card.metaLine(),
                                                year = card.year?.toString(),
                                            )
                                            KindMark(kind = card.kind)
                                        }
                                    }

                                    if (explainsRefusal) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            AddingRefused(connection = connection, modifier = Modifier.padding(top = 12.dp))
                                        }
                                    }
                                    if (asksProvider) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Column(
                                                modifier = Modifier.padding(top = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = source?.name
                                                        ?.let { name -> t("tv.add.from", "From {source}", "source" to name) }
                                                        ?: t("tv.search.fromProvider", "From the provider"),
                                                    style = KitsuType.h2,
                                                )
                                                SourcePicker(choice = sources)
                                                Text(
                                                    text = candidatesProblem
                                                        ?: if (askingProvider) {
                                                            t("tv.add.asking", "Asking {source}…", "source" to (source?.name ?: t("tv.search.theProvider", "the provider")))
                                                        } else if (candidates.isEmpty()) {
                                                            t("tv.search.nothingThere", "Nothing there by that name — another source may have it.")
                                                        } else {
                                                            t("tv.search.notInLibrary", "Not in the library yet. Choosing one adds it and opens it.")
                                                        },
                                                    style = KitsuType.body.copy(
                                                        color = if (candidatesProblem != null) Kitsu.danger else Kitsu.foregroundMuted,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    items(candidates, key = { candidate -> "provider:${candidate.kind}:${candidate.externalId}" }) { candidate ->
                                        CandidateTile(
                                            connection = connection,
                                            candidate = candidate,
                                            alreadyHere = libraryCopyOf(candidate),
                                            importing = importing,
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
