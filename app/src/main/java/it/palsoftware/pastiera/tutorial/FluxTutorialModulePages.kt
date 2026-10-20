package it.palsoftware.pastiera.tutorial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.TutorialFeaturePageContent
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.settings.ReadinessCard

/**
 * The first run's choice: which modules you'll use. Each is switched on or off as it's picked,
 * and only the chosen ones' pages follow.
 */
@Composable
fun FluxTutorialModulesPageContent(chosen: Set<FluxModule>, onChange: (FluxModule, Boolean) -> Unit, modifier: Modifier = Modifier) {
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_modules_title),
        description = stringResource(R.string.flux_tutorial_modules_description),
        icon = Icons.Filled.Apps,
        tint = MaterialTheme.colorScheme.primary,
        bullets = emptyList(),
        // Next moves on: the choices are the page
        buttonText = null,
        onButtonClick = {},
        modifier = modifier,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModuleChoice(Icons.Filled.Keyboard, R.string.module_keyboard_title, R.string.flux_tutorial_modules_keyboard,
                    FluxModule.KEYBOARD in chosen) { onChange(FluxModule.KEYBOARD, it) }
                ModuleChoice(Icons.Filled.SportsEsports, R.string.game_mode_title, R.string.flux_tutorial_modules_gaming,
                    FluxModule.GAMING in chosen) { onChange(FluxModule.GAMING, it) }
                ModuleChoice(Icons.Filled.WifiTethering, R.string.module_streaming_title, R.string.flux_tutorial_modules_streaming,
                    FluxModule.STREAMING in chosen) { onChange(FluxModule.STREAMING, it) }
                Text(
                    stringResource(R.string.flux_tutorial_modules_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** One module to pick: its icon, what it does, and its switch (a tap anywhere toggles). */
@Composable
private fun ModuleChoice(icon: ImageVector, title: Int, text: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable { onChange(!on) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = on, onCheckedChange = onChange)
        }
    }
}

/** Gaming mode, chosen: what it does, what it still needs (each a tap from its fix), its settings. */
@Composable
fun FluxTutorialGamingPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.game_mode_title),
        description = stringResource(R.string.flux_tutorial_gaming_description),
        icon = Icons.Filled.SportsEsports,
        tint = MaterialTheme.colorScheme.tertiary,
        bullets = listOf(
            stringResource(R.string.flux_tutorial_gaming_bullet_keys),
            stringResource(R.string.flux_tutorial_gaming_bullet_games),
            stringResource(R.string.flux_tutorial_gaming_bullet_place)
        ),
        buttonText = stringResource(R.string.flux_tutorial_gaming_button),
        onButtonClick = { openTutorialSettingById(context, "main.root.game_mode") },
        modifier = modifier,
        extraContent = {
            ReadinessCard(FluxModule.GAMING, modifier = Modifier.padding(top = 8.dp), readyText = stringResource(R.string.flux_tutorial_gaming_ready))
        }
    )
}

/** Streaming, chosen: what it does, what it still needs (pairing included), its settings. */
@Composable
fun FluxTutorialStreamingPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.module_streaming_title),
        description = stringResource(R.string.flux_tutorial_streaming_description),
        icon = Icons.Filled.WifiTethering,
        tint = MaterialTheme.colorScheme.secondary,
        bullets = listOf(
            stringResource(R.string.flux_tutorial_streaming_bullet_pair),
            stringResource(R.string.flux_tutorial_streaming_bullet_apps),
            stringResource(R.string.flux_tutorial_streaming_bullet_tile)
        ),
        buttonText = stringResource(R.string.flux_tutorial_streaming_button),
        onButtonClick = { openTutorialSettingById(context, "main.streaming") },
        modifier = modifier,
        extraContent = {
            ReadinessCard(FluxModule.STREAMING, modifier = Modifier.padding(top = 8.dp), readyText = stringResource(R.string.flux_tutorial_streaming_ready))
        }
    )
}
