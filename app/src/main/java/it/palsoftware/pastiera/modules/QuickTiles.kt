package it.palsoftware.pastiera.modules

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.streaming.StreamHost
import it.palsoftware.pastiera.streaming.StreamHostService

/**
 * Quick Settings tiles for gaming mode and streaming: a tap switches it on or off, a long-press
 * opens its settings (the same page as its home tile's). Each says what it still needs, or
 * who it's streaming to.
 */
object QuickTiles {
    /** Both tiles brought up to date (a module, streaming or a stream changed). */
    fun refresh(context: Context) {
        listOf(GamingTileService::class.java, StreamingTileService::class.java).forEach { service ->
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
                ModuleReadiness.Need.PAIRED -> R.string.tile_need_paired
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

/** Streaming to Moonlight on or off (the streaming module switched on with it if it's off). */
class StreamingTileService : TileService() {
    override fun onStartListening() = update()

    override fun onClick() {
        val app = applicationContext
        val on = !StreamHostService.enabled(app)
        if (on && !FluxModules.on(app, FluxModule.STREAMING)) FluxModules.setOn(app, FluxModule.STREAMING, true)
        runCatching { StreamHostService.setEnabled(app, on) }
        update()
    }

    private fun update() {
        val on = FluxModules.on(this, FluxModule.STREAMING) && StreamHostService.enabled(this)
        val device = StreamHost.streamingDevice.takeIf { StreamHost.session != null }
        QuickTiles.show(qsTile, on, when {
            device != null -> getString(R.string.stream_now_to, device)
            on -> QuickTiles.needLine(this, FluxModule.STREAMING)
            else -> null
        })
    }
}

/** A Quick Settings tile long-pressed: its module's settings. */
class QuickTileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val tile = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        val destination = if (tile?.className == StreamingTileService::class.java.name)
            it.palsoftware.pastiera.settings.SettingsDestination.Streaming
        else it.palsoftware.pastiera.settings.SettingsDestination.GameMode
        runCatching {
            it.palsoftware.pastiera.settings.openSettingsPage(this, it.palsoftware.pastiera.settings.SettingsPage(destination))
        }
        finish()
    }
}
