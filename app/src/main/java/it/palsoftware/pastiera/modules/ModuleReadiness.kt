package it.palsoftware.pastiera.modules

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.ImeIdentity
import it.palsoftware.pastiera.gaming.GameProfiles

/**
 * What a module still needs before it works, each with its fix on the module's page (the ready
 * checklist) and its tile's status on the home screen. Only what a module can't do without:
 * extras that ask for themselves when used aren't listed.
 */
object ModuleReadiness {
    enum class Need {
        /** The keyboard switched on in Android's keyboards. */
        KEYBOARD_ON,
        /** The keyboard chosen as the one in use. */
        KEYBOARD_CHOSEN,
        /** The keyboard's accessibility helper: gaming mode reads the keys through it. */
        ACCESSIBILITY,
        /** The built-in shell (or Shizuku) running. */
        SHELL,
        /** Drawing over other apps: placing keys on a game's own buttons. */
        OVERLAY,
        /** Notifications: gaming mode's (placing keys, profiles). */
        NOTIFICATIONS
    }

    data class ImeStatus(val enabled: Boolean, val selected: Boolean)

    /** Whether the keyboard is switched on in Android, and chosen as the one in use. */
    fun imeStatus(context: Context): ImeStatus = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val enabled = enabledMethods.any { it.packageName == ImeIdentity.packageName || ImeIdentity.matchesImeId(it.id) }
        if (!enabled) return@runCatching ImeStatus(false, false)
        val selected = runCatching {
            ImeIdentity.matchesImeId(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
        }.getOrElse {
            // Android 14 and later may refuse to say which keyboard is in use: the only one switched on is
            enabledMethods.size == 1
        }
        ImeStatus(true, selected)
    }.getOrDefault(ImeStatus(false, false))

    fun accessibilityOn(context: Context): Boolean = runCatching {
        it.palsoftware.pastiera.apps.isPastieraAccessibilityServiceEnabled(context)
    }.getOrDefault(false)

    fun shellOn(): Boolean = runCatching { it.palsoftware.pastiera.adb.AdbShell.available() }.getOrDefault(false)

    fun notificationsOn(context: Context): Boolean =
        it.palsoftware.pastiera.inputmethod.NotificationHelper.hasNotificationPermission(context)

    /** A game is played by keys placed on its screen (the placer draws over it). */
    private fun placesKeys(context: Context): Boolean = runCatching {
        GameProfiles.all(context).any { it.taps.isNotEmpty() }
    }.getOrDefault(false)

    /** A module's line on its home tile: what it still needs. */
    data class Status(val needs: List<Need>)

    fun status(context: Context, module: FluxModule): Status = Status(needs(context, module))

    /** What [module] needs that isn't there yet, most important first. */
    fun needs(context: Context, module: FluxModule): List<Need> = when (module) {
        FluxModule.KEYBOARD -> {
            val ime = imeStatus(context)
            listOfNotNull(
                Need.KEYBOARD_ON.takeIf { !ime.enabled },
                Need.KEYBOARD_CHOSEN.takeIf { ime.enabled && !ime.selected }
            )
        }
        FluxModule.GAMING -> listOfNotNull(
            Need.SHELL.takeIf { !shellOn() },
            Need.ACCESSIBILITY.takeIf { !accessibilityOn(context) },
            Need.OVERLAY.takeIf { placesKeys(context) && !Settings.canDrawOverlays(context) },
            Need.NOTIFICATIONS.takeIf { !notificationsOn(context) }
        )
        FluxModule.DESKTOP -> emptyList()
    }
}
