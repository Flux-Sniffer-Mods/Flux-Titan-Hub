package it.palsoftware.pastiera.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchCategoriesTest {
    @Test
    fun everyPageHasAMainCategory() {
        SettingsDestination.values().forEach { page ->
            assertTrue(page.name, SettingLinkRegistry.categoryOf(page) in SettingLinkRegistry.categories)
        }
        assertEquals(SettingsDestination.Typing, SettingLinkRegistry.categoryOf(SettingsDestination.AutoCorrection))
        assertEquals(SettingsDestination.Apps, SettingLinkRegistry.categoryOf(SettingsDestination.Customization))
        // What every module shares is under Settings; the keyboard's privacy page under its own heading
        assertEquals(SettingsDestination.Main, SettingLinkRegistry.categoryOf(SettingsDestination.FluxOffline))
        assertEquals(SettingsDestination.Main, SettingLinkRegistry.categoryOf(SettingsDestination.AppLanguage))
        assertEquals(SettingsDestination.Advanced, SettingLinkRegistry.categoryOf(SettingsDestination.Advanced))
        assertEquals(SettingsDestination.ShellExtras, SettingLinkRegistry.categoryOf(SettingsDestination.ShellExtras))
        SettingLinkRegistry.categories.forEach { assertTrue(it.name, it in SettingLinkRegistry.destinationTitles) }
    }
}
