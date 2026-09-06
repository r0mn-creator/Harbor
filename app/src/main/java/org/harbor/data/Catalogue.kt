// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.data

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The list of systems Harbor knows about — every console from 2000 onward,
 * plus the earlier ones already in use.
 *
 * Systems with no emulator yet (PS4, PS5, Xbox One, Xbox Series, Switch 2,
 * N-Gage) are listed deliberately. When an emulator appears, adding support is
 * editing this file or a profile — not shipping an app update.
 */
@Serializable
data class Catalogue(val systems: List<CatalogueSystem> = emptyList())

@Serializable
data class CatalogueSystem(
    val id: String,
    val name: String,
    @SerialName("short_name") val shortName: String,
    val year: Int = 0,
    @SerialName("aspect_ratio") val aspectRatio: String = "3:4",
    val extensions: List<String> = emptyList(),
    val emulators: List<CatalogueEmulator> = emptyList(),
)

@Serializable
data class CatalogueEmulator(
    val `package`: String,
    val name: String,
    /** True only where a real game was launched on a real device. */
    val verified: Boolean = false,
    val launch: LaunchSpec? = null,
)

class CatalogueStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun load(): Catalogue = runCatching {
        context.assets.open("catalogue.json").bufferedReader().use {
            json.decodeFromString<Catalogue>(it.readText())
        }
    }.getOrElse { Catalogue() }

    /** Which of a system's emulators are actually installed, best first. */
    fun installedFor(system: CatalogueSystem): List<CatalogueEmulator> {
        val pm = context.packageManager
        return system.emulators.filter { e ->
            runCatching { pm.getPackageInfo(e.`package`, 0); true }.getOrDefault(false)
        }.sortedByDescending { it.verified }
    }

    /**
     * Build a profile for a system.
     *
     * Where an installed emulator carries a known launch contract we use it and
     * inherit its `verified` flag. Where it does not, the profile is created
     * with the package only and marked unverified — which is honest, and the
     * intent editor exists precisely so the user can finish it. Dolphin proved
     * why: a bare package plus ACTION_VIEW looks reasonable and launches
     * nothing, because its EmulationActivity is not exported.
     */
    fun profileFor(
        system: CatalogueSystem,
        emulator: CatalogueEmulator?,
        order: Int,
    ): PlatformProfile {
        // A system with no file extensions has nothing to scan for. Android and
        // Windows both work this way: the games are apps or shortcuts the user
        // has already created elsewhere (GameNative, for instance), and the
        // shelf is a list they curate from the app library - not a folder.
        val provider =
            if (system.extensions.isEmpty()) SourceSpec.INSTALLED_APPS
            else SourceSpec.FOLDER

        return PlatformProfile(
            id = system.id,
            name = system.name,
            shortName = system.shortName,
            order = order,
            aspectRatio = system.aspectRatio,
            source = SourceSpec(
                provider = provider,
                roots = emptyList(),
                extensions = system.extensions,
                app = if (provider == SourceSpec.SHORTCUTS) emulator?.`package` else null,
            ),
            launch = emulator?.launch
                ?: LaunchSpec(
                    component = emulator?.`package`,
                    action = "android.intent.action.VIEW",
                    romMode = if (system.extensions.isEmpty()) LaunchSpec.ROM_NONE
                              else LaunchSpec.ROM_DATA_URI,
                    flags = listOf(LaunchSpec.FLAG_GRANT_READ, "NEW_TASK"),
                ),
            scraper = ScraperSpec(system.id),
            verified = emulator?.verified == true,
        )
    }
}
