package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.data.api.AccessQuestion
import org.jmouse.project.kitsu.tv.data.api.AccessAnswer
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import java.util.Locale
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.MetadataSource
import org.jmouse.project.kitsu.tv.data.api.ProviderResult
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.kitsuContainer
import org.jmouse.project.kitsu.tv.ui.components.KindMark
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/*
  ==============================================================================
  Taking a title from the provider into the library, from a remote.

  ⚠️ ONE IMPORT IN FLIGHT, AND THE LOCK IS HERE RATHER THAN ON A TILE. A remote
  repeats OK when it is held, and a person who sees nothing happen presses it
  again — two presses were two imports of the same film. The server's import is
  an upsert keyed on the provider's id, so the second would not duplicate the row;
  it would still be a second fetch of somebody else's rate-limited service and a
  second navigation stacked on the first. So: while one import runs, every press
  on every candidate is refused.
  ==============================================================================
 */

/** What adding to the library needs, as the server's policy spells it. */
private const val TITLE_WRITE = "title:write"

/** ⚠️ Fewer letters than this match half the provider's catalogue and spend a request on noise. */
const val PROVIDER_QUERY_MINIMUM = 3

/**
 * ⚠️ Longer than the library's pause: this spends somebody else's rate-limited quota, and a remote
 * types a letter every half second at best — one request per word, not per letter.
 */
const val PROVIDER_DEBOUNCE_MILLISECONDS = 600L

/** The language the provider is asked in — the television's own. */
fun providerLanguage(): String = Locale.getDefault().toLanguageTag()

/** Whether this profile may add titles, once the server has said — see [PermissionAnswer]. */
typealias AddingPermission = PermissionAnswer

/** Whether this profile may add titles — asked once, as a hint for drawing; see [rememberPermission]. */
@Composable
fun rememberAddingPermission(connection: ServerConnection): AddingPermission =
    rememberPermission(connection) { held -> TITLE_WRITE in held }

/** The same question, where only "may this profile add, as far as is known" matters. */
@Composable
fun rememberMayAdd(connection: ServerConnection): Boolean =
    rememberAddingPermission(connection) == AddingPermission.Allowed

/**
 * Why this profile may not add — in the server's words, and only the server's.
 *
 * ⚠️ NOTHING HERE IS WRITTEN BY THE TELEVISION. The heading, the policy author's sentence, the rule
 * and the condition all come from `/access/may`, so a household that rewrites a policy's
 * `reason "…"` sees the new sentence here without a new app. While the answer is on its way the card
 * is empty rather than guessing.
 */
@Composable
fun AddingRefused(connection: ServerConnection, modifier: Modifier = Modifier) {
    val refusal = rememberRefusal(connection, TITLE_WRITE) ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        refusal.title?.let { title ->
            Text(text = title, style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle))
        }
        (refusal.explanation ?: refusal.words)?.let { lead ->
            Text(text = lead, style = KitsuType.lead.copy(color = Kitsu.foreground))
        }
        val mechanism = refusal.words
            ?.let { words -> refusal.explanation?.let { explanation -> words.removePrefix(explanation).trim() } }
            ?.takeIf { rest -> rest.isNotBlank() }
        if (refusal.rule != null || mechanism != null) {
            Column(
                modifier = Modifier.background(Kitsu.backgroundSunken).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                refusal.rule?.let { rule ->
                    Text(text = rule, style = KitsuType.meta.copy(color = Kitsu.accentText, fontFamily = DataFamily))
                }
                mechanism?.let { rest ->
                    Text(text = rest, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle, fontFamily = DataFamily))
                }
            }
        }
    }
}

/** The server's refusal of [permission] for the caller, installation-wide — null while asking or when allowed. */
@Composable
fun rememberRefusal(connection: ServerConnection, permission: String): AccessAnswer? {
    var refusal by remember(connection, permission) { mutableStateOf<AccessAnswer?>(null) }
    LaunchedEffect(connection, permission) {
        refusal = runCatching { connection.api.may(listOf(AccessQuestion(permission))) }
            .getOrNull()
            ?.firstOrNull { answer -> !answer.allowed }
    }
    return refusal
}

/**
 * The one import in flight, if any, and the way to start one.
 *
 * @param onOpened where a candidate goes once it is in the library — or already was
 * @param onProblem what the server said when an import was refused
 */
class ProviderImport internal constructor(
    private val connection: ServerConnection,
    private val scope: CoroutineScope,
    private val onOpened: (TitleCard) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    /** The provider id of the candidate being imported, or null when nothing is. */
    var adding by mutableStateOf<String?>(null)
        private set

    /**
     * Opens what is already in the library, or imports the candidate and opens it.
     *
     * ⚠️ A press while another import runs does NOTHING — not a queue, not a second request. See
     * the note at the top of this file.
     */
    fun choose(candidate: ProviderResult, alreadyHere: TitleCard?) {
        if (alreadyHere != null) {
            onOpened(alreadyHere)
            return
        }
        if (adding != null) {
            return
        }
        adding = candidate.externalId
        scope.launch {
            // ⚠️ The candidate's own source travels back with its id — see ProviderResult.source.
            runCatching {
                connection.api.importTitle(candidate.kind, candidate.externalId, providerLanguage(), candidate.source)
            }
                .onSuccess { imported ->
                    val slug = imported.slug
                    if (slug != null) {
                        onOpened(TitleCard(id = imported.id, kind = imported.kind, slug = slug, displayName = imported.displayName))
                    }
                }
                .onFailure { failure -> onProblem(failure.describeForScreen()) }
            adding = null
        }
    }
}

@Composable
fun rememberProviderImport(
    connection: ServerConnection,
    onOpened: (TitleCard) -> Unit,
    onProblem: (String) -> Unit,
): ProviderImport {
    val scope = rememberCoroutineScope()
    return remember(connection) { ProviderImport(connection, scope, onOpened, onProblem) }
}

/**
 * The library's copy of a candidate, if the house already has it.
 *
 * ⚠️ THE SERVER SAYS SO, keyed on the provider's own id (`librarySlug`). Matching here on a name
 * and a year was the first version and it was a guess: a remake, a translated title or a re-release
 * would either miss or match the wrong film. The only identity that settles it is the provider's,
 * and only the server holds it.
 */
fun libraryCopyOf(candidate: ProviderResult): TitleCard? {
    val slug = candidate.librarySlug ?: return null
    return TitleCard(id = slug, kind = candidate.kind, slug = slug, displayName = candidate.title, year = candidate.year)
}

/** A provider candidate as a poster, with what pressing it will do written under it. */
@Composable
fun CandidateTile(
    connection: ServerConnection,
    candidate: ProviderResult,
    alreadyHere: TitleCard?,
    importing: ProviderImport,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        PosterTile(
            title = candidate.title,
            imageUrl = connection.resolve(candidate.posterUrl),
            onClick = { importing.choose(candidate, alreadyHere) },
            /* ⚠️ The busy state lives on the tile that was pressed, because that is where the eye is
               while it runs. */
            meta = when {
                alreadyHere != null -> t("tv.import.inLibrary", "In the library")
                importing.adding == candidate.externalId -> t("tv.import.adding", "Adding…")
                importing.adding != null -> t("tv.import.waitAdding", "Wait — adding another")
                else -> t("tv.import.add", "Add to the library")
            },
            year = candidate.year?.toString(),
            busy = importing.adding == candidate.externalId,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KindMark(kind = candidate.kind)
            candidate.source?.let { source ->
                /* ⚠️ Where it came from, on every tile: an IMDb candidate and a TMDB one for the same
                   film look identical otherwise, and which one is pressed decides what is fetched. */
                Text(text = sourceMark(source), style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle))
            }
        }
    }
}

/* ==============================================================================
   WHERE a search goes — chosen, not assumed.

   ⚠️ «пошукати тайтл через провайдера, вказуючи явно де шукаємо». The server can
   search more than one film database, and a film one of them lacks is often in
   the other; so the television names them and lets the person pick, rather than
   silently asking whichever the server ranks first.
   ============================================================================== */

/** The film databases on offer and which one is chosen; `choose` remembers it for the session. */
class ProviderSourceChoice internal constructor(
    val all: List<MetadataSource>,
    val chosen: MetadataSource?,
    private val remember: (String) -> Unit,
) {
    fun choose(source: MetadataSource) {
        if (source.available) {
            remember(source.source)
        }
    }
}

/**
 * The sources, and the one a search should use: the last one chosen this session while it is still
 * available, otherwise the first available in the household's order.
 *
 * ⚠️ Asked only when this profile may add — the route is behind `title:write`, and a list nobody can
 * use is a refusal drawn as a control.
 */
@Composable
fun rememberProviderSources(connection: ServerConnection, mayAdd: Boolean): ProviderSourceChoice {
    val container = LocalContext.current.kitsuContainer
    var sources by remember { mutableStateOf<List<MetadataSource>>(emptyList()) }
    val remembered by container.providerSource.collectAsState()

    LaunchedEffect(connection, mayAdd) {
        sources = if (mayAdd) {
            runCatching { connection.api.metadataSources() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    val chosen = sources.firstOrNull { source -> source.available && source.source == remembered }
        ?: sources.firstOrNull { source -> source.available }

    return ProviderSourceChoice(sources, chosen) { source -> container.providerSource.value = source }
}

/**
 * A row of chips, one per film database; the chosen one pressed.
 *
 * ⚠️ ONE THAT IS NOT SET UP IS SHOWN, DIMMED, WITH ITS REASON — not hidden. "Where is IMDb?" is the
 * question a missing chip raises and cannot answer; a dim chip and "no key on the Providers screen"
 * answers it on the spot. It is still focusable, so the reason can be reached, and pressing it
 * does nothing.
 */
@Composable
fun SourcePicker(choice: ProviderSourceChoice, modifier: Modifier = Modifier) {
    if (choice.all.isEmpty()) {
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = t("tv.import.searchIn", "Search in"), style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle))
            choice.all.forEach { source ->
                TvChip(
                    text = source.name,
                    pressed = source == choice.chosen,
                    onClick = { choice.choose(source) },
                    modifier = if (source.available) Modifier else Modifier.alpha(0.45f),
                )
            }
        }
        choice.all.filter { source -> !source.available }.forEach { source ->
            Text(
                text = "${source.name}: ${source.reason ?: t("tv.import.notSetUp", "not set up on this server")}",
                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            )
        }
    }
}

/** The short name of a source, for a tile — the full name is on the chip above. */
fun sourceMark(source: String): String = when (source.uppercase()) {
    "IMDB" -> "IMDb"
    "TMDB" -> "TMDB"
    else -> source
}
