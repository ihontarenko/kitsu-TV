package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.formatDate
import org.jmouse.project.kitsu.tv.text.formatDateTime
import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.lazy.rememberLazyListState
import org.jmouse.project.kitsu.tv.data.api.filesOfWork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.CreditEntry
import org.jmouse.project.kitsu.tv.data.api.MediaAssetResponse
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
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.kindLabel
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


data class TitleContent(
    val detail: TitleDetail,
    val credits: List<CreditEntry>,
    val files: List<MediaAssetResponse>,
    val watched: Watched?,
    val similar: List<TitleCard>,
    val kept: Boolean,
)

/**
 * A film: the artwork owns the top, the name and its buttons sit on it; everything you might read
 * next is a row below — cast, more like this, and what is on the disk.
 */
@Composable
fun TitleScreen(
    connection: ServerConnection,
    deck: DeckState,
    slug: String,
    onPlay: (playKey: String) -> Unit,
    onTitle: (TitleCard) -> Unit,
    onPerson: (personId: String) -> Unit,
    onAbout: (slug: String) -> Unit,
) {
    var state by rememberLoaded<TitleContent>()
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(slug, attempt) {
        /* ⚠️ A REFRESH KEEPS THE PAGE. "Start over" and every other change on this page re-read it, and
           going back to Loading first unmounted the whole body — the page reopened at its top with
           focus on Play, which read as being thrown back to the title. Only a first load or a retry
           after a failure shows the waiting state; a refresh swaps the content under the cursor. */
        if (state !is Loaded.Ready) {
            state = Loaded.Pending
        }
        state = runCatching {
            coroutineScope {
                val api = connection.api
                val detail = api.title(slug)
                val credits = async { runCatching { api.credits(slug) }.getOrDefault(emptyList()) }
                val files = async { runCatching { api.filesOfWork(slug) }.getOrDefault(emptyList()) }
                val progress = async { runCatching { api.progress(all = true) }.getOrDefault(emptyList()) }
                val favourites = async { runCatching { api.favourites() }.getOrDefault(emptyList()) }
                val similar = async { api.similarTo(detail) }
                TitleContent(
                    detail = detail,
                    credits = credits.await(),
                    files = files.await(),
                    watched = progress.await().firstOrNull { it.playKey == slug },
                    similar = similar.await(),
                    kept = favourites.await().any { it.slug == slug },
                )
            }
        }.fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    Screen(deck = deck, current = Bay.Films) {
        when (val current = state) {
            Loaded.Pending -> Loading()
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> TitleBody(connection, current.value, onPlay, onTitle, onPerson, onAbout, onChanged = { attempt++ })
        }
    }
}

@Composable
private fun TitleBody(
    connection: ServerConnection,
    content: TitleContent,
    onPlay: (String) -> Unit,
    onTitle: (TitleCard) -> Unit,
    onPerson: (String) -> Unit,
    onAbout: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val detail = content.detail
    /* The copy the server would play — it picks the largest present file, so this names the same one. */
    val file = content.files.maxByOrNull { it.sizeBytes }
    val firstFocus = rememberInitialFocus(detail.slug)

    // A film is played by its slug (the server's PlaybackService: `Playing.alone(title, slug, …)`).
    val playKey = detail.slug
    val resumeAt = content.watched?.takeIf { !it.finished && it.positionSeconds > 0 }?.positionSeconds

    val pageState = rememberLazyListState()
    LazyColumn(
        state = pageState,
        modifier = Modifier.fillMaxSize().revealsPageEdges(pageState),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            TitleHero(connection, detail) {
                val director = content.credits.firstOrNull { it.job.equals("Director", ignoreCase = true) }?.personName
                Eyebrow(listOfNotNull(kindLabel(detail.kind), detail.year?.toString(), director).joinToString(" · "))
                TitleName(detail)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaLine(
                        formatRuntime(detail.runtimeMinutes) ?: file?.durationSeconds?.let { formatRuntime(it / 60) },
                        detail.rating?.let { formatRating(it) },
                        file?.pictureLine(),
                    )
                    if (file != null) {
                        TvBadge(text = t("tv.title.onDisk", "On disk"), tone = BadgeTone.Ok)
                    } else {
                        TvBadge(text = t("tv.title.noFile", "No file"), tone = BadgeTone.Muted)
                    }
                }
                TitleSummary(detail)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    /* ⚠️ NO PLAY BUTTON AT ALL WITHOUT A FILE, rather than a disabled one. A disabled
                       button can hold no focus, so the page opened with the cursor nowhere; and the
                       badge beside the facts already says why there is nothing to play. */
                    if (file != null) {
                        TvButton(
                            onClick = { onPlay(playKey) },
                            text = if (resumeAt != null) t("tv.title.resume", "Resume") else t("tv.title.play", "Play"),
                            hint = resumeAt?.let { formatSeconds(it) },
                            weight = ButtonWeight.Primary,
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                        KeepButton(connection, detail.slug, content.kept)
                        /* ⚠️ ALWAYS DRAWN, not only while there is progress: the button vanishing under the cursor the moment it
                           worked left focus nowhere, and the page jumped to find somewhere to put it. */
                        StartOverButton(connection, detail.slug, askTwice = false, onForgotten = onChanged)
                    } else {
                        KeepButton(connection, detail.slug, content.kept, modifier = Modifier.focusRequester(firstFocus))
                    }
                    /* ⚠️ The way to READ about a work without watching it. This page clips the
                       summary to three lines, because somebody in front of it has already decided to
                       watch something; the other visit needs a page of its own. */
                    /* Onto a shelf of the profile's own, or the household's — a title found at the film
                       database and just added arrives here, so this is where it gets filed. */
                    CollectionsButton(connection, detail.slug, detail.displayName, detail.nodes)
                    TvButton(onClick = { onAbout(detail.slug) }, text = t("tv.title.about", "About"), icon = Icons.Outlined.Info)
                }
            }
        }

        item {
            CastShelf(connection, content.credits, onPerson)
        }

        item {
            SimilarShelf(connection, content.similar, onTitle)
        }

        item {
            Facts(detail, file)
        }
    }
}

@Composable
private fun Facts(detail: TitleDetail, file: MediaAssetResponse?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Kitsu.gutter)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        FactBlock(t("tv.title.aboutFilm", "About the film"), modifier = Modifier.weight(1f)) {
            FactLine(t("tv.title.originalLanguage", "Original language"), detail.originalLanguage)
            FactLine(t("tv.title.released", "Released"), formatDate(detail.releasedOn))
            FactLine(t("tv.title.added", "Added"), formatDateTime(detail.addedAt))
            val genres = detail.nodes.filter { it.kind == "genre" }
            if (genres.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    genres.forEach { genre -> TvChip(text = genre.displayName, onClick = {}) }
                }
            }
        }
        FactBlock(t("tv.title.onTheDisk", "On the disk"), modifier = Modifier.weight(1f)) {
            if (file == null) {
                Text(
                    text = t("tv.title.noFileMatched", "No file has been matched to this title yet."),
                    style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                )
            } else {
                FactLine(t("tv.file.file", "File"), file.fileName)
                FactLine(t("tv.file.size", "Size"), formatBytes(file.sizeBytes) + (file.durationSeconds?.let { " · ${formatSeconds(it)}" } ?: ""))
                FactLine(t("tv.file.video", "Video"), listOfNotNull(file.pictureLine(), file.frameRate?.let { rate -> formatFrameRate(rate) }).joinToString(" · ").ifBlank { null })
                FactLine(t("tv.file.bitrate", "Bitrate"), formatBitrate(file.bitrate))
                FactLine(t("tv.file.container", "Container"), containerOf(file.fileName))
            }
        }
    }
}
