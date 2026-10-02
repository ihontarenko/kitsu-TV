package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu

/**
 * Something is being done and has not answered yet.
 *
 * <h2>⚠️ IT IS THE MARK NOW, AND IT USED TO BE A BORROWED SHAPE</h2>
 *
 * <p>This was a stroked arc turning in the accent — drawn here rather than borrowed only because the
 * TV Material library ships no progress indicator. An arc is what every application on the panel
 * shows while it waits, so the product said nothing about itself at the one moment a person is
 * looking straight at the screen with nothing else to read.
 *
 * <p>⚠️ THE NAME AND THE SIGNATURE ARE KEPT ON PURPOSE. Two dozen call sites — a poster mid-press, a
 * wall filling, the gate — ask for `BusySpinner` and every one of them still means the same thing.
 * Pointing the name at {@link BrandSpinner} changes what waiting LOOKS like across the whole client
 * in one edit; renaming the call sites as well would bury that in a diff about imports.
 */
@Composable
fun BusySpinner(modifier: Modifier = Modifier, size: Dp = 32.dp, colour: Color = Kitsu.accent) {
    BrandSpinner(modifier = modifier, size = size, colour = colour)
}
