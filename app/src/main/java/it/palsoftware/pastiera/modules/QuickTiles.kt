package it.palsoftware.pastiera.modules

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import it.palsoftware.pastiera.R

/**
 * A Quick Settings tile for gaming mode: a tap switches it on or off, a long-press opens its
 * settings (the same page as its home tile's). It says what it still needs.
 */
object QuickTiles {
    /** The tiles brought up to date (a module changed). */
    fun refresh(context: Context) {
        listOf(GamingTileService::class.java).forEach { service ->
            runCatching { TileService.requestListeningState(context, ComponentName(context, service)) }
        }
    }

    /** The first thing [module] still needs, as its tile's line. */
    internal fun needLine(context: Context, module: FluxModule): String? =
        ModuleReadiness.needs(context, module).firstOrNull()?.let { need ->
            context.getString(when (need) {
                ModuleReadiness.Need.KEYBOARD_ON -> R.string.tile_need_keyboard_on
                ModuleReadiness.Need.KEYBOARD_CHOSEN -> R.string.tile_need_keyboard_chosen
                ModuleReadiness.Need.SHELL -> R.string.tile_need_shell
                ModuleReadiness.Need.ACCESSIBILITY -> R.string.tile_need_accessibility
                ModuleReadiness.Need.OVERLAY -> R.string.tile_need_overlay
                ModuleReadiness.Need.NOTIFICATIONS -> R.string.tile_need_notifications
            })
        }

    internal fun show(tile: Tile?, on: Boolean, subtitle: String?) {
        tile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Nothing to add: the system says On or Off itself
        tile.subtitle = subtitle
        tile.updateTile()
    }
}

/** Gaming mode on or off. */
class GamingTileService : TileService() {
    override fun onStartListening() = update()

    override fun onClick() {
        FluxModules.setOn(this, FluxModule.GAMING, !FluxModules.on(this, FluxModule.GAMING))
        update()
    }

    private fun update() {
        val on = FluxModules.on(this, FluxModule.GAMING)
        QuickTiles.show(qsTile, on, if (on) QuickTiles.needLine(this, FluxModule.GAMING) else null)
    }
}

/** A Quick Settings tile long-pressed: its module's settings. */
class QuickTileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = it.palsoftware.pastiera.settings.SettingsDestination.GameMode
        runCatching {
            it.palsoftware.pastiera.settings.openSettingsPage(this, it.palsoftware.pastiera.settings.SettingsPage(destination))
        }
        finish()
    }
}
