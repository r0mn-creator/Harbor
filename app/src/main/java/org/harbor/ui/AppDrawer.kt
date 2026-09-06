// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * The app drawer, reached with B from the home screen.
 *
 * Grid of every launchable app, driven by the pad with the same explicit cursor
 * the game grid uses, and tappable.
 */
const val DRAWER_COLUMNS = 6

@Composable
fun AppDrawerScreen(
    apps: List<Pair<String, String>>,
    cursor: Int,
    onSelect: (Int) -> Unit,
    onLaunch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalTheme.current
    val state = rememberLazyGridState()

    LaunchedEffect(cursor) {
        val row = cursor / DRAWER_COLUMNS
        val first = state.firstVisibleItemIndex / DRAWER_COLUMNS
        if (row < first) state.animateScrollToItem(row * DRAWER_COLUMNS)
        else if (row >= first + 2) {
            state.animateScrollToItem(((row - 1) * DRAWER_COLUMNS).coerceAtLeast(0))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Apps", color = theme.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("${apps.size} installed", color = theme.textSecondary, fontSize = 13.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(DRAWER_COLUMNS),
            state = state,
            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(apps) { i, (pkg, label) ->
                AppTile(pkg, label, focused = i == cursor) {
                    if (i == cursor) onLaunch(pkg) else onSelect(i)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 30.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadHint("B", "Back to games", onBack)
            Spacer(Modifier.weight(1f))
            PadHint("A", "Open", { apps.getOrNull(cursor)?.let { onLaunch(it.first) } })
        }
    }
}

@Composable
private fun AppTile(pkg: String, label: String, focused: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val icon = rememberAppIcon(pkg)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) theme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .then(
                if (focused) Modifier.border(1.dp, theme.primary.copy(alpha = 0.8f),
                    RoundedCornerShape(12.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(BitmapPainter(icon), contentDescription = label,
                    modifier = Modifier.fillMaxSize())
            } else {
                Text(label.take(1).uppercase(), color = theme.textSecondary, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (focused) theme.textPrimary else theme.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx: Context = LocalContext.current
    return remember(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = android.graphics.Bitmap.createBitmap(
                d.intrinsicWidth.coerceAtLeast(1),
                d.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val c = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }
}
