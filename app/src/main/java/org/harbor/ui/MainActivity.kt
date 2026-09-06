// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.harbor.HarborApp
import org.harbor.core.DisplayGame
import org.harbor.core.LaunchIntentBuilder
import org.harbor.core.LibraryMerge
import org.harbor.data.PlatformProfile
import org.harbor.provider.Providers
import org.harbor.theme.LocalTheme

/**
 * The launcher.
 *
 * Driven with a pad: bumpers page through systems, d-pad/left stick move the
 * cursor, A launches. Focus is an explicit index (see GamepadNav) rather than
 * Compose focus, so the selection behaves like a console UI instead of a
 * scrollable list.
 */
class MainActivity : ComponentActivity() {

    private var pages by mutableStateOf<List<SystemPage>>(emptyList())
    private var systemIndex by mutableIntStateOf(0)
    private var cursor by mutableStateOf(GridCursor())
    private var pendingFolderFor: String? = null
    /** Settings navigation stack: a path of node ids. Empty = root. */
    private var menuPath by mutableStateOf<List<String>>(emptyList())
    private var showSettings by mutableStateOf(false)
    private var showDrawer by mutableStateOf(false)
    /** One cursor per screen depth, so backing out restores where you were. */
    private var menuCursors by mutableStateOf<Map<String, Int>>(emptyMap())
    private var drawerCursor by mutableIntStateOf(0)
    private var menuForward by mutableStateOf(true)
    private var categoryIndex by mutableIntStateOf(0)
    private var pane by mutableStateOf(Pane.RAIL)
    /** Bumped when the palette changes, to force a recompose. */
    private var colorEpoch by mutableIntStateOf(0)
    private var showAppPicker by mutableStateOf(false)
    /** Which shelf the app picker is editing. Android and Windows are both
     *  curated from installed apps, so "the app shelf" is no longer unique. */
    private var appPickerFor by mutableStateOf<String?>(null)
    /** Held down on a tile: that game's own menu is open. */
    private var contextMenuFor by mutableStateOf<Pair<SystemPage, DisplayGame>?>(null)
    private var contextMenuCursor by mutableStateOf(0)
    /** True once "Remove game" has been picked but not yet confirmed. */
    private var contextMenuConfirming by mutableStateOf(false)
    private var artPickerFor by mutableStateOf<Pair<SystemPage, DisplayGame>?>(null)
    private var artCandidates by mutableStateOf<List<org.harbor.scrape.CoverScraper.ArtCandidate>>(emptyList())
    private var artPickerLoading by mutableStateOf(false)
    private var artPickerCursor by mutableStateOf(0)
    /** Non-null while first-run setup is on screen. */
    private var onboardStep by mutableStateOf<Step?>(null)
    private var onboardCursor by mutableStateOf(0)
    /** The one system chosen during first-run setup. */
    private var onboardPicked by mutableStateOf<String?>(null)
    /** Platform ids waiting for a box-art pass, and the worker's status line. */
    private val artQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var artStatus by mutableStateOf<String?>(null)
    @Volatile private var artRunning = false
    /** Non-null while a long job runs; the text is what the user sees. */
    private var busy by mutableStateOf<String?>(null)
    /** Platform whose launch intent is being edited, and the working copy. */
    private var editingId by mutableStateOf<String?>(null)
    private var editingSpec by mutableStateOf<org.harbor.data.LaunchSpec?>(null)
    /** Set while a test launch is out; answered when the user comes back. */
    private var verifyFor by mutableStateOf<Pair<String, String>?>(null)
    private var editorCursor by mutableIntStateOf(0)
    private var appPickerCursor by mutableIntStateOf(0)
    /** Open text prompt: title, hint, current value, and what to do with it. */
    private var prompt by mutableStateOf<Triple<String, String?, String>?>(null)
    private var promptApply: ((String) -> Unit)? = null
    /** The on-screen keyboard's own state - this app draws its own, see OnScreenKeyboard.kt. */
    private var promptText by mutableStateOf("")
    /** Index into [promptText] - typing, backspace and paste all act here,
     *  not always at the end, so this is a real cursor, not a fiction. */
    private var promptCursor by mutableStateOf(0)
    private var promptSymbols by mutableStateOf(false)
    private var promptShift by mutableStateOf(false)
    private var kbRow by mutableStateOf(0)
    private var kbCol by mutableStateOf(0)
    private var editingPickPackage by mutableStateOf(false)
    private var pkgCursor by mutableIntStateOf(0)
    /**
     * Systems still waiting for a folder, walked one picker at a time.
     *
     * After importing a library every system needs its folder re-granted (a
     * SAF grant belongs to the app that asked for it), so making the user find
     * each one separately would be eleven trips through the UI for one task.
     */
    private var setupQueue: List<String> = emptyList()
    private var setupTotal = 0

    private val app get() = HarborApp.instance

    /**
     * The editor's pending state has to OUTLIVE THE PROCESS.
     *
     * A test launch starts an emulator, and an emulator on a handheld will
     * happily evict the launcher. Coming back to find the editor gone - and the
     * verification question never asked - makes the feature unusable in exactly
     * the case it exists for. So the pending answer is written to prefs before
     * we leave, and picked up again in onCreate.
     */
    private val prefs get() = getSharedPreferences("harbor_ui", MODE_PRIVATE)

    /** Picks a .theme file. Any mime: ".theme" has no registered type. */
    private val themePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val uri = res.data?.data ?: return@registerForActivityResult
        val name = queryDisplayName(uri) ?: "Imported"
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) { toast("Could not read that file"); return@registerForActivityResult }
        app.colors.importFrom(name, text)
            .onSuccess { toast("Added the \"" + it + "\" theme"); colorEpoch++; reload() }
            .onFailure { toast("Not a theme file: " + (it.message ?: "could not be read")) }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    private fun importColorTheme() {
        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching { themePicker.launch(i) }
            .onFailure { toast("No file picker available on this device") }
    }

    /** Launches another one of these companion apps by package - tries the release id, then the
     * ".debug" variant, since these are sideloaded and either could be the one installed. */
    private fun openOtherApp(releasePackage: String) {
        val intent = packageManager.getLaunchIntentForPackage(releasePackage)
            ?: packageManager.getLaunchIntentForPackage("$releasePackage.debug")
        if (intent != null) startActivity(intent) else toast("Not installed on this device")
    }

    private val folderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val tree: Uri? = result.data?.data
        val id = pendingFolderFor
        pendingFolderFor = null
        if (tree == null || id == null) return@registerForActivityResult
        val profile = loadProfileById(id)
        if (profile == null) {
            toast("Could not find that platform")
            return@registerForActivityResult
        }
        if (FolderPicker.accept(this, app.profiles, profile, tree) == null) {
            toast("Could not keep access to that folder")
            setupQueue = emptyList()
        } else {
            advanceSetup(justGranted = profile)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        immersive()
        setContent {
            // Re-read on every recomposition trigger so a theme change applies
            // immediately rather than after a restart.
            val theme = remember(colorEpoch) { app.activeColors() }
            CompositionLocalProvider(LocalTheme provides theme) {
              Box(Modifier.fillMaxSize()) {
                val ed = editingId
                val vf = verifyFor
                if (vf != null) {
                    VerifyDialog(vf.second) { ok -> answerVerify(ok) }
                } else if (onboardStep != null && !showSettings && !showAppPicker) {
                    val st = onboardStep!!
                    val node = Onboarding.node(st, onboardingState(), onboardActions)
                    ConsoleMenuScreen(
                        node = node,
                        cursor = onboardCursor.coerceIn(0, (node.items.size - 1).coerceAtLeast(0)),
                        forward = true,
                        depth = if (st == Step.WELCOME) 0 else 1,
                        onSelect = { onboardCursor = it },
                        onActivate = { i -> activate(node, i) },
                        onBack = { onboardBack() },
                    )
                } else if (ed != null && editingPickPackage) {
                    val node = packageNode()
                    ConsoleMenuScreen(
                        node = node,
                        cursor = pkgCursor.coerceIn(0, (node.items.size - 1).coerceAtLeast(0)),
                        forward = true,
                        depth = 1,
                        onSelect = { pkgCursor = it },
                        onActivate = { i -> activate(node, i) },
                        onBack = { editingPickPackage = false },
                    )
                } else if (ed != null && editingSpec != null) {
                    val node = intentNode(ed, editingSpec!!)
                    ConsoleMenuScreen(
                        node = node,
                        cursor = clampEditorCursor(node),
                        forward = true,
                        depth = 1,
                        onSelect = { editorCursor = it },
                        onActivate = { i -> activate(node, i) },
                        onBack = { editingId = null; editingSpec = null },
                    )
                } else if (artPickerFor != null) {
                    val (pg, g) = artPickerFor!!
                    ArtPickerScreen(
                        title = g.title,
                        candidates = artCandidates,
                        loading = artPickerLoading,
                        cursor = artPickerCursor.coerceIn(0, (artCandidates.size - 1).coerceAtLeast(0)),
                        onSelect = { artPickerCursor = it },
                        onPick = { i -> artCandidates.getOrNull(i)?.let { applyArtCandidate(pg, g, it) } },
                        onBack = { artPickerFor = null },
                    )
                } else if (showAppPicker) {
                    val rows = appPickerRows()
                    AppPickerScreen(
                        apps = rows,
                        cursor = appPickerCursor.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
                        onSelect = { appPickerCursor = it },
                        onToggle = ::toggleApp,
                        onClose = { showAppPicker = false; appPickerFor = null; reload() },
                    )
                } else if (showDrawer) {
                    AppDrawerScreen(
                        apps = drawerApps,
                        cursor = drawerCursor,
                        onSelect = { drawerCursor = it },
                        onLaunch = ::launchApp,
                        onBack = { showDrawer = false },
                    )
                } else if (showSettings) {
                    val st = settingsState()
                    val tree = MenuTree(st, menuActions)
                    val nodeNow = tree.nodeFor(currentPath())
                    SettingsShell(
                        categories = SETTINGS_CATEGORIES,
                        categoryIndex = categoryIndex,
                        node = nodeNow,
                        contentCursor = cursorFor(nodeNow),
                        pane = pane,
                        forward = menuForward,
                        subtitle = st.gamesPlayable.toString() + " of " +
                            st.gamesTotal.toString() + " games playable",
                        onPickCategory = { i -> categoryIndex = i; menuPath = emptyList() },
                        onFocusContent = { pane = Pane.CONTENT },
                        onSelectContent = { setCursor(nodeNow, it) },
                        onActivate = { activate(nodeNow, it) },
                        onBack = ::menuBack,
                    )
                } else {
                    HomeScreen(
                        pages = pages,
                        systemIndex = systemIndex,
                        cursor = cursor,
                        onChooseFolder = ::chooseFolder,
                        onImport = ::runImport,
                        onLaunch = ::launch,
                        onSelect = { i -> cursor = cursor.copy(index = i) },
                        onPrevSystem = { handle(Nav.PREV_SYSTEM) },
                        onNextSystem = { handle(Nav.NEXT_SYSTEM) },
                        onPickSystem = { i ->
                            systemIndex = i.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                            cursor = cursor.copy(index = 0)
                        },
                        onSettings = { showSettings = true },
                        onApps = ::openDrawer,
                        onLongPress = { pg, g -> contextMenuFor = pg to g },
                        artStatus = artStatus,
                    )
                }
                // Global, not scoped to any one screen: the SteamGridDB key is
                // edited from Settings, the launch intent's fields from the
                // editor, and a theme rename from Settings too - one overlay,
                // triggered from wherever a plain string needs typing.
                prompt?.let { (t, h, _) ->
                    TextPromptOverlay(
                        title = t, hint = h, text = promptText, textCursor = promptCursor,
                        symbols = promptSymbols, shift = promptShift,
                        cursorRow = kbRow, cursorCol = kbCol,
                        onKeyTap = { r, c -> kbRow = r; kbCol = c; pressKey() },
                        onCancel = { prompt = null },
                    )
                }
                contextMenuFor?.let { (_, g) ->
                    GameContextMenu(
                        title = g.title,
                        items = contextMenuItems(g),
                        confirming = contextMenuConfirming,
                        cursor = contextMenuCursor,
                        onSelect = { contextMenuCursor = it },
                        onActivate = { handle(Nav.LAUNCH) },
                        onDismiss = { contextMenuFor = null; contextMenuConfirming = false },
                    )
                }
                busy?.let { BusyOverlay(it) }
              }
            }
        }
        restorePending()
        // Shown only until it is completed or skipped. Deliberately AFTER
        // restorePending: a pending "did the game load?" question is a reply to
        // something the user did and must not be buried under a wizard.
        if (!app.config.setupComplete && verifyFor == null) onboardStep = Step.WELCOME
        reload()
    }

    private fun restorePending() {
        val id = prefs.getString(KEY_VERIFY_ID, null)
        val title = prefs.getString(KEY_VERIFY_TITLE, null)
        if (id != null && title != null) {
            verifyFor = id to title
            editingId = id
            editingSpec = loadProfileById(id)?.launch
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    /** Full screen: a launcher owns the display. */
    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---- data ---------------------------------------------------------------

    /** @param landOn platform id to select once the rebuild finishes. */
    private fun reload(landOn: String? = null) {
        lifecycleScope.launch {
            val built = withContext(Dispatchers.IO) { buildPages() }
            pages = built
            val wanted = landOn?.let { id -> built.indexOfFirst { it.profile.id == id } }
                ?.takeIf { it >= 0 }
            systemIndex = (wanted ?: systemIndex).coerceIn(0, (built.size - 1).coerceAtLeast(0))
            cursor = cursor.copy(index = 0)
            // Anything newly scanned queues itself. Granting a folder, adding a
            // system and finishing an import all end here, so this is the one
            // place that needs to know - and asking for art should never be a
            // thing the user remembers to do.
            queueArtForAll()
        }
    }

    /**
     * Every profile becomes a page, including broken ones. A platform that fails
     * validation is shown with its reason rather than disappearing - an absent
     * system looks identical to one that was never configured.
     */
    private fun buildPages(): List<SystemPage> {
        val loaded = app.profiles.load()
        val out = mutableListOf<SystemPage>()

        for (p in loaded.profiles.filter { it.enabled }) {
            val d = Providers.discover(this, p)
            val games = LibraryMerge.merge(d.games, app.library.forPlatform(p.id))
            out += SystemPage(p, games, d.notes)
        }
        for ((which, why) in loaded.problems) {
            val p = loadProfileById(which.removeSuffix(".json"))
            if (p != null) {
                // Imported games still belong to this system even while the
                // profile is unusable, so the page is never mysteriously empty.
                val games = LibraryMerge.merge(emptyList(), app.library.forPlatform(p.id))
                out += SystemPage(p, games, emptyList(), why)
            }
        }
        return out.sortedBy { it.profile.order }
    }

    private fun loadProfileById(id: String): PlatformProfile? = runCatching {
        val f = java.io.File(app.profiles.dir, if (id.endsWith(".json")) id else "$id.json")
        if (!f.isFile) null
        else kotlinx.serialization.json.Json { isLenient = true; ignoreUnknownKeys = true }
            .decodeFromString<PlatformProfile>(f.readText())
    }.getOrNull()

    // ---- input --------------------------------------------------------------

    /**
     * dispatchKeyEvent, not onKeyDown.
     *
     * Compose's focus system consumes the d-pad before an Activity's onKeyDown
     * ever runs, so with onKeyDown the bumpers worked and the d-pad silently did
     * nothing. Intercepting at dispatch keeps the cursor authoritative, which is
     * the whole point of driving selection by index.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val nav = GamepadNav.fromKey(event.keyCode)
            if (nav != null) { handle(nav); return true }
        } else if (event.action == KeyEvent.ACTION_UP) {
            // Swallow the matching UP so nothing downstream reacts to it.
            if (GamepadNav.fromKey(event.keyCode) != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val nav = GamepadNav.fromMotion(event)
        if (nav != null) { handle(nav); return true }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val nav = GamepadNav.fromMotion(event)
        if (nav != null) { handle(nav); return true }
        return super.onGenericMotionEvent(event)
    }

    /** Rows a game's own menu currently shows - depends on whether it can have art at all. */
    private fun contextMenuItems(g: DisplayGame): List<Pair<String, Boolean>> = buildList {
        add("Edit name" to false)
        if (g.entry != null || g.record != null) add("Edit box art" to false)
        add("Remove game" to true)
    }

    private fun handle(nav: Nav) {
        val page = pages.getOrNull(systemIndex)
        when (nav) {
            // L1/R1 double as the text cursor while a prompt is open - there is
            // no system tab visible to page through underneath it, and a d-pad
            // row is already spoken for by the keyboard grid itself.
            Nav.PREV_SYSTEM -> if (prompt != null) {
                promptCursor = (promptCursor - 1).coerceAtLeast(0)
            } else if (pages.isNotEmpty()) {
                systemIndex = (systemIndex - 1 + pages.size) % pages.size
                cursor = cursor.copy(index = 0)
            }
            Nav.NEXT_SYSTEM -> if (prompt != null) {
                promptCursor = (promptCursor + 1).coerceAtMost(promptText.length)
            } else if (pages.isNotEmpty()) {
                systemIndex = (systemIndex + 1) % pages.size
                cursor = cursor.copy(index = 0)
            }
            Nav.LEFT, Nav.RIGHT, Nav.UP, Nav.DOWN -> when {
                prompt != null -> {
                    val rows = keyboardRows(promptSymbols)
                    kbRow = kbRow.coerceIn(0, rows.size - 1)
                    when (nav) {
                        Nav.UP, Nav.DOWN -> {
                            kbRow = (kbRow + if (nav == Nav.DOWN) 1 else -1).coerceIn(0, rows.size - 1)
                            kbCol = kbCol.coerceIn(0, rows[kbRow].size - 1)
                        }
                        Nav.LEFT -> kbCol = (kbCol - 1).coerceAtLeast(0)
                        Nav.RIGHT -> kbCol = (kbCol + 1).coerceAtMost(rows[kbRow].size - 1)
                        else -> Unit
                    }
                }
                contextMenuFor != null -> {
                    val n = if (contextMenuConfirming) 2 else contextMenuItems(contextMenuFor!!.second).size
                    if (n > 0) contextMenuCursor = when (nav) {
                        Nav.UP, Nav.LEFT -> (contextMenuCursor - 1).coerceAtLeast(0)
                        Nav.DOWN, Nav.RIGHT -> (contextMenuCursor + 1).coerceAtMost(n - 1)
                        else -> contextMenuCursor
                    }
                }
                artPickerFor != null -> {
                    val n = artCandidates.size
                    if (n > 0) artPickerCursor = GridCursor(artPickerCursor, 4).move(nav, n).index
                }
                editingPickPackage -> {
                    val n = packageNode().items.size
                    if (n > 0) pkgCursor = when (nav) {
                        Nav.UP, Nav.LEFT -> (pkgCursor - 1).coerceAtLeast(0)
                        Nav.DOWN, Nav.RIGHT -> (pkgCursor + 1).coerceAtMost(n - 1)
                        else -> pkgCursor
                    }
                }
                editingId != null -> moveEditor(nav)
                showAppPicker -> {
                    val n = appPickerRows().size
                    if (n > 0) appPickerCursor = when (nav) {
                        Nav.UP, Nav.LEFT -> (appPickerCursor - 1).coerceAtLeast(0)
                        Nav.DOWN, Nav.RIGHT -> (appPickerCursor + 1).coerceAtMost(n - 1)
                        else -> appPickerCursor
                    }
                }
                onboardStep != null && !showSettings -> {
                    val n = Onboarding.node(onboardStep!!, onboardingState(), onboardActions)
                        .items.size
                    if (n > 0) onboardCursor = when (nav) {
                        Nav.UP, Nav.LEFT -> (onboardCursor - 1).coerceAtLeast(0)
                        Nav.DOWN, Nav.RIGHT -> (onboardCursor + 1).coerceAtMost(n - 1)
                        else -> onboardCursor
                    }
                }
                showDrawer -> drawerCursor = GridCursor(drawerCursor, DRAWER_COLUMNS)
                    .move(nav, drawerApps.size).index
                showSettings -> moveMenu(nav)
                else -> cursor = cursor.move(nav, page?.games?.size ?: 0)
            }
            Nav.LAUNCH -> if (prompt != null) {
                pressKey()
            } else if (contextMenuFor != null) {
                val (pg, g) = contextMenuFor!!
                if (contextMenuConfirming) {
                    // Cursor defaults to Cancel (index 1) when this step opens,
                    // so a stray extra press on the same button that opened the
                    // menu cannot also be the press that deletes something.
                    if (contextMenuCursor == 0) { contextMenuFor = null; hideGame(pg, g) }
                    contextMenuConfirming = false
                    contextMenuCursor = 0
                } else {
                    when (contextMenuItems(g).getOrNull(contextMenuCursor)?.first) {
                        "Edit name" -> {
                            contextMenuFor = null
                            askText("Edit name", null, g.title) { renameGame(pg, g, it) }
                        }
                        "Edit box art" -> { contextMenuFor = null; openArtPicker(pg, g) }
                        "Remove game" -> { contextMenuConfirming = true; contextMenuCursor = 1 }
                    }
                }
            } else if (artPickerFor != null) {
                val (pg, g) = artPickerFor!!
                artCandidates.getOrNull(artPickerCursor)?.let { applyArtCandidate(pg, g, it) }
            } else if (editingPickPackage) {
                val n = packageNode()
                activate(n, pkgCursor.coerceIn(0, (n.items.size - 1).coerceAtLeast(0)))
            } else if (editingId != null) {
                val n = intentNode(editingId!!, editingSpec!!)
                activate(n, clampEditorCursor(n))
            } else if (onboardStep != null && !showSettings) {
                val n = Onboarding.node(onboardStep!!, onboardingState(), onboardActions)
                activate(n, onboardCursor.coerceIn(0, (n.items.size - 1).coerceAtLeast(0)))
            } else if (showAppPicker) {
                appPickerRows().getOrNull(appPickerCursor)?.let { toggleApp(it.pkg, !it.chosen) }
            } else if (showDrawer) {
                drawerApps.getOrNull(drawerCursor)?.let { launchApp(it.first) }
            } else if (showSettings) {
                if (pane == Pane.RAIL) {
                    pane = Pane.CONTENT
                } else {
                    val n = MenuTree(settingsState(), menuActions).nodeFor(currentPath())
                    activate(n, cursorFor(n))
                }
            } else {
                val g = page?.games?.getOrNull(cursor.index)
                if (page != null && g != null) launch(page, g)
                else if (page != null && page.problem?.contains("no roots") == true) {
                    chooseFolder(page.profile.id)
                }
            }
            Nav.BACK -> when {
                prompt != null -> prompt = null
                verifyFor != null -> Unit          // must be answered
                contextMenuFor != null -> if (contextMenuConfirming) {
                    contextMenuConfirming = false; contextMenuCursor = 0
                } else {
                    contextMenuFor = null
                }
                artPickerFor != null -> artPickerFor = null
                editingPickPackage -> editingPickPackage = false
                editingId != null -> { editingId = null; editingSpec = null }
                showAppPicker -> { showAppPicker = false; appPickerFor = null; reload() }
                showDrawer -> showDrawer = false
                showSettings -> menuBack()
                onboardStep != null -> onboardBack()
                // On the home screen B opens the app drawer.
                else -> openDrawer()
            }
            Nav.MENU -> if (showSettings) {
                showSettings = false
            } else {
                menuPath = emptyList(); menuForward = true
                pane = Pane.RAIL; categoryIndex = 0; showSettings = true
            }
            // X doubles as "open this game's menu" - the pad equivalent of
            // holding a tile down - since Search has nowhere else to live yet
            // and a pad has no touchscreen to hold.
            Nav.SEARCH -> {
                val g = page?.games?.getOrNull(cursor.index)
                if (page != null && g != null && prompt == null && contextMenuFor == null &&
                    artPickerFor == null && !editingPickPackage && editingId == null &&
                    onboardStep == null && !showAppPicker && !showDrawer && !showSettings
                ) {
                    contextMenuFor = page to g
                } else {
                    toast("Search is not built yet")
                }
            }
            // LT/RT page the persistent tab bar in the redesigned UI (see
            // org.harbor.ui.mock) - this screen has no tab bar yet.
            Nav.PREV_TAB, Nav.NEXT_TAB -> {}
        }
    }

    // ---- actions ------------------------------------------------------------

    /**
     * Start walking every system that has no folder yet.
     */
    private fun startSetup() {
        val needing = pages
            .filter { it.profile.source.provider == org.harbor.data.SourceSpec.FOLDER }
            .filter { FolderPicker.needsFolder(this, it.profile.source.roots) }
            .map { it.profile.id }
        if (needing.isEmpty()) {
            toast("Every system already has a folder")
            return
        }
        // Systems whose folder we already know go first: those are a single tap
        // because the picker can be opened straight at the folder.
        setupQueue = needing.sortedBy { id ->
            val roots = pages.firstOrNull { it.profile.id == id }?.profile?.source?.roots
            if (roots.isNullOrEmpty()) 1 else 0
        }
        setupTotal = needing.size
        android.util.Log.i("LH.Setup", "queue=" + setupQueue.joinToString())
        nextInSetup()
    }

    private fun nextInSetup() {
        val id = setupQueue.firstOrNull()
        if (id == null) {
            // Report against the library, not against the pickers: what matters
            // is how many games actually became playable, which is the thing the
            // name-matching in LibraryMerge is responsible for.
            reload()
            lifecycleScope.launch {
                kotlinx.coroutines.delay(400)
                val playable = pages.sumOf { p -> p.games.count { it.playable } }
                val total = pages.sumOf { it.games.size }
                toast("Setup done — $playable of $total games are now playable")
            }
            return
        }
        val name = pages.firstOrNull { it.profile.id == id }?.profile?.name ?: id
        val step = setupTotal - setupQueue.size + 1
        toast("Folder $step of $setupTotal — pick the folder for $name")
        chooseFolder(id)
    }

    private fun advanceSetup(justGranted: PlatformProfile) {
        if (setupQueue.isEmpty()) { reload(); return }
        setupQueue = setupQueue.drop(1)
        // Rebuild first so the next step sees the grant we just took.
        lifecycleScope.launch {
            val built = withContext(Dispatchers.IO) { buildPages() }
            pages = built
            nextInSetup()
        }
    }

    private fun chooseFolder(platformId: String) {
        pendingFolderFor = platformId
        // Open the picker at the folder we already know about, when we know one.
        val known = pages.firstOrNull { it.profile.id == platformId }
            ?.profile?.source?.roots?.firstOrNull()
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        folderPicker.launch(FolderPicker.intent(known, FolderPicker.defaultStart(this)))
    }

    /**
     * Records still backed by something on disk, matched exactly as the shelf
     * matches them - so cleanup can never remove a game the grid is showing.
     */
    /**
     * Work out which records have no game, and which platforms are in no state
     * to say. Three ways a scan can be blind, each seen for real on this device:
     * a shelf with no filesystem (Android apps), a root that failed to open, and
     * a grant that landed on ONE game's own subfolder - which made a 22-game
     * system scan exactly 1 and the other 21 look deleted.
     */
    private fun cleanupPlan(): CleanupPlan {
        val removals = mutableListOf<CleanupGroup>()
        val skipped = mutableListOf<Pair<String, String>>()
        for (pg in pages) {
            val records = app.library.forPlatform(pg.profile.id)
            if (records.isEmpty()) continue
            val found = pg.games.count { it.playable }
            val reason = when {
                pg.profile.source.provider != org.harbor.data.SourceSpec.FOLDER ->
                    "not scanned from a folder"
                pg.notes.any { it.contains("Cannot read", true) } ->
                    "its folder could not be read"
                found == 0 -> "the scan found nothing"
                records.size >= 6 && found * 3 < records.size ->
                    "the scan found only $found of ${records.size}"
                else -> null
            }
            if (reason != null) { skipped += pg.profile.name to reason; continue }
            val alive = pg.games.filter { it.playable }.mapNotNull { it.record?.key }.toSet()
            val dead = records.filter { it.key !in alive }
            if (dead.isNotEmpty()) {
                // Say which are duplicates - another record for the same game
                // survives - because that is the difference between "tidying an
                // import" and "losing a game", and the list is worthless if the
                // reader has to take it on faith. Unique titles sort first: they
                // are the ones worth looking at twice.
                val keptNames = records.filter { it.key in alive }.map { it.matchName }.toSet()
                val labelled = dead
                    .map { r -> r.title to (r.matchName in keptNames) }
                    .sortedWith(compareBy({ it.second }, { it.first.lowercase() }))
                    .map { (title, dup) -> if (dup) "$title (duplicate)" else title }
                removals += CleanupGroup(pg.profile.name, labelled, dead.map { it.key })
            }
        }
        return CleanupPlan(removals, skipped)
    }

    private fun cleanupLibrary() {
        val plan = cleanupPlan()
        if (plan.total == 0) { toast("Nothing to forget"); return }
        val doomed = plan.removals.flatMap { it.keys }.toSet()
        val keep = app.library.all().map { it.key }.filterNot { it in doomed }.toSet()
        val removed = app.library.forgetMissing(keep)
        reload()
        menuBack()
        toast("Forgot $removed record(s) with no game on disk")
    }

    /**
     * Fetch box art for everything that has none.
     *
     * Runs off the main thread and reports progress: this walks a 1.7 MB index
     * per platform and then one download per game, so a silent freeze would be
     * indistinguishable from a hang.
     */
    // ---- box art, fetched on its own ---------------------------------------

    /**
     * Games we have already looked up and failed to match.
     *
     * Without this the queue would re-request every art-less game on every
     * launch: the misses are permanent (libretro simply has no cover for that
     * title) so retrying them is pure network traffic and a status line that
     * never goes away. "Get missing box art" clears the list to force a retry.
     */
    private val artMissFile: java.io.File
        get() = java.io.File(getExternalFilesDir(null), "art_misses.txt")

    private fun artMisses(): MutableSet<String> = runCatching {
        if (artMissFile.isFile) artMissFile.readLines().filter { it.isNotBlank() }.toMutableSet()
        else mutableSetOf()
    }.getOrDefault(mutableSetOf())

    private fun rememberArtMisses(keys: Collection<String>) {
        if (keys.isEmpty()) return
        runCatching { artMissFile.appendText(keys.joinToString("\n", postfix = "\n")) }
    }

    /** Queue a platform for a background art pass. Safe to call repeatedly. */
    private fun queueArt(platformId: String) {
        if (!artQueue.contains(platformId)) artQueue.add(platformId)
        startArtWorker()
    }

    /**
     * One worker, one platform at a time, off the main thread.
     *
     * Deliberately serial: a handheld on hotel wifi does not benefit from eight
     * parallel index downloads, and a single ordered queue is what makes the
     * status line meaningful.
     */
    private fun startArtWorker() {
        if (artRunning) return
        artRunning = true
        lifecycleScope.launch {
            try {
                while (true) {
                    val id = artQueue.poll() ?: break
                    val page = pages.firstOrNull { it.profile.id == id } ?: continue
                    val misses = artMisses()
                    val targets = page.games
                        .filter { it.coverPath == null && (it.entry != null || it.record != null) }
                        .map { g ->
                            org.harbor.scrape.CoverScraper.Target(
                                key = g.record?.key ?: g.entry?.key ?: g.title,
                                platformId = page.profile.id,
                                platformName = page.profile.name,
                                title = g.title,
                            )
                        }
                        .filterNot { it.key in misses }
                    if (targets.isEmpty()) continue

                    artStatus = "Box art: ${page.profile.shortName}…"
                    val report = withContext(Dispatchers.IO) {
                        org.harbor.scrape.CoverScraper.scrape(
                            targets, app.library.mediaDir, app.config.steamGridDbKey,
                        ) { step ->
                            // Short form only: this sits in the hint bar beside
                            // Play, not in a dialog with room to explain.
                            runOnUiThread {
                                artStatus = "Box art: ${page.profile.shortName} " +
                                    step.substringAfterLast('(').substringBefore(')')
                                        .takeIf { it.contains(" of ") }.orEmpty()
                            }
                        }
                    }
                    applyArt(report, targets)
                    rememberArtMisses(
                        targets.map { it.key }.toSet() - report.found.map { it.key }.toSet()
                    )
                }
            } finally {
                artRunning = false
                artStatus = null
            }
        }
    }

    /** Attach fetched covers, creating records for scanned games that lack one. */
    private fun applyArt(
        report: org.harbor.scrape.CoverScraper.Report,
        targets: List<org.harbor.scrape.CoverScraper.Target>,
    ) {
        if (report.found.isEmpty()) return
        val byKey = targets.associateBy { it.key }
        val updates = report.found.mapNotNull { f ->
            val t = byKey[f.key] ?: return@mapNotNull null
            val existing = app.library.all().firstOrNull { it.key == f.key }
            (existing ?: org.harbor.data.GameRecord(
                key = f.key,
                platformId = t.platformId,
                title = t.title,
                uri = f.key.takeIf { it.startsWith("content://") },
            )).copy(coverPath = f.file.absolutePath)
        }
        if (updates.isNotEmpty()) { app.library.put(updates); reload() }
    }

    /** Queue every platform that still has art-less games. */
    private fun queueArtForAll() {
        val misses = artMisses()
        for (pg in pages) {
            val pending = pg.games.any {
                it.coverPath == null && (it.entry != null || it.record != null) &&
                    (it.record?.key ?: it.entry?.key ?: it.title) !in misses
            }
            if (pending) queueArt(pg.profile.id)
        }
    }

    /**
     * Force a full retry, including titles that previously found no match.
     *
     * The background pass skips known misses forever, which is right for
     * routine use and wrong when libretro has since added the cover - so the
     * manual action clears that memory and re-queues everything.
     */
    private fun scrapeCovers() {
        val cfg = app.config
        if (cfg.steamGridDbReplaceAll && !cfg.steamGridDbKey.isNullOrBlank()) {
            replaceAllArtFromSteamGridDb(cfg.steamGridDbKey!!)
            return
        }
        runCatching { artMissFile.delete() }
        val before = artQueue.size
        queueArtForAll()
        toast(
            if (artQueue.isEmpty() && !artRunning) "Every game already has box art"
            else "Fetching box art in the background" +
                (artQueue.size - before).let { if (it > 0) " — ${artQueue.size} system(s)" else "" }
        )
    }

    /**
     * Every game, refetched from SteamGridDB, overwriting whatever cover it
     * already has. Run as a blocking, visible pass rather than the quiet
     * background queue: it is a deliberate rewrite of the whole shelf, not a
     * gap-fill, and letting it interleave with the background worker risks
     * doubling up on the same SteamGridDB rate limit.
     */
    private fun replaceAllArtFromSteamGridDb(key: String) {
        if (busy != null) return
        val targets = pages.flatMap { pg ->
            pg.games.filter { it.entry != null || it.record != null }.map { g ->
                org.harbor.scrape.CoverScraper.Target(
                    key = g.record?.key ?: g.entry?.key ?: g.title,
                    platformId = pg.profile.id,
                    platformName = pg.profile.name,
                    title = g.title,
                )
            }
        }
        if (targets.isEmpty()) { toast("No games to fetch art for"); return }

        busy = "Replacing box art with SteamGridDB…"
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                org.harbor.scrape.CoverScraper.scrape(
                    targets, app.library.mediaDir, key, preferSteamGridDb = true,
                ) { step -> runOnUiThread { busy = step } }
            }
            applyArt(report, targets)
            busy = null
            toast(report.summary() + report.notes.firstOrNull()?.let { " — $it" }.orEmpty())
        }
    }

    /**
     * Text-entry for a single config value, reusing the same overlay the
     * launch-intent editor uses - keeping text entry in one place is what lets
     * every pad-driven screen in the app stay pad-driven.
     */
    // ---- a game's own menu (long-press) --------------------------------------

    private fun keyFor(g: DisplayGame): String = g.record?.key ?: g.entry?.key ?: g.title

    /** The record to edit, creating a minimal one for a scanned game that has
     *  never had a record before - the same pattern the scraper already uses. */
    private fun recordFor(pg: SystemPage, g: DisplayGame): org.harbor.data.GameRecord =
        g.record ?: org.harbor.data.GameRecord(
            key = keyFor(g),
            platformId = pg.profile.id,
            title = g.title,
            uri = g.entry?.uri?.toString(),
        )

    private fun renameGame(pg: SystemPage, g: DisplayGame, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty() || title == g.title) return
        app.library.put(listOf(recordFor(pg, g).copy(title = title)))
        reload()
        toast("Renamed to \"$title\"")
    }

    /**
     * Hides a game from the shelf without touching its file.
     *
     * A plain delete of the record would not stick for a scanned game: the
     * next rescan rebuilds a record-less DisplayGame straight from the file on
     * disk regardless. Marking it hidden is the only removal that survives a
     * rescan, which is why LibraryMerge filters on this flag rather than on
     * whether a record exists at all.
     */
    private fun hideGame(pg: SystemPage, g: DisplayGame) {
        app.library.put(listOf(recordFor(pg, g).copy(hidden = true)))
        reload()
        toast("Removed \"${g.title}\" from the shelf")
    }

    private fun openArtPicker(pg: SystemPage, g: DisplayGame) {
        artPickerFor = pg to g
        artPickerLoading = true
        artCandidates = emptyList()
        artPickerCursor = 0
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                org.harbor.scrape.CoverScraper.candidates(
                    g.title, pg.profile.name, app.config.steamGridDbKey,
                )
            }
            // The picker may have been dismissed while the search was in
            // flight; do not resurrect it with a stale game's results.
            if (artPickerFor?.second?.title == g.title) {
                artCandidates = list
                artPickerLoading = false
            }
        }
    }

    private fun applyArtCandidate(
        pg: SystemPage,
        g: DisplayGame,
        candidate: org.harbor.scrape.CoverScraper.ArtCandidate,
    ) {
        lifecycleScope.launch {
            busy = "Downloading cover…"
            val file = withContext(Dispatchers.IO) {
                org.harbor.scrape.CoverScraper.applyCandidate(
                    candidate, pg.profile.id, g.title, app.library.mediaDir,
                )
            }
            busy = null
            if (file == null) { toast("Could not download that image"); return@launch }
            app.library.put(listOf(recordFor(pg, g).copy(coverPath = file.absolutePath)))
            artPickerFor = null
            reload()
            toast("Cover updated")
        }
    }

    private fun editSteamGridDbKey() {
        askText(
            "SteamGridDB key",
            "Paste the key from steamgriddb.com › Preferences › API. Leave empty to remove it.",
            app.config.steamGridDbKey ?: "",
        ) { v ->
            app.config.steamGridDbKey = v
            reload()
            toast(if (v.isBlank()) "SteamGridDB key removed" else "SteamGridDB key saved")
        }
    }

    // ---- first-run setup ----------------------------------------------------

    private fun onboardingState(): OnboardingState {
        val cat = runCatching { app.catalogue.load().systems }.getOrDefault(emptyList())
        val have = pages.map { it.profile.id }.toSet()
        // Installed emulators the user has not added yet, plus whatever they
        // picked in this run - so the choice stays visible after it is made.
        val detected = cat
            .filter { it.id !in have || it.id == onboardPicked }
            .mapNotNull { sys ->
                app.catalogue.installedFor(sys).firstOrNull()?.let { sys to it.name }
            }
        val page = pages.firstOrNull { it.profile.id == onboardPicked }
        return OnboardingState(
            beaconExport = ImportSource.find()?.absolutePath,
            detected = detected,
            pickedId = onboardPicked,
            pickedName = page?.profile?.name
                ?: cat.firstOrNull { it.id == onboardPicked }?.name,
            pickedEmulator = cat.firstOrNull { it.id == onboardPicked }
                ?.let { app.catalogue.installedFor(it).firstOrNull()?.name },
            pickedHasFolder = page != null &&
                !FolderPicker.needsFolder(this, page.profile.source.roots),
            pickedGames = page?.games?.count { it.playable } ?: 0,
            systemsAdded = pages.size,
            gamesFound = pages.sumOf { pg -> pg.games.count { it.playable } },
            busy = busy != null,
        )
    }

    private fun onboardNext() {
        val order = Step.entries
        val i = order.indexOf(onboardStep ?: Step.WELCOME)
        onboardStep = order.getOrNull(i + 1) ?: Step.DONE
        onboardCursor = 0
    }

    private fun onboardBack() {
        val order = Step.entries
        val i = order.indexOf(onboardStep ?: Step.WELCOME)
        if (i <= 0) { finishOnboarding(); return }
        onboardStep = order[i - 1]
        onboardCursor = 0
    }

    private fun finishOnboarding() {
        app.config.setupComplete = true
        onboardStep = null
        onboardCursor = 0
        // Open on the system that was just set up. Landing on an unconfigured
        // bundled shelf reading "folder source has no roots" makes a successful
        // setup look like it failed.
        reload(landOn = onboardPicked)
    }

    private val onboardActions = object : OnboardingActions {
        override fun next() = onboardNext()
        override fun finish() = finishOnboarding()

        override fun importBeacon() {
            lifecycleScope.launch {
                busy = "Reading the Beacon export…"
                val r = withContext(Dispatchers.IO) { ImportSource.run(this@MainActivity) }
                busy = null
                toast(r.summary())
                reload()
            }
        }

        override fun pickSystem(system: org.harbor.data.CatalogueSystem) {
            if (pages.none { it.profile.id == system.id }) addSystem(system)
            onboardPicked = system.id
            reload()
            // Choosing the emulator IS this step; asking the user to then find
            // a Next button below a list of twelve is a step for its own sake.
            onboardNext()
        }

        override fun chooseFolderForPicked() {
            onboardPicked?.let { chooseFolder(it) }
        }

        override fun openCatalogue() {
            // Hand over to the real catalogue rather than building a second
            // one; the wizard is still underneath and returns when it closes.
            showSettings = true
            categoryIndex = SETTINGS_CATEGORIES.indexOfFirst { it.id == "add" }
                .coerceAtLeast(0)
            menuPath = emptyList()
            pane = Pane.CONTENT
        }
    }

    private fun runImport() {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { ImportSource.run(this@MainActivity) }
            toast(r.summary())
            reload()
        }
    }

    private fun launch(page: SystemPage, game: DisplayGame) {
        val entry = game.entry
        if (entry == null) {
            toast("\"${game.title}\" needs its folder re-granted in Harbor")
            return
        }
        if (entry.shortcutId != null && entry.shortcutPackage != null) {
            val la = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (la == null) { toast("Shortcuts unavailable"); return }
            runCatching {
                la.startShortcut(
                    entry.shortcutPackage, entry.shortcutId, null, null, Process.myUserHandle()
                )
            }.onFailure {
                toast("Could not start shortcut — Harbor must be the default launcher")
            }
            return
        }
        val target = LaunchIntentBuilder.Target(entry.uri, entry.id, game.title)
        when (val r = LaunchIntentBuilder.build(page.profile.launch, target)) {
            is LaunchIntentBuilder.Result.Unbuildable -> toast("Cannot launch: ${r.reason}")
            is LaunchIntentBuilder.Result.Ready ->
                runCatching { startActivity(r.intent) }
                    .onFailure { toast("Launch failed: ${it.message ?: it::class.simpleName}") }
        }
    }

    // ---- menu navigation ----------------------------------------------------

    private val drawerApps: List<Pair<String, String>>
        get() = org.harbor.provider.InstalledAppsProvider.installedApps(this)

    private fun openDrawer() { drawerCursor = 0; showDrawer = true }

    private fun launchApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { toast("That app has no launcher entry"); return }
        runCatching { startActivity(i) }
            .onFailure { toast("Could not open: ${it.message}") }
    }

    /** Category id, plus any drilled-into detail. */
    private fun currentPath(): List<String> =
        listOf(SETTINGS_CATEGORIES[categoryIndex].id) + menuPath

    private fun cursorFor(node: MenuNode): Int {
        val stored = menuCursors[node.id]
        val ok = node.selectable()
        if (ok.isEmpty()) return 0
        return stored?.takeIf { it in ok } ?: ok.first()
    }

    private fun setCursor(node: MenuNode, i: Int) {
        menuCursors = menuCursors + (node.id to i)
    }

    private fun moveMenu(nav: Nav) {
        val node = MenuTree(settingsState(), menuActions).nodeFor(currentPath())
        if (pane == Pane.RAIL) {
            when (nav) {
                Nav.UP -> categoryIndex = (categoryIndex - 1)
                    .coerceIn(0, SETTINGS_CATEGORIES.size - 1)
                Nav.DOWN -> categoryIndex = (categoryIndex + 1)
                    .coerceIn(0, SETTINGS_CATEGORIES.size - 1)
                // Right crosses into the content pane - the spatial move the
                // two-pane layout implies.
                Nav.RIGHT -> if (node.selectable().isNotEmpty()) pane = Pane.CONTENT
                else -> Unit
            }
            if (nav == Nav.UP || nav == Nav.DOWN) menuPath = emptyList()
            return
        }
        val ok = node.selectable()
        if (ok.isEmpty()) { pane = Pane.RAIL; return }
        val at = ok.indexOf(cursorFor(node)).coerceAtLeast(0)
        when (nav) {
            Nav.UP -> setCursor(node, ok[(at - 1).coerceAtLeast(0)])
            Nav.DOWN -> setCursor(node, ok[(at + 1).coerceAtMost(ok.size - 1)])
            // Left backs out: to the parent detail if we are in one, else to
            // the rail.
            Nav.LEFT -> menuBack()
            else -> Unit
        }
    }

    private fun activate(node: MenuNode, index: Int) {
        when (val item = node.items.getOrNull(index)) {
            is MenuItem.Submenu -> { menuForward = true; menuPath = menuPath + item.id }
            is MenuItem.Action -> if (item.enabled) item.run()
            is MenuItem.Toggle -> item.set(!item.on)
            is MenuItem.Choice -> item.cycle()
            else -> Unit
        }
    }

    private fun menuBack() {
        when {
            menuPath.isNotEmpty() -> { menuForward = false; menuPath = menuPath.dropLast(1) }
            pane == Pane.CONTENT -> pane = Pane.RAIL
            else -> showSettings = false
        }
    }

    private val menuActions = object : MenuTree.MenuActions {
        override fun import() = runImport()
        override fun setupFolders() { showSettings = false; startSetup() }
        override fun rescan() { reload(); toast("Rescanning…") }
        override fun cleanupLibrary() = this@MainActivity.cleanupLibrary()
        override fun scrapeCovers() = this@MainActivity.scrapeCovers()
        override fun editSteamGridDbKey() = this@MainActivity.editSteamGridDbKey()
        override fun setSteamGridDbReplaceAll(v: Boolean) {
            app.config.steamGridDbReplaceAll = v
            reload()
        }
        override fun chooseFolder(platformId: String) {
            showSettings = false
            this@MainActivity.chooseFolder(platformId)
        }
        override fun setEmulator(platformId: String, pkg: String) =
            this@MainActivity.setEmulator(platformId, pkg)
        override fun chooseApps(platformId: String) {
            appPickerFor = platformId; showAppPicker = true
        }
        override fun editIntent(platformId: String) = startEditing(platformId)
        override fun cycleAspect(platformId: String) = this@MainActivity.cycleAspect(platformId)
        override fun setEnabled(platformId: String, enabled: Boolean) =
            togglePlatform(platformId, enabled)
        override fun remove(platformId: String) {
            removePlatform(platformId)
            menuBack()
        }
        override fun addSystem(system: org.harbor.data.CatalogueSystem) =
            this@MainActivity.addSystem(system)
        override fun pickColorTheme(name: String?) = this@MainActivity.pickColorTheme(name)
        override fun importColorTheme() = this@MainActivity.importColorTheme()
        override fun openColorFolder() {
            toast("Colour themes live in " + app.colors.dir.absolutePath)
        }
        override fun openOtherApp(releasePackage: String) = this@MainActivity.openOtherApp(releasePackage)
    }

    // ---- settings -----------------------------------------------------------

    private fun settingsState(): SettingsState {
        val loaded = app.profiles.load()
        val rows = pages.map { pg ->
            PlatformRowState(
                id = pg.profile.id,
                name = pg.profile.name,
                games = pg.games.size,
                playable = pg.games.count { it.playable },
                verified = pg.profile.verified,
                needsFolder = pg.profile.source.provider == org.harbor.data.SourceSpec.FOLDER &&
                    FolderPicker.needsFolder(this, pg.profile.source.roots),
                isAppShelf = pg.profile.source.provider ==
                    org.harbor.data.SourceSpec.INSTALLED_APPS,
                aspectRatio = pg.profile.aspectRatio,
                emulators = emulatorOptions(pg.profile),
                currentEmulator = currentEmulatorLabel(pg.profile),
                enabled = pg.profile.enabled,
                problem = pg.problem,
            )
        }
        val have = rows.map { it.id }.toSet()
        val cat = app.catalogue.load().systems
            .sortedBy { it.year }
            .map { sys ->
                CatalogueRowState(
                    system = sys,
                    alreadyAdded = sys.id in have,
                    installedEmulator = app.catalogue.installedFor(sys).firstOrNull()?.name,
                )
            }
        val colors = app.colors.load()   // one read; this runs on every keypress
        return SettingsState(
            platforms = rows,
            catalogue = cat,
            colorThemes = colors.themes.map { it.name },
            activeColorTheme = app.config.colorTheme
                ?: org.harbor.theme.ColorThemes.DEFAULT_NAME,
            colorProblems = colors.problems,
            gamesTotal = pages.sumOf { it.games.size },
            gamesPlayable = pages.sumOf { pg -> pg.games.count { it.playable } },
            problems = loaded.problems,
            cleanupPlan = cleanupPlan(),
            missingArt = pages.sumOf { pg ->
                pg.games.count { it.coverPath == null && (it.entry != null || it.record != null) }
            },
            steamGridDbKey = app.config.steamGridDbKey,
            steamGridDbReplaceAll = app.config.steamGridDbReplaceAll,
        )
    }

    /** The installed_apps platform (the Android shelf), if one exists. */
    /** The shelf the picker is editing, or the only one if nothing is pinned. */
    private fun appShelfProfile(): PlatformProfile? {
        val all = app.profiles.load().profiles
            .filter { it.source.provider == org.harbor.data.SourceSpec.INSTALLED_APPS }
        return appPickerFor?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
    }

    /** The ratios real box art actually comes in. */
    private val aspectPresets = listOf("3:4", "1:1", "8:7", "3:5", "2:3", "1:2")

    private fun cycleAspect(id: String) {
        val p = loadProfileById(id) ?: return
        val i = aspectPresets.indexOf(p.aspectRatio)
        val next = aspectPresets[(i + 1) % aspectPresets.size]
        app.profiles.save(p.copy(aspectRatio = next))?.let { toast(it) }
        reload()
    }

    // ---- intent editor ------------------------------------------------------

    private fun startEditing(id: String) {
        val p = loadProfileById(id) ?: return
        editingId = id
        editingSpec = p.launch
        showSettings = false
    }

    /** Choosing which installed app is this console's emulator. */
    private fun packageNode(): MenuNode = MenuNode(
        id = "pickpkg",
        title = "Choose emulator",
        subtitle = "Any installed app can be a console's player",
        items = org.harbor.provider.InstalledAppsProvider.installedApps(this).map { (pkg, label) ->
            MenuItem.Action(label, pkg) {
                // Keep only the package: a bare package lets Android resolve the
                // activity, which is right far more often than pinning a class.
                editingSpec = editingSpec?.copy(component = pkg)
                editingPickPackage = false
            }
        },
    )

    private fun clampEditorCursor(node: MenuNode): Int {
        val ok = node.selectable()
        if (ok.isEmpty()) return 0
        return if (editorCursor in ok) editorCursor else ok.first()
    }

    private fun moveEditor(nav: Nav) {
        val node = intentNode(editingId ?: return, editingSpec ?: return)
        val ok = node.selectable()
        if (ok.isEmpty()) return
        val at = ok.indexOf(clampEditorCursor(node)).coerceAtLeast(0)
        editorCursor = when (nav) {
            Nav.UP, Nav.LEFT -> ok[(at - 1).coerceAtLeast(0)]
            Nav.DOWN, Nav.RIGHT -> ok[(at + 1).coerceAtMost(ok.size - 1)]
            else -> ok[at]
        }
    }

    /** Presses whatever key the on-screen keyboard's cursor is currently on. */
    private fun pressKey() {
        val key = keyboardRows(promptSymbols).getOrNull(kbRow)?.getOrNull(kbCol) ?: return
        when (key) {
            Key.Shift -> promptShift = !promptShift
            // Cursor resets rather than trying to carry a position across two
            // different-shaped layouts, which would as often land on the
            // wrong key as the right one.
            Key.Symbols -> { promptSymbols = !promptSymbols; kbRow = 0; kbCol = 0 }
            Key.Done -> { promptApply?.invoke(promptText.trim()); prompt = null }
            Key.Copy -> {
                clipboard.setPrimaryClip(ClipData.newPlainText("Harbor", promptText))
                toast("Copied")
            }
            Key.Paste -> {
                val pasted = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
                if (pasted.isNullOrEmpty()) {
                    toast("Clipboard is empty")
                } else {
                    val (t, c) = insertAt(promptText, promptCursor, pasted)
                    promptText = t; promptCursor = c
                }
            }
            else -> {
                val (t, c) = applyKey(key, promptText, promptCursor, promptShift)
                promptText = t; promptCursor = c
            }
        }
    }

    private val clipboard: ClipboardManager
        get() = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

    private fun askText(title: String, hint: String?, current: String, apply: (String) -> Unit) {
        promptApply = apply
        prompt = Triple(title, hint, current)
        promptText = current
        promptCursor = current.length
        promptSymbols = false
        promptShift = false
        kbRow = 0
        kbCol = 0
    }

    private fun intentNode(id: String, spec: org.harbor.data.LaunchSpec): MenuNode {
        val st = editorState(id, spec)
        val acts = st.activities
        return IntentMenu.node(
            platformName = st.platformName,
            spec = spec,
            verified = st.verified,
            testGame = st.testGame,
            preview = st.preview,
            a = object : IntentMenu.Actions {
                override fun pickPackage() { editingPickPackage = true }
                override fun promptText(field: IntentMenu.TextField, current: String) {
                    when (field) {
                        IntentMenu.TextField.ACTION -> askText("Action",
                            "e.g. android.intent.action.VIEW", current) {
                            editingSpec = editingSpec?.copy(action = it)
                        }
                        IntentMenu.TextField.MIME -> askText("MIME type",
                            "Leave blank for none", current) {
                            editingSpec = editingSpec?.copy(type = it.ifBlank { null })
                        }
                        IntentMenu.TextField.ROM_EXTRA -> askText("Extra name",
                            "The extra that receives the game's URI", current) {
                            editingSpec = editingSpec?.copy(romExtra = it.ifBlank { null })
                        }
                        else -> Unit
                    }
                }
                override fun cycleActivity() {
                    editingSpec = editingSpec?.let { IntentMenu.withActivity(it, acts) }
                }
                override fun cycleAction() {
                    editingSpec = editingSpec?.let {
                        it.copy(action = IntentMenu.cycled(IntentMenu.ACTIONS, it.action))
                    }
                }
                override fun cycleRomMode() {
                    editingSpec = editingSpec?.let {
                        it.copy(romMode = IntentMenu.cycled(IntentMenu.ROM_MODES, it.romMode))
                    }
                }
                override fun toggleFlag(flag: String) {
                    editingSpec = editingSpec?.let {
                        it.copy(flags = if (flag in it.flags) it.flags - flag else it.flags + flag)
                    }
                }
                override fun addExtra() {
                    editingSpec = editingSpec?.let {
                        it.copy(extras = it.extras +
                            org.harbor.data.ExtraSpec("key", "string", "{file}"))
                    }
                }
                override fun editExtra(index: Int, part: IntentMenu.ExtraPart) {
                    val e = editingSpec?.extras?.getOrNull(index) ?: return
                    when (part) {
                        IntentMenu.ExtraPart.KEY -> askText("Extra key", null, e.key) { v ->
                            editingSpec = editingSpec?.let {
                                IntentMenu.withExtraAt(it, index) { x -> x.copy(key = v) }
                            }
                        }
                        IntentMenu.ExtraPart.VALUE -> askText("Extra value",
                            "{file}, {id} and {title} are filled in at launch", e.value) { v ->
                            editingSpec = editingSpec?.let {
                                IntentMenu.withExtraAt(it, index) { x -> x.copy(value = v) }
                            }
                        }
                    }
                }
                override fun cycleExtraType(index: Int) {
                    val types = listOf("string", "int", "long", "bool")
                    editingSpec = editingSpec?.let {
                        IntentMenu.withExtraAt(it, index) { x ->
                            x.copy(type = IntentMenu.cycled(types, x.type))
                        }
                    }
                }
                override fun removeExtra(index: Int) {
                    editingSpec = editingSpec?.let {
                        it.copy(extras = it.extras.filterIndexed { j, _ -> j != index })
                    }
                }
                override fun test() = testLaunch()
                override fun save() = saveEditing()
            },
        )
    }

    /** Rows for the Android app-shelf picker. */
    private fun appPickerRows(): List<AppChoice> {
        val chosen = appShelfProfile()?.source?.packages.orEmpty().toSet()
        return org.harbor.provider.InstalledAppsProvider.installedApps(this)
            .map { (pkg, label) -> AppChoice(pkg, label, pkg in chosen) }
            .sortedWith(compareByDescending<AppChoice> { it.chosen }
                .thenBy { it.label.lowercase() })
    }

    private fun editorState(id: String, spec: org.harbor.data.LaunchSpec): IntentEditorState {
        val page = pages.firstOrNull { it.profile.id == id }
        val game = page?.games?.firstOrNull { it.playable }
        val pkg = spec.component?.substringBefore('/').orEmpty()
        val target = LaunchIntentBuilder.Target(
            game?.entry?.uri, game?.entry?.id, game?.title ?: "Example Game"
        )
        val preview = when (val r = LaunchIntentBuilder.build(spec, target)) {
            is LaunchIntentBuilder.Result.Ready -> LaunchIntentBuilder.describe(r.intent)
            is LaunchIntentBuilder.Result.Unbuildable -> "Cannot build: ${r.reason}"
        }
        return IntentEditorState(
            platformId = id,
            platformName = page?.profile?.name ?: id,
            spec = spec,
            verified = loadProfileById(id)?.verified == true,
            testGame = game?.title,
            preview = preview,
            activities = if (pkg.isBlank()) emptyList() else exportedActivities(this, pkg),
            installedApps = org.harbor.provider.InstalledAppsProvider.installedApps(this),
        )
    }

    private fun saveEditing() {
        val id = editingId ?: return
        val spec = editingSpec ?: return
        val p = loadProfileById(id) ?: return
        // Editing the launch invalidates any previous verification: the thing
        // that was proven to work is not the thing about to run.
        val err = app.profiles.save(p.copy(launch = spec, verified = false))
        if (err != null) { toast(err); return }
        editingId = null; editingSpec = null
        reload()
        toast("Saved. Test it to mark it verified.")
    }

    private fun testLaunch() {
        val id = editingId ?: return
        val spec = editingSpec ?: return
        val page = pages.firstOrNull { it.profile.id == id } ?: return
        val game = page.games.firstOrNull { it.playable } ?: return
        val e = game.entry ?: return
        val target = LaunchIntentBuilder.Target(e.uri, e.id, game.title)
        when (val r = LaunchIntentBuilder.build(spec, target)) {
            is LaunchIntentBuilder.Result.Unbuildable -> toast("Cannot launch: ${r.reason}")
            is LaunchIntentBuilder.Result.Ready -> {
                runCatching { startActivity(r.intent) }
                    .onSuccess {
                        verifyFor = id to game.title
                        // Persist BEFORE the emulator takes over: if this
                        // process is killed we still know what to ask.
                        prefs.edit()
                            .putString(KEY_VERIFY_ID, id)
                            .putString(KEY_VERIFY_TITLE, game.title)
                            .putString(KEY_VERIFY_SPEC, specJson(spec))
                            .apply()
                    }
                    .onFailure { toast("Launch failed: ${it.message}") }
            }
        }
    }

    /**
     * The user's answer is the only thing that sets `verified`. Never infer it
     * from the app having started - a wrong intent can leave an emulator sitting
     * on its own menu looking perfectly healthy.
     */
    private fun answerVerify(loaded: Boolean) {
        val (id, _) = verifyFor ?: return
        verifyFor = null
        // The in-memory copy is gone if the process was killed while the
        // emulator ran, so fall back to what we persisted.
        val spec = editingSpec ?: prefs.getString(KEY_VERIFY_SPEC, null)?.let(::specFromJson)
        prefs.edit()
            .remove(KEY_VERIFY_ID).remove(KEY_VERIFY_TITLE).remove(KEY_VERIFY_SPEC)
            .apply()
        val p = loadProfileById(id) ?: return
        if (loaded && spec != null) {
            val err = app.profiles.save(p.copy(launch = spec, verified = true))
            if (err != null) { toast(err); return }
            editingId = null; editingSpec = null
            reload()
            toast("Verified — ${p.name} launches correctly")
        } else {
            toast("Left unverified. Adjust the intent and test again.")
        }
    }

    private fun specJson(spec: org.harbor.data.LaunchSpec): String =
        kotlinx.serialization.json.Json.encodeToString(
            org.harbor.data.LaunchSpec.serializer(), spec)

    private fun specFromJson(s: String): org.harbor.data.LaunchSpec? = runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(org.harbor.data.LaunchSpec.serializer(), s)
    }.getOrNull()

    private fun toggleApp(pkg: String, add: Boolean) {
        val p = appShelfProfile() ?: run {
            toast("Add an app-based system first, such as Android or Windows")
            return
        }
        val next = if (add) (p.source.packages + pkg).distinct()
                   else p.source.packages - pkg
        app.profiles.save(p.copy(source = p.source.copy(packages = next)))?.let { toast(it) }
    }

    private fun togglePlatform(id: String, enabled: Boolean) {
        val p = loadProfileById(id) ?: return
        app.profiles.save(p.copy(enabled = enabled))?.let { toast(it) }
        reload()
    }

    private fun removePlatform(id: String) {
        // Only the profile goes; imported games and art stay in the library so
        // re-adding the system does not mean re-importing it.
        app.profiles.delete(id)
        reload()
        toast("Removed. Imported games and art are kept.")
    }

    /** The package a profile currently launches, if it names one. */
    private fun launchPackage(p: PlatformProfile): String? =
        p.launch.component?.substringBefore('/')?.takeIf { it.isNotBlank() }

    private fun appLabel(pkg: String): String? = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun currentEmulatorLabel(p: PlatformProfile): String? {
        val pkg = launchPackage(p) ?: return null
        val known = app.catalogue.load().systems.firstOrNull { it.id == p.id }
            ?.emulators?.firstOrNull { it.`package` == pkg }?.name
        val label = known ?: appLabel(pkg) ?: pkg
        return if (appLabel(pkg) == null) "$label — not installed" else label
    }

    private fun emulatorOptions(p: PlatformProfile): List<EmulatorOption> {
        val current = launchPackage(p)
        val known = app.catalogue.load().systems.firstOrNull { it.id == p.id }?.emulators.orEmpty()
        val options = known.map { e ->
            EmulatorOption(
                pkg = e.`package`,
                name = e.name,
                installed = appLabel(e.`package`) != null,
                verified = e.verified,
                inUse = e.`package` == current,
            )
        }.toMutableList()
        // Whatever it is set to belongs on the list even when the catalogue has
        // never heard of it - otherwise a hand-configured emulator looks unset.
        if (current != null && options.none { it.pkg == current }) {
            options += EmulatorOption(
                pkg = current,
                name = appLabel(current) ?: current,
                installed = appLabel(current) != null,
                verified = p.verified,
                inUse = true,
            )
        }
        return options.sortedWith(
            compareByDescending<EmulatorOption> { it.inUse }
                .thenByDescending { it.installed }
                .thenByDescending { it.verified }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Point a system at a different app.
     *
     * Takes the whole launch contract from the catalogue, not just the package:
     * Dolphin needs an extra named AutoStartFile while X1-BOX wants the ROM as
     * the data URI, so swapping only the package would produce a profile that
     * launches the app and never passes the game.
     */
    private fun setEmulator(platformId: String, pkg: String) {
        val profile = loadProfileById(platformId) ?: return
        val sys = app.catalogue.load().systems.firstOrNull { it.id == platformId }
        val emu = sys?.emulators?.firstOrNull { it.`package` == pkg }
        val spec = emu?.launch
            ?: profile.launch.copy(component = pkg)   // unknown app: keep the rest
        val err = app.profiles.save(
            profile.copy(launch = spec, verified = emu?.verified == true)
        )
        if (err != null) { toast(err); return }
        reload()
        menuBack()
        toast(
            if (emu?.verified == true) "Using ${emu.name}"
            else "Using ${emu?.name ?: appLabel(pkg) ?: pkg} — test a game to confirm it works"
        )
    }

    private fun addSystem(system: org.harbor.data.CatalogueSystem) {
        // Ids are not enough to spot a duplicate: an imported Dreamcast arrives
        // as "dc" while the catalogue calls it "dreamcast", and the result was
        // two shelves both labelled DC. Compare the names as the user reads
        // them - "Sega Dreamcast" and "Sega - Dreamcast" are one console.
        val already = app.profiles.declared().firstOrNull {
            it.id == system.id ||
                org.harbor.data.normaliseTitle(it.name) ==
                org.harbor.data.normaliseTitle(system.name)
        }
        if (already != null && already.id != system.id) {
            toast("${system.name} is already here as \"${already.name}\"")
            return
        }
        val emu = app.catalogue.installedFor(system).firstOrNull()
        val order = (pages.maxOfOrNull { it.profile.order } ?: 0) + 1
        app.profiles.save(app.catalogue.profileFor(system, emu, order))?.let { toast(it) }
        reload()
        toast(
            if (emu == null) "Added ${system.name} — set its emulator and folder in Settings"
            else "Added ${system.name} using ${emu.name}"
        )
    }

    private fun pickColorTheme(name: String?) {
        val err = app.config.set(
            org.harbor.data.LauncherConfig.KEY_COLOR_THEME,
            name?.takeIf { it != org.harbor.theme.ColorThemes.DEFAULT_NAME },
        )
        if (err != null) { toast(err); return }
        colorEpoch++
        toast(if (name == null) "Using the default colours" else "Colours: " + name)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val KEY_VERIFY_ID = "verify_id"
        const val KEY_VERIFY_TITLE = "verify_title"
        const val KEY_VERIFY_SPEC = "verify_spec"
    }
}
