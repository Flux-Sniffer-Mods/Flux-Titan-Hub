package it.palsoftware.pastiera.modules

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GamesActivity

/** Flux Titan Hub's modules: a tile each on its home screen. */
enum class FluxModule { KEYBOARD, GAMING, DESKTOP }

/**
 * Which modules are on. The keyboard and gaming mode are switched on when wanted (their code
 * comes with the app: switching one on adds its parts, such as the keyboard itself and the Games
 * app in the app list, and off takes them away); the keyboard is on unless switched off. The
 * desktop is still to come. Everything else asks here before doing a module's work.
 */
object FluxModules {
    private const val TAG = "FluxModules"
    private const val KEY_GAMING = "module_gaming"
    private const val KEY_KEYBOARD = "module_keyboard"
    /** The keyboard itself, offered to Android as a keyboard while the module is on. */
    private const val KEYBOARD_SERVICE = "it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService"
    private const val KEY_SET_UP = "modules_set_up"

    fun on(context: Context, module: FluxModule): Boolean = when (module) {
        FluxModule.KEYBOARD -> SettingsManager.getPreferences(context).getBoolean(KEY_KEYBOARD, true)
        FluxModule.DESKTOP -> false
        FluxModule.GAMING -> SettingsManager.getPreferences(context).getBoolean(KEY_GAMING, false)
    }

    /** Switched on and off from its tile: not the desktop (to come). */
    fun switchable(module: FluxModule): Boolean = module != FluxModule.DESKTOP

    /** Coming in a later version: shown, not opened. */
    fun comingSoon(module: FluxModule): Boolean = module == FluxModule.DESKTOP

    fun setOn(context: Context, module: FluxModule, on: Boolean) {
        if (!switchable(module)) return
        val app = context.applicationContext
        val key = when (module) { FluxModule.GAMING -> KEY_GAMING; else -> KEY_KEYBOARD }
        SettingsManager.getPreferences(app).edit().putBoolean(key, on).apply()
        when (module) {
            // Gaming mode itself, and the Games app in the app list, come and go with it
            FluxModule.GAMING -> {
                GameProfiles.setEnabled(app, on)
                GamesActivity.setEnabled(app, on)
            }
            // Off: Android no longer offers it as a keyboard (another takes over if it was in
            // use), and its entry leaves the app list
            FluxModule.KEYBOARD -> {
                app.packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(app, KEYBOARD_SERVICE),
                    if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                if (!on && it.palsoftware.pastiera.KeyboardActivity.inAppList(app)) it.palsoftware.pastiera.KeyboardActivity.setInAppList(app, false)
            }
            else -> Unit
        }
        // Its Quick Settings tile kept in step
        QuickTiles.refresh(app)
        FluxLog.w(TAG, "${module.name.lowercase()} ${if (on) "on" else "off"}")
    }

    /**
     * Once, as this version first starts: a fresh install has the keyboard alone; an update keeps
     * on what's in use (gaming mode or the Games app).
     */
    fun setUp(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_SET_UP, false)) return
        val gaming = GameProfiles.enabled(context) || GamesActivity.enabled(context)
        prefs.edit().putBoolean(KEY_GAMING, gaming).putBoolean(KEY_SET_UP, true).apply()
        FluxLog.w(TAG, "modules set up: gaming ${if (gaming) "on" else "off"}")
    }
}
