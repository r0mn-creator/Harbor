// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.data.LaunchSpec
import org.harbor.theme.LocalTheme

/**
 * The launch-intent editor is built from the shared menu vocabulary - see
 * IntentMenu. What remains here is the state it reads, the verification dialog,
 * and the activity lookup.
 */
/** Everything the editor needs that it cannot work out itself. */
data class IntentEditorState(
    val platformId: String,
    val platformName: String,
    val spec: LaunchSpec,
    val verified: Boolean,
    /** Title of the game Test will use, or null if the platform has none yet. */
    val testGame: String?,
    val preview: String,
    /** Exported activities of the chosen package. */
    val activities: List<String>,
    val installedApps: List<Pair<String, String>>,
)

/**
 * Asked AFTER a test launch, when the user comes back.
 *
 * Deliberately a question, not an inference. X1-BOX spawns its emulator process
 * and sits on "Please insert an Xbox disc" when the intent is wrong, which is
 * indistinguishable from success if you only check that something started.
 */
@Composable
private fun EPill(label: String, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, theme.primary.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Exported activities of a package, for the activity chooser. */
fun exportedActivities(context: Context, pkg: String): List<String> = runCatching {
    context.packageManager
        .getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        .activities.orEmpty()
        .filter { it.exported }
        .map { it.name.removePrefix(pkg) }
        .sorted()
}.getOrDefault(emptyList())
