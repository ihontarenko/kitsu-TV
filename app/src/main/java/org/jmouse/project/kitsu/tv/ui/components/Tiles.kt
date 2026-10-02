package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.theme.MediaShape
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import androidx.compose.ui.draw.clip

/*
  Tiles. A poster (2:3), a still (16:9) or a text tile — one frame, one caption.
  When artwork is missing the frame draws itself from the name and never
  pretends to be a photograph.
 */

/** A picture, or the drawn stand-in when there is none. */
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    year: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.clip(MediaShape).background(Kitsu.backgroundElevated),
        )
    } else {
        DrawnPoster(title = title, year = year, modifier = modifier)
    }
}

/**
 * A poster drawn from the name when no artwork exists: two tones chosen from
 * the title so the same film always gets the same picture, a hairline inset,
 * and the name at the foot.
 */
@Composable
fun DrawnPoster(title: String, year: String?, modifier: Modifier = Modifier) {
    val seed = title.hashCode()
    val hue = ((seed % 360) + 360) % 360
    val top = Color.hsl(hue.toFloat(), 0.32f, 0.22f)
    val bottom = Color.hsl(hue.toFloat(), 0.36f, 0.09f)
    Box(
        modifier = modifier
            .clip(MediaShape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .border(1.dp, Kitsu.border, MediaShape)
            .padding(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().border(1.dp, Kitsu.foreground.copy(alpha = 0.18f)))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = KitsuType.caption.copy(fontSize = 12.sp, lineHeight = 14.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Kitsu.foreground,
            )
            if (year != null) {
                Text(text = year, style = KitsuType.meta.copy(fontSize = 11.sp))
            }
        }
    }
}


/**
 * A poster tile: the 2:3 frame wears the focus ring and lifts; the caption
 * under it goes from muted to full ink when the tile is in hand.
 */
@Composable
fun PosterTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    year: String? = null,
    progress: Float? = null,
    width: androidx.compose.ui.unit.Dp = Kitsu.posterWidth,
    /** Pressing it started something that has not answered yet — the poster dims and a spinner turns on it. */
    busy: Boolean = false,
) {
    Column(modifier = modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(MediaShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Kitsu.backgroundElevated,
                focusedContainerColor = Kitsu.backgroundElevated,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent), shape = MediaShape),
                focusedBorder = focusRing(shape = MediaShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Artwork(url = imageUrl, title = title, year = year, modifier = Modifier.fillMaxSize())
                if (progress != null && progress > 0f) {
                    ProgressRule(fraction = progress, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
                if (busy) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Kitsu.scrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        BusySpinner()
                    }
                }
            }
        }
        Column {
            Text(text = title, style = KitsuType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Kitsu.foregroundMuted)
            if (meta != null) {
                Text(text = meta, style = KitsuType.meta.copy(fontSize = 11.sp, color = Kitsu.foregroundSubtle), maxLines = 1)
            }
        }
    }
}

/** A still tile: 16:9, for an episode in progress or a collection; a badge in the corner, a bar at the foot. */
@Composable
fun StillTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    badge: String? = null,
    progress: Float? = null,
    width: androidx.compose.ui.unit.Dp = Kitsu.stillWidth,
) {
    Column(modifier = modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(MediaShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Kitsu.backgroundElevated,
                focusedContainerColor = Kitsu.backgroundElevated,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent), shape = MediaShape),
                focusedBorder = focusRing(shape = MediaShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Artwork(url = imageUrl, title = title, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Kitsu.scrim)),
                )
                if (badge != null) {
                    Box(modifier = Modifier.padding(6.dp).background(Kitsu.scrim, ChipShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = badge, style = KitsuType.meta.copy(fontSize = 11.sp, color = Kitsu.foreground))
                    }
                }
                if (progress != null && progress > 0f) {
                    ProgressRule(
                        fraction = progress,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
        Column {
            Text(text = title, style = KitsuType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Kitsu.foregroundMuted)
            if (meta != null) {
                Text(text = meta, style = KitsuType.meta.copy(fontSize = 11.sp, color = Kitsu.foregroundSubtle), maxLines = 1)
            }
        }
    }
}

/** A text tile for a genre: a name on a tinted ground, 2:1. */
@Composable
fun TextTile(
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Kitsu.kindGenre,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(160.dp).aspectRatio(2f),
        shape = ClickableSurfaceDefaults.shape(SurfaceShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Kitsu.backgroundElevated,
            focusedContainerColor = Kitsu.backgroundElevated,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Kitsu.border), shape = SurfaceShape),
            focusedBorder = focusRing(shape = SurfaceShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f).compositeOver(Kitsu.backgroundElevated), Kitsu.backgroundElevated)))
                .padding(10.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(text = title, style = KitsuType.lead.copy(color = Kitsu.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) {
                    Text(text = meta, style = KitsuType.meta.copy(fontSize = 11.sp, color = Kitsu.foregroundSubtle))
                }
            }
        }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    return Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )
}

/** A square portrait for a person, initials when there is no picture. */
@Composable
fun PersonTile(name: String, role: String?, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(96.dp),
            shape = ClickableSurfaceDefaults.shape(MediaShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Kitsu.backgroundElevated,
                focusedContainerColor = Kitsu.backgroundElevated,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Kitsu.border), shape = MediaShape),
                focusedBorder = focusRing(shape = MediaShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(text = initialsOf(name), style = KitsuType.h2.copy(color = Kitsu.foregroundSubtle))
                }
            }
        }
        Text(text = name, style = KitsuType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Kitsu.foregroundMuted)
        if (role != null) {
            Text(text = role, style = KitsuType.meta.copy(fontSize = 11.sp, color = Kitsu.foregroundSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun initialsOf(name: String): String {
    return name.split(" ", "-").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

/**
 * A shelf: a heading, then a row that scrolls sideways under the cursor. The
 * row's padding is the gutter, so the first tile lines up with the heading and
 * the last one can still be brought into view.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Shelf(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    count: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = Kitsu.gutter),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = KitsuType.h2)
            if (note != null) {
                Text(text = note, style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle), modifier = Modifier.padding(bottom = 2.dp))
            }
            if (count != null) {
                Box(modifier = Modifier.weight(1f))
                Text(text = count, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle), modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        /* focusRestorer: the row remembers which tile was last in hand, so coming
           back to it from the row above lands where you left — what every
           television does, and what a geometric focus search alone would not. */
        RowScrolling {
            LazyRow(
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = Kitsu.gutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(Kitsu.tileGap),
                content = content,
            )
        }
    }
}
