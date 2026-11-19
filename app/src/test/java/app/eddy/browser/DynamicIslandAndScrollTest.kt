package app.eddy.browser

import app.eddy.browser.browser.AdaptiveHeaderController
import app.eddy.browser.browser.EdgeSwipeDecision
import app.eddy.browser.browser.HeaderInteractionState
import app.eddy.browser.browser.HeaderMode
import app.eddy.browser.browser.HeaderScrollThresholds
import app.eddy.browser.browser.ScrollChromeAction
import app.eddy.browser.browser.resolveEdgeCandidate
import app.eddy.browser.browser.resolveEdgeSwipeDecision
import app.eddy.browser.browser.resolveScrollChromeAction
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicIslandAndScrollTest {

    @Test
    fun `compactDomain strips common mobile and www prefixes`() {
        assertEquals("github.com", UrlUtils.compactDomain("https://www.github.com/android"))
        assertEquals("wikipedia.org", UrlUtils.compactDomain("https://m.wikipedia.org/wiki/Kotlin"))
        assertEquals("news.bbc.co.uk", UrlUtils.compactDomain("https://mobile.news.bbc.co.uk/story"))
        assertEquals("en.wikipedia.org", UrlUtils.compactDomain("https://en.wikipedia.org/wiki/Android"))
        assertEquals("example.org", UrlUtils.compactDomain("about:blank", fallback = "www.example.org"))
    }

    @Test
    fun `scroll down past threshold collapses header only below top buffer`() {
        // At scrollY = 30 (< minCollapseY = 48), even large dy must not collapse yet
        val (nearTopAction, _) = resolveScrollChromeAction(
            dy = 80,
            scrollY = 30,
            chromeVisible = true,
            scrollAccum = 0,
            nowMs = 1000L,
            ignoreUntilMs = 0L,
        )
        assertEquals(ScrollChromeAction.NONE, nearTopAction)

        // Past minCollapseY and past down threshold collapses header cleanly
        val (collapseAction, resetAccum) = resolveScrollChromeAction(
            dy = 70,
            scrollY = 120,
            chromeVisible = true,
            scrollAccum = 0,
            nowMs = 1000L,
            ignoreUntilMs = 0L,
        )
        assertEquals(ScrollChromeAction.HIDE, collapseAction)
        assertEquals(0, resetAccum)
    }

    @Test
    fun `micro jitter in opposite direction resets accumulator instead of flickering`() {
        val (action1, acc1) = resolveScrollChromeAction(
            dy = 35,
            scrollY = 140,
            chromeVisible = true,
            scrollAccum = 0,
            nowMs = 1000L,
            ignoreUntilMs = 0L,
        )
        assertEquals(ScrollChromeAction.NONE, action1)
        assertEquals(35, acc1)

        val (action2, acc2) = resolveScrollChromeAction(
            dy = -12,
            scrollY = 128,
            chromeVisible = true,
            scrollAccum = acc1,
            nowMs = 1016L,
            ignoreUntilMs = 0L,
        )
        assertEquals(ScrollChromeAction.NONE, action2)
        assertEquals(-12, acc2)
    }

    @Test
    fun `scrolling back to very top reveals header immediately even during cooldown`() {
        val (action, accum) = resolveScrollChromeAction(
            dy = -10,
            scrollY = 4,
            chromeVisible = false,
            scrollAccum = -10,
            nowMs = 1100L,
            ignoreUntilMs = 1400L,
        )
        assertEquals(ScrollChromeAction.SHOW, action)
        assertEquals(0, accum)
    }

    @Test
    fun `scrolling up past up threshold expands compact pill`() {
        val (action, accum) = resolveScrollChromeAction(
            dy = -45,
            scrollY = 420,
            chromeVisible = false,
            scrollAccum = 0,
            nowMs = 2000L,
            ignoreUntilMs = 1400L,
        )
        assertEquals(ScrollChromeAction.SHOW, action)
        assertEquals(0, accum)
    }

    @Test
    fun `adaptive header transitions EXPANDED to COMPACT to HIDDEN and reveals smoothly`() = runBlocking {
        var clock = 10_000L
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = AdaptiveHeaderController(
                scope = scope,
                clockMs = { clock },
                thresholds = HeaderScrollThresholds(autoHideDelayMs = 60L),
            )
            assertEquals(HeaderMode.EXPANDED, controller.mode)

            // Downward scroll past threshold enters COMPACT
            controller.onScroll(dy = 80, scrollY = 150)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Wait for inactivity timer -> automatically enters HIDDEN
            delay(100L)
            assertEquals(HeaderMode.HIDDEN, controller.mode)

            // Scrolling further downward while HIDDEN must not pop header open
            clock += 500L
            controller.onScroll(dy = 90, scrollY = 300)
            assertEquals(HeaderMode.HIDDEN, controller.mode)

            // Small upward micro-scroll jitter is ignored
            controller.onScroll(dy = -20, scrollY = 280)
            assertEquals(HeaderMode.HIDDEN, controller.mode)

            // Moderate upward scroll reveals COMPACT island cleanly
            controller.onScroll(dy = -60, scrollY = 220)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Deliberate upward scroll past cooldown restores EXPANDED
            clock += 400L
            controller.onScroll(dy = -50, scrollY = 170)
            assertEquals(HeaderMode.EXPANDED, controller.mode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `auto-hide pauses while header touched or menu open and resumes after release`() = runBlocking {
        var clock = 1_000L
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = AdaptiveHeaderController(
                scope = scope,
                clockMs = { clock },
                thresholds = HeaderScrollThresholds(autoHideDelayMs = 60L),
            )

            controller.onScroll(dy = 90, scrollY = 200)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Touching the island cancels/pauses auto-hide
            controller.onHeaderTouchChanged(true)
            delay(100L)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Opening menu also pauses auto-hide even after finger releases
            controller.syncUiBlockers(HeaderInteractionState(menuVisible = true))
            controller.onHeaderTouchChanged(false)
            delay(100L)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Closing menu restarts timer and auto-hides cleanly
            controller.syncUiBlockers(HeaderInteractionState(menuVisible = false))
            delay(100L)
            assertEquals(HeaderMode.HIDDEN, controller.mode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `manual double-tap toggle and layout suppression behave deterministically`() {
        var clock = 5_000L
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = AdaptiveHeaderController(
                scope = scope,
                clockMs = { clock },
                thresholds = HeaderScrollThresholds(autoHideDelayMs = 5_000L),
            )

            // Double-tap while EXPANDED hides manually
            controller.toggleManualVisibility()
            assertEquals(HeaderMode.HIDDEN, controller.mode)
            assertTrue(controller.manualHideActive)

            // Minor layout scrollY=0 noise after manual hide keeps header hidden
            clock += 400L
            controller.onScroll(dy = 0, scrollY = 0)
            assertEquals(HeaderMode.HIDDEN, controller.mode)

            // Second double-tap restores EXPANDED
            controller.toggleManualVisibility()
            assertEquals(HeaderMode.EXPANDED, controller.mode)
            assertFalse(controller.manualHideActive)

            // Layout transition suppression ignores immediate resize scroll jitter
            controller.suppressScrollForLayoutTransition(340L)
            controller.onScroll(dy = 120, scrollY = 200)
            assertEquals(HeaderMode.EXPANDED, controller.mode)

            // After cooldown ends, downward scroll works normally again
            clock += 400L
            controller.onScroll(dy = 90, scrollY = 290)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Tab switch resets back to EXPANDED
            controller.onTabOrNavigationReset()
            assertEquals(HeaderMode.EXPANDED, controller.mode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `editing and home states force EXPANDED and fullscreen defaults to HIDDEN`() {
        var clock = 2_000L
        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = AdaptiveHeaderController(scope = scope, clockMs = { clock })
            controller.onScroll(dy = 100, scrollY = 300)
            assertEquals(HeaderMode.COMPACT, controller.mode)

            // Editing forces EXPANDED immediately
            controller.syncUiBlockers(HeaderInteractionState(editing = true))
            assertEquals(HeaderMode.EXPANDED, controller.mode)

            // Leaving editing keeps EXPANDED cleanly
            controller.syncUiBlockers(HeaderInteractionState(editing = false))
            assertEquals(HeaderMode.EXPANDED, controller.mode)

            // Entering fullscreen switches to HIDDEN cleanly
            controller.onFullscreenChanged(true)
            assertEquals(HeaderMode.HIDDEN, controller.mode)
            controller.onFullscreenChanged(false)
            assertEquals(HeaderMode.EXPANDED, controller.mode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `edge swipe navigation activates only in edge zone when history permits and page does not scroll horizontally`() {
        // Outside edge zones returns 0
        assertEquals(0, resolveEdgeCandidate(x = 200f, viewWidth = 1080, edgeZonePx = 60f, canGoBack = true, canGoForward = true))

        // Left & right edges obey history availability
        assertEquals(-1, resolveEdgeCandidate(x = 30f, viewWidth = 1080, edgeZonePx = 60f, canGoBack = true, canGoForward = false))
        assertEquals(0, resolveEdgeCandidate(x = 30f, viewWidth = 1080, edgeZonePx = 60f, canGoBack = false, canGoForward = true))
        assertEquals(1, resolveEdgeCandidate(x = 1050f, viewWidth = 1080, edgeZonePx = 60f, canGoBack = false, canGoForward = true))

        // Activates on dominant inward horizontal drag when page cannot scroll horizontally
        assertEquals(
            EdgeSwipeDecision.ACTIVATE,
            resolveEdgeSwipeDecision(candidateDir = -1, dx = 40f, dy = 6f, touchSlop = 16f, canPageScrollHorizontally = false),
        )
        assertEquals(
            EdgeSwipeDecision.ACTIVATE,
            resolveEdgeSwipeDecision(candidateDir = 1, dx = -42f, dy = 8f, touchSlop = 16f, canPageScrollHorizontally = false),
        )

        // Yields to page carousels and vertical scrolling cleanly
        assertEquals(
            EdgeSwipeDecision.YIELD_TO_PAGE,
            resolveEdgeSwipeDecision(candidateDir = -1, dx = 40f, dy = 6f, touchSlop = 16f, canPageScrollHorizontally = true),
        )
        assertEquals(
            EdgeSwipeDecision.YIELD_TO_PAGE,
            resolveEdgeSwipeDecision(candidateDir = -1, dx = 12f, dy = 36f, touchSlop = 16f, canPageScrollHorizontally = false),
        )
    }
}
