package it.palsoftware.pastiera.settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R

/**
 * Settings > Backup & restore: the recommended settings ([RecommendedSettings]), with
 * an Apply button that says how many would change.
 */
@Composable
internal fun RecommendedSettingsRow() {
    val context = LocalContext.current
    var differing by remember { mutableStateOf<List<RecommendedSettings.Change>?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .settingRow(SettingLinkIds.RECOMMENDED_SETTINGS)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recommended_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.recommended_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = { differing = RecommendedSettings.changes(context) }) {
                Text(stringResource(R.string.recommended_settings_apply))
            }
        }
    }
    differing?.let { changes ->
        AlertDialog(
            onDismissRequest = { differing = null },
            title = { Text(stringResource(R.string.recommended_settings_title)) },
            text = {
                // What would change, each from your setting to the recommended one
                Column(
                    modifier = Modifier.heightIn(max = 420.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        if (changes.isEmpty()) stringResource(R.string.recommended_settings_nothing)
                        else pluralStringResource(R.plurals.recommended_settings_differ, changes.size, changes.size),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    RecommendedChangesList(changes)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    differing = null
                    if (!RecommendedSettings.apply(context)) {
                        Toast.makeText(context, R.string.recommended_settings_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.recommended_settings_apply)) }
            },
            dismissButton = { TextButton(onClick = { differing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
