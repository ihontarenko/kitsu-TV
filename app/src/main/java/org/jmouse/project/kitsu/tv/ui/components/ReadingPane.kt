package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu


/** How far one press moves the text: most of the pane, with a few lines kept for the eye to follow. */
private const val READING_STEP = 0.8f

/**
 * A block of text longer than the screen, read with the remote — a rule's source, one long answer.
 *
 * <h2>⚠️ THE PANE HOLDS THE FOCUS, NOT THE TEXT</h2>
 *
 * Text holds no focus, so a scrolling column of it could not be scrolled with a D-pad at all: the
 * focus had nowhere to stand inside it. The pane is one stop; while it is in hand, UP and DOWN page
 * through the text, and only at either end do they move the focus on — the same "the edge first,
 * then away" as a page (see [revealsPageEdges]).
 */
@Composable
fun ReadingPane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val textScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .onSizeChanged { size -> viewportHeight = size.height }
            .onFocusChanged { state -> focused = state.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    event.key == Key.DirectionDown && textScroll.canScrollForward -> {
                        scope.launch { textScroll.animateScrollBy(viewportHeight * READING_STEP) }
                        true
                    }
                    event.key == Key.DirectionUp && textScroll.canScrollBackward -> {
                        scope.launch { textScroll.animateScrollBy(-viewportHeight * READING_STEP) }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .background(Kitsu.backgroundSunken)
            .border(if (focused) Kitsu.focusRingWidth else 1.dp, if (focused) Kitsu.accent else Kitsu.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(textScroll)
                .padding(16.dp),
            content = content,
        )
    }
}
