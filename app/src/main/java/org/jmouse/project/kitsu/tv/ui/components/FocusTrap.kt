package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps the D-pad inside an overlay drawn in the page's own window.
 *
 * ⚠️ AN OVERLAY IN THE SAME COMPOSITION IS NOT A WALL. Focus search is geometric across the whole
 * tree, so LEFT from the edge of a PIN pad found the profile faces behind the scrim and the cursor
 * left the dialog it was typing into. A focus group whose exit is cancelled stops at its own edge in
 * every direction; BACK is still the overlay's own to handle.
 *
 * An overlay drawn with `androidx.compose.ui.window.Dialog` needs none of this — a window of its own
 * is already a boundary.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trapsFocus(): Modifier = this
    .focusProperties {
        exit = { FocusRequester.Cancel }
    }
    .focusGroup()
