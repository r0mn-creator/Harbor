// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.provider

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserManager
import androidx.documentfile.provider.DocumentFile
import org.harbor.data.PlatformProfile
import org.harbor.data.SourceSpec

/** One game, however it was discovered. */
data class GameEntry(
    val key: String,
    val title: String,
    val platformId: String,
    /** File-backed platforms. */
    val uri: Uri? = null,
    /** Library-backed platforms (GameNative's Steam app_id). */
    val id: String? = null,
    /** Shortcut passthrough: launched via LauncherApps, not a built intent. */
    val shortcutId: String? = null,
    val shortcutPackage: String? = null,
    /** >1 when this entry stands for a multi-disc set. */
    val discCount: Int = 1,
)

data class Discovery(
    val games: List<GameEntry>,
    /** Non-fatal explanations to show in the UI, never swallowed. */
    val notes: List<String> = emptyList(),
)

/**
 * How games are DISCOVERED. A fixed, generic set - adding an emulator never
 * needs a new provider, which is the seam that lets platforms stay pure data.
 */
interface GameProvider {
    val name: String
    fun discover(context: Context, profile: PlatformProfile): Discovery
}

/** Scan SAF trees for matching extensions. Serves nearly every emulator. */
object FolderProvider : GameProvider {
    override val name = SourceSpec.FOLDER

    /**
     * Disc formats where ONE game is many files.
     *
     * A Dreamcast game is a .gdi or .cue plus a track .bin per audio/data
     * track; a PlayStation game is a .cue plus its .bin. Counting every file
     * turns 41 Dreamcast games into 179 entries, most of them unlaunchable
     * fragments. So within a directory, if any descriptor is present, only the
     * highest-priority descriptor counts and the tracks it references are
     * ignored.
     *
     * Ordered most-preferred first: a folder holding both .gdi and .cue for the
     * same game should yield one entry, not two.
     */
    private val DESCRIPTORS = listOf("m3u", "gdi", "cue", "chd", "cdi")

    /** Files that are only ever parts of a disc set, never a game on their own. */
    private val TRACKS = setOf("bin", "raw", "sub", "ccd", "img")

    /**
     * "(Disc 2)", "(Disk 2)", "(CD2)", "(Disc 1 of 2)" and the bracketed forms.
     *
     * A two-disc game is two files and one game. Listing both puts an entry on
     * the shelf that starts the story halfway through, which is never what
     * someone means to pick.
     */
    private val DISC = Regex(
        """[\s._-]*[\(\[]\s*(?:dis[ck]|cd)\s*0*(\d+)(?:\s*of\s*\d+)?\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    private fun discNumber(name: String): Int? =
        DISC.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /**
     * The name with the disc marker AND the extension removed.
     *
     * Region and revision tags are deliberately kept, so "Game (USA)" and
     * "Game (Europe)" stay separate games. The extension has to go because a
     * set can mix formats - this library has "Enter the Matrix (Disc 1).ciso"
     * next to "(Disc 2).7z", which are one game.
     */
    private fun discBase(name: String): String =
        name.substringBeforeLast('.')
            .replace(DISC, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    /**
     * A bare trailing number: "Final Fantasy IX 1.cue" beside "... 2.cue".
     *
     * Only ever treated as a disc marker when a numbered **1 is actually
     * present** in the same folder. That single condition is what keeps
     * sequels intact: "Dino Crisis 2" sits next to "Dino Crisis" with no
     * "Dino Crisis 1", so it is a game, not a second disc.
     */
    private val BARE = Regex("""[\s._-]+0*(\d{1,2})$""")

    private fun bareNumber(n: String): Int? =
        BARE.find(n.substringBeforeLast('.'))?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun bareBase(n: String): String =
        n.substringBeforeLast('.').replace(BARE, "").trim()

    /** Archives are a last resort: an emulator may not read them, and they are
     *  slower when it can. */
    private val ARCHIVES = setOf("7z", "zip", "rar")

    override fun discover(context: Context, profile: PlatformProfile): Discovery {
        val games = mutableListOf<GameEntry>()
        val notes = mutableListOf<String>()
        val exts = profile.source.extensions.map { it.lowercase().removePrefix(".") }.toSet()

        if (profile.source.roots.isEmpty()) {
            return Discovery(emptyList(), listOf("No folder set for ${profile.name}."))
        }

        for (root in profile.source.roots) {
            val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(root)) }.getOrNull()
            if (tree == null || !tree.canRead()) {
                // SAF grants do NOT transfer between apps, and are lost if the
                // user clears them. Say so plainly - an empty section with no
                // explanation is the worst outcome.
                notes += "Cannot read folder for ${profile.name}. " +
                    "Re-pick it in Settings to grant access."
                continue
            }
            walk(tree, exts, profile, games, depth = 0)
        }
        if (games.isEmpty() && notes.isEmpty()) {
            notes += "No files matching ${exts.joinToString(", ")} in ${profile.name}."
        }
        return Discovery(games, notes)
    }

    private fun walk(
        dir: DocumentFile,
        exts: Set<String>,
        profile: PlatformProfile,
        out: MutableList<GameEntry>,
        depth: Int,
    ) {
        if (depth > 6) return          // ROM sets nest, but not that deeply

        val entries = dir.listFiles()
        val files = entries.filter { !it.isDirectory }
        val present = files.mapNotNull { it.name?.substringAfterLast('.', "")?.lowercase() }.toSet()

        // If this directory describes discs, one descriptor kind wins here and
        // the track files belonging to it are not games.
        val winner = DESCRIPTORS.firstOrNull { it in present && it in exts }

        val candidates = mutableListOf<Pair<String, DocumentFile>>()
        for (f in entries) {
            if (f.isDirectory) {
                walk(f, exts, profile, out, depth + 1)
                continue
            }
            val n = f.name ?: continue
            val ext = n.substringAfterLast('.', "").lowercase()
            if (ext !in exts) continue
            if (winner != null && ext != winner) continue   // tracks and rival descriptors
            if (winner == null && ext in TRACKS) continue    // a stray track with no descriptor
            candidates += n to f
        }

        // Bases where bare numbering really is a disc set: more than one number
        // AND a 1 among them. Decided per folder, over the whole candidate list,
        // because a single filename can never tell you which it is.
        val bareSets = candidates
            .mapNotNull { (n, _) -> bareNumber(n)?.let { bareBase(n).lowercase() to it } }
            .groupBy({ it.first }, { it.second })
            .filterValues { 1 in it && it.distinct().size > 1 }
            .keys

        fun isBareDisc(n: String) = bareNumber(n) != null && bareBase(n).lowercase() in bareSets
        fun groupKey(n: String) =
            if (isBareDisc(n)) bareBase(n).lowercase() else discBase(n).lowercase()
        fun disc(n: String) = discNumber(n) ?: bareNumber(n)?.takeIf { isBareDisc(n) }

        // Collapse multi-disc sets to one entry, launching the lowest disc
        // present. An .m3u already won above, so this only runs where the set
        // has no playlist.
        for ((_, group) in candidates.groupBy { (n, _) -> groupKey(n) }) {
            // Lowest disc first, then prefer a directly readable format over an
            // archive of the same disc.
            val pick = group.minWithOrNull(
                compareBy(
                    { (n, _) -> disc(n) ?: 0 },
                    { (n, _) -> if (n.substringAfterLast('.', "").lowercase() in ARCHIVES) 1 else 0 },
                    { (n, _) -> n.lowercase() },
                )
            ) ?: continue
            val (name, file) = pick
            val discs = group.count { (n, _) -> disc(n) != null }
            out += GameEntry(
                key = file.uri.toString(),
                title = prettyTitle(name),
                platformId = profile.id,
                uri = file.uri,
                discCount = if (discs > 1) discs else 1,
            )
        }
    }

    /** "Halo 2 (USA, Europe) (En,Ja).xiso.iso" -> "Halo 2" */
    fun prettyTitle(fileName: String): String =
        fileName
            .substringBeforeLast('.')
            .replace(Regex("""\.(xiso|xex|iso|nkit)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { fileName }
}

/**
 * The "Android" section: a CURATED shelf of installed apps.
 *
 * There is no folder to scan here, so the section shows exactly the packages
 * the user added and nothing else. Enumerating every launchable app would bury
 * a handful of games under a hundred utilities.
 */
object InstalledAppsProvider : GameProvider {
    override val name = SourceSpec.INSTALLED_APPS

    override fun discover(context: Context, profile: PlatformProfile): Discovery {
        val wanted = profile.source.packages
        if (wanted.isEmpty()) {
            return Discovery(
                emptyList(),
                // Name the shelf being looked at: Android is no longer the only
                // one, and "Settings ▸ Android" is wrong on the Windows shelf.
                listOf("No apps added yet. Settings ▸ Consoles ▸ ${profile.name} " +
                    "▸ Choose apps.")
            )
        }
        val pm = context.packageManager
        val games = mutableListOf<GameEntry>()
        val missing = mutableListOf<String>()
        for (pkg in wanted) {
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            if (info == null) { missing += pkg; continue }
            games += GameEntry(
                key = pkg,
                title = pm.getApplicationLabel(info).toString(),
                platformId = profile.id,
                id = pkg,
            )
        }
        val notes = if (missing.isEmpty()) emptyList()
        else listOf("${missing.size} added app(s) are no longer installed: " +
            missing.take(3).joinToString())
        return Discovery(games.sortedBy { it.title.lowercase() }, notes)
    }

    /** Every launchable app, for the picker. */
    fun installedApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
}

/**
 * Shortcut passthrough - the fallback for anything with no usable intent. The
 * emulator creates its own shortcut and Harbor hosts it.
 *
 * ⚠️ Only works when Harbor is the DEFAULT LAUNCHER, and FLAG_MATCH_PINNED
 * only returns shortcuts pinned to the calling launcher. Anything pinned while
 * another launcher was default is invisible to us; seeing those needs the
 * system ACCESS_SHORTCUTS permission, which a normal app cannot hold. The note
 * below is deliberately user-facing rather than a silent empty list.
 */
object ShortcutProvider : GameProvider {
    override val name = SourceSpec.SHORTCUTS

    override fun discover(context: Context, profile: PlatformProfile): Discovery {
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return Discovery(emptyList(), listOf("Shortcuts unavailable on this device."))
        val users = (context.getSystemService(Context.USER_SERVICE) as UserManager)
            .userProfiles.ifEmpty { listOf(Process.myUserHandle()) }

        val games = mutableListOf<GameEntry>()
        val notes = mutableListOf<String>()
        val q = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        profile.source.app?.let { q.setPackage(it) }

        for (u in users) {
            val list = runCatching { la.getShortcuts(q, u) }.getOrElse {
                notes += "Harbor must be the default launcher to read shortcuts."
                return Discovery(emptyList(), notes)
            } ?: continue
            for (s in list) {
                games += GameEntry(
                    key = "${s.`package`}#${s.id}",
                    title = (s.longLabel ?: s.shortLabel ?: s.id).toString(),
                    platformId = profile.id,
                    shortcutId = s.id,
                    shortcutPackage = s.`package`,
                )
            }
        }
        if (games.isEmpty()) {
            notes += "No shortcuts found. Shortcuts are only visible if they were " +
                "pinned while Harbor was the default launcher."
        }
        return Discovery(games, notes)
    }
}

/**
 * Read another app's game library. GameNative identifies games by a numeric
 * Steam/Epic/GOG app_id, not a file, so there is nothing on disk to scan.
 *
 * Not implemented yet: reading GameNative's library needs either its exported
 * data or the shortcut route. Returns an honest note rather than an empty
 * section that looks like "you own no games".
 */
object LibraryProvider : GameProvider {
    override val name = SourceSpec.LIBRARY

    override fun discover(context: Context, profile: PlatformProfile): Discovery {
        val app = profile.source.app ?: return Discovery(emptyList(), listOf("No app configured."))
        val installed = runCatching {
            context.packageManager.getPackageInfo(app, 0); true
        }.getOrDefault(false)
        return Discovery(
            emptyList(),
            listOf(
                if (!installed) "$app is not installed."
                else "Reading $app's library is not implemented yet. " +
                    "Create a shortcut in $app and add it via the Shortcuts source."
            )
        )
    }
}

object Providers {
    private val all = listOf(
        FolderProvider, InstalledAppsProvider, ShortcutProvider, LibraryProvider,
    ).associateBy { it.name }

    fun discover(context: Context, profile: PlatformProfile): Discovery {
        val p = all[profile.source.provider]
            ?: return Discovery(
                emptyList(),
                listOf("Unknown source provider '${profile.source.provider}'.")
            )
        return runCatching { p.discover(context, profile) }.getOrElse {
            Discovery(emptyList(), listOf("Scan failed: ${it.message ?: it::class.simpleName}"))
        }
    }
}
