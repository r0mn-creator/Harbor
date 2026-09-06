// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.core

import org.harbor.data.GameRecord
import org.harbor.provider.GameEntry

/**
 * One game as the UI sees it: what discovery found, plus what the library knows.
 */
data class DisplayGame(
    val title: String,
    val platformId: String,
    val entry: GameEntry?,
    val record: GameRecord?,
) {
    val coverPath: String? get() = record?.coverPath
    val favourite: Boolean get() = record?.favourite == true
    val lastPlayed: Long? get() = record?.lastPlayed
    /** Playable only if discovery actually found the file (or it is id-backed). */
    val playable: Boolean get() = entry != null
}

/**
 * Merge scanned files with stored metadata.
 *
 * Matching is by URI first, then by normalised name. The name fallback is not a
 * nicety: an imported record carries a document URI that was granted to the app
 * it came from, so once the user re-picks the folder Harbor gets a different
 * URI for the very same file. Without the name pass, every imported game would
 * appear twice - once with art and unplayable, once playable and blank.
 */
object LibraryMerge {

    fun merge(found: List<GameEntry>, known: List<GameRecord>): List<DisplayGame> {
        val byUri = known.associateBy { it.uri }
        val byName = known.groupBy { it.matchName }
        val usedRecords = mutableSetOf<String>()
        val out = mutableListOf<DisplayGame>()

        for (e in found) {
            val viaUri = e.uri?.toString()?.let { byUri[it] }
            val viaName = viaUri ?: byName[normalise(e.title)]
                ?.firstOrNull { it.key !in usedRecords }
            viaName?.let { usedRecords += it.key }
            out += DisplayGame(
                title = viaName?.title ?: e.title,
                platformId = e.platformId,
                entry = e,
                record = viaName,
            )
        }

        // Records with no file on disk are still shown - greyed and unplayable -
        // because "my game vanished" is worse than "my game is here but the
        // folder needs re-granting", and the second is actionable.
        for (r in known) {
            if (r.key in usedRecords) continue
            if (found.any { it.uri?.toString() == r.uri }) continue
            out += DisplayGame(r.title, r.platformId, null, r)
        }

        // A hidden game keeps its record and its file untouched - only the
        // shelf stops showing it - so removing one is always reversible by
        // clearing this field, never by re-scanning or re-importing.
        return out.filterNot { it.record?.hidden == true }.sortedWith(
            compareByDescending<DisplayGame> { it.favourite }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun normalise(s: String): String = org.harbor.data.normaliseTitle(s)
}
