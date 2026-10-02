package org.jmouse.project.kitsu.tv.ui.components

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/*
  ==============================================================================
  Typing on a remote.

  ⚠️ ONE PAD FOR EVERY SCREEN THAT ASKS FOR WORDS. The library search had it
  first; adding a title from the provider needs exactly the same thing. Two
  copies of a keyboard drift the first time one gains a letter — the Cyrillic
  row already has, twice — and a person who learnt one pad would find the other
  laid out differently.
  ==============================================================================
 */

/** The alphabets a person switches between. The rows are the pad, top to bottom. */
enum class Keyboard(val label: String, val rows: List<String>) {
    Latin("ABC", listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz-'")),
    Cyrillic("АБВ", listOf("абвгґдеє", "жзиіїйкл", "мнопрсту", "фхцчшщью", "яʼ")),
    Digits("123", listOf("1234567", "890.:-")),

    /** A server's address — an IP, a domain, a port, a scheme. Nothing a word needs, nothing an address lacks. */
    Address("URL", listOf("1234567890", "abcdefghij", "klmnopqrst", "uvwxyz.:-/")),
    ;

    companion object {
        /** What a screen asking for words offers. */
        val WORDS = listOf(Latin, Cyrillic, Digits)
    }
}

/**
 * The query as it is being typed, on a 2dp rule, with a caret.
 *
 * @param placeholder what the field says while it is empty
 */
@Composable
fun QueryField(query: String, placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .drawUnderline()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = query.ifEmpty { placeholder },
            style = KitsuType.h2.copy(color = if (query.isEmpty()) Kitsu.foregroundSubtle else Kitsu.foreground),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.width(2.dp).height(20.dp).background(Kitsu.accentText))
    }
}

/**
 * The letter grid, its alphabet switch, and space / delete / clear.
 *
 * @param firstKey takes focus on arrival, so the D-pad does something on the first press
 */
@Composable
fun LetterPad(
    query: String,
    onQueryChange: (String) -> Unit,
    firstKey: FocusRequester,
    onSubmit: (() -> Unit)? = null,
    keyboards: List<Keyboard> = Keyboard.WORDS,
) {
    var keyboard by remember { mutableStateOf(keyboards.first()) }
    /* An address has no spaces, and a space typed into one is a fault the server would name badly. */
    val typesWords = keyboard != Keyboard.Address

    /* ⚠️ IntrinsicSize.Max: the pad is as wide as its widest row of letters, and the space row and
       Search fill exactly that — they used to run to the column's edge, past the grid. */
    Column(modifier = Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (keyboards.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                keyboards.forEach { option ->
                    TvChip(text = option.label, pressed = option == keyboard, onClick = { keyboard = option })
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keyboard.rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEachIndexed { index, character ->
                        TvKey(
                            label = character.toString(),
                            onClick = { onQueryChange(query + character) },
                            modifier = if (rowIndex == 0 && index == 0) Modifier.focusRequester(firstKey) else Modifier,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (typesWords) {
                    TvButton(onClick = { onQueryChange("$query ") }, text = t("tv.keyboard.space", "space"), modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                TvIconButton(
                    onClick = { onQueryChange(query.dropLast(1)) },
                    icon = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = t("tv.keyboard.deleteLetter", "Delete last letter"),
                )
                TvButton(onClick = { onQueryChange("") }, text = t("tv.keyboard.clear", "clear"), weight = ButtonWeight.Quiet)
            }
            /* ⚠️ THE WAY OUT OF THE PAD. Results arrive as letters land, but a person who has
               typed expects to say "done" — and without this the only way to the results is
               guessing that RIGHT leaves the grid. */
            if (onSubmit != null) {
                TvButton(
                    onClick = onSubmit,
                    text = t("tv.keyboard.search", "Search"),
                    weight = ButtonWeight.Primary,
                    icon = Icons.Outlined.Search,
                    enabled = query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Lets a remote with letters — or a keyboard — type straight into the query, so the pad is never
 * in the way of somebody who has one.
 */
fun Modifier.typesInto(
    query: String,
    onSubmit: (() -> Unit)? = null,
    onQueryChange: (String) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
        return@onPreviewKeyEvent false
    }
    when (event.key) {
        /* ⚠️ A keyboard's Enter and Search submit — never the remote's centre button
           (DirectionCenter), which is how every letter on the pad is pressed. */
        Key.Enter, Key.NumPadEnter, Key.Search -> {
            if (onSubmit != null && query.isNotBlank()) {
                onSubmit()
                true
            } else {
                false
            }
        }
        Key.Backspace, Key.Delete -> {
            if (query.isNotEmpty()) {
                onQueryChange(query.dropLast(1))
                true
            } else {
                false
            }
        }
        else -> {
            val character = event.utf16CodePoint.toChar()
            if (character.isLetterOrDigit() || character == ' ') {
                onQueryChange(query + character)
                true
            } else {
                false
            }
        }
    }
}

/** The query's ground: a 2dp rule under it, on the page's own field. */
private fun Modifier.drawUnderline(): Modifier = drawBehind {
    drawRect(
        color = Kitsu.borderStrong,
        topLeft = Offset(0f, size.height - 2.dp.toPx()),
        size = Size(size.width, 2.dp.toPx()),
    )
}
