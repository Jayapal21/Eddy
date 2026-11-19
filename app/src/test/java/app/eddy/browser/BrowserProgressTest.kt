package app.eddy.browser

import app.eddy.browser.browser.resolveBrowserProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserProgressTest {

    @Test
    fun `home and error states hide progress bar immediately`() {
        val homeTarget = resolveBrowserProgress(isHome = true, isLoading = true, rawProgress = 50, hasError = false)
        assertFalse(homeTarget.visible)
        assertFalse(homeTarget.completedCleanly)
        assertEquals(0f, homeTarget.fraction, 0.0001f)

        val errorTarget = resolveBrowserProgress(isHome = false, isLoading = true, rawProgress = 80, hasError = true)
        assertFalse(errorTarget.visible)
        assertFalse(errorTarget.completedCleanly)
        assertEquals(0f, errorTarget.fraction, 0.0001f)
    }

    @Test
    fun `active loading clamps progress between 5 and 100 percent`() {
        val start = resolveBrowserProgress(isHome = false, isLoading = true, rawProgress = 0, hasError = false)
        assertTrue(start.visible)
        assertFalse(start.completedCleanly)
        assertEquals(0.05f, start.fraction, 0.0001f)

        val mid = resolveBrowserProgress(isHome = false, isLoading = true, rawProgress = 48, hasError = false)
        assertTrue(mid.visible)
        assertEquals(0.48f, mid.fraction, 0.0001f)

        val clampedMax = resolveBrowserProgress(isHome = false, isLoading = true, rawProgress = 120, hasError = false)
        assertTrue(clampedMax.visible)
        assertEquals(1f, clampedMax.fraction, 0.0001f)
    }

    @Test
    fun `idle state distinguishes clean completion from cancelled load`() {
        val completed = resolveBrowserProgress(isHome = false, isLoading = false, rawProgress = 100, hasError = false)
        assertFalse(completed.visible)
        assertTrue(completed.completedCleanly)
        assertEquals(1f, completed.fraction, 0.0001f)

        val cancelled = resolveBrowserProgress(isHome = false, isLoading = false, rawProgress = 42, hasError = false)
        assertFalse(cancelled.visible)
        assertFalse(cancelled.completedCleanly)
        assertEquals(0f, cancelled.fraction, 0.0001f)
    }
}
