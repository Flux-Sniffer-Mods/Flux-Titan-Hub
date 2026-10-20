package it.palsoftware.pastiera.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R


/**
 * The keyboard's page, the same however it's opened (the home screen, the keyboard's own
 * Settings button): what it still needs (nothing shown once it's on and chosen), and its
 * settings with a search.
 */
@Composable
internal fun KeyboardPage(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    FluxScreenScaffold(stringResource(R.string.module_keyboard_title), onBack, modifier) {
        ReadinessCard(readyText = stringResource(R.string.keyboard_ready))
        SettingsSearchArea(
            fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            scope = SettingLinkRegistry.keyboardCategories,
            hint = stringResource(R.string.keyboard_search)
        ) {
            // The keyboard as it's tuned for this phone, a tap away (it only changes the keyboard)
            RecommendedSettingsRow()
            KeyboardSettingsRows { destination -> openSettingsPage(context, SettingsPage(destination)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** The keyboard's settings, a row per section: on its page and the home screen. */
@Composable
internal fun KeyboardSettingsRows(onNavigate: (SettingsDestination) -> Unit) {
    SettingsCategoryRow(
        icon = Icons.Filled.Tune,
        title = stringResource(R.string.quick_settings_title),
        description = stringResource(R.string.quick_settings_row_description),
        linkId = "main.quick_settings",
        onClick = { onNavigate(SettingsDestination.QuickSettings) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.TextFields,
        title = stringResource(R.string.settings_typing_title),
        description = stringResource(R.string.settings_typing_description),
        linkId = SettingLinkIds.MAIN_TYPING,
        onClick = { onNavigate(SettingsDestination.Typing) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.Keyboard,
        title = stringResource(R.string.settings_keyboards_layouts_title),
        description = stringResource(R.string.settings_keyboards_layouts_description),
        linkId = SettingLinkIds.MAIN_KEYBOARDS_LAYOUTS,
        onClick = { onNavigate(SettingsDestination.KeyboardsLayouts) }
    )
    SettingsCategoryRow(
        iconRes = R.drawable.modifier_keys_24,
        title = stringResource(R.string.settings_modifiers_sym_title),
        description = stringResource(R.string.settings_modifiers_sym_description),
        linkId = SettingLinkIds.MAIN_MODIFIERS,
        onClick = { onNavigate(SettingsDestination.Modifiers) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.EmojiEmotions,
        title = stringResource(R.string.settings_emoji_symbols_gifs_title),
        description = stringResource(R.string.settings_emoji_symbols_gifs_description),
        linkId = SettingLinkIds.MAIN_FLUX_EMOJI_GIFS,
        onClick = { onNavigate(SettingsDestination.FluxEmojiGifs) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.Apps,
        title = stringResource(R.string.settings_apps_title),
        description = stringResource(R.string.settings_apps_description),
        linkId = SettingLinkIds.MAIN_APPS,
        onClick = { onNavigate(SettingsDestination.Apps) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.Palette,
        title = stringResource(R.string.settings_look_sound_title),
        description = stringResource(R.string.settings_look_sound_description),
        linkId = SettingLinkIds.MAIN_LOOK_SOUND,
        onClick = { onNavigate(SettingsDestination.LookSound) }
    )
    SettingsCategoryRow(
        icon = Icons.Filled.Shield,
        title = stringResource(R.string.settings_privacy_clipboard_title),
        description = stringResource(R.string.settings_privacy_clipboard_description),
        linkId = SettingLinkIds.MAIN_ADVANCED,
        onClick = { onNavigate(SettingsDestination.Advanced) }
    )
    // What the shell adds: the keyboard light, screen sizes and shell shortcuts
    SettingsCategoryRow(
        icon = Icons.Filled.Terminal,
        title = stringResource(R.string.shell_extras_title),
        description = stringResource(R.string.shell_extras_description),
        linkId = "main.shell_extras",
        onClick = { onNavigate(SettingsDestination.ShellExtras) }
    )
}
