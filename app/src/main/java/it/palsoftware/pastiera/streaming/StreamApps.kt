package it.palsoftware.pastiera.streaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import it.palsoftware.pastiera.SettingsManager

/**
 * The Streaming app's apps: every app the phone can open, to show on the TV while streaming, and
 * Moonlight's own list beyond the games: every app (or the ones added in the Streaming app), and
 * Termux:Widget's scripts if wanted, each started straight from there on a screen of its own.
 */
object StreamApps {
    private const val KEY_LISTED = "stream_apps_listed"
    private const val KEY_IN_APP_LIST = "streaming_app_enabled"
    private const val KEY_ALL_APPS = "stream_apps_all"
    private const val KEY_TERMUX = "stream_apps_termux"
    /** Its entry in the app list, shown with Streaming in the app list on. */
    private const val LAUNCHER_ALIAS = "it.palsoftware.pastiera.streaming.StreamingLauncher"

    data class App(val packageName: String, val label: String)

    /** Every app with a screen to open, A to Z (not Flux Titan Hub itself). */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .map { App(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    fun listed(context: Context): Set<String> =
        SettingsManager.getPreferences(context).getStringSet(KEY_LISTED, emptySet()).orEmpty()

    fun setListed(context: Context, packageName: String, on: Boolean) {
        val now = listed(context).let { if (on) it + packageName else it - packageName }
        SettingsManager.getPreferences(context).edit().putStringSet(KEY_LISTED, now).apply()
    }

    /** Every app in Moonlight's list (on), or only the ones added in the Streaming app. */
    fun allApps(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ALL_APPS, false)

    fun setAllApps(context: Context, on: Boolean) = SettingsManager.getPreferences(context).edit().putBoolean(KEY_ALL_APPS, on).apply()

    /** Termux:Widget's scripts in Moonlight's list too. */
    fun termux(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_TERMUX, false)

    fun setTermux(context: Context, on: Boolean) = SettingsManager.getPreferences(context).edit().putBoolean(KEY_TERMUX, on).apply()

    /** The apps in Moonlight's list (besides [skip], the games' own apps, listed as games already). */
    fun inMoonlight(context: Context, skip: Set<String> = emptySet()): List<App> {
        val all = installed(context)
        val listed = listed(context)
        return (if (allApps(context)) all else all.filter { it.packageName in listed }).filter { it.packageName !in skip }
    }

    /** Termux:Widget's terminal scripts in Moonlight's list (not its background tasks: nothing to show). */
    fun scripts(context: Context): List<String> =
        if (!termux(context) || !it.palsoftware.pastiera.shortcuts.TermuxScripts.available(context)) emptyList()
        else it.palsoftware.pastiera.shortcuts.TermuxScripts.found(context).filter { !it.startsWith("tasks/") }

    /** An app's number in Moonlight's list: the same every time, apart from the phone's own entries. */
    fun idOf(packageName: String): Int = ("app|$packageName".hashCode() and 0x7FFFFFFF).coerceAtLeast(100)

    /** A script's number in Moonlight's list. */
    fun scriptId(script: String): Int = ("termux|$script".hashCode() and 0x7FFFFFFF).coerceAtLeast(100)

    /**
     * An app tapped in the Streaming app with nothing streaming: the TV is asked to start it, so
     * it's put in Moonlight's list until its stream ends, so Moonlight finds it.
     */
    @Volatile var requested: String? = null

    /** A Termux script tapped in the Streaming app with nothing streaming, as [requested]. */
    @Volatile var requestedScript: String? = null

    /** The app Moonlight asked for by its number, if it's in its list (or just asked for) and still installed. */
    fun byId(context: Context, id: Int): String? =
        requested?.takeIf { idOf(it) == id && context.packageManager.getLaunchIntentForPackage(it) != null }
            ?: inMoonlight(context).firstOrNull { idOf(it.packageName) == id }?.packageName

    /** The script Moonlight asked for by its number. */
    fun scriptById(context: Context, id: Int): String? =
        requestedScript?.takeIf { scriptId(it) == id } ?: scripts(context).firstOrNull { scriptId(it) == id }

    fun label(context: Context, packageName: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun inAppList(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_IN_APP_LIST, false)

    /** Streaming in the app list (on), or not. */
    fun setInAppList(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_IN_APP_LIST, on).apply()
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, LAUNCHER_ALIAS),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
