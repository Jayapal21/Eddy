package app.eddy.browser

import app.eddy.browser.data.preferences.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLazyInitTest {

    @Test
    fun `resolveOnboardingMigration skips isUpdatedInstall check when preference already exists`() {
        var ipcCalled = false
        assertNull(SettingsStore.resolveOnboardingMigration(existingOnboardingDone = true) {
            ipcCalled = true
            true
        })
        assertFalse(ipcCalled)

        assertNull(SettingsStore.resolveOnboardingMigration(existingOnboardingDone = false) {
            ipcCalled = true
            true
        })
        assertFalse(ipcCalled)
    }

    @Test
    fun `resolveOnboardingMigration queries isUpdatedInstall only when preference is unset`() {
        var calls = 0
        val updated = SettingsStore.resolveOnboardingMigration(existingOnboardingDone = null) {
            calls++
            true
        }
        assertEquals(true, updated)
        assertEquals(1, calls)

        val fresh = SettingsStore.resolveOnboardingMigration(existingOnboardingDone = null) {
            calls++
            false
        }
        assertNull(fresh)
        assertEquals(2, calls)
    }
}
