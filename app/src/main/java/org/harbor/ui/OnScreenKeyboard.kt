// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * An on-screen keyboard a d-pad can drive.
 *
 * The system IME is a touch keyboard: nothing in it responds to a d-pad, so
 * relying on it broke the one promise the rest of the launcher keeps - that
 * everything works from a controller on the sofa with the device docked to a
 * TV. Every key here is a row/column position a cursor can reach, the same
 * explicit-index model every other list in this app already uses.
 */
sealed interface Key {
    data class Letter(val lower: Char) : Key
    data object Shift : Key
    data object Backspace : Key
    data object Space : Key
    data object Symbols : Key
    data object Done : Key
    /** Whole-buffer clipboard ops - there is no cursor/selection model here,
     *  so Copy takes the entire field and Paste appends to its end, same as
     *  every other key. Handled outside [applyKey] - both need a Context. */
    data object Copy : Key
    data object Paste : Key
}

/** Rows are independent lengths on purpose - a keyboard is not a uniform grid. */
private val LETTER_ROWS: List<List<Key>> = listOf(
    "qwertyuiop".map { Key.Letter(it) },
    "asdfghjkl".map { Key.Letter(it) },
    listOf(Key.Shift) + "zxcvbnm".map { Key.Letter(it) } + listOf(Key.Copy, Key.Backspace),
    listOf(Key.Symbols, Key.Paste, Key.Space, Key.Done),
)

private val SYMBOL_ROWS: List<List<Key>> = listOf(
    "1234567890".map { Key.Letter(it) },
    "-:;'\"(),.!".map { Key.Letter(it) },
    listOf(Key.Shift) + "&_/@#\$%*+?".map { Key.Letter(it) } + listOf(Key.Copy, Key.Backspace),
    listOf(Key.Symbols, Key.Paste, Key.Space, Key.Done),
)

fun keyboardRows(symbols: Boolean): List<List<Key>> = if (symbols) SYMBOL_ROWS else LETTER_ROWS

/**
 * What pressing a key does to the buffer, at the cursor rather than always
 * the end - a real text field lets you fix a typo in the middle without
 * retyping the tail. Pure, so it is trivial to test. Copy/Paste touch the
 * clipboard and are handled by the caller instead.
 */
fun applyKey(key: Key, text: String, cursor: Int, shift: Boolean): Pair<String, Int> = when (key) {
    is Key.Letter -> {
        val ch = if (shift) key.lower.uppercaseChar() else key.lower
        (text.substring(0, cursor) + ch + text.substring(cursor)) to (cursor + 1)
    }
    Key.Space -> (text.substring(0, cursor) + " " + text.substring(cursor)) to (cursor + 1)
    Key.Backspace -> if (cursor > 0) {
        (text.substring(0, cursor - 1) + text.substring(cursor)) to (cursor - 1)
    } else {
        text to cursor
    }
    Key.Shift, Key.Symbols, Key.Done, Key.Copy, Key.Paste -> text to cursor
}

/** Inserts at the cursor rather than always appending - used by Paste. */
fun insertAt(text: String, cursor: Int, insert: String): Pair<String, Int> =
    (text.substring(0, cursor) + insert + text.substring(cursor)) to (cursor + insert.length)

@Composable
fun KeyboardPanel(
    symbols: Boolean,
    shift: Boolean,
    cursorRow: Int,
    cursorCol: Int,
    onTap: (row: Int, col: Int) -> Unit,
) {
    val rows = keyboardRows(symbols)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        rows.forEachIndexed { r, row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEachIndexed { c, key ->
                    KeyCap(
                        key = key,
                        shift = shift,
                        symbols = symbols,
                        focused = r == cursorRow && c == cursorCol,
                        weight = weightOf(key),
                        onClick = { onTap(r, c) },
                    )
                }
            }
        }
    }
}

private fun weightOf(key: Key): Float = when (key) {
    Key.Space -> 4f
    Key.Done, Key.Symbols, Key.Paste -> 2f
    Key.Shift, Key.Backspace, Key.Copy -> 1.5f
    else -> 1f
}

private fun labelOf(key: Key, shift: Boolean, symbols: Boolean): String = when (key) {
    is Key.Letter -> (if (shift) key.lower.uppercaseChar() else key.lower).toString()
    Key.Shift -> "⇧"
    Key.Backspace -> "⌫"
    Key.Space -> "space"
    Key.Symbols -> if (symbols) "ABC" else "123"
    Key.Done -> "Done"
    Key.Copy -> "Copy"
    Key.Paste -> "Paste"
}

/** A RowScope extension so `Modifier.weight` - which only exists on that
 *  receiver - can size each key relative to its neighbours in the same row. */
@Composable
private fun RowScope.KeyCap(
    key: Key,
    shift: Boolean,
    symbols: Boolean,
    focused: Boolean,
    weight: Float,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    val active = key == Key.Shift && shift
    Box(
        Modifier
            .weight(weight)
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                // Baked with lerp rather than alpha, so these stay fully
                // solid keys even though the panel behind them is now
                // slightly see-through - only the panel is meant to be.
                when {
                    focused -> lerp(theme.surfaceVariant, theme.primary, 0.85f)
                    active -> lerp(theme.surfaceVariant, theme.primary, 0.35f)
                    key == Key.Done -> lerp(theme.surfaceVariant, theme.primary, 0.18f)
                    else -> theme.surfaceVariant
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, theme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            labelOf(key, shift, symbols),
            color = if (focused) theme.background else theme.textPrimary,
            fontSize = if (key is Key.Letter) 17.sp else 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
