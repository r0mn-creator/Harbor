@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.theme.LocalTheme

internal data class MockConsole(val name: String, val games: List<String>)

internal val mockConsoles = listOf(
    MockConsole("Xbox 360", listOf("Halo 3", "Gears of War", "Forza 2", "NFS: Carbon", "Ninja Gaiden II", "Halo 3: ODST")),
    MockConsole("Wii U", listOf("NFS: Most Wanted U", "Mario Kart 8", "Bayonetta 2", "Splatoon")),
    MockConsole("Xbox", listOf("Halo 2", "Fable", "NFS: Underground 2", "Ninja Gaiden Black")),
    MockConsole("PlayStation 3", listOf("Uncharted 2", "Demon's Souls", "Gran Turismo 5")),
)

/** Library tab: a top glass bar of consoles (paged with LB/RB, same as the games grid below it) over a grid of that console's library. */
@Composable
internal fun LibraryContent(scale: Scale, navState: HarborNavState) {
    val console = mockConsoles[navState.consoleIndex]
    Column(Modifier.fillMaxSize()) {
        ConsoleBar(scale, navState)
        Spacer(Modifier.height(scale.dp(24)))
        Text(
            console.name,
            color = LocalTheme.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(16),
            modifier = Modifier.padding(start = scale.dp(40), bottom = scale.dp(10)),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(horizontal = scale.dp(40)),
            horizontalArrangement = Arrangement.spacedBy(scale.dp(14)),
            verticalArrangement = Arrangement.spacedBy(scale.dp(14)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(console.games) { title -> GameCell(title, scale) }
        }
    }
}

@Composable
private fun GameCell(title: String, scale: Scale) {
    val theme = LocalTheme.current
    Column {
        Surface(
            onClick = {},
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = theme.surfaceVariant,
                focusedContainerColor = theme.accent.copy(alpha = 0.9f),
            ),
            modifier = Modifier.fillMaxWidth().size(scale.dp(84)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.VideogameAsset,
                    contentDescription = null,
                    tint = theme.textPrimary,
                    modifier = Modifier.size(scale.dp(22)),
                )
            }
        }
        Spacer(Modifier.height(scale.dp(6)))
        Text(title, color = theme.textSecondary, fontSize = scale.sp(11), maxLines = 1)
    }
}

@Composable
private fun ConsoleBar(scale: Scale, navState: HarborNavState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = scale.dp(24), start = scale.dp(24), end = scale.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriggerBadge("LB", scale, onClick = navState::prevConsole)
        Spacer(Modifier.width(scale.dp(12)))
        Row(
            Modifier
                .weight(1f)
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                .padding(horizontal = scale.dp(10), vertical = scale.dp(6)),
            horizontalArrangement = Arrangement.Center,
        ) {
            mockConsoles.forEachIndexed { index, console ->
                ConsolePill(
                    name = console.name,
                    selected = index == navState.consoleIndex,
                    scale = scale,
                    onClick = { navState.selectConsole(index) },
                )
                if (index != mockConsoles.lastIndex) Spacer(Modifier.width(scale.dp(4)))
            }
        }
        Spacer(Modifier.width(scale.dp(12)))
        TriggerBadge("RB", scale, onClick = navState::nextConsole)
    }
}

@Composable
private fun ConsolePill(name: String, selected: Boolean, scale: Scale, onClick: () -> Unit) {
    val pillShape = RoundedCornerShape(50)
    val accent = LocalTheme.current.accent
    Surface(
        onClick = onClick,
        modifier = Modifier
            .clickable(onClick = onClick)
            .let { if (selected) it.frostedSelection(pillShape) else it },
        shape = ClickableSurfaceDefaults.shape(shape = pillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = accent.copy(alpha = 0.3f),
        ),
    ) {
        Text(
            name,
            color = if (selected) accent else Color.White.copy(alpha = 0.7f),
            fontSize = scale.sp(13),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = scale.dp(14), vertical = scale.dp(8)),
        )
    }
}
