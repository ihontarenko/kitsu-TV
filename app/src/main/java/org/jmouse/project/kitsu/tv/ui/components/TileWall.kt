package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu

/**
 * A wall of tiles — the Films, Series, search and film-database pages are all one.
 *
 * ⚠️ ONE COMPOSABLE RATHER THAN SEVEN COPIES OF THE SAME GRID, because a wall is also a page and has
 * to scroll like one: its heading row and its tail are reached through [revealsPageEdges], and a copy
 * that forgot to ask for that was a page whose top could not be scrolled back to.
 *
 * <p>The six-point padding is the focus ring's room: a tile on the wall's edge draws its ring outside
 * itself, and without it the ring was clipped by the grid on the first row and column.
 *
 * @param minimumWidth the narrowest a track may be — a poster's width for posters, wider for controls
 * @param rowGap the space between rows
 */
@Composable
fun TileWall(
    minimumWidth: Dp,
    modifier: Modifier = Modifier,
    rowGap: Dp = 20.dp,
    content: LazyGridScope.() -> Unit,
) {
    val wallState = rememberLazyGridState()
    LazyVerticalGrid(
        state = wallState,
        columns = GridCells.Adaptive(minimumWidth),
        modifier = modifier.revealsPageEdges(wallState),
        horizontalArrangement = Arrangement.spacedBy(Kitsu.tileGap),
        verticalArrangement = Arrangement.spacedBy(rowGap),
        contentPadding = PaddingValues(6.dp),
        content = content,
    )
}
