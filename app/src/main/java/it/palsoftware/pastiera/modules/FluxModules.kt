package it.palsoftware.pastiera.modules

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager

/** Flux Titan Hub's modules: a tile each on its home screen. */
enum class FluxModule { KEYBOARD, DESKTOP }

/**
 * Which modules are on. The keyboard is on unless switched off (off takes its parts away: the
 * keyboard itself and its entry in the app list). The desktop is still to come. Everything else
 * asks here before doing a module's work.
 */
object FluxModules {
    private const val TAG = "FluxModules"
    private const val KEY_KEYBOARD = "module_keyboard"
    /** The keyboard itself, offered to Android as a keyboard while the module is on. */
    private const val KEYBOARD_SERVICE = "it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService"

    fun on(context: Context, module: FluxModule): Boolean = when (module) {
        FluxModule.KEYBOARD -> SettingsManager.getPreferences(context).getBoolean(KEY_KEYBOARD, true)
        FluxModule.DESKTOP -> false
    }

    /** Switched on and off from its tile: not the desktop (to come). */
    fun switchable(module: FluxModule): Boolean = module != FluxModule.DESKTOP

    /** Coming in a later version: shown, not opened. */
    fun comingSoon(module: FluxModule): Boolean = module == FluxModule.DESKTOP

    fun setOn(context: Context, module: FluxModule, on: Boolean) {
        if (!switchable(module)) return
        val app = context.applicationContext
        SettingsManager.getPreferences(app).edit().putBoolean(KEY_KEYBOARD, on).apply()
        when (module) {
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
        FluxLog.w(TAG, "${module.name.lowercase()} ${if (on) "on" else "off"}")
    }
}
