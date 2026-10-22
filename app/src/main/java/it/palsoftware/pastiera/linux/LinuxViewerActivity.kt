package it.palsoftware.pastiera.linux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.R

/**
 * The Linux desktop, full screen: its picture from the VNC server on this phone, the keyboard,
 * touch and mouse sent back. Back leaves it (the desktop keeps running); opened on a TV screen,
 * it shows there.
 */
class LinuxViewerActivity : Activity() {
    private lateinit var view: DesktopView
    private lateinit var message: TextView
    @Volatile private var client: VncClient? = null
    @Volatile private var open = true
    /** Each key's keysym as it went down, so it's let go as the same. */
    private val held = HashMap<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DesktopView(this)
        message = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            text = getString(R.string.linux_viewer_connecting)
        }
        setContentView(FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view.requestFocus()
        Thread({ connect() }, "LinuxViewer").apply { isDaemon = true; start() }
    }

    private fun connect() {
        val password = LinuxDesktop.password(this)
        // The server may still be starting: tried for half a minute
        val until = System.currentTimeMillis() + 30_000
        var vnc: VncClient? = null
        while (open && vnc == null && System.currentTimeMillis() < until) {
            vnc = runCatching {
                VncClient.connect("127.0.0.1", LinuxDesktop.PORT,
                    onFrame = { x, y, w, h -> view.changed(x, y, w, h) },
                    onResize = { w, h -> view.post { view.resize(w, h) } },
                    onClipboard = { text -> runOnUiThread { copied(text) } }
                ).also { if (!it.handshake(password)) { it.close(); throw IllegalStateException("password refused") } }
            }.getOrNull()
            if (vnc == null) Thread.sleep(700)
        }
        if (vnc == null) { show(R.string.linux_viewer_unreachable); return }
        client = vnc
        view.attach(vnc)
        runOnUiThread { message.visibility = android.view.View.GONE }
        runCatching { vnc.run() }.onFailure { if (open) FluxLog.w("FluxLinux", "viewer: $it") }
        if (open) show(R.string.linux_viewer_ended)
    }

    private fun show(text: Int) = runOnUiThread {
        message.text = getString(text)
        message.visibility = android.view.View.VISIBLE
    }

    /** Text copied in Linux: the phone's clipboard too. */
    private fun copied(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Linux", text))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vnc = client ?: return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        // Volume stays the phone's
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Ctrl+V pastes the phone's clipboard into Linux's first
                if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V && event.repeatCount == 0) paste(vnc)
                val keysym = keysymOf(event)
                if (keysym == 0) return super.dispatchKeyEvent(event)
                held[event.keyCode] = keysym
                vnc.key(keysym, true)
            }
            KeyEvent.ACTION_UP -> {
                val keysym = held.remove(event.keyCode) ?: keysymOf(event)
                if (keysym == 0) return super.dispatchKeyEvent(event)
                vnc.key(keysym, false)
            }
            KeyEvent.ACTION_MULTIPLE -> event.characters?.codePoints()?.forEach { char ->
                val keysym = Keysyms.of(char)
                if (keysym != 0) { vnc.key(keysym, true); vnc.key(keysym, false) }
            }
        }
        return true
    }

    private fun keysymOf(event: KeyEvent): Int {
        val unicode = event.getUnicodeChar(event.metaState)
        // Held with Ctrl or Alt there's no character: the key's own
        return if (unicode > 0 && !event.isCtrlPressed) Keysyms.of(event.keyCode, unicode)
        else Keysyms.of(event.keyCode, 0).takeIf { it != 0 } ?: Keysyms.base(event)
    }

    private fun paste(vnc: VncClient) {
        val text = getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) vnc.clipboard(text)
    }

    override fun onDestroy() {
        open = false
        client?.close()
        super.onDestroy()
    }
}
