// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * A console-style menu: a stack of screens that slide.
 *
 * A drills in and the screen slides left; B goes back and it slides right. That
 * spatial cue is what makes a pad-driven UI legible - a flat scrolling form with
 * everything on one page is a phone settings screen, not a console.
 *
 * Selection is an explicit cursor index per screen, matching the game grid, so
 * the pad is authoritative and every row is also a touch target.
 */

sealed interface MenuItem {
    val label: String
    val detail: String?
    val enabled: Boolean

    /** Runs something and stays put. */
    data class Action(
        override val label: String,
        override val detail: String? = null,
        override val enabled: Boolean = true,
        val danger: Boolean = false,
        val run: () -> Unit,
    ) : MenuItem

    /** Drills into another screen. */
    data class Submenu(
        val id: String,
        override val label: String,
        override val detail: String? = null,
        override val enabled: Boolean = true,
        val badge: String? = null,
    ) : MenuItem

    /** Flips a boolean in place. */
    data class Toggle(
        override val label: String,
        override val detail: String? = null,
        override val enabled: Boolean = true,
        val on: Boolean,
        val set: (Boolean) -> Unit,
    ) : MenuItem

    /** Cycles through values in place; the current one is always shown. */
    data class Choice(
        override val label: String,
        override val detail: String? = null,
        override val enabled: Boolean = true,
        val value: String,
        val cycle: () -> Unit,
    ) : MenuItem

    /** Non-interactive explanation. Skipped by the cursor. */
    data class Note(
        override val label: String,
        override val detail: String? = null,
    ) : MenuItem {
        override val enabled = false
    }
}

data class MenuNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<MenuItem>,
)

/** Indices the cursor may land on - Notes are skipped. */
fun MenuNode.selectable(): List<Int> =
    items.indices.filter { items[it] !is MenuItem.Note }

@Composable
fun ConsoleMenuScreen(
    node: MenuNode,
    cursor: Int,
    /** true when we just drilled in, false when we came back. */
    forward: Boolean,
    depth: Int,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalTheme.current

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        AnimatedContent(
            targetState = node.id,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(220)) { w -> dir * w / 3 } + fadeIn(tween(180)))
                    .togetherWith(
                        slideOutHorizontally(tween(220)) { w -> -dir * w / 3 } + fadeOut(tween(140))
                    )
            },
            label = "menu",
            modifier = Modifier.weight(1f),
        ) { _ ->
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 22.dp,
                        bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(node.title, color = theme.textPrimary, fontSize = 26.sp,
                            fontWeight = FontWeight.Bold)
                        node.subtitle?.let {
                            Text(it, color = theme.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
                MenuList(node, cursor, onSelect, onActivate)
            }
        }

        MenuHints(node, cursor, depth, onBack) { onActivate(cursor) }
    }
}

@Composable
private fun MenuList(
    node: MenuNode,
    cursor: Int,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
) {
    val theme = LocalTheme.current
    val state = rememberLazyListState()

    LaunchedEffect(cursor, node.id) {
        // Keep the selection comfortably on screen rather than pinned to an edge.
        val target = (cursor - 2).coerceAtLeast(0)
        state.animateScrollToItem(target)
    }

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 30.dp, end = 30.dp, bottom = 20.dp),
    ) {
        itemsIndexed(node.items) { i, item ->
            MenuRow(
                item = item,
                focused = i == cursor,
                onClick = { if (item.enabled) { onSelect(i); onActivate(i) } },
            )
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, focused: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current

    if (item is MenuItem.Note) {
        Text(
            item.label,
            color = theme.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
        return
    }

    val danger = (item as? MenuItem.Action)?.danger == true
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) theme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (focused) Modifier.border(1.dp, theme.primary.copy(alpha = 0.7f),
                    RoundedCornerShape(10.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                color = when {
                    danger -> theme.error
                    !item.enabled -> theme.textDisabled
                    else -> theme.textPrimary
                },
                fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            )
            item.detail?.let {
                Text(it, color = theme.textSecondary, fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        when (item) {
            is MenuItem.Submenu -> {
                item.badge?.let {
                    Text(it, color = theme.secondary, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                }
                Text("›", color = theme.textSecondary, fontSize = 22.sp)
            }
            is MenuItem.Toggle -> Switchy(item.on)
            is MenuItem.Choice -> Text(item.value, color = theme.primary, fontSize = 15.sp,
                fontWeight = FontWeight.Medium)
            else -> Unit
        }
    }
}

@Composable
private fun Switchy(on: Boolean) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) theme.primary.copy(alpha = 0.85f) else theme.surfaceVariant),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (on) theme.background else theme.textSecondary)
        )
    }
}

@Composable
private fun MenuHints(
    node: MenuNode,
    cursor: Int,
    depth: Int,
    onBack: () -> Unit,
    onActivate: () -> Unit,
) {
    val theme = LocalTheme.current
    val item = node.items.getOrNull(cursor)
    // Say what A will do HERE, rather than a generic "Select".
    val aLabel = when (item) {
        is MenuItem.Submenu -> "Open"
        is MenuItem.Toggle -> if (item.on) "Turn off" else "Turn on"
        is MenuItem.Choice -> "Change"
        is MenuItem.Action -> item.label
        else -> "—"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 30.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadHint("B", if (depth == 0) "Close" else "Back", onBack)
        Spacer(Modifier.weight(1f))
        PadHint("A", aLabel, onActivate, dim = item?.enabled != true)
    }
}

@Composable
fun PadHint(button: String, label: String, onClick: () -> Unit, dim: Boolean = false) {
    val theme = LocalTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.textPrimary.copy(alpha = if (dim) 0.35f else 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(button, color = theme.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (dim) theme.textSecondary else theme.textPrimary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
