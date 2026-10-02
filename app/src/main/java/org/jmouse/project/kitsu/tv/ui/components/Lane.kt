package org.jmouse.project.kitsu.tv.ui.components

import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch


/*
  How a page scrolls to what is in hand.

  On a television Compose's default bring-into-view pivots: whatever takes the
  focus is scrolled to a fixed fraction of the container, every time. That is
  right for a row of tiles — the tile in hand stays at the gutter — and wrong
  for a page: a button inside the hero drags the hero half off the top the
  moment it is focused, and the title of the film goes with it.

  So the page gets the lane rule from the design's `tv.js`: nothing moves while
  the thing in hand is already in view; when it is not, its row lands on the
  lane. The rows keep the platform's pivot.

  ⚠️ THE LANE HOLDS IN BOTH DIRECTIONS. Moving up, a row already in view but ABOVE the lane is pulled
  down onto it, so whatever heads it — a shelf's title, a section's eyebrow — comes back into view
  with it. Only "fully visible → stay" was written at first, and a page scrolled down and walked back
  up stopped with every heading above the cursor cut off; the list clamps at its start, so near the
  top this simply lands the page home.
 */

@OptIn(ExperimentalFoundationApi::class)
object LaneBringIntoViewSpec : BringIntoViewSpec {

    /** Where a row lands when it has to be scrolled to: 22% down the page, under the deck. */
    private const val LANE = 0.22f

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val lane = containerSize * LANE
        val fullyVisible = offset >= 0f && offset + size <= containerSize
        if (fullyVisible && offset >= lane) {
            return 0f
        }
        if (fullyVisible && size > containerSize - lane) {
            return 0f
        }
        return offset - lane
    }
}

/** The platform's own spec, kept for the rows after the page has replaced it. */
@OptIn(ExperimentalFoundationApi::class)
val LocalRowBringIntoViewSpec = compositionLocalOf<BringIntoViewSpec> { LaneBringIntoViewSpec }

/** Wraps a page: the lane rule vertically, the platform's pivot remembered for its rows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LaneScrolling(content: @Composable () -> Unit) {
    val platform = LocalBringIntoViewSpec.current
    CompositionLocalProvider(
        LocalRowBringIntoViewSpec provides platform,
        LocalBringIntoViewSpec provides LaneBringIntoViewSpec,
        content = content,
    )
}

/** Wraps a row: the platform's pivot again, so the tile in hand sits at the gutter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides LocalRowBringIntoViewSpec.current, content = content)
}

/*
  How a page's two ends are reached.

  The lane rule only ever moves the page to something that can hold the focus. Whatever sits above
  the first focusable row — a hero's picture, a page title, an eyebrow — or below the last one — the
  facts under a film — can therefore never be scrolled to: the cursor has nowhere to stand there.
  That was the "scrolls down but never back to the top" report, and every page had it; the server
  page was patched on its own once, which is how it was found to be everyone's.

  So a page refuses to let the focus leave it upwards while it is not at its start: the first press
  that would have left it scrolls it home instead, and the next one leaves for the deck. Downwards
  the same, a step at a time — a step, not "to the end", because a tall tail would carry the row in
  hand off the top with it.
 */

/** How far one refused press downwards moves the page: a fifth of it, so the row in hand stays in view. */
private const val TAIL_STEP = 0.2f

/**
 * ⚠️ How far refused presses downwards may carry the page IN ALL, until some other key is pressed.
 * Each press moved it another fifth, so holding Down on the last row walked the row in hand off the
 * top and left the page "scrolled somewhere down" with the cursor nowhere visible.
 */
private const val TAIL_LIMIT = 0.6f

/**
 * ⚠️ Moving up, a page this close to its start goes the rest of the way. The lane leaves a button
 * that is already in view where it is — right for the middle of a page, wrong at its head: walking
 * back up to a hero's button left the hero's eyebrow and title cut off above it, and only one more
 * press (the edge rule) brought them back. Half a screen is "close": anything hidden above that is
 * short enough to be the head of the page rather than content.
 */
private const val HOME_WITHIN = 0.5f

/** How long a move up is given to land before the page is taken home — so the two do not fight. */
private const val SETTLE_MILLISECONDS = 160L

/** The page's ends reachable with the remote — see above. For a page that is a lazy column. */
fun Modifier.revealsPageEdges(state: LazyListState): Modifier =
    revealsPageEdges(
        state,
        hiddenAbove = { _ -> if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE },
    ) { state.animateScrollToItem(0) }

/** The same, for a page that is a wall of tiles. */
fun Modifier.revealsPageEdges(state: LazyGridState): Modifier =
    revealsPageEdges(
        state,
        hiddenAbove = { _ -> if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE },
    ) { state.animateScrollToItem(0) }

/** The same, for a page that scrolls as one column. */
fun Modifier.revealsPageEdges(state: ScrollState): Modifier =
    revealsPageEdges(state, hiddenAbove = { _ -> state.value.toFloat() }) { state.animateScrollTo(0) }

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.revealsPageEdges(
    state: ScrollableState,
    hiddenAbove: (viewport: Float) -> Float,
    scrollToStart: suspend () -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var viewportHeight by remember { mutableIntStateOf(0) }
    var tailScrolled by remember { mutableFloatStateOf(0f) }
    onSizeChanged { size -> viewportHeight = size.height }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key != Key.DirectionDown) {
                tailScrolled = 0f
            }
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                /* After the move has landed: if what is hidden above is only the head of the page,
                   take the page home so the head is read with the row it belongs to. */
                scope.launch {
                    delay(SETTLE_MILLISECONDS)
                    val hidden = hiddenAbove(viewportHeight.toFloat())
                    if (state.canScrollBackward && hidden < viewportHeight * HOME_WITHIN) {
                        scrollToStart()
                    }
                }
            }
            false
        }
        .focusProperties {
            exit = { direction ->
                when {
                    direction == FocusDirection.Up && state.canScrollBackward -> {
                        scope.launch { scrollToStart() }
                        FocusRequester.Cancel
                    }
                    /* ⚠️ Refused, NOT scrolled, here — the key handler below scrolls. Scrolling in
                       both places moved the page twice for one press whenever there WAS somewhere
                       outside the page to go. */
                    direction == FocusDirection.Down && state.canScrollForward -> FocusRequester.Cancel
                    else -> FocusRequester.Default
                }
            }
        }
        .focusGroup()
        /*
          ⚠️ DOWN WITH NOWHERE TO GO SCROLLS THE PAGE — and the `exit` rule above could not do that on
          its own. It runs only when the focus is about to LEAVE the page for somewhere; below the last
          button of a page like "Server and build" there is nowhere at all, so the search found nothing,
          `exit` was never asked, and every press of Down did nothing while the text under the logo
          stayed off the screen.

          ⚠️ `onKeyEvent`, NOT the preview: this runs only if nothing inside the page consumed the key —
          a lane moving along its row, a reading pane paging its text — and before the default
          navigation, so the move is made here, once, and scrolling is what happens when it fails.
         */
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
                return@onKeyEvent false
            }
            if (focusManager.moveFocus(FocusDirection.Down)) {
                return@onKeyEvent true
            }
            if (state.canScrollForward) {
                val room = viewportHeight * TAIL_LIMIT - tailScrolled
                if (room > 1f) {
                    val step = minOf(viewportHeight * TAIL_STEP, room)
                    tailScrolled += step
                    scope.launch { state.animateScrollBy(step) }
                }
                return@onKeyEvent true
            }
            false
        }
}
