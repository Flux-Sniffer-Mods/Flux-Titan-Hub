package it.palsoftware.pastiera

import it.palsoftware.pastiera.data.layout.LayoutRepositoryManager
import it.palsoftware.pastiera.dictionaries.DictionaryRepositoryManager
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineModeTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun backOnline() {
        SettingsManager.setOfflineMode(context, false)
    }

    @Test
    fun offlineModeStopsEveryRequest() {
        SettingsManager.setOfflineMode(context, true)

        assertTrue(OfflineMode.enabled)
        assertFalse(shouldUseGithubUpdateChecks(context))
        runBlocking {
            assertTrue(DictionaryRepositoryManager.fetchManifest().isFailure)
            assertTrue(LayoutRepositoryManager.fetchManifest().isFailure)
        }
    }

    @Test
    fun turningItOffGoesBackOnline() {
        SettingsManager.setOfflineMode(context, true)

        SettingsManager.setOfflineMode(context, false)

        assertFalse(OfflineMode.enabled)
    }
}
