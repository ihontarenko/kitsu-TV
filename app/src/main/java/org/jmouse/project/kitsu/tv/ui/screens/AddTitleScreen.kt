package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.jmouse.project.kitsu.tv.ui.components.asNotice
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.components.NoticeTone
import org.jmouse.project.kitsu.tv.ui.components.Notice
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ProviderPerson
import org.jmouse.project.kitsu.tv.data.api.ProviderResult
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.LetterPad
import org.jmouse.project.kitsu.tv.ui.components.PersonTile
import org.jmouse.project.kitsu.tv.ui.components.QueryField
import org.jmouse.project.kitsu.tv.ui.components.TileWall
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.typesInto
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape


/**
 * Adding a title from the provider — the television's own "Add", reached from the Titles bay.
 *
 * <p>⚠️ THE PROVIDER ONLY, deliberately. Search already answers "is it in the library, and if not,
 * can I add it" in one place; this screen is for somebody who came to ADD, and a library shelf at
 * the top of it would push the answer they came for below the fold. A candidate the house already
 * holds is still recognised — it says "In the library" and opens the copy it has.
 *
 * <p>⚠️ The Titles bay stays lit here, because this is where Titles led.
 */
@Composable
fun AddTitleScreen(
    connection: ServerConnection,
    deck: DeckState,
    onTitle: (TitleCard) -> Unit,
    onPerson: (ProviderPerson) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var lookingFor by remember { mutableStateOf(LookingFor.Title) }
    var candidates by rememberLoaded<List<ProviderResult>>()
    var people by rememberLoaded<List<ProviderPerson>>()
    var problem by remember { mutableStateOf<String?>(null) }

    val permission = rememberAddingPermission(connection)
    val sources = rememberProviderSources(connection, permission == AddingPermission.Allowed)
    val source = sources.chosen

    val importing = rememberProviderImport(
        connection = connection,
        onOpened = onTitle,
        onProblem = { refusal -> problem = refusal },
    )

    // ⚠️ Keyed on the SOURCE as well: choosing another film database asks again for the same words.
    LaunchedEffect(query, source?.source, lookingFor, permission) {
        problem = null
        val asked = query.trim()
        if (asked.length < PROVIDER_QUERY_MINIMUM || permission == AddingPermission.Refused) {
            candidates = Loaded.Ready(emptyList())
            people = Loaded.Ready(emptyList())
            return@LaunchedEffect
        }
        delay(PROVIDER_DEBOUNCE_MILLISECONDS)
        if (lookingFor == LookingFor.Person) {
            people = Loaded.Pending
            people = runCatching { connection.api.searchPeople(asked, providerLanguage(), source?.source) }
                .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
        } else {
            candidates = Loaded.Pending
            candidates = runCatching { connection.api.searchProvider(asked, providerLanguage(), source?.source) }
                .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
        }
    }

    val firstKey = remember { FocusRequester() }
    /* ⚠️ Submitting hands focus to the results beside the pad: they already arrive as letters
       land, so "search" means "I am done typing, take me to what was found". */
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    /* ⚠️ WHEN THERE IS NOTHING TO GO TO, THE SERVER IS ASKED AND ITS ANSWER SHOWN. A Search that does
       nothing reads as a broken remote; the server knows why — a policy refusal with its rule, a film
       database with no key — and says it better than a guess made here. */
    val submit: () -> Unit = submit@{
        val asked = query.trim()
        if (asked.length < PROVIDER_QUERY_MINIMUM) {
            Notices.show(Notice(title = t("tv.add.heading", "Add a title"), lead = t("tv.add.threeLetters", "Type three letters or more."), tone = NoticeTone.Information))
            return@submit
        }
        /* ⚠️ ONLY WHEN THERE ARE RESULTS. With none, "right" found nothing on the page and carried
           the cursor up into the deck — the press looked like it did nothing at all. */
        val hasResults = if (lookingFor == LookingFor.Person) {
            (people as? Loaded.Ready)?.value?.isNotEmpty() == true
        } else {
            (candidates as? Loaded.Ready)?.value?.isNotEmpty() == true
        }
        if (hasResults && focusManager.moveFocus(FocusDirection.Right)) {
            return@submit
        }
        scope.launch {
            runCatching {
                if (lookingFor == LookingFor.Person) {
                    connection.api.searchPeople(asked, providerLanguage(), source?.source).size
                } else {
                    connection.api.searchProvider(asked, providerLanguage(), source?.source).size
                }
            }.onSuccess { found ->
                val databaseName = source?.name ?: t("tv.add.theDatabase", "The film database")
                Notices.show(
                    Notice(
                        title = t("tv.add.heading", "Add a title"),
                        lead = if (found == 0) {
                            t("tv.add.knowsNothingBy", "{database} knows nothing by “{query}”.", "database" to databaseName, "query" to asked)
                        } else {
                            t("tv.add.stillArriving", "Still arriving — try again in a moment.")
                        },
                        tone = NoticeTone.Information,
                    ),
                )
            }.onFailure { failure ->
                Notices.show(failure.asNotice(t("tv.add.searchFailed", "Could not search the film database")))
            }
        }
    }
    LaunchedEffect(Unit) {
        runCatching { firstKey.requestFocus() }
    }

    Screen(deck = deck, current = Bay.Titles) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Kitsu.gutter, vertical = 16.dp)
                .typesInto(query, onSubmit = submit) { query = it },
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(modifier = Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = t("tv.add.heading", "Add a title"), style = KitsuType.h1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvChip(text = t("tv.add.aTitle", "A title"), pressed = lookingFor == LookingFor.Title, onClick = { lookingFor = LookingFor.Title })
                    TvChip(text = t("tv.add.aPerson", "A person"), pressed = lookingFor == LookingFor.Person, onClick = { lookingFor = LookingFor.Person })
                }
                QueryField(
                    query = query,
                    placeholder = if (lookingFor == LookingFor.Person) {
                        t("tv.add.personPlaceholder", "Name of a writer, director, actor")
                    } else {
                        t("tv.add.titlePlaceholder", "Name of a film or series")
                    },
                )
                LetterPad(query = query, onQueryChange = { query = it }, firstKey = firstKey, onSubmit = submit)
                Text(
                    text = if (lookingFor == LookingFor.Person) {
                        t("tv.add.personHint", "Choosing a person lists everything they worked on there — and adds all of it, or some of it, at once.")
                    } else {
                        t("tv.add.titleHint", "Found at the film database. Choosing one adds it to the library and opens it — with or without a file for it.")
                    },
                    style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (permission == AddingPermission.Refused) {
                    AddingRefused(connection = connection, modifier = Modifier.fillMaxWidth().border(1.dp, Kitsu.border, SurfaceShape).padding(24.dp))
                    return@Column
                }
                /* ⚠️ At the head of the results, where it always was — shown before a letter is typed,
                   and absent only for a profile that may not add, which is told why instead. */
                SourcePicker(choice = sources)
                source?.let { asked ->
                    Text(text = t("tv.add.from", "From {source}", "source" to asked.name), style = KitsuType.h2)
                }
                problem?.let { refusal ->
                    Text(text = refusal, style = KitsuType.body.copy(color = Kitsu.danger))
                }
                if (lookingFor == LookingFor.Person) {
                    PeopleFound(connection = connection, people = people, query = query, onPerson = onPerson)
                } else when (val current = candidates) {
                    Loaded.Pending -> Text(
                        text = t("tv.add.asking", "Asking {source}…", "source" to (source?.name ?: t("tv.add.theDatabaseLower", "the film database"))),
                        style = KitsuType.meta,
                    )
                    is Loaded.Failed -> Text(text = current.problem, style = KitsuType.lead.copy(color = Kitsu.danger))
                    is Loaded.Ready -> when {
                        query.trim().length < PROVIDER_QUERY_MINIMUM -> Column(
                            modifier = Modifier.fillMaxWidth().border(1.dp, Kitsu.border, SurfaceShape).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = t("tv.add.typeName", "Type the name — three letters or more."), style = KitsuType.lead.copy(color = Kitsu.foreground))
                            Text(
                                text = t("tv.add.typeNameHint", "Every candidate the film database knows appears here; pressing one adds it."),
                                style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                            )
                        }
                        current.value.isEmpty() -> Text(
                            text = t(
                                "tv.add.knowsNothing",
                                "{database} knows nothing by that name. Another source may.",
                                "database" to (source?.name ?: t("tv.add.theDatabase", "The film database")),
                            ),
                            style = KitsuType.lead,
                        )
                        else -> TileWall(minimumWidth = Kitsu.posterWidth) {
                            items(current.value, key = { candidate -> "provider:${candidate.kind}:${candidate.externalId}" }) { candidate ->
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

/** What the add screen is searching the film database for. */
private enum class LookingFor { Title, Person }

/**
 * People the film database found by that name. ⚠️ With what they are known for under each, because
 * three people are called Stephen King and only one of them wrote the books.
 */
@Composable
private fun PeopleFound(
    connection: ServerConnection,
    people: Loaded<List<ProviderPerson>>,
    query: String,
    onPerson: (ProviderPerson) -> Unit,
) {
    when (people) {
        Loaded.Pending -> Text(text = t("tv.add.askingPeople", "Asking for people by that name…"), style = KitsuType.meta)
        is Loaded.Failed -> Text(text = people.problem, style = KitsuType.lead.copy(color = Kitsu.danger))
        is Loaded.Ready -> when {
            query.trim().length < PROVIDER_QUERY_MINIMUM -> Text(
                text = t("tv.add.typeName", "Type the name — three letters or more."),
                style = KitsuType.lead.copy(color = Kitsu.foreground),
            )
            people.value.isEmpty() -> Text(text = t("tv.add.nobody", "Nobody by that name there."), style = KitsuType.lead)
            else -> TileWall(minimumWidth = 120.dp) {
                items(people.value, key = { person -> "person:${person.source}:${person.externalId}" }) { person ->
                    PersonTile(
                        name = person.name,
                        role = listOfNotNull(person.knownFor, person.knownForWorks.firstOrNull()).joinToString(" · "),
                        imageUrl = connection.resolve(person.portraitUrl),
                        onClick = { onPerson(person) },
                    )
                }
            }
        }
    }
}
