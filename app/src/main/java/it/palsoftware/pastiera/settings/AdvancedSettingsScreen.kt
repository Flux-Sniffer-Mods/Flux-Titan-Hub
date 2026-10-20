package it.palsoftware.pastiera.settings
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import it.palsoftware.pastiera.R
import androidx.compose.material3.Surface
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.device.TrackpadGestureSettingsScreen
import it.palsoftware.pastiera.getExperimentalCandidatesViewEnabled
import it.palsoftware.pastiera.setExperimentalCandidatesViewEnabled

/**
 * The keyboard's privacy and clipboard: incognito, what's suggested from the clipboard and
 * notifications, and how long the clipboard keeps things.
 */
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val prefs = remember { SettingsManager.getPreferences(context) }

    var clipboardRetentionTime by remember {
        mutableStateOf(SettingsManager.getClipboardRetentionTime(context).toString())
    }
    var incognitoAlways by remember { mutableStateOf(SettingsManager.getIncognitoAlways(context)) }
    var incognitoFollowApps by remember { mutableStateOf(SettingsManager.getIncognitoFollowApps(context)) }
    var experimentalCandidatesViewEnabled by remember {
        mutableStateOf(SettingsManager.getExperimentalCandidatesViewEnabled(context))
    }
    val currentDestination = remember { when (settingsChild(context, "advanced")) {
        "ImeTest" -> AdvancedDestination.ImeTest
        "TrackpadGestures" -> AdvancedDestination.TrackpadGestures
        else -> if (context.settingsActivity().intent.data?.let(SettingLinkRegistry::parseSettingLinkUri) in TRACKPAD_SETTING_LINK_IDS) AdvancedDestination.TrackpadGestures else AdvancedDestination.Main
    } }
    val highlightedSettingId = LocalSettingHighlightId.current
    // Listen to SharedPreferences changes to update UI when values are restored
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "clipboard_retention_time" -> {
                    clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
                }
                "experimental_candidates_view_enabled" -> {
                    experimentalCandidatesViewEnabled = SettingsManager.getExperimentalCandidatesViewEnabled(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }


    fun navigateTo(destination: AdvancedDestination) {
        openSettingsChild(context, "advanced", when (destination) { AdvancedDestination.Main -> "Main"; AdvancedDestination.ImeTest -> "ImeTest"; AdvancedDestination.TrackpadGestures -> "TrackpadGestures" })
    }
    fun navigateBack() { context.settingsActivity().finish() }


    val destination = currentDestination
    when (destination) {
        AdvancedDestination.Main -> {
            Scaffold(
                topBar = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navigateBack() }) {
                                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_back_content_description)
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_privacy_clipboard_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsSectionDivider(stringResource(R.string.settings_section_privacy))
                    FluxSwitchRow(
                        linkId = SettingLinkIds.PRIVACY_INCOGNITO_ALWAYS,
                        title = stringResource(R.string.incognito_always_title),
                        description = stringResource(R.string.incognito_always_description),
                        checked = incognitoAlways,
                        onCheckedChange = {
                            incognitoAlways = it
                            SettingsManager.setIncognitoAlways(context, it)
                        }
                    )
                    if (!incognitoAlways) {
                        FluxSwitchRow(
                            linkId = SettingLinkIds.PRIVACY_INCOGNITO_FOLLOW_APPS,
                            title = stringResource(R.string.incognito_follow_apps_title),
                            description = stringResource(R.string.incognito_follow_apps_description),
                            checked = incognitoFollowApps,
                            onCheckedChange = {
                                incognitoFollowApps = it
                                SettingsManager.setIncognitoFollowApps(context, it)
                            }
                        )
                    }

                    SettingsSectionDivider(stringResource(R.string.settings_section_clipboard))
                    // Clipboard Retention Time
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .settingRow(SettingLinkIds.ADVANCED_CLIPBOARD_RETENTION_TIME)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.clipboard_retention_time_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.clipboard_retention_time_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedTextField(
                                value = clipboardRetentionTime,
                                onValueChange = { text ->
                                    val filtered = text.filter { it.isDigit() }.take(5)
                                    clipboardRetentionTime = filtered
                                },
                                placeholder = { Text("min") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.widthIn(max = 120.dp),
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val minutes = clipboardRetentionTime.toLongOrNull()
                                    if (minutes != null) {
                                        SettingsManager.setClipboardRetentionTime(context, minutes)
                                    }
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.clipboard_retention_apply)
                                )
                            }
                        }
                    }

                    SettingsSectionDivider(stringResource(R.string.settings_section_experimental))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingRow(SettingLinkIds.ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.experimental_candidates_view_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.experimental_candidates_view_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = experimentalCandidatesViewEnabled,
                                onCheckedChange = { enabled ->
                                    experimentalCandidatesViewEnabled = enabled
                                    SettingsManager.setExperimentalCandidatesViewEnabled(context, enabled)
                                }
                            )
                        }
                    }



                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        AdvancedDestination.ImeTest -> {
            ImeTestScreen(
                modifier = modifier,
                onBack = { navigateBack() }
            )
        }

        AdvancedDestination.TrackpadGestures -> {
            TrackpadGestureSettingsScreen(
                modifier = modifier,
                onBack = { navigateBack() }
            )
        }

    }

}

private sealed class AdvancedDestination {
    object Main : AdvancedDestination()
    object ImeTest : AdvancedDestination()
    object TrackpadGestures : AdvancedDestination()
}



private val TRACKPAD_SETTING_LINK_IDS = setOf(
    "trackpad.add_word",
    "trackpad.add_word_full_width",
    "trackpad.swipe_to_delete",
    "trackpad.swipe_to_delete_provider",
    "trackpad.suggestion_swipe_directions",
    "trackpad.phone_settings",
    SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
    SettingLinkIds.TRACKPAD_PROVIDER,
    SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
    SettingLinkIds.TRACKPAD_SENSITIVITY,
    SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_SIDE_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DEBUG
)
