package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/**
 * One place in the tree, as a control rather than as a tile.
 *
 * <h2>⚠️ A BUTTON, NOT A POSTER — because a genre has no face</h2>
 *
 * This was a 2:1 tile with a name in the corner, which is a poster that failed to load: four fifths
 * of it was empty ground, and a wall of them read as a library whose artwork had not arrived. A
 * genre is a CHOICE, and the thing being chosen by is the name and the count. So the control is the
 * size of what it says.
 *
 * <h2>⚠️ THE PICTURE IS NOT IN HERE, AND THAT WAS THE CORRECTION</h2>
 *
 * A place's artwork was drawn inside this button, behind the label. It made every genre a small
 * poster and the row a wall of them — which is the opposite of what a row of choices is for, and it
 * is what Ivan reported: the picture belongs to the SCREEN, as its ground, not to the control.
 *
 * So the button reports its focus upwards and draws nothing but the mark, the name and the count;
 * `Screen(backdrop = …)` is what paints the picture, behind the whole page.
 *
 * ⚠️ WHICH MEANS THE ARTWORK ONLY SHOWS FOR THE FOCUSED ONE. That is the trade, and it is the right
 * way round: the row stays a row of legible choices, and the picture answers "what am I on" at the
 * size a television is actually read at.
 */
@Composable
fun NodeButton(
    name: String,
    count: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val glyph = nodeIcon(icon)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) {
                    onFocused?.invoke()
                }
            },
        shape = ClickableSurfaceDefaults.shape(SurfaceShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Kitsu.backgroundElevated,
            focusedContainerColor = Kitsu.backgroundElevated,
            contentColor = Kitsu.foregroundMuted,
            focusedContentColor = Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Kitsu.border), shape = SurfaceShape),
            focusedBorder = focusRing(shape = SurfaceShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (glyph != null) {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = name,
                        style = KitsuType.lead.copy(color = Kitsu.foreground),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (count != null) {
                        Text(
                            text = count,
                            style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
