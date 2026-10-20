package it.palsoftware.pastiera.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's last times not responding, with where each thread was (its main thread first), kept
 * where the shell can read them; null when there are none.
 */
private fun anrTraces(context: android.content.Context): java.io.File? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(android.app.ActivityManager::class.java) ?: return null
    val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        .filter { it.reason == android.app.ApplicationExitInfo.REASON_ANR }.take(2)
    if (exits.isEmpty()) return null
    val dir = context.getExternalFilesDir(null) ?: return null
    val file = java.io.File(dir, "anr-traces.txt")
    file.bufferedWriter().use { out ->
        for (exit in exits) {
            out.write("--- ${java.util.Date(exit.timestamp)}: ${exit.description}\n")
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // The main thread, then the rest up to a size worth sending
            val main = Regex("(?s)\"main\".*?\n\n").find(trace)?.value.orEmpty()
            out.write(main)
            out.write(trace.take(60_000))
            out.write("\n")
        }
    }
    file
}.getOrNull()

/** Android's record of the last crashes (Flux Keyboard's and its web pages'), saved to share. */
@Composable
internal fun CrashLogRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = "system.crash_log",
        icon = "\u26A0",
        title = stringResource(R.string.crash_log),
        description = stringResource(R.string.crash_log_description)
    ) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val file = "/storage/emulated/0/Download/Pastiera/crash-log.txt"
                val app = it.palsoftware.pastiera.BuildConfig.APPLICATION_ID
                // When the app stopped answering: where its threads were, which only the app can read
                val traces = anrTraces(context)
                it.palsoftware.pastiera.adb.AdbShell.run(
                    "f='$file'; mkdir -p \"\${f%/*}\"; { " +
                        // Why Android last ended the app: a crash, memory running low
                        "echo '== why it stopped =='; dumpsys activity exit-info $app | head -n 120; " +
                        "echo '== crashes =='; logcat -d -b crash -t 400; " +
                        "echo '== warnings and errors =='; logcat -d -b main,system '*:W' 'ActivityTaskManager:I' | " +
                        "grep -i -E '$app|Permission Denial|quickstep|launcher3|Recents|OverviewProxy|AndroidRuntime|lowmemorykiller|has died|Killing' | grep -v 'Changing focus' | tail -n 400; " +
                        (if (traces != null) "echo '== not responding: threads =='; cat '${traces.path}'; " else "") +
                        // Everything through a pipe: Android's services can't write into a file in Download
                        "} 2>&1 | cat > \"\$f\"; echo ok",
                    15_000
                )?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (saved) R.string.crash_log_saved else R.string.adb_not_running, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    // A fresh start: Android's logs and Flux Keyboard's own record emptied, so the next crash
    // log holds only what happens from now (and saves quickly)
    FluxActionRow(
        linkId = "system.clear_logs",
        icon = "\u2715",
        title = stringResource(R.string.crash_log_clear),
        description = stringResource(R.string.crash_log_clear_description)
    ) {
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(context.getExternalFilesDir(null), it.palsoftware.pastiera.FluxLog.FILE_NAME).delete()
                }
                it.palsoftware.pastiera.adb.AdbShell.run("logcat -c -b main -b system -b crash; echo ok", 10_000)?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (cleared) R.string.crash_log_cleared else R.string.adb_not_running, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

