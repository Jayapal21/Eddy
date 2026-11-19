package app.eddy.browser

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.eddy.browser.browser.shouldShowChangelog
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ThemeMode
import app.eddy.browser.data.preferences.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreAndChangelogTest {

    @Test
    fun `shouldShowChangelog requires onboarding completion and unseen newer version`() {
        val preOnboarding = Settings(onboardingCompleted = false, changelogSeen = 0)
        assertFalse(shouldShowChangelog(preOnboarding, currentVersionCode = 50))

        val freshInstallPostOnboarding = Settings(onboardingCompleted = true, changelogSeen = 50)
        assertFalse(shouldShowChangelog(freshInstallPostOnboarding, currentVersionCode = 50))

        val updatedInstall = Settings(onboardingCompleted = true, changelogSeen = 49)
        assertTrue(shouldShowChangelog(updatedInstall, currentVersionCode = 50))

        val legacyUpdatedWithoutSeen = Settings(onboardingCompleted = true, changelogSeen = 0)
        assertTrue(shouldShowChangelog(legacyUpdatedWithoutSeen, currentVersionCode = 50))

        val downgradedVersion = Settings(onboardingCompleted = true, changelogSeen = 51)
        assertFalse(shouldShowChangelog(downgradedVersion, currentVersionCode = 50))
    }

    @Test
    fun `decodePreferences ignores legacy discord_announced key while preserving all unrelated settings`() {
        val legacyPrefs = mutablePreferencesOf(
            booleanPreferencesKey("discord_announced") to true,
            booleanPreferencesKey("onboarding_done") to true,
            intPreferencesKey("changelog_seen") to 42,
            stringPreferencesKey("theme") to ThemeMode.DARK.name,
            booleanPreferencesKey("ad_block") to false,
            intPreferencesKey("text_zoom") to 125,
            stringPreferencesKey("homepage_url") to "https://example.org",
        )

        val decoded = SettingsStore.decodePreferences(legacyPrefs)

        assertTrue(decoded.onboardingCompleted)
        assertEquals(42, decoded.changelogSeen)
        assertEquals(ThemeMode.DARK, decoded.themeMode)
        assertFalse(decoded.adBlock)
        assertEquals(125, decoded.textZoom)
        assertEquals("https://example.org", decoded.homepageUrl)
    }
}
