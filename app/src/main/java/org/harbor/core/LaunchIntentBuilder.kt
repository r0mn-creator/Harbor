// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.core

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import org.harbor.data.ExtraSpec
import org.harbor.data.LaunchSpec

/**
 * Turns a LaunchSpec + a game into a real Intent.
 *
 * This is the heart of Harbor. The failure mode it exists to avoid is
 * sending ACTION_MAIN/LAUNCHER with no data for an emulator that is not
 * recognised: the emulator opens to its own menu and the ROM is never passed,
 * which looks like a launch and is not one. Everything below is driven
 * by the profile, so a new or changed emulator is a config edit, not a release.
 */
object LaunchIntentBuilder {

    /** What we know about the game being launched, for placeholder expansion. */
    data class Target(
        /** content:// URI of the ROM, for file-backed platforms. */
        val uri: Uri? = null,
        /** Opaque id for library-backed platforms (GameNative's Steam app_id). */
        val id: String? = null,
        val title: String? = null,
    )

    sealed interface Result {
        data class Ready(val intent: Intent) : Result
        /** Never silently produce a half-formed intent - say what is missing. */
        data class Unbuildable(val reason: String) : Result
    }

    fun build(spec: LaunchSpec, target: Target): Result {
        val intent = Intent(spec.action)

        spec.component?.takeIf { it.isNotBlank() }?.let { c ->
            if (!c.contains('/')) {
                // Bare package: constrain the intent to this app and let Android
                // choose the activity that declares a matching filter. This is
                // what makes an imported platform work at all, since an import
                // often carries only a package name, and it is more robust than
                // pinning an activity that may be renamed in the next release.
                intent.setPackage(c)
                return@let
            }
            val pkg = c.substringBefore('/')
            var cls = c.substringAfter('/')
            if (cls.startsWith('.')) cls = pkg + cls
            if (pkg.isBlank() || cls.isBlank()) {
                return Result.Unbuildable("component '$c' is not 'package/activity'")
            }
            intent.component = ComponentName(pkg, cls)
        }

        when (spec.romMode) {
            LaunchSpec.ROM_DATA_URI -> {
                val uri = target.uri
                    ?: return Result.Unbuildable(
                        "this platform passes the game as a data URI, but the game has no URI"
                    )
                if (spec.type != null) intent.setDataAndType(uri, spec.type)
                else intent.data = uri
            }

            LaunchSpec.ROM_EXTRA -> {
                val uri = target.uri
                    ?: return Result.Unbuildable(
                        "this platform passes the game in an extra, but the game has no URI"
                    )
                val key = spec.romExtra
                    ?: return Result.Unbuildable("rom_mode 'extra' but no rom_extra named")
                intent.putExtra(key, uri.toString())
                spec.type?.let { intent.type = it }
            }

            LaunchSpec.ROM_NONE -> {
                // Library-backed: the game is identified purely by extras. Nothing
                // file-based to attach.
                spec.type?.let { intent.type = it }
            }

            else -> return Result.Unbuildable("unknown rom_mode '${spec.romMode}'")
        }

        for (e in spec.extras) {
            when (val r = putExtra(intent, e, target)) {
                null -> Unit
                else -> return Result.Unbuildable(r)
            }
        }

        var flags = 0
        for (f in spec.flags) {
            val bit = FLAGS[f] ?: return Result.Unbuildable("unknown flag '$f'")
            flags = flags or bit
        }
        // A launcher starts games from outside any task of its own.
        flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.flags = flags

        return Result.Ready(intent)
    }

    /** @return null on success, or a reason string on failure. */
    private fun putExtra(intent: Intent, e: ExtraSpec, target: Target): String? {
        val raw = expand(e.value, target)
        when (e.type) {
            "string" -> intent.putExtra(e.key, raw)
            "bool" -> intent.putExtra(e.key, raw.equals("true", ignoreCase = true))
            "int" -> {
                val v = raw.toIntOrNull()
                    ?: return "extra '${e.key}' expects an int but got '$raw'"
                intent.putExtra(e.key, v)
            }
            "long" -> {
                val v = raw.toLongOrNull()
                    ?: return "extra '${e.key}' expects a long but got '$raw'"
                intent.putExtra(e.key, v)
            }
            else -> return "extra '${e.key}' has unknown type '${e.type}'"
        }
        return null
    }

    private fun expand(template: String, t: Target): String =
        template
            .replace("{file}", t.uri?.toString() ?: "")
            .replace("{id}", t.id ?: "")
            .replace("{title}", t.title ?: "")

    /**
     * GRANT_READ_URI_PERMISSION is in every default profile for a measured
     * reason - see LaunchSpec.FLAG_GRANT_READ. It is harmless when the intent
     * carries no URI, so there is never a reason to leave it off.
     */
    private val FLAGS: Map<String, Int> = mapOf(
        "GRANT_READ_URI_PERMISSION" to Intent.FLAG_GRANT_READ_URI_PERMISSION,
        "GRANT_WRITE_URI_PERMISSION" to Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        "GRANT_PERSISTABLE_URI_PERMISSION" to Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        "NEW_TASK" to Intent.FLAG_ACTIVITY_NEW_TASK,
        "CLEAR_TOP" to Intent.FLAG_ACTIVITY_CLEAR_TOP,
        "CLEAR_TASK" to Intent.FLAG_ACTIVITY_CLEAR_TASK,
        "SINGLE_TOP" to Intent.FLAG_ACTIVITY_SINGLE_TOP,
        "NO_ANIMATION" to Intent.FLAG_ACTIVITY_NO_ANIMATION,
        "EXCLUDE_FROM_RECENTS" to Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
    )

    /** Human-readable `am start …`, for the intent editor and bug reports. */
    fun describe(intent: Intent): String = buildString {
        append("am start")
        intent.action?.let { append(" -a $it") }
        intent.data?.let { append(" -d $it") }
        intent.type?.let { append(" -t $it") }
        intent.component?.let { append(" -n ${it.packageName}/${it.className}") }
        if (intent.flags != 0) append(" -f 0x%x".format(intent.flags))
        intent.extras?.keySet()?.forEach { k ->
            when (val v = intent.extras?.get(k)) {
                is Int -> append(" --ei $k $v")
                is Long -> append(" --el $k $v")
                is Boolean -> append(" --ez $k $v")
                else -> append(" --es $k \"$v\"")
            }
        }
    }
}
