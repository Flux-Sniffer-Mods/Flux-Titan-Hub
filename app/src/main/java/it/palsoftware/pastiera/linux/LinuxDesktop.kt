package it.palsoftware.pastiera.linux

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.adb.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * The Linux desktop: Debian with Xfce, run by the built-in shell. With root (Shell access through
 * su, any root manager) it's a chroot, at full speed; without, PRoot (built into the app) runs it
 * as the shell user, slower but needing nothing. Its picture is a VNC server on the phone itself
 * (127.0.0.1:5901, a fresh password each start), shown by [LinuxViewerActivity] on the phone or
 * on a TV screen. Blocking calls run on threads of their own; [state] says how it's going.
 */
object LinuxDesktop {
    private const val TAG = "FluxLinux"
    private const val KEY_MODE = "linux_mode"
    private const val KEY_PASSWORD = "linux_vnc_password"
    const val PORT = 5901
    private const val ROOTFS_URL =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/dist-arm64v8/bookworm/oci/blobs/rootfs.tar.gz"

    enum class Mode { PROOT, CHROOT }

    sealed class State {
        object Checking : State()
        object NotInstalled : State()
        data class Downloading(val percent: Int) : State()
        object Installing : State()
        object Stopped : State()
        object Starting : State()
        object Running : State()
        object Stopping : State()
        data class Failed(val step: Step) : State()
    }

    enum class Step { NO_SHELL, NO_ROOT, DOWNLOAD, INSTALL, START }

    private val mutableState = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = mutableState
    /** The last lines the install or the desktop printed, for a look when something fails. */
    private val mutableLog = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = mutableLog

    @Volatile private var session: Process? = null

    private fun set(value: State) { mutableState.value = value }

    private fun line(text: String) {
        mutableLog.value = (mutableLog.value + text).takeLast(200)
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    /** How it's installed; before that, chroot where root is on, else PRoot. */
    fun mode(context: Context): Mode = prefs(context).getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        ?: if (RootShell.active) Mode.CHROOT else Mode.PROOT

    fun installedMode(context: Context): Mode? = prefs(context).getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }

    /** What runs it is there: root for a chroot, the shell for PRoot. */
    fun canRun(mode: Mode): Boolean = if (mode == Mode.CHROOT) RootShell.active else AdbShell.available()

    private fun base(mode: Mode) = if (mode == Mode.CHROOT) "/data/local/flux-linux" else "/data/local/tmp/flux-linux"

    private fun files(context: Context): File = File(context.getExternalFilesDir(null), "linux").apply { mkdirs() }

    private fun tarball(context: Context) = File(files(context), "debian-rootfs.tar.gz")

    /** A file of the app's as the shell (or root, through Android's own storage) reaches it. */
    private fun reach(file: File, mode: Mode): String {
        val path = file.absolutePath
        return if (mode == Mode.CHROOT) path.replace(Regex("^/storage/emulated/(\\d+)/"), "/data/media/$1/") else path
    }

    /** The scripts, copied where the shell can read them. */
    private fun scripts(context: Context): Pair<File, File> {
        val dir = files(context)
        fun copy(name: String) = File(dir, name).also { out ->
            context.assets.open("fork/linux/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return copy("flux-linux.sh") to copy("flux-guest.sh")
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun command(context: Context, mode: Mode, action: String, extra: Map<String, String> = emptyMap()): String {
        val (script, guest) = scripts(context)
        val env = linkedMapOf(
            "MODE" to if (mode == Mode.CHROOT) "chroot" else "proot",
            "BASE" to base(mode),
            "TARBALL" to reach(tarball(context), mode),
            "LIBDIR" to context.applicationInfo.nativeLibraryDir,
            "GUEST" to reach(guest, mode)
        ) + extra
        return env.entries.joinToString(" ") { "${it.key}=${quote(it.value)}" } + " sh ${quote(reach(script, mode))} $action 2>&1"
    }

    /** Runs [action], each line it prints to [log]; its exit status (-1 when it couldn't start). */
    private fun run(context: Context, mode: Mode, action: String, extra: Map<String, String> = emptyMap(), keep: Boolean = false): Int {
        return runCatching {
            val process = AdbShell.newProcess(arrayOf("sh", "-c", command(context, mode, action, extra)))
            if (keep) session = process
            process.inputStream.bufferedReader().forEachLine { text ->
                line(text)
                if (text == "flux: starting") set(State.Starting)
            }
            process.waitFor()
        }.onFailure { FluxLog.w(TAG, "$action: $it") }.getOrDefault(-1)
    }

    private fun background(work: () -> Unit) = Thread({
        runCatching(work).onFailure { FluxLog.w(TAG, "$it") }
    }, "FluxLinux").apply { isDaemon = true; start() }

    /** Looks again: installed, running or neither. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        background {
            val mode = installedMode(app)
            if (mode == null) { set(State.NotInstalled); return@background }
            if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return@background }
            val out = AdbShell.run(command(app, mode, "status"), 8_000).orEmpty()
            set(when {
                "not-installed" in out -> State.NotInstalled
                "running" in out -> State.Running
                else -> State.Stopped
            })
        }
    }

    /** Downloads Debian (once) and sets the desktop up in [mode]: some minutes, most of it downloading. */
    fun install(context: Context, mode: Mode) {
        val app = context.applicationContext
        if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return }
        mutableLog.value = emptyList()
        LinuxService.start(app)
        background {
            try {
                if (!download(app)) { set(State.Failed(Step.DOWNLOAD)); return@background }
                set(State.Installing)
                val result = run(app, mode, "install")
                if (result != 0) { set(State.Failed(Step.INSTALL)); return@background }
                prefs(app).edit().putString(KEY_MODE, mode.name).apply()
                // The download's no longer needed: Debian is unpacked
                tarball(app).delete()
                set(State.Stopped)
            } finally {
                LinuxService.idle(app)
            }
        }
    }

    private fun download(context: Context): Boolean {
        val target = tarball(context)
        if (target.isFile && target.length() > 10_000_000) return true
        val part = File(target.path + ".part")
        set(State.Downloading(0))
        line("flux: downloading Debian")
        return runCatching {
            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
            // A download cut short goes on from where it stopped
            val have = if (part.isFile) part.length() else 0L
            val request = Request.Builder().url(ROOTFS_URL).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val resumed = response.code == 206
                val body = response.body ?: throw IllegalStateException("empty")
                val total = (if (resumed) have else 0L) + body.contentLength()
                var done = if (resumed) have else 0L
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    val input = body.byteStream()
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != lastPercent) { lastPercent = percent; set(State.Downloading(percent)) }
                    }
                }
            }
            part.renameTo(target)
        }.onFailure { line("flux: download failed: $it"); FluxLog.w(TAG, "download: $it") }.getOrDefault(false)
    }

    /** The VNC password for this run (new each start). */
    fun password(context: Context): String = prefs(context).getString(KEY_PASSWORD, null).orEmpty()

    /** Starts the desktop at [width] x [height]; [onReady] (on its own thread) once its picture can be seen. */
    fun start(context: Context, width: Int, height: Int, onReady: () -> Unit = {}) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return }
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val password = String(CharArray(8) { alphabet[random.nextInt(alphabet.length)] })
        prefs(app).edit().putString(KEY_PASSWORD, password).apply()
        mutableLog.value = emptyList()
        set(State.Starting)
        LinuxService.start(app)
        background {
            run(app, mode, "start", mapOf("GEOMETRY" to "${width and 1.inv()}x${height and 1.inv()}", "PASSWORD" to password), keep = true)
            session = null
            // Ended before its picture ever came: it couldn't start (the log says why)
            set(if (state.value == State.Starting) State.Failed(Step.START) else State.Stopped)
            LinuxService.idle(app)
        }
        // Ready once the VNC server answers
        background {
            val until = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < until && (state.value == State.Starting)) {
                if (listening()) { set(State.Running); LinuxService.idle(app); onReady(); return@background }
                Thread.sleep(500)
            }
        }
    }

    /** The desktop's VNC server answers. */
    fun listening(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 500); true }
    }.getOrDefault(false)

    fun stop(context: Context) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        set(State.Stopping)
        background {
            run(app, mode, "stop")
            runCatching { session?.destroy() }
            session = null
            set(State.Stopped)
            LinuxService.idle(app)
        }
    }

    /** Deletes Debian and everything in it (the phone's own files, mounted there, are left alone). */
    fun remove(context: Context) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        set(State.Stopping)
        background {
            val result = run(app, mode, "remove")
            if (result == 0) {
                prefs(app).edit().remove(KEY_MODE).apply()
                tarball(app).delete()
                File(tarball(app).path + ".part").delete()
                set(State.NotInstalled)
            } else set(State.Stopped)
        }
    }

    /** The desktop's picture opened: on [displayId] (a TV screen), or this phone's. */
    fun show(context: Context, displayId: Int? = null) {
        val intent = Intent(context, LinuxViewerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (displayId == null) context.startActivity(intent)
        else Thread { runCatching { it.palsoftware.pastiera.display.ExtraScreen.bring(context.applicationContext, intent, displayId) } }.start()
    }
}
