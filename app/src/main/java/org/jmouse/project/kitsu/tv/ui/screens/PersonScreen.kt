package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.formatDate
import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jmouse.project.kitsu.tv.data.ImageSize
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.Collaborator
import org.jmouse.project.kitsu.tv.data.api.PersonCredit
import org.jmouse.project.kitsu.tv.data.api.PersonDetail
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.MetaLine
import org.jmouse.project.kitsu.tv.ui.components.PersonTile
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.Shelf
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.initialsOf
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.MediaShape


data class PersonContent(
    val detail: PersonDetail,
    val filmography: List<PersonCredit>,
    val collaborators: List<Collaborator>,
)

/**
 * A person: portrait and the few facts a card carries, then everything of
 * theirs in this library as a shelf, and the people they keep working with.
 * The point of the page is the filmography — a cast list that leads nowhere is
 * a cast list nobody can use.
 */
@Composable
fun PersonScreen(
    connection: ServerConnection,
    deck: DeckState,
    personId: String,
    onTitle: (TitleCard) -> Unit,
    onPerson: (personId: String) -> Unit,
    onCareer: (source: String, externalId: String, name: String) -> Unit,
) {
    var state by rememberLoaded<PersonContent>()
    val mayAdd = rememberMayAdd(connection)
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(personId, attempt) {
        state = Loaded.Pending
        state = runCatching {
            coroutineScope {
                val detail = async { connection.api.person(personId) }
                val filmography = async { runCatching { connection.api.filmography(personId) }.getOrDefault(emptyList()) }
                val collaborators = async { runCatching { connection.api.collaborators(personId) }.getOrDefault(emptyList()) }
                PersonContent(detail.await(), filmography.await(), collaborators.await())
            }
        }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    Screen(deck = deck, current = null) {
        when (val current = state) {
            Loaded.Pending -> Loading()
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> PersonBody(connection, current.value, mayAdd, onTitle, onPerson, onCareer)
        }
    }
}

@Composable
private fun PersonBody(
    connection: ServerConnection,
    content: PersonContent,
    mayAdd: Boolean,
    onTitle: (TitleCard) -> Unit,
    onPerson: (String) -> Unit,
    onCareer: (source: String, externalId: String, name: String) -> Unit,
) {
    val person = content.detail
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(person.id) {
        runCatching { firstFocus.requestFocus() }
    }

    /* A person is credited once per title on this shelf, whatever they did on it. */
    val titles = content.filmography
        .groupBy { it.titleSlug }
        .map { (_, credits) -> credits.first() to credits.mapNotNull { it.characterName ?: it.job }.distinct() }
        .sortedByDescending { (credit, _) -> credit.year ?: 0 }

    val pageState = rememberLazyListState()
    LazyColumn(state = pageState, modifier = Modifier.fillMaxSize().revealsPageEdges(pageState), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Kitsu.gutter).padding(top = 20.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val portrait = connection.imageUrl(person.portraitPath, person.portraitFileId, ImageSize.FULL)
                Box(
                    modifier = Modifier.size(128.dp).border(1.dp, Kitsu.borderStrong, MediaShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (portrait != null) {
                        AsyncImage(model = portrait, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(text = initialsOf(person.displayName), style = KitsuType.hero.copy(color = Kitsu.foregroundSubtle))
                    }
                }
                Column(modifier = Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(listOfNotNull(t("tv.kind.person", "Person"), person.knownFor).joinToString(" · "))
                    Text(text = person.displayName, style = KitsuType.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MetaLine(
                        person.bornOn?.let { born ->
                            val died = person.diedOn
                            if (died == null) {
                                t("tv.person.born", "born {born}", "born" to formatDate(born))
                            } else {
                                t("tv.person.bornDied", "born {born} – {died}", "born" to formatDate(born), "died" to formatDate(died))
                            }
                        },
                        person.bornIn,
                        t("tv.person.inLibraryCount", "{titles} in this library", "titles" to Counted.titles(titles.size)),
                    )
                    if (!person.summary.isNullOrBlank()) {
                        Text(text = person.summary, style = KitsuType.body.copy(color = Kitsu.foregroundMuted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    val careerSource = person.externalSource
                    val careerId = person.externalId
                    /* ⚠️ Only for a profile that may add: the career is offered so that its missing works
                       can be taken in, and a list nobody here can act on is a refusal drawn as a door. */
                    if (mayAdd && careerSource != null && careerId != null) {
                        TvButton(
                            onClick = { onCareer(careerSource, careerId, person.displayName) },
                            text = t("tv.person.everythingBy", "Everything by {name} at {source}", "name" to person.displayName, "source" to sourceMark(careerSource)),
                        )
                    }
                    if (person.aliases.isNotEmpty()) {
                        Text(text = t("tv.person.aliases", "Also credited as {names}", "names" to person.aliases.joinToString(", ")), style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            if (titles.isEmpty()) {
                Text(
                    text = t("tv.person.nothingHere", "Nothing of theirs is in this library yet."),
                    style = KitsuType.lead,
                    modifier = Modifier.padding(horizontal = Kitsu.gutter),
                )
            } else {
                Shelf(title = t("tv.person.inLibrary", "In this library"), note = t("tv.person.newestFirst", "Newest first"), count = Counted.titles(titles.size)) {
                    items(titles.size, key = { titles[it].first.titleSlug }) { index ->
                        val (credit, roles) = titles[index]
                        val card = TitleCard(
                            id = credit.titleId,
                            kind = credit.titleKind,
                            slug = credit.titleSlug,
                            displayName = credit.titleName,
                            year = credit.year,
                            posterPath = credit.posterPath,
                            posterFileId = credit.posterFileId,
                        )
                        PosterTile(
                            title = credit.titleName,
                            imageUrl = connection.imageUrl(credit.posterPath, credit.posterFileId),
                            onClick = { onTitle(card) },
                            meta = listOfNotNull(credit.year?.toString(), roles.firstOrNull()).joinToString("  ·  "),
                            year = credit.year?.toString(),
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
        }

        if (content.collaborators.isNotEmpty()) {
            item {
                Shelf(title = t("tv.person.worksWith", "Works with"), note = t("tv.person.worksWithNote", "People on the same titles, most often first")) {
                    items(content.collaborators.size, key = { content.collaborators[it].personId }) { index ->
                        val collaborator = content.collaborators[index]
                        PersonTile(
                            name = collaborator.personName,
                            role = t("tv.person.together", "{titles} together", "titles" to Counted.titles(collaborator.together.toInt())),
                            imageUrl = connection.imageUrl(collaborator.portraitPath, collaborator.portraitFileId),
                            onClick = { onPerson(collaborator.personId) },
                        )
                    }
                }
            }
        }
    }
}
