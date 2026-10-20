package it.palsoftware.pastiera.settings

import it.palsoftware.pastiera.apps.appLabel
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.modules.FluxModules
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.size

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import it.palsoftware.pastiera.apps.AppPickerDialog
import it.palsoftware.pastiera.gaming.GameLibrary
import it.palsoftware.pastiera.gaming.GameProfile
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GameApps
import it.palsoftware.pastiera.gaming.FluxLayout
import it.palsoftware.pastiera.gaming.DualScreens
import it.palsoftware.pastiera.gaming.DolphinPads
import it.palsoftware.pastiera.gaming.GameFiles
import it.palsoftware.pastiera.gaming.GameIds
import it.palsoftware.pastiera.gaming.TrackpadRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gaming mode's settings, the same from its tile and the Games app's ⚙: switched on or off, what
 * it still needs, a search of gaming's own settings, then a page per group (the controls, where
 * games come from, the Games app, the phone). The games and their apps themselves are set up in
 * the Games app, a long-press on each tile.
 */
@Composable
fun GameModeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(GameProfiles.enabled(context)) }
    FluxScreenScaffold(stringResource(R.string.game_mode_title), onBack, modifier) {
        FluxSwitchRow(
            linkId = "main.root.game_mode",
            title = stringResource(R.string.game_mode_title),
            description = stringResource(R.string.game_mode_switch_description),
            checked = enabled
        ) { on ->
            enabled = on
            FluxModules.setOn(context, FluxModule.GAMING, on)
        }
        // What gaming mode still needs, each a tap from its fix
        if (enabled) ReadinessCard(FluxModule.GAMING)
        SettingsSearchArea(
            fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            scope = setOf(SettingsDestination.GameMode),
            hint = stringResource(R.string.game_mode_search)
        ) {
            val open = { destination: SettingsDestination -> openSettingsPage(context, SettingsPage(destination)) }
            SettingsCategoryRow(
                icon = androidx.compose.material.icons.Icons.Filled.Keyboard,
                title = stringResource(R.string.game_mode_controls_section),
                description = stringResource(R.string.game_mode_controls_description),
                onClick = { open(SettingsDestination.GameControls) }
            )
            SettingsCategoryRow(
                icon = androidx.compose.material.icons.Icons.Filled.Folder,
                title = stringResource(R.string.game_mode_sources_section),
                description = stringResource(R.string.game_mode_sources_description),
                onClick = { open(SettingsDestination.GameSources) }
            )
            SettingsCategoryRow(
                icon = androidx.compose.material.icons.Icons.Filled.Apps,
                title = stringResource(R.string.game_mode_games_app_section),
                description = stringResource(R.string.game_mode_games_app_description),
                onClick = { open(SettingsDestination.GamesApp) }
            )
            SettingsCategoryRow(
                icon = androidx.compose.material.icons.Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.game_mode_phone_section),
                description = stringResource(R.string.game_mode_phone_description),
                onClick = { open(SettingsDestination.GamePhone) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Gaming mode's controls: the keys as a controller, and what the keyboard can and can't do. */
@Composable
internal fun GameControlsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var remapping by remember { mutableStateOf(false) }
    if (remapping) {
        androidx.activity.compose.BackHandler { remapping = false }
        GameLayoutRemapper(modifier) { remapping = false }
        return
    }
    FluxScreenScaffold(stringResource(R.string.game_mode_controls_section), onBack, modifier) {
        FluxActionRow(
            linkId = "game_mode.layout",
            icon = "\u2328",
            title = stringResource(R.string.game_mode_layout_title),
            description = stringResource(R.string.game_remap_row_description)
        ) { remapping = true }
        // The keyboard's limits, and what plays best: a controller made for the phone
        FluxNote(stringResource(R.string.game_mode_disclaimer))
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Where games come from: launchers and folders, Xbox's cloud, or added by hand. */
@Composable
internal fun GameSourcesScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(GameProfiles.all(context)) }
    var editing by remember { mutableStateOf<GameProfile?>(null) }
    var importing by remember { mutableStateOf(false) }
    var gameNativeSetup by remember { mutableStateOf(false) }
    var addingOwn by remember { mutableStateOf(false) }
    var addingXbox by remember { mutableStateOf(false) }
    val xboxApp: String? = remember { it.palsoftware.pastiera.gaming.XboxCloud.app(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun refresh() { profiles = GameProfiles.all(context) }
    // The game folders read again (games found join the list, not set up yet), and games named
    // by their file given their titles once their IDs are read
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (it.palsoftware.pastiera.adb.AdbShell.available()) it.palsoftware.pastiera.gaming.GameFolders.games(context)
            else GameProfiles.addDetected(context, GameLibrary.installedGames(context))
            GameProfiles.retitle(context)
        }
        refresh()
    }

    editing?.let { profile ->
        GameProfileEditor(profile, onDone = { saved ->
            saveEditedProfile(context, saved)
            editing = null
            refresh()
        }, onDelete = {
            deleteProfile(context, profile)
            editing = null
            refresh()
        })
        return
    }

    FluxScreenScaffold(stringResource(R.string.game_mode_sources_section), onBack, modifier) {
        // One way in for every game: the launchers' and folders' games, or one by hand
        FluxActionRow(
            linkId = "game_mode.add_game",
            icon = "+",
            title = stringResource(R.string.game_mode_import),
            description = stringResource(R.string.game_mode_import_description)
        ) { importing = true }
        // The app a game last ran in: a launcher may play its games in a part of its own, which a
        // profile then needs as one of its apps
        val seen = remember { it.palsoftware.pastiera.gaming.GameMode.lastApp() }
        if (seen != null && profiles.isNotEmpty() && profiles.none { p -> p.packages.any { GameProfiles.covers(it, seen) } }) {
            var addingSeen by remember { mutableStateOf(false) }
            FluxActionRow(
                linkId = null,
                icon = "+",
                title = stringResource(R.string.game_mode_seen_title, context.appLabel(seen)),
                description = stringResource(R.string.game_mode_seen_description, seen)
            ) { addingSeen = true }
            if (addingSeen) {
                AlertDialog(
                    onDismissRequest = { addingSeen = false },
                    title = { Text(stringResource(R.string.game_mode_seen_title, context.appLabel(seen))) },
                    text = {
                        Column {
                            profiles.filter { !it.isGame }.forEach { profile ->
                                Text(profile.name, modifier = Modifier.fillMaxWidth().clickable {
                                    GameProfiles.save(context, profile.copy(packages = profile.packages + seen))
                                    refresh()
                                    addingSeen = false
                                }.padding(vertical = 10.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { addingSeen = false }) { Text(stringResource(R.string.cancel)) } }
                )
            }
        }
        FluxActionRow(
            linkId = "game_mode.folders",
            icon = "+",
            title = stringResource(R.string.game_mode_folders),
            description = stringResource(R.string.game_mode_folders_description)
        ) {
            scope.launch {
                val made = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameFolders.create() }
                android.widget.Toast.makeText(
                    context, if (made) R.string.game_mode_folders_made else R.string.adb_not_running, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        CustomGameFolders()
        // The official GameHub can only be opened; GameHub Lite starts the game itself
        val gameHubLite = remember { it.palsoftware.pastiera.gaming.GameFolders.gameHubLiteMissing(context) }
        if (gameHubLite) FluxNote(stringResource(R.string.game_mode_gamehub_lite_note))
        // GameHub Lite has no Export for frontend: the official GameHub's, its games started in Lite
        val gameHubOfficial = remember { it.palsoftware.pastiera.gaming.GameFolders.gameHubOfficialMissing(context) }
        if (gameHubOfficial) FluxNote(stringResource(R.string.game_mode_gamehub_official_note))
        val bothGameHubs = remember { it.palsoftware.pastiera.gaming.GameFolders.bothGameHubs(context) }
        if (bothGameHubs) {
            var inLite by remember { mutableStateOf(it.palsoftware.pastiera.gaming.GameFolders.gameHubInLite(context)) }
            val gameHubScope = androidx.compose.runtime.rememberCoroutineScope()
            val options = listOf(stringResource(R.string.game_mode_gamehub_in_lite), stringResource(R.string.game_mode_gamehub_in_official))
            ChoiceRow(stringResource(R.string.game_mode_gamehub_games_in), options[if (inLite) 0 else 1], options) { index ->
                inLite = index == 0
                val app = context.applicationContext
                gameHubScope.launch(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameFolders.setGameHubInLite(app, index == 0) }
            }
        }
        // Xbox Cloud Gaming's games, from its catalogue, each a game of its own
        if (xboxApp != null) FluxActionRow(
            linkId = "game_mode.xbox_add",
            icon = "+",
            title = stringResource(R.string.game_mode_xbox_add),
            description = stringResource(R.string.game_mode_xbox_add_description)
        ) { addingXbox = true }
        // Signing in to Xbox (a plain browser page, keys typing), or out once signed in
        val xbox = rememberXboxAccount()
        FluxActionRow(
            linkId = null,
            icon = "\u2192",
            title = stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out else R.string.game_mode_xbox_sign_in),
            description = stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out_description else R.string.game_mode_xbox_sign_in_description)
        ) { xbox.click() }
        // Which cloud games the catalogue lists: the ones the Game Pass plan plays
        XboxPlanRow()
        Spacer(modifier = Modifier.height(16.dp))
    }

    val pickGame: (GameLibrary.Game) -> Unit = { game ->
        importing = false
        addingOwn = false
        // Already found by itself: that one, set up now; else a new one with its app's settings
        editing = GameProfiles.profileFor(context, game)
        refresh()
        if (game.packageName == it.palsoftware.pastiera.gaming.GameNativeBridge.PACKAGE) gameNativeSetup = true
    }
    if (importing) GameImportDialog(onPicked = pickGame, onByHand = { importing = false; addingOwn = true }, onDismiss = { importing = false })
    if (addingOwn) OwnGameDialog(onAdded = pickGame, onDismiss = { addingOwn = false })
    if (addingXbox && xboxApp != null) XboxCloudDialog(xboxApp, onPicked = { game -> addingXbox = false; pickGame(game) }, onDismiss = { addingXbox = false })
    if (gameNativeSetup) {
        AlertDialog(
            onDismissRequest = { gameNativeSetup = false },
            title = { Text(stringResource(R.string.game_mode_gamenative_save)) },
            text = { Text(stringResource(R.string.game_mode_gamenative_steps)) },
            confirmButton = {
                TextButton(onClick = {
                    gameNativeSetup = false
                    context.packageManager.getLaunchIntentForPackage(it.palsoftware.pastiera.gaming.GameNativeBridge.PACKAGE)
                        ?.let { launch -> runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }) { Text(stringResource(R.string.game_mode_gamenative_open)) }
            },
            dismissButton = { TextButton(onClick = { gameNativeSetup = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** The Games app: in the app list, moved through with keys, its games in the quick launcher and on the home screen. */
@Composable
internal fun GamesAppSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    FluxScreenScaffold(stringResource(R.string.game_mode_games_app_section), onBack, modifier) {
        FluxActionRow(
            linkId = "game_mode.open_games",
            icon = "\u25A6",
            title = stringResource(R.string.games_app_open),
            description = stringResource(R.string.games_app_open_description)
        ) {
            // Back to the Games app when it opened these settings, else opened
            context.startActivity(android.content.Intent(context, it.palsoftware.pastiera.gaming.GamesActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        // The games in the home screen's app list too, as an app of their own (Niagara's list)
        var inAppList by remember { mutableStateOf(it.palsoftware.pastiera.gaming.GamesActivity.enabled(context)) }
        FluxSwitchRow(
            linkId = "game_mode.games_app",
            title = stringResource(R.string.games_app_switch),
            description = stringResource(R.string.games_app_switch_description),
            checked = inAppList
        ) { on ->
            inAppList = on
            it.palsoftware.pastiera.gaming.GamesActivity.setEnabled(context, on)
        }
        var controllerKeys by remember { mutableStateOf(it.palsoftware.pastiera.gaming.GamesActivity.controllerKeys(context)) }
        FluxSwitchRow(
            linkId = "game_mode.games_app_controller_keys",
            title = stringResource(R.string.games_app_controller_keys),
            description = stringResource(R.string.games_app_controller_keys_description),
            checked = controllerKeys
        ) { on ->
            controllerKeys = on
            it.palsoftware.pastiera.gaming.GamesActivity.setControllerKeys(context, on)
        }
        FluxActionRow(
            linkId = "game_mode.pin_all",
            icon = "+",
            title = stringResource(R.string.games_pin_all),
            description = stringResource(R.string.games_pin_all_description)
        ) { it.palsoftware.pastiera.gaming.GamesActivity.pinAll(context) }
        // The games in the quick launcher too, as apps are
        var inLauncher by remember { mutableStateOf(it.palsoftware.pastiera.commands.GameCommandSource.enabled(context)) }
        FluxSwitchRow(
            linkId = "game_mode.quick_launcher",
            title = stringResource(R.string.game_mode_quick_launcher),
            description = stringResource(R.string.game_mode_quick_launcher_description),
            checked = inLauncher
        ) { on ->
            inLauncher = on
            it.palsoftware.pastiera.commands.GameCommandSource.setEnabled(context, on)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The phone while gaming: its own game mode, and Shell access, which keys and touches reach games through. */
@Composable
internal fun GamePhoneScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    FluxScreenScaffold(stringResource(R.string.game_mode_phone_section), onBack, modifier) {
        PhoneGameModeRow()
        ShellAccessRow(R.string.shell_access_for_gaming)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A game you add yourself, like GameNative's and GameHub's custom games: its name, the launcher
 * or emulator you added it to, and its file (an emulator's game, a launcher's exported file) or ID.
 */
@Composable
internal fun OwnGameDialog(onAdded: (GameLibrary.Game) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val folders = it.palsoftware.pastiera.gaming.GameFolders
    val players = remember { folders.installedPlayers(context) }
    var name by remember { mutableStateOf("") }
    var player by remember { mutableStateOf(players.firstOrNull()) }
    var file by remember { mutableStateOf<android.net.Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var id by remember { mutableStateOf("") }
    val stores = listOf("STEAM" to "Steam", "EPIC" to "Epic", "GOG" to "GOG", "AMAZON" to "Amazon", "CUSTOM_GAME" to stringResource(R.string.game_mode_own_game_custom_store), "LOCAL" to "GameHub")
    var store by remember { mutableStateOf(stores.first()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        file = uri
        fileName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (name.isBlank()) name = fileName?.substringBeforeLast('.').orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_own_game)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (players.isEmpty()) {
                    Text(stringResource(R.string.game_mode_own_game_none))
                    return@Column
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.game_mode_profile_name)) }, modifier = Modifier.fillMaxWidth())
                ChoiceRow(stringResource(R.string.game_mode_own_game_added_to), player.orEmpty(), players) { player = players[it] }
                val launcher = player?.let { folders.isLauncher(it) } == true
                TextButton(onClick = { runCatching { picker.launch(arrayOf("*/*")) } }) {
                    Text(fileName ?: stringResource(if (launcher) R.string.game_mode_own_game_export_file else R.string.game_mode_own_game_file))
                }
                if (launcher && file == null) {
                    OutlinedTextField(value = id, onValueChange = { id = it.trim() }, singleLine = true,
                        label = { Text(stringResource(R.string.game_mode_own_game_id)) }, modifier = Modifier.fillMaxWidth())
                    ChoiceRow(stringResource(R.string.game_mode_own_game_store), store.second, stores.map { it.second }) { store = stores[it] }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = player != null && name.isNotBlank(), onClick = {
                val game = folders.customGame(context, name.trim(), player ?: return@TextButton, file, id, store.first)
                if (game == null) {
                    android.widget.Toast.makeText(context, R.string.game_mode_own_game_none, android.widget.Toast.LENGTH_LONG).show()
                } else onAdded(game)
            }) { Text(stringResource(R.string.game_mode_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Your own game folders: the ones set in a launcher or emulator (picked with Android's folder
 * picker), each read for games of the player it's set to, or by its folders' names.
 */
@Composable
private fun CustomGameFolders() {
    val context = LocalContext.current
    val folders = it.palsoftware.pastiera.gaming.GameFolders
    var custom by remember { mutableStateOf(folders.custom(context)) }
    fun save(list: List<it.palsoftware.pastiera.gaming.GameFolders.Custom>) {
        custom = list
        folders.setCustom(context, list)
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val path = tree?.let { folders.pathOf(it) } ?: return@rememberLauncherForActivityResult
        if (custom.none { it.path == path }) save(custom + it.palsoftware.pastiera.gaming.GameFolders.Custom(path))
    }
    val byNames = stringResource(R.string.game_mode_folder_by_names)
    custom.forEach { folder ->
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.path.removePrefix("/storage/emulated/0/"), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { save(custom - folder) }) { Text(stringResource(R.string.game_mode_remove)) }
            }
            ChoiceRow(stringResource(R.string.game_mode_folder_games_of), folder.player ?: byNames, listOf(byNames) + folders.playerNames) { index ->
                val player = if (index == 0) null else folders.playerNames[index - 1]
                save(custom.map { if (it == folder) it.copy(player = player) else it })
            }
        }
    }
    FluxActionRow(
        linkId = "game_mode.folder_add",
        icon = "+",
        title = stringResource(R.string.game_mode_folder_add),
        description = stringResource(R.string.game_mode_folder_add_description)
    ) { runCatching { picker.launch(null) } }
}


/** The games of the game launchers installed, one tap making a profile for it. */
@Composable
internal fun GameImportDialog(onPicked: (GameLibrary.Game) -> Unit, onByHand: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<GameLibrary.Game>?>(null) }
    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) {
            GameLibrary.launchers(context).flatMap { launcher ->
                GameLibrary.games(launcher.packageName) +
                    // The launcher itself, for a profile covering all its games
                    GameLibrary.Game(launcher.appName, launcher.packageName)
            }.let { fromLaunchers ->
                // The game folders' games first: an exported file knows the game's store and ID
                // exactly, where a home screen shortcut only gives its ID
                val fromFolders = it.palsoftware.pastiera.gaming.GameFolders.games(context)
                val known = fromFolders.map { it.name.lowercase() to it.packageName }.toSet()
                fromFolders + fromLaunchers.filter { (it.name.lowercase() to it.packageName) !in known }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_import)) },
        text = {
            Column {
                val list = games
                when {
                    list == null -> Text(stringResource(R.string.game_mode_import_reading))
                    list.isEmpty() -> Text(stringResource(R.string.game_mode_import_none))
                    else -> list.forEach { game ->
                        Text(
                            "${game.name}  (${context.appLabel(game.packageName)})",
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(game) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        // A game added to a launcher or emulator by hand
        dismissButton = { TextButton(onClick = onByHand) { Text(stringResource(R.string.game_mode_own_game)) } }
    )
}

/** A game's or app's settings, done with: kept (a found game, looked at, set up from now on). */
internal fun saveEditedProfile(context: android.content.Context, saved: GameProfile?) {
    saved ?: return
    if (saved.pending) GameProfiles.confirm(context, saved) else GameProfiles.save(context, saved)
    // An emulator given the layout: written again if A and B are now the other way round
    if (!saved.isGame) {
        val app = context.applicationContext
        Thread {
            saved.packages.filter { pkg -> it.palsoftware.pastiera.gaming.GameApps.emulator(pkg) in setOf("Dolphin", "PPSSPP") }
                .forEach { pkg -> runCatching { it.palsoftware.pastiera.gaming.FluxLayout.refreshIfOutdated(app, pkg) } }
        }.start()
    }
}

/** A game's or app's settings deleted, with the art found or picked for it. */
internal fun deleteProfile(context: android.content.Context, profile: GameProfile) {
    GameProfiles.delete(context, profile.id)
    it.palsoftware.pastiera.gaming.GameArt.forget(context, profile)
    it.palsoftware.pastiera.gaming.GameArt.clearCustom(context, profile)
}

/**
 * A game's or app's settings, grouped like the rest of settings: the game (its name, art and
 * home screen shortcut), its screen, its controls, Dolphin's for a Dolphin game, and an app's
 * own parts. Each change is kept as it's made; Delete, at the bottom, asks first.
 */
@Composable
internal fun GameProfileEditor(profile: GameProfile, onDone: (GameProfile?) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(profile) }
    var addingApp by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Kept as it's changed (a found game is set up as it's left)
    LaunchedEffect(current) {
        if (current == profile) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        withContext(Dispatchers.IO) { GameProfiles.save(context, current) }
    }
    // The phone's Back is done too, as the arrow is
    androidx.activity.compose.BackHandler { onDone(current) }
    FluxScreenScaffold(current.name, { onDone(current) }, Modifier) {
        // The game: its name, its art, a shortcut on the home screen
        SettingsSectionDivider(stringResource(if (current.isGame) R.string.game_section_game else R.string.game_section_app))
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { current = current.copy(name = it) },
                label = { Text(stringResource(R.string.game_mode_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Its art: a game's list entry, an app's tile in the Games app
            GameArtworkRow(current)
        }
        val pinScope = androidx.compose.runtime.rememberCoroutineScope()
        if (current.isGame) FluxActionRow(
            linkId = null,
            icon = "\u2302",
            title = stringResource(R.string.game_mode_home_screen),
            description = stringResource(R.string.game_mode_home_screen_description)
        ) {
            val pinned = current.also { saved -> GameProfiles.save(context, saved) }
            pinScope.launch {
                // The game's art for its icon, as in the list (fetched if need be)
                val art = withContext(Dispatchers.IO) {
                    runCatching { it.palsoftware.pastiera.gaming.GameArt.load(context, pinned) }.getOrNull()
                }
                if (!it.palsoftware.pastiera.gaming.GameLaunchActivity.pin(context, pinned, art)) {
                    android.widget.Toast.makeText(context, R.string.game_mode_home_screen_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        // Its screen: how it turns, a DS or 3DS's two screens, a game's sizes
        SettingsSectionDivider(stringResource(R.string.game_section_screen))
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenChoice(current) { current = it }
            DualScreenRows(current) { current = it }
            if (current.isGame) ResolutionChoices(current) { current = it }
        }

        // Its controls: a game plays with its app's, unless it has its own
        SettingsSectionDivider(stringResource(R.string.game_mode_controls_section))
        if (current.isGame) {
            val ownScope = androidx.compose.runtime.rememberCoroutineScope()
            val appName = current.packages.firstOrNull()?.let { pkg -> context.appLabel(pkg) }.orEmpty()
            FluxSwitchRow(
                linkId = null,
                title = stringResource(R.string.game_mode_own_settings),
                description = stringResource(if (current.own) R.string.game_mode_own_settings_on else R.string.game_mode_own_settings_off, appName),
                checked = current.own
            ) { on ->
                if (!on) current = current.copy(own = false)
                // Starting from the app's settings, so only what differs needs changing
                else ownScope.launch {
                    val app = withContext(Dispatchers.IO) { GameProfiles.effective(context, current.copy(own = false)) }
                    current = current.copy(
                        own = true, leftHalf = app.leftHalf, rightHalf = app.rightHalf, stickZones = app.stickZones, taps = app.taps,
                        screen = app.screen, swapFace = app.swapFace
                    )
                }
            }
        }
        // Keys of its own, for anything gaming mode sends buttons to (Dolphin reads the keyboard itself: its own below)
        if (current.packages.none { p -> GameApps.emulator(p) == "Dolphin" }) GameKeysRow(current) { changed -> current = changed }
        if (!current.isGame || current.own) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ChoiceRow(stringResource(R.string.game_mode_left_half), current.leftHalf.label, TrackpadRole.entries.map { it.label }) {
                    current = current.copy(leftHalf = TrackpadRole.entries[it])
                }
                ChoiceRow(stringResource(R.string.game_mode_right_half), current.rightHalf.label, TrackpadRole.entries.map { it.label }) {
                    current = current.copy(rightHalf = TrackpadRole.entries[it])
                }
                // How far the Wii pointer moves for a slide
                if (current.pointer) {
                    Text(stringResource(R.string.game_mode_pointer_speed, Math.round(current.pointerSpeed * 100)))
                    Text(stringResource(R.string.game_mode_pointer_speed_description), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.Slider(
                        value = current.pointerSpeed,
                        onValueChange = { value -> current = current.copy(pointerSpeed = Math.round(value * 20) / 20f) },
                        valueRange = 0.25f..2f,
                        steps = 34
                    )
                }
            }
            // Nintendo's A and B (X and Y) where they sit on its controllers
            FluxSwitchRow(
                linkId = null,
                title = stringResource(R.string.game_mode_swap_face),
                description = stringResource(R.string.game_mode_swap_face_description),
                checked = current.swapsFace
            ) { on -> current = current.copy(swapFace = on) }
            // An emulator: Flux Keyboard's layout written into its own controller settings; other
            // apps map the keys themselves
            val emulatorPkg = current.packages.firstOrNull { pkg -> it.palsoftware.pastiera.gaming.GameApps.emulator(pkg) != null }
            if (emulatorPkg == null) FluxNote(
                when (val app = GameApps.appFor(current)) {
                    // Cloud gaming takes no keyboard: a real controller, or its touch controls
                    GameApps.XBOX_NAME -> stringResource(R.string.game_mode_xbox_note)
                    null -> stringResource(R.string.game_mode_keys_pass)
                    else -> stringResource(R.string.game_mode_keys_pass_app, app)
                }
            )
            if (emulatorPkg != null) EmulatorSetupRows(emulatorPkg)
        }
        // Apps whose graphics settings can't be written: what suits this phone, to set by hand
        val advice = remember(current.packages) {
            val app = GameApps.appFor(current)
            if (current.packages.any { pkg -> it.palsoftware.pastiera.gaming.GraphicsTuning.writable(pkg) }) null
            else runCatching { it.palsoftware.pastiera.gaming.GraphicsTuning.advice(context, app) }.getOrNull()
        }
        if (advice != null) {
            var showAdvice by remember { mutableStateOf(false) }
            FluxActionRow(
                linkId = null,
                icon = "\u2699",
                title = stringResource(R.string.game_mode_advice),
                description = stringResource(R.string.game_mode_advice_description)
            ) { showAdvice = true }
            if (showAdvice) AlertDialog(
                onDismissRequest = { showAdvice = false },
                title = { Text(stringResource(R.string.game_mode_advice)) },
                text = { Text(advice) },
                confirmButton = { TextButton(onClick = { showAdvice = false }) { Text(stringResource(android.R.string.ok)) } }
            )
        }
        // GameNative: its on-screen controller to turn on, for the trackpad's sticks
        if (!current.isGame && GameApps.appFor(current) == "GameNative") {
            var steps by remember { mutableStateOf(false) }
            FluxActionRow(
                linkId = null,
                icon = "\u2913",
                title = stringResource(R.string.game_mode_gamenative_save),
                description = stringResource(R.string.game_mode_gamenative_save_description)
            ) { steps = true }
            if (steps) AlertDialog(
                onDismissRequest = { steps = false },
                title = { Text(stringResource(R.string.game_mode_gamenative_save)) },
                text = { Text(stringResource(R.string.game_mode_gamenative_steps)) },
                confirmButton = { TextButton(onClick = { steps = false }) { Text(stringResource(android.R.string.ok)) } }
            )
        }

        // A Dolphin game: the Wii controller that suits it, set in its own Dolphin settings
        if (current.isGame) DolphinControllerRows(current) { changed -> current = changed }

        // An app's own parts that play its games under another name (a launcher's player)
        if (!current.isGame) {
            SettingsSectionDivider(stringResource(R.string.game_mode_apps))
            current.packages.forEach { pkg ->
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(context.appLabel(pkg), modifier = Modifier.weight(1f))
                    TextButton(onClick = { current = current.copy(packages = current.packages - pkg) }) {
                        Text(stringResource(R.string.game_mode_remove))
                    }
                }
            }
            FluxActionRow(
                linkId = null,
                icon = "+",
                title = stringResource(R.string.game_mode_add_app),
                description = stringResource(R.string.game_mode_add_app_description)
            ) { addingApp = true }
        }

        // Deleted, once asked
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        FluxActionRow(
            linkId = null,
            icon = "\u2715",
            title = stringResource(R.string.game_mode_delete),
            description = stringResource(R.string.game_mode_delete_description)
        ) { deleting = true }
        Spacer(Modifier.height(16.dp))
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.game_mode_delete_confirm_title, current.name)) },
        text = { Text(stringResource(R.string.game_mode_delete_description)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.game_mode_delete_confirm)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } }
    )
    if (addingApp) {
        AppPickerDialog(
            onAppSelected = { app ->
                addingApp = false
                current = current.copy(packages = current.packages + app.packageName)
            },
            onDismiss = { addingApp = false },
            excludePackages = current.packages
        )
    }
}

/**
 * An emulator's own settings, written by Flux Keyboard: its controls set to the keyboard's
 * layout (its graphics too, where they can be written), or all of it put back as it was.
 */
@Composable
private fun EmulatorSetupRows(emulatorPkg: String) {
    val context = LocalContext.current
    val emulatorName = it.palsoftware.pastiera.gaming.GameApps.emulator(emulatorPkg).orEmpty()
    var setupResult by remember { mutableStateOf<String?>(null) }
    val setupScope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = null,
        icon = "\u2328",
        title = stringResource(R.string.game_mode_setup_controls, emulatorName),
        description = stringResource(R.string.game_mode_setup_controls_description, emulatorName)
    ) {
        setupScope.launch {
            val result = withContext(Dispatchers.IO) {
                it.palsoftware.pastiera.gaming.FluxLayout.apply(context, emulatorPkg).also { done ->
                    // The games' own Dolphin profiles built on the layout just written
                    if (done == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE && GameApps.emulator(emulatorPkg) == "Dolphin")
                        it.palsoftware.pastiera.gaming.FluxLayout.refreshGames(context, emulatorPkg)
                }
            }
            if (result == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE) it.palsoftware.pastiera.gaming.FluxLayout.markWritten(context, emulatorPkg)
            // Its graphics too, where its settings can be written: suited to this phone's GPU
            val graphics = withContext(Dispatchers.IO) {
                if (it.palsoftware.pastiera.gaming.GraphicsTuning.writable(emulatorPkg) &&
                    it.palsoftware.pastiera.gaming.GraphicsTuning.tune(context, emulatorPkg) == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE
                ) "\n\n" + it.palsoftware.pastiera.gaming.GraphicsTuning.summary(context, emulatorPkg) else ""
            }
            setupResult = when (result) {
                it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE -> context.getString(R.string.game_mode_setup_done, emulatorName) + graphics
                it.palsoftware.pastiera.gaming.FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, emulatorName)
                it.palsoftware.pastiera.gaming.FluxLayout.Result.BY_HAND ->
                    context.getString(R.string.game_mode_setup_by_hand, emulatorName, it.palsoftware.pastiera.gaming.FluxLayout.bindList())
                it.palsoftware.pastiera.gaming.FluxLayout.Result.FAILED -> context.getString(R.string.adb_not_running)
            }
        }
    }
    // Everything Flux Keyboard set in the emulator, put back as it was
    if (it.palsoftware.pastiera.gaming.GraphicsTuning.writable(emulatorPkg)) {
        var resetting by remember { mutableStateOf(false) }
        FluxActionRow(
            linkId = null,
            icon = "\u21BA",
            title = stringResource(R.string.game_mode_reset_emulator, emulatorName),
            description = stringResource(R.string.game_mode_reset_emulator_description)
        ) { resetting = true }
        if (resetting) AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text(stringResource(R.string.game_mode_reset_emulator, emulatorName)) },
            text = { Text(stringResource(R.string.game_mode_reset_emulator_confirm, emulatorName)) },
            confirmButton = {
                TextButton(onClick = {
                    resetting = false
                    setupScope.launch {
                        val result = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.FluxLayout.reset(emulatorPkg) }
                        setupResult = when (result) {
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE -> context.getString(R.string.game_mode_reset_emulator_done, emulatorName)
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, emulatorName)
                            else -> context.getString(R.string.adb_not_running)
                        }
                    }
                }) { Text(stringResource(R.string.game_mode_reset)) }
            },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    setupResult?.let { message ->
        AlertDialog(
            onDismissRequest = { setupResult = null },
            title = { Text(stringResource(R.string.game_mode_setup_controls, emulatorName)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { setupResult = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

/** A label and its value, the value picked from a menu. */
/**
 * A Dolphin game's controller, set in its own Dolphin settings: for a Wii game, Wii Remote and
 * Nunchuk, Classic Controller, held sideways or a GameCube controller; and for any game, keys of
 * its own for that controller's buttons, drawn as the controller ([DolphinPadRemapper]).
 */
/**
 * A DS, 3DS or Wii U emulator's screens: which shows, which is bigger, which comes first, written
 * into the emulator where its settings can be reached (choosing one sets Screen to suit it), else
 * a note on where to set them.
 */
@Composable
private fun DualScreenRows(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    val pkg = profile.packages.firstOrNull { p -> GameApps.twoScreens(p) != null || GameApps.emulator(p) == "Cemu" } ?: return
    val appName = context.appLabel(pkg)
    val layouts = DualScreens.layouts(pkg)
    if (layouts.isEmpty()) {
        Text(
            if (GameApps.emulator(pkg) == "Cemu") stringResource(R.string.dual_screens_note_cemu)
            else stringResource(R.string.dual_screens_note_other, appName),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val labels = mapOf(
        DualScreens.Layout.STACKED to R.string.dual_screens_stacked,
        DualScreens.Layout.STACKED_BOTTOM_FIRST to R.string.dual_screens_stacked_bottom_first,
        DualScreens.Layout.TOP_LARGE to R.string.dual_screens_top_large,
        DualScreens.Layout.BOTTOM_LARGE to R.string.dual_screens_bottom_large,
        DualScreens.Layout.SIDE_BY_SIDE to R.string.dual_screens_side_by_side,
        DualScreens.Layout.TOP_ONLY to R.string.dual_screens_top_only,
        DualScreens.Layout.BOTTOM_ONLY to R.string.dual_screens_bottom_only
    ).mapValues { stringResource(it.value) }
    val names = listOf(stringResource(R.string.dual_screens_as_set, appName)) + layouts.map { labels.getValue(it) }
    val chosen = DualScreens.layout(profile.screens)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ChoiceRow(stringResource(R.string.dual_screens_title), chosen?.let { labels[it] } ?: names[0], names) { index ->
        val layout = layouts.getOrNull(index - 1)
        // The screen that suits it: one above the other upright, the rest landscape
        val changed = if (layout == null) profile.copy(screens = null)
            else profile.copy(screens = layout.name, screen = layout.screen, ownScreen = profile.isGame || profile.ownScreen)
        onChanged(changed)
        // An app's layout written now (a game's as it starts), for games started from the emulator itself
        if (layout != null && !profile.isGame) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val window = DualScreens.window(context, layout.landscape, profile.phoneHeight)
                window != null && DualScreens.apply(pkg, layout, window)
            }
            if (!ok) android.widget.Toast.makeText(context, context.getString(R.string.game_mode_setup_not_found, appName), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Text(
        stringResource(if (DualScreens.appliesAtNextStart(pkg)) R.string.dual_screens_note_noods else R.string.dual_screens_note_written, appName),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DolphinControllerRows(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    val pkg = profile.packages.firstOrNull { p -> GameApps.emulator(p) == "Dolphin" } ?: return
    // The game's ID, and whether it's a Wii game (read from its file through the shell)
    var game by remember(profile.launch) { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(profile.launch) {
        game = withContext(Dispatchers.IO) {
            val path = profile.launch?.let { launch -> GameFiles.gameFile(launch) } ?: return@withContext null
            GameIds.id(context, "Dolphin", path)?.let { it to GameFiles.isWii(path) }
        }
    }
    val (id, wii) = game ?: return
    SettingsSectionDivider("Dolphin")
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val names = listOf(
        stringResource(R.string.game_mode_wii_nunchuk),
        stringResource(R.string.game_mode_wii_classic),
        stringResource(R.string.game_mode_wii_sideways),
        stringResource(R.string.game_mode_wii_gamecube)
    )
    val pad = if (!wii) FluxLayout.WiiPad.GAMECUBE
        else FluxLayout.WiiPad.entries.firstOrNull { it.name == profile.wiiPad } ?: FluxLayout.WiiPad.NUNCHUK
    /** Written into Dolphin now (it's also written again each time the game starts from Games). */
    fun apply(changed: GameProfile, message: String) {
        val chosen = if (!wii) FluxLayout.WiiPad.GAMECUBE
            else FluxLayout.WiiPad.entries.firstOrNull { it.name == changed.wiiPad } ?: FluxLayout.WiiPad.NUNCHUK
        scope.launch {
            val result = withContext(Dispatchers.IO) { FluxLayout.setGamePad(context, pkg, id, wii, chosen, changed.padRemap(chosen), activate = false, keepChosen = changed.keepDolphinProfile, motion = changed.wiiMotion) }
            android.widget.Toast.makeText(
                context,
                when (result) {
                    FluxLayout.Result.DONE -> message
                    FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, "Dolphin")
                    else -> context.getString(R.string.adb_not_running)
                },
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    if (wii) Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChoiceRow(stringResource(R.string.game_mode_wii_controller), names[pad.ordinal], names) { index ->
            val picked = FluxLayout.WiiPad.entries[index]
            val changed = profile.copy(wiiPad = picked.name.takeIf { picked != FluxLayout.WiiPad.NUNCHUK })
            onChanged(changed)
            apply(changed, context.getString(R.string.game_mode_wii_set, names[index]))
        }
    }
    // The Wii Remote moved by moving the phone, with the Nunchuk or held sideways
    if (wii && pad.moves) FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.dolphin_wii_motion),
        description = stringResource(R.string.dolphin_wii_motion_description),
        checked = profile.wiiMotion
    ) { on ->
        val changed = profile.copy(wiiMotion = on)
        onChanged(changed)
        apply(changed, context.getString(if (on) R.string.dolphin_wii_motion_on else R.string.dolphin_wii_motion_off))
    }
    // When it all takes effect, and what doesn't follow
    FluxNote(stringResource(R.string.dolphin_game_applied_note))
    // Flux Keyboard's controls, or a profile chosen for the game in Dolphin itself
    FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.dolphin_use_flux_title),
        description = stringResource(R.string.dolphin_use_flux_description),
        checked = !profile.keepDolphinProfile
    ) { on ->
        val changed = profile.copy(keepDolphinProfile = !on)
        onChanged(changed)
        apply(changed, context.getString(R.string.dolphin_pad_saved))
    }
    // Dolphin's on-screen controls in this game, set as it starts from Games
    val overlays = listOf(
        stringResource(R.string.dolphin_overlay_off),
        stringResource(R.string.dolphin_overlay_invisible),
        stringResource(R.string.dolphin_overlay_shown)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChoiceRow(stringResource(R.string.dolphin_overlay_title), overlays[profile.overlayMode.ordinal], overlays) { index ->
            onChanged(profile.copy(dolphinOverlay = FluxLayout.DolphinOverlay.entries[index].name))
        }
    }
    var remapping by remember { mutableStateOf(false) }
    FluxActionRow(
        linkId = null,
        icon = "\u271A",
        title = stringResource(R.string.dolphin_pad_row),
        description = stringResource(R.string.dolphin_pad_row_description, names[pad.ordinal])
    ) { remapping = true }
    if (remapping) DolphinPadRemapper(
        pad = pad,
        padName = names[pad.ordinal],
        remap = profile.padRemap(pad),
        swap = FluxLayout.swapFor(context, pkg),
        onSave = { remap ->
            if (remap == profile.padRemap(pad)) return@DolphinPadRemapper
            val text = DolphinPads.format(remap)
            val changed = profile.copy(padControls = if (remap.isEmpty()) profile.padControls - pad.name else profile.padControls + (pad.name to text))
            onChanged(changed)
            apply(changed, context.getString(R.string.dolphin_pad_saved))
        },
        onDismiss = { remapping = false }
    )
}

/** A game's or app's own keys, on top of Keyboard controls: drawn as the keyboard, changed there. */
@Composable
private fun GameKeysRow(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    val own = remember(profile.keyOverrides) { FluxLayout.parseOverrides(profile.keyOverrides.orEmpty()) }
    FluxActionRow(
        linkId = null,
        icon = "\u2328",
        title = stringResource(if (profile.isGame) R.string.game_keys_row else R.string.game_keys_row_app),
        description = stringResource(R.string.game_keys_description)
    ) { editing = true }
    if (editing) androidx.compose.ui.window.Dialog(
        onDismissRequest = { editing = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val base = remember { FluxLayout.loadOverrides(context); true }
        KeyLayoutEditor(
            title = profile.name,
            initial = own,
            base = { code, layer -> if (base) FluxLayout.layoutControlFor(code, FluxLayout.modifiersOf(layer)) else null },
            onChange = { changed -> onChanged(profile.copy(keyOverrides = FluxLayout.formatOverrides(changed).ifEmpty { null })) },
            onBack = { editing = false },
            modifier = Modifier.fillMaxSize(),
            note = stringResource(R.string.game_keys_note)
        )
    }
}

/** Xbox Cloud Gaming's games, searched by name: one picked becomes a game of its own. */
@Composable
internal fun XboxCloudDialog(
    pkg: String,
    onPicked: (GameLibrary.Game) -> Unit,
    onDismiss: () -> Unit,
    /** A tap plays the game straight away (the Games app); else a tap picks it, as [onPicked]. */
    onPlay: ((GameLibrary.Game) -> Unit)? = null,
    onAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    // A game long-pressed: added to the games (the Games app), or its settings
    var menuFor by remember { mutableStateOf<it.palsoftware.pastiera.gaming.XboxCloud.CloudGame?>(null) }
    menuFor?.let { cloud ->
        val game = it.palsoftware.pastiera.gaming.XboxCloud.asGame(cloud, pkg)
        val profile = remember(cloud.id) { GameProfiles.profileFor(context, game) }
        val added = remember(cloud.id) { GameProfiles.all(context).any { p -> p.id == profile.id } }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(cloud.name) },
            text = { Text(stringResource(if (added) R.string.game_mode_xbox_in_games else R.string.game_mode_xbox_add_to_games_description)) },
            confirmButton = {
                if (added) TextButton(onClick = {
                    GameProfiles.delete(context, profile.id)
                    menuFor = null
                    onAdded()
                }) { Text(stringResource(R.string.game_mode_xbox_remove_from_games)) }
                if (!added) androidx.compose.material3.Button(onClick = {
                    GameProfiles.save(context, profile)
                    android.widget.Toast.makeText(context, context.getString(R.string.game_mode_xbox_added, cloud.name), android.widget.Toast.LENGTH_SHORT).show()
                    menuFor = null
                    onAdded()
                }) { Text(stringResource(R.string.game_mode_xbox_add_to_games)) }
            },
            dismissButton = {
                Row {
                    if (onPlay != null) TextButton(onClick = { menuFor = null; onPicked(game) }) { Text(stringResource(R.string.games_app_settings_short)) }
                    TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
    var games by remember { mutableStateOf<List<it.palsoftware.pastiera.gaming.XboxCloud.CloudGame>?>(null) }
    var query by remember { mutableStateOf("") }
    // Signed in first: the games only play once Xbox knows who's playing
    val signedIn = remember { it.palsoftware.pastiera.gaming.XboxCloud.signedIn() }
    if (!signedIn) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.game_mode_xbox_add)) },
            text = { Text(stringResource(R.string.game_mode_xbox_sign_in_first)) },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    runCatching { context.startActivity(it.palsoftware.pastiera.gaming.XboxPlayActivity.signIn(context)) }
                    onDismiss()
                }) { Text(stringResource(R.string.game_mode_xbox_sign_in)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
        return
    }
    LaunchedEffect(Unit) { games = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.XboxCloud.games(context) } }
    // A grid of the games' covers, a search above: nothing else
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.game_mode_xbox_search)) }, modifier = Modifier.fillMaxWidth()
                )
                val list = games
                when {
                    list == null -> Text(stringResource(R.string.game_mode_import_reading), modifier = Modifier.padding(top = 12.dp))
                    list.isEmpty() -> Text(stringResource(R.string.game_mode_xbox_none), modifier = Modifier.padding(top = 12.dp))
                    else -> {
                        // Xbox Cloud Gaming itself first, for every game
                        val shown = (listOf(it.palsoftware.pastiera.gaming.XboxCloud.HOME) + list)
                            .filter { game -> query.isBlank() || game.name.contains(query.trim(), ignoreCase = true) }
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(96.dp),
                            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(shown.size, key = { shown[it].id.ifEmpty { "home" } }) { i ->
                                val game = shown[i]
                                CloudGameTile(game, onLongClick = { if (game.id.isNotEmpty()) menuFor = game }) {
                                    val picked = it.palsoftware.pastiera.gaming.XboxCloud.asGame(game, pkg)
                                    onPlay?.invoke(picked) ?: onPicked(picked)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A cloud game's cover and name. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CloudGameTile(game: it.palsoftware.pastiera.gaming.XboxCloud.CloudGame, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val context = LocalContext.current
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, game.art) {
        value = withContext(Dispatchers.IO) {
            // Xbox Cloud Gaming itself: the XCloud Games tile's art, as in the Games app
            if (game.id.isEmpty()) runCatching { it.palsoftware.pastiera.gaming.GameArt.xboxTile(context)?.asImageBitmap() }.getOrNull()
            else game.art?.takeIf { it.isNotEmpty() }?.let { url -> it.palsoftware.pastiera.gaming.GameArt.thumbnail(url)?.asImageBitmap() }
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .then(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)),
            contentAlignment = Alignment.Center
        ) {
            image?.let {
                androidx.compose.foundation.Image(bitmap = it, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
            } ?: Text("\u2601", style = MaterialTheme.typography.headlineMedium)
        }
        Text(game.name, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

/**
 * How the screen turns while it plays: as the app wants, or kept upright (a landscape-shaped
 * screen). A game can also follow its app's choice, unless it has all its own settings.
 */
@Composable
private fun ScreenChoice(profile: GameProfile, onChange: (GameProfile) -> Unit) {
    val screens = it.palsoftware.pastiera.gaming.ScreenMode.entries
    val follows = profile.isGame && !profile.own
    val names = listOfNotNull(
        if (follows) stringResource(R.string.game_mode_screen_follow) else null,
        stringResource(R.string.game_mode_screen_app),
        stringResource(R.string.game_mode_screen_sideways),
        stringResource(R.string.game_mode_screen_upright)
    )
    val chosen = if (follows && !profile.ownScreen) 0 else profile.screen.ordinal + if (follows) 1 else 0
    Column {
        ChoiceRow(stringResource(R.string.game_mode_screen), names[chosen], names) { index ->
            onChange(
                when {
                    follows && index == 0 -> profile.copy(ownScreen = false)
                    else -> profile.copy(ownScreen = profile.isGame, screen = screens[index - if (follows) 1 else 0])
                }
            )
        }
        if (!(follows && !profile.ownScreen)) Text(
            stringResource(
                when (profile.screen) {
                    it.palsoftware.pastiera.gaming.ScreenMode.APP -> R.string.game_mode_screen_app_description
                    it.palsoftware.pastiera.gaming.ScreenMode.SIDEWAYS -> R.string.game_mode_screen_sideways_description
                    it.palsoftware.pastiera.gaming.ScreenMode.UPRIGHT -> R.string.game_mode_screen_upright_description
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** A game's size on the phone (kept upright, its shape kept). */
@Composable
private fun ResolutionChoices(profile: GameProfile, onChange: (GameProfile) -> Unit) {
    val heights = listOf(null, 1080, 900, 720, 540)
    val phoneNames = listOf(stringResource(R.string.game_mode_resolution_full)) + heights.drop(1).map { "${it}p" }
    Column {
        ChoiceRow(stringResource(R.string.game_mode_resolution_phone), phoneNames[heights.indexOf(profile.phoneHeight).coerceAtLeast(0)], phoneNames) { index ->
            onChange(profile.copy(phoneHeight = heights[index]))
        }
        Text(stringResource(R.string.game_mode_resolution_phone_description), style = MaterialTheme.typography.bodySmall)
    }
}

/** A game's art, picked from the phone's pictures for its list entry, or the art found for it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GameArtworkRow(profile: GameProfile) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableLongStateOf(it.palsoftware.pastiera.gaming.GameArt.version(context, profile)) }
    fun changed() {
        it.palsoftware.pastiera.gaming.GameArt.forget(context, profile)
        version = it.palsoftware.pastiera.gaming.GameArt.version(context, profile)
        val app = context.applicationContext
        Thread { runCatching { it.palsoftware.pastiera.gaming.GamesActivity.sync(app) } }.start()
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.setCustom(context, profile, uri) }
            if (ok) changed()
            else android.widget.Toast.makeText(context, R.string.game_mode_artwork_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.runtime.key(version) { GameArtIcon(profile, version) }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.game_mode_artwork))
            Text(stringResource(R.string.game_mode_artwork_description), style = MaterialTheme.typography.bodySmall)
        }
    }
    var choosing by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.game_mode_artwork_found)) }
        androidx.compose.material3.OutlinedButton(onClick = {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(stringResource(R.string.game_mode_artwork_choose)) }
        if (version > 0) androidx.compose.material3.OutlinedButton(onClick = {
            it.palsoftware.pastiera.gaming.GameArt.clearCustom(context, profile)
            changed()
        }) { Text(stringResource(R.string.game_mode_artwork_reset)) }
    }
    if (choosing) ArtChoicesDialog(profile, onDismiss = { choosing = false }) { url, upright ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.choose(context, profile, url, upright) }
            if (ok) changed()
            else android.widget.Toast.makeText(context, R.string.game_mode_artwork_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Every art the sources have for a game, to pick from: icons (its list entry and shortcut) and
 * upright covers (the Games app). Each tap keeps that one; the dialog stays open to pick the other.
 */
@Composable
private fun ArtChoicesDialog(profile: GameProfile, onDismiss: () -> Unit, onPicked: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    // A title of its own to look for, instead of renaming the game
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    val found by androidx.compose.runtime.produceState<Pair<List<it.palsoftware.pastiera.gaming.GameArt.Choice>, List<it.palsoftware.pastiera.gaming.GameArt.Choice>>?>(null, profile.id, searched) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { it.palsoftware.pastiera.gaming.GameArt.choices(context, profile, searched) }.getOrDefault(emptyList<it.palsoftware.pastiera.gaming.GameArt.Choice>() to emptyList())
        }
    }
    var picked by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_artwork_found)) },
        text = {
            val choices = found
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(profile.name) },
                    label = { Text(stringResource(R.string.game_mode_artwork_search)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searched = query }),
                    trailingIcon = { TextButton(onClick = { searched = query }) { Text("\u2192") } },
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    choices == null -> Text(stringResource(R.string.game_mode_import_reading))
                    choices.first.isEmpty() && choices.second.isEmpty() -> Text(stringResource(R.string.game_mode_artwork_none))
                    else -> {
                        for ((title, list, upright) in listOf(
                            Triple(R.string.game_mode_artwork_icons, choices.first, false),
                            Triple(R.string.game_mode_artwork_upright, choices.second, true)
                        )) {
                            if (list.isEmpty()) continue
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(list.size) { index ->
                                    val choice = list[index]
                                    ArtThumbnail(choice, upright, choice.full in picked) {
                                        picked = picked + choice.full
                                        onPicked(choice.full, upright)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
    )
}

@Composable
private fun ArtThumbnail(choice: it.palsoftware.pastiera.gaming.GameArt.Choice, upright: Boolean, picked: Boolean, onClick: () -> Unit) {
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, choice.thumb) {
        value = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.thumbnail(choice.thumb)?.asImageBitmap() }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val size = if (upright) Modifier.size(width = 80.dp, height = 120.dp) else Modifier.size(96.dp)
    Box(
        modifier = size.clip(shape)
            .then(if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        image?.let {
            androidx.compose.foundation.Image(bitmap = it, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        } ?: androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/** Signed in to Xbox in Flux Keyboard's player, checked again whenever the screen comes back. */
internal class XboxAccount(private val context: android.content.Context) {
    var signedIn by mutableStateOf(it.palsoftware.pastiera.gaming.XboxCloud.signedIn())
    var confirming by mutableStateOf(false)

    /** Signs in, or asks before signing out. */
    fun click() {
        if (signedIn) confirming = true
        else runCatching { context.startActivity(it.palsoftware.pastiera.gaming.XboxPlayActivity.signIn(context)) }
    }
}

@Composable
internal fun rememberXboxAccount(): XboxAccount {
    val context = LocalContext.current
    val account = remember { XboxAccount(context) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) account.signedIn = it.palsoftware.pastiera.gaming.XboxCloud.signedIn()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (account.confirming) AlertDialog(
        onDismissRequest = { account.confirming = false },
        title = { Text(stringResource(R.string.game_mode_xbox_sign_out)) },
        text = { Text(stringResource(R.string.game_mode_xbox_sign_out_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                it.palsoftware.pastiera.gaming.XboxCloud.signOut()
                account.signedIn = false
                account.confirming = false
            }) { Text(stringResource(R.string.game_mode_xbox_sign_out)) }
        },
        dismissButton = { TextButton(onClick = { account.confirming = false }) { Text(stringResource(R.string.cancel)) } }
    )
    return account
}

/** A profile's game art, cropped square; its app's icon while there's none. */
@Composable
internal fun GameArtIcon(profile: GameProfile, version: Long = 0) {
    val context = LocalContext.current
    val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, profile.id, profile.launch, version) {
        value = withContext(Dispatchers.IO) {
            runCatching { it.palsoftware.pastiera.gaming.GameArt.load(context, profile)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val image = art
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(shape)
        )
    } else {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            profile.packages.firstOrNull()?.let { pkg -> FluxAppIcon(pkg) }
        }
    }
}

/** A recording of every touch surface, saved to share: what the trackpad reports. */
@Composable
internal fun TrackpadRecordRow() {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf<Pair<String?, Int>?>(null) }
    var recordFailed by remember { mutableStateOf(false) }
    val recordScope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = null,
        icon = "\u25CE",
        title = stringResource(if (recording) R.string.game_mode_trackpad_test_running else R.string.game_mode_trackpad_test),
        description = stringResource(R.string.game_mode_trackpad_test_description, it.palsoftware.pastiera.gaming.TrackpadTest.SECONDS)
    ) {
        if (!recording) {
            recording = true
            recordScope.launch {
                val result = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.TrackpadTest.record() }
                recording = false
                if (result == null) recordFailed = true else recorded = result
            }
        }
    }
    recorded?.let { (device, fingers) ->
        AlertDialog(
            onDismissRequest = { recorded = null },
            title = { Text(stringResource(R.string.game_mode_trackpad_test)) },
            text = { Text(stringResource(R.string.game_mode_trackpad_test_result, fingers, device ?: "-")) },
            confirmButton = { TextButton(onClick = { recorded = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
    if (recordFailed) AlertDialog(
        onDismissRequest = { recordFailed = false },
        title = { Text(stringResource(R.string.game_mode_trackpad_test)) },
        text = { Text(stringResource(R.string.adb_not_running)) },
        confirmButton = { TextButton(onClick = { recordFailed = false }) { Text(stringResource(android.R.string.ok)) } }
    )
}

/**
 * The phone's own game mode (Unihertz's, com.agui.game): it can take over as the home screen,
 * at odds with gaming mode. Turned off here, or back on (through the shell: it's a system app);
 * not shown on phones without it.
 */
@Composable
internal fun PhoneGameModeRow() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Installed and turned on: null while it's being read, nothing shown when it isn't there
    var state by remember { mutableStateOf<Boolean?>(null) }
    var present by remember { mutableStateOf(false) }
    fun load() = scope.launch {
        val shell = it.palsoftware.pastiera.adb.AdbShell
        val (installed, on) = withContext(Dispatchers.IO) {
            val all = shell.run("pm list packages -s $PHONE_GAME_MODE ; true", 5_000).orEmpty()
            val disabled = shell.run("pm list packages -d $PHONE_GAME_MODE ; true", 5_000).orEmpty()
            fun has(list: String) = list.lines().any { it.trim() == "package:$PHONE_GAME_MODE" }
            has(all) to !has(disabled)
        }
        present = installed
        state = on
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }
    if (!present) return
    FluxSwitchRow(
        linkId = "game_mode.phone_game_mode",
        title = stringResource(R.string.phone_game_mode),
        description = stringResource(R.string.phone_game_mode_description),
        checked = state == true
    ) { want ->
        state = want
        scope.launch {
            withContext(Dispatchers.IO) {
                it.palsoftware.pastiera.adb.AdbShell.run(
                    if (want) "pm enable $PHONE_GAME_MODE" else "pm disable-user --user 0 $PHONE_GAME_MODE", 5_000
                )
            }
            load()
        }
    }
}

private const val PHONE_GAME_MODE = "com.agui.game"

/** The Game Pass plan: the XCloud catalogue lists only the cloud games it plays. */
@Composable
internal fun XboxPlanRow() {
    val context = LocalContext.current
    val plans = it.palsoftware.pastiera.gaming.XboxCloud.Plan.entries
    val names = listOf(
        stringResource(R.string.game_mode_xbox_plan_ultimate),
        stringResource(R.string.game_mode_xbox_plan_premium),
        stringResource(R.string.game_mode_xbox_plan_essential)
    )
    var plan by remember { mutableStateOf(it.palsoftware.pastiera.gaming.XboxCloud.plan(context)) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChoiceRow(stringResource(R.string.game_mode_xbox_plan), names[plans.indexOf(plan)], names) { index ->
            plan = plans[index]
            it.palsoftware.pastiera.gaming.XboxCloud.setPlan(context, plan)
        }
        Text(stringResource(R.string.game_mode_xbox_plan_description), style = MaterialTheme.typography.bodySmall)
    }
}
