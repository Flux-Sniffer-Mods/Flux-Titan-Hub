package it.palsoftware.pastiera.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.ShizukuStatus
import it.palsoftware.pastiera.resolveShizukuStatus

/** Shell access: Shizuku, which the keyboard's extras run their commands through. */
@Composable
fun AdbSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val shell = rememberShellState()
    var status by shell.status
    fun refresh() = shell.refresh()
    // Shizuku's answer to the permission request
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> status = resolveShizukuStatus() }
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) } }
    }
    // A setting that uses the shell, opened where it is
    fun jump(id: String) = context.startActivity(
        android.content.Intent(context, SettingsActivity::class.java).setData(android.net.Uri.parse("pastiera://setting/$id"))
    )

    FluxScreenScaffold(stringResource(R.string.root_title), onBack, modifier) {
        FluxNote(
            stringResource(
                when (status) {
                    ShizukuStatus.Connected -> R.string.root_note
                    ShizukuStatus.NotAuthorized -> R.string.adb_not_allowed
                    ShizukuStatus.NotConnected -> R.string.adb_not_running
                }
            )
        )
        if (status == ShizukuStatus.NotAuthorized) {
            FluxActionRow(
                linkId = null,
                title = stringResource(R.string.adb_allow_title),
                description = stringResource(R.string.adb_allow_description)
            ) {
                it.palsoftware.pastiera.adb.ShizukuPermission.request(context) { status = resolveShizukuStatus() }
            }
        }
        // What uses it, each opened where it is
        SettingsSectionDivider(stringResource(R.string.shell_elsewhere_section))
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.trackpad_provider_title),
            description = stringResource(R.string.shell_elsewhere_trackpad)
        ) { jump(SettingLinkIds.TRACKPAD_PROVIDER) }
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.one_time_codes_title),
            description = stringResource(R.string.shell_elsewhere_otp)
        ) { jump(SettingLinkIds.PRIVACY_ONE_TIME_CODES) }
    }
}

/** Whether the shell can run commands, re-read whenever the screen comes back. */
internal class ShellState {
    val status = mutableStateOf(resolveShizukuStatus())
    fun refresh() {
        status.value = resolveShizukuStatus()
    }
}

@Composable
internal fun rememberShellState(): ShellState {
    val state = remember { ShellState() }
    // Started (or allowed) while this screen was away: checked again on coming back
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * Shell access where a page needs it: [why] it's needed there, whether it's running, and a tap
 * to its page to set it up or start it.
 */
@Composable
internal fun ShellAccessRow(why: Int) {
    val context = LocalContext.current
    val shell = rememberShellState()
    val status by shell.status
    val state = stringResource(
        if (status == ShizukuStatus.Connected) R.string.shell_access_on else R.string.shell_access_off
    )
    FluxActionRow(
        linkId = null,
        icon = SHELL_ICON,
        title = stringResource(R.string.root_title),
        description = stringResource(why) + " " + state
    ) { openSettingsPage(context, SettingsPage(SettingsDestination.Root)) }
}

/** The shell's mark on its rows: a prompt. */
private const val SHELL_ICON = "\u276F"
