package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.formatDecimal
import org.jmouse.project.kitsu.tv.text.Texts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.RestartAlt
import org.jmouse.project.kitsu.tv.data.ImageSize
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.CreditEntry
import org.jmouse.project.kitsu.tv.data.api.MediaAssetResponse
import org.jmouse.project.kitsu.tv.data.api.KitsuApi
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.data.api.TitleDetail
import org.jmouse.project.kitsu.tv.ui.components.PersonTile
import org.jmouse.project.kitsu.tv.ui.components.PosterTile
import org.jmouse.project.kitsu.tv.ui.components.Shelf
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvIconButton
import org.jmouse.project.kitsu.tv.ui.components.Notice
import org.jmouse.project.kitsu.tv.ui.components.NoticeTone
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.MediaShape

/*
  The parts a FILM and a SERIES page share.

  ⚠️ THEY WERE TWO PAGES DRAWN TWICE, and the second one was drawn poorer: the film had a poster, a
  director, a favourite button, a way to read about it, cast, "more like this" and a block of facts;
  the series had a shorter picture, one button and a list. A series is not a lesser work — it is a
  work with numbered parts — so everything a film page says about the WORK is said here once, and the
  series page adds only what is its own: seasons and episodes.
 */

/**
 * The top of a title page: the backdrop owns it, the poster stands at the right, and [content] — the
 * name, the facts, the buttons — sits on the left over a wash that keeps it readable.
 */
@Composable
fun TitleHero(
    connection: ServerConnection,
    detail: TitleDetail,
    modifier: Modifier = Modifier,
    height: Dp = 380.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        val backdrop = connection.imageUrl(detail.backdropPath, detail.backdropFileId, ImageSize.BACKDROP)
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Kitsu.background))
                .background(
                    Brush.horizontalGradient(
                        0f to Kitsu.background.copy(alpha = 0.9f),
                        0.45f to Kitsu.background.copy(alpha = 0.45f),
                        0.75f to Color.Transparent,
                    ),
                ),
        )

        val poster = connection.imageUrl(detail.posterPath, detail.posterFileId, ImageSize.FULL)
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Kitsu.gutter, bottom = 24.dp)
                    .width(152.dp)
                    .aspectRatio(2f / 3f)
                    .border(1.dp, Kitsu.borderStrong, MediaShape),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Kitsu.gutter, vertical = 24.dp)
                .widthIn(max = 660.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** The work's name, and its tagline in italic when it has one. */
@Composable
fun TitleName(detail: TitleDetail) {
    Text(text = detail.displayName, style = KitsuType.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (!detail.tagline.isNullOrBlank()) {
        Text(
            text = detail.tagline,
            style = KitsuType.lead.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The summary, clipped: somebody on this page has come to watch; reading is the About page. */
@Composable
fun TitleSummary(detail: TitleDetail) {
    if (!detail.summary.isNullOrBlank()) {
        Text(
            text = detail.summary,
            style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Keep / release as a favourite — the answer from the server is what the button then says. */
@Composable
fun KeepButton(connection: ServerConnection, slug: String, keptAtFirst: Boolean, modifier: Modifier = Modifier) {
    var kept by remember(slug) { mutableStateOf(keptAtFirst) }
    val scope = rememberCoroutineScope()
    /* ⚠️ AN ICON, NOT A LABELLED BUTTON. A title page carries Play, Favourite, Start over,
       Collections and About in one row, and with words on all of them the row ran off the screen.
       The heart says which state it is in — filled and in the accent when kept — and the words stay
       in the accessible name. */
    TvIconButton(
        onClick = {
            scope.launch {
                val answer = runCatching {
                    if (kept) connection.api.release(slug) else connection.api.keep(slug)
                }
                answer.onSuccess { kept = it.kept }
            }
        },
        icon = if (kept) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = if (kept) t("tv.title.kept", "Kept") else t("tv.title.favourite", "Favourite"),
        tint = if (kept) Kitsu.accent else null,
        modifier = modifier,
    )
}

/** Cast first, then crew, in the order the film database gives. */
@Composable
fun CastShelf(connection: ServerConnection, credits: List<CreditEntry>, onPerson: (String) -> Unit) {
    if (credits.isEmpty()) {
        return
    }
    Shelf(title = t("tv.title.cast", "Cast & crew")) {
        val people = credits.sortedBy { it.sortOrder }.take(24)
        items(people.size, key = { people[it].personId + people[it].sortOrder }) { index ->
            val credit = people[index]
            PersonTile(
                name = credit.personName,
                role = credit.characterName ?: credit.job ?: credit.department,
                imageUrl = connection.imageUrl(credit.portraitPath, credit.portraitFileId),
                onClick = { onPerson(credit.personId) },
            )
        }
    }
}

@Composable
fun SimilarShelf(connection: ServerConnection, similar: List<TitleCard>, onTitle: (TitleCard) -> Unit) {
    if (similar.isEmpty()) {
        return
    }
    Shelf(title = t("tv.title.similar", "More like this"), note = t("tv.title.similarNote", "Same genre, in this library")) {
        items(similar.size, key = { similar[it].id }) { index ->
            val card = similar[index]
            PosterTile(
                title = card.displayName,
                imageUrl = connection.imageUrl(card.posterPath, card.posterFileId),
                onClick = { onTitle(card) },
                meta = card.metaLine(),
                year = card.year?.toString(),
            )
        }
    }
}

/** Works of the same kind sharing this one's first genre. */
suspend fun KitsuApi.similarTo(detail: TitleDetail): List<TitleCard> {
    val genre = detail.nodes.firstOrNull { it.kind == "genre" } ?: return emptyList()
    return runCatching {
        titles(kind = detail.kind, nodeId = genre.id, size = 12).content.filter { it.slug != detail.slug }
    }.getOrDefault(emptyList())
}

/** A heading over a column of facts. */
@Composable
fun FactBlock(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = KitsuType.h2, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

/** One labelled fact; nothing at all when there is no value — an empty row is a row that lies. */
@Composable
fun FactLine(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            modifier = Modifier.width(132.dp),
        )
        Text(
            text = value,
            style = KitsuType.meta.copy(color = Kitsu.foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ── What a file is, in words ─────────────────────────────────────────────── */

/** `1080p · HEVC · HDR10` — the picture a file carries. */
fun MediaAssetResponse.pictureLine(): String? =
    listOfNotNull(resolution(width, height), videoCodec?.uppercase(), hdrFormat)
        .joinToString(" · ")
        .ifBlank { null }

/**
 * The container as the file's own extension.
 *
 * ⚠️ Not the server's `container`, which is the probe's format list — `matroska,webm`,
 * `mov,mp4,m4a,3gp,3g2,mj2` — and not a word for a screen.
 */
fun containerOf(fileName: String?): String? =
    fileName?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 }?.uppercase()

fun formatBitrate(bitsPerSecond: Long?): String? {
    if (bitsPerSecond == null || bitsPerSecond <= 0) {
        return null
    }
    return t("tv.unit.megabitsPerSecond", "{value} Mb/s", "value" to formatDecimal(bitsPerSecond / 1_000_000.0))
}

fun formatBytes(bytes: Long): String {
    val gigabytes = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gigabytes >= 1) {
        return t("tv.unit.gigabytes", "{value} GB", "value" to formatDecimal(gigabytes))
    }
    return t("tv.unit.megabytes", "{value} MB", "value" to formatDecimal(bytes / (1024.0 * 1024.0), digits = 0))
}

/** `23.976 fps` → `24 fps`, three significant figures, in the language's own separator. */
fun formatFrameRate(framesPerSecond: Double): String =
    t("tv.unit.framesPerSecond", "{value} fps", "value" to String.format(Texts.locale, "%.3g", framesPerSecond))

/**
 * "Start over" — forgets this profile's progress through a film, a series or a season (MH-283).
 *
 * <p>⚠️ A SERIES OR A SEASON ASKS TWICE. Dozens of positions go and cannot be put back, and a remote
 * has no hover to read a warning by; so the first press arms it ("Press again to start over") for a
 * few seconds and only the second does it. A film is one position and goes on the first press.
 *
 * <p>⚠️ THIS PROFILE ONLY — the server never touches another face's history.
 */
@Composable
fun StartOverButton(
    connection: ServerConnection,
    title: String,
    season: Int? = null,
    askTwice: Boolean,
    onForgotten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var armed by remember(title, season) { mutableStateOf(false) }
    var busy by remember(title, season) { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(START_OVER_ARMED_MILLIS)
            armed = false
        }
    }

    /* ⚠️ AN ICON, AND THE SECOND-PRESS PROMPT IS A NOTICE, NOT NEW WORDS ON THE BUTTON. The label
       used to change to "Press again to start over", the row grew, and the page jumped to keep the
       focused button in view — the top of the page vanished under the remote. The button keeps its
       size; while armed it turns the warning colour and the notice says what the next press does. */
    TvIconButton(
        onClick = {
            if (!busy) {
                if (askTwice && !armed) {
                    armed = true
                    Notices.show(
                        Notice(
                            title = t("tv.progress.confirm", "Press again to start over"),
                            lead = t("tv.progress.confirmLead", "This profile's progress here is forgotten. Everybody else keeps theirs."),
                            tone = NoticeTone.Information,
                        ),
                    )
                } else {
                    busy = true
                    scope.launch {
                        runCatching { connection.api.forgetProgress(title, season) }
                        busy = false
                        armed = false
                        onForgotten()
                    }
                }
            }
        },
        icon = Icons.Outlined.RestartAlt,
        contentDescription = if (armed) t("tv.progress.confirm", "Press again to start over") else t("tv.progress.startOver", "Start over"),
        tint = if (armed) Kitsu.danger else null,
        modifier = modifier,
    )
}

private const val START_OVER_ARMED_MILLIS = 4000L
