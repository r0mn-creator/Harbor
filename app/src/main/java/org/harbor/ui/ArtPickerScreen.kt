// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.harbor.scrape.CoverScraper
import org.harbor.theme.LocalTheme

private const val COLUMNS = 4

/**
 * Every plausible cover for one game, so the user can pick one instead of
 * trusting the automatic matcher.
 *
 * Deliberately not the same safety-first matching the background scraper
 * uses: a person looking at a grid of real thumbnails before choosing IS the
 * safety check, so this shows near-misses too rather than one best guess.
 */
@Composable
fun ArtPickerScreen(
    title: String,
    candidates: List<CoverScraper.ArtCandidate>,
    loading: Boolean,
    cursor: Int,
    onSelect: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalTheme.current
    val state = rememberLazyGridState()

    LaunchedEffect(cursor) {
        val row = cursor / COLUMNS
        val first = state.firstVisibleItemIndex / COLUMNS
        if (row < first) state.animateScrollToItem(row * COLUMNS)
        else if (row >= first + 2) {
            state.animateScrollToItem(((row - 1) * COLUMNS).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize().background(theme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 10.dp),
        ) {
            Column {
                Text("Choose box art", color = theme.textPrimary, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Text(title, color = theme.textSecondary, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        when {
            loading -> CentreNote("Searching libretro and SteamGridDB…")
            candidates.isEmpty() -> CentreNote(
                "No candidates found. Try Edit name first if the title is unusual."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = state,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(candidates) { i, c ->
                    ArtTile(c, focused = i == cursor) {
                        if (i == cursor) onPick(i) else onSelect(i)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadHint("B", "Cancel", onBack)
            Spacer(Modifier.width(20.dp))
            Text("D-pad to move · A to use the highlighted cover",
                color = theme.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CentreNote(text: String) {
    val theme = LocalTheme.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = theme.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.7f))
    }
}

@Composable
private fun ArtTile(c: CoverScraper.ArtCandidate, focused: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, theme.primary, RoundedCornerShape(8.dp))
                    else Modifier
                )
        ) {
            AsyncImage(
                model = c.previewUrl,
                contentDescription = c.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.background.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(c.source, color = theme.textSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Text(
            c.label,
            color = if (focused) theme.textPrimary else theme.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
