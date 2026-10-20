package it.palsoftware.pastiera.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ImeIdentity
import it.palsoftware.pastiera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the keyboard still needs before it works, each with its fix on the ready checklist. */
private enum class Need {
    /** The keyboard switched on in Android's keyboards. */
    KEYBOARD_ON,
    /** The keyboard chosen as the one in use. */
    KEYBOARD_CHOSEN
}

/** What the keyboard needs that isn't there yet, most important first. */
private fun needs(context: Context): List<Need> = runCatching {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledMethods = imm.enabledInputMethodList
    val enabled = enabledMethods.any { it.packageName == ImeIdentity.packageName || ImeIdentity.matchesImeId(it.id) }
    if (!enabled) return@runCatching listOf(Need.KEYBOARD_ON)
    val selected = runCatching {
        ImeIdentity.matchesImeId(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
    }.getOrElse {
        // Android 14 and later may refuse to say which keyboard is in use: the only one switched on is
        enabledMethods.size == 1
    }
    listOfNotNull(Need.KEYBOARD_CHOSEN.takeIf { !selected })
}.getOrDefault(listOf(Need.KEYBOARD_ON))

/**
 * The keyboard's ready checklist, at the top of its page: only what it still needs, each a tap
 * from its fix; nothing at all once it's ready. Checked again on coming back and every few seconds.
 */
@Composable
internal fun ReadinessCard(modifier: Modifier = Modifier, readyText: String? = null) {
    val context = LocalContext.current
    var needs by remember { mutableStateOf(needs(context)) }
    var check by remember { mutableStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(check) {
        while (true) {
            needs = withContext(Dispatchers.IO) { needs(context) }
            delay(3_000)
        }
    }
    if (needs.isEmpty()) {
        // Ready: one quiet line, where the page says so
        if (readyText != null) Text(
            "\u2713 " + readyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.readiness_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            needs.forEach { need -> NeedRow(need) { check++ } }
        }
    }
}

/** One thing still needed: what it is, why, and a tap to fix it. */
@Composable
private fun NeedRow(need: Need, onDone: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(when (need) {
        Need.KEYBOARD_ON -> R.string.enable_pastiera
        Need.KEYBOARD_CHOSEN -> R.string.readiness_keyboard_chosen
    })
    val description = when (need) {
        Need.KEYBOARD_ON -> stringResource(R.string.readiness_keyboard_on_description)
        Need.KEYBOARD_CHOSEN -> stringResource(R.string.readiness_keyboard_chosen_description)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { fix(context, need); onDone() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("○", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

/** The fix for [need]: the page where it's switched on, or the switch itself. */
private fun fix(context: Context, need: Need) {
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    when (need) {
        Need.KEYBOARD_ON -> open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        Need.KEYBOARD_CHOSEN -> runCatching {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
    }
}
