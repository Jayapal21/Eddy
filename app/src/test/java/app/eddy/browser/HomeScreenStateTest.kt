package app.eddy.browser

import app.eddy.browser.browser.BrowserTab
import app.eddy.browser.browser.ErrorKind
import app.eddy.browser.browser.PageError
import app.eddy.browser.browser.Security
import app.eddy.browser.browser.resetTabStateForHome
import app.eddy.browser.browser.shouldShowHomeOverlay
import app.eddy.browser.browser.shouldShowOverlayOmnibox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenStateTest {

    @Test
    fun `shouldShowHomeOverlay prevents dual chrome overlap while leaving home and yields on paint or error`() {
        // Pure home tab always shows HomeScreen
        assertTrue(shouldShowHomeOverlay(isHome = true, painted = true, leftHome = false, hasError = false))

        // Navigating from home keeps HomeScreen visible until first frame paints (so top Chrome stays hidden)
        assertTrue(shouldShowHomeOverlay(isHome = false, painted = false, leftHome = true, hasError = false))

        // Once painted, HomeScreen steps aside so Chrome and webpage render cleanly
        assertFalse(shouldShowHomeOverlay(isHome = false, painted = true, leftHome = true, hasError = false))

        // If navigation errors before painting, ErrorPage takes over instead of staying trapped behind HomeScreen
        assertFalse(shouldShowHomeOverlay(isHome = false, painted = false, leftHome = true, hasError = true))

        // Background/restored non-home tab before paint shows backdrop, not HomeScreen
        assertFalse(shouldShowHomeOverlay(isHome = false, painted = false, leftHome = false, hasError = false))
    }

    @Test
    fun `shouldShowOverlayOmnibox activates overlay editor when home search bar is hidden`() {
        // Home screen with inline search bar visible uses HomeSearchBar instead of overlay omnibox
        assertFalse(shouldShowOverlayOmnibox(editing = true, showingHomeOverlay = true, homeShowSearch = true))

        // Home screen with inline search bar hidden (e.g. from widget or preference) MUST show overlay omnibox
        assertTrue(shouldShowOverlayOmnibox(editing = true, showingHomeOverlay = true, homeShowSearch = false))

        // Non-home webpage always uses overlay omnibox when editing
        assertTrue(shouldShowOverlayOmnibox(editing = true, showingHomeOverlay = false, homeShowSearch = true))
        assertTrue(shouldShowOverlayOmnibox(editing = true, showingHomeOverlay = false, homeShowSearch = false))

        // Not editing never shows overlay omnibox
        assertFalse(shouldShowOverlayOmnibox(editing = false, showingHomeOverlay = false, homeShowSearch = false))
        assertFalse(shouldShowOverlayOmnibox(editing = false, showingHomeOverlay = true, homeShowSearch = false))
    }

    @Test
    fun `resetTabStateForHome clears navigation, paint, security, pull-refresh, and tool flags`() {
        val tab = BrowserTab(id = "tab-home-reset", incognito = false, initialUrl = "https://example.com", initialTitle = "Example")
        tab.isLoading = true
        tab.progress = 73
        tab.canGoBack = true
        tab.canGoForward = true
        tab.security = Security.ERROR
        tab.error = PageError(ErrorKind.UNAVAILABLE, "https://example.com", "boom")
        tab.blockedRaw = 9
        tab.blockedCount = 9
        tab.pageHost = "example.com"
        tab.httpsUpgradedFrom = "http://example.com"
        tab.desktopOverride = true
        tab.desktopActive = true
        tab.devToolsActive = true
        tab.shieldShown = true
        tab.siteBlockingOff = true
        tab.blockingActive = false
        tab.trackerBlockingActive = false
        tab.pullDistance = 120
        tab.refreshing = true
        tab.painted = false
        tab.leftHome = true
        tab.hasPersistedState = true
        tab.readerAvailable = true
        tab.readerActive = true

        resetTabStateForHome(tab)

        assertTrue(tab.isHome)
        assertEquals("", tab.url)
        assertEquals("", tab.title)
        assertNull(tab.favicon)
        assertFalse(tab.isLoading)
        assertEquals(0, tab.progress)
        assertFalse(tab.canGoBack)
        assertFalse(tab.canGoForward)
        assertEquals(Security.NONE, tab.security)
        assertNull(tab.error)
        assertEquals(0, tab.blockedRaw)
        assertEquals(0, tab.blockedCount)
        assertEquals("", tab.pageHost)
        assertNull(tab.httpsUpgradedFrom)
        assertNull(tab.desktopOverride)
        assertFalse(tab.desktopActive)
        assertFalse(tab.devToolsActive)
        assertFalse(tab.shieldShown)
        assertFalse(tab.siteBlockingOff)
        assertTrue(tab.blockingActive)
        assertTrue(tab.trackerBlockingActive)
        assertEquals(0, tab.pullDistance)
        assertFalse(tab.refreshing)
        assertTrue(tab.painted)
        assertFalse(tab.leftHome)
        assertFalse(tab.hasPersistedState)
        assertNull(tab.savedState)
        assertFalse(tab.readerAvailable)
        assertFalse(tab.readerActive)
    }
}
