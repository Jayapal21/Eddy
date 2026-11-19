package app.eddy.browser.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Three-state adaptive header lifecycle:
 * - [EXPANDED]: Default full browser toolbar (normal non-obscuring page layout).
 * - [COMPACT]: Centered floating Dynamic Island capsule after downward scroll (edge-to-edge WebView).
 * - [HIDDEN]: Auto-hidden or manually dismissed state maximizing viewport space (edge-to-edge WebView).
 */
enum class HeaderMode {
    EXPANDED,
    COMPACT,
    HIDDEN,
}

/**
 * Immutable UI-blocker snapshot indicating whether the Dynamic Island should stay
 * expanded or pause auto-hiding while the user interacts with browser chrome.
 */
data class HeaderInteractionState(
    val isHome: Boolean = false,
    val editing: Boolean = false,
    val findActive: Boolean = false,
    val menuVisible: Boolean = false,
    val siteInfoVisible: Boolean = false,
    val translateVisible: Boolean = false,
    val linkSheetVisible: Boolean = false,
    val savePromptVisible: Boolean = false,
    val autofillVisible: Boolean = false,
    val promptsCount: Int = 0,
    val fullscreen: Boolean = false,
    val customViewActive: Boolean = false,
    val screenIsBrowser: Boolean = true,
) {
    val forcesExpanded: Boolean
        get() = isHome || editing || findActive

    val pausesAutoHide: Boolean
        get() = forcesExpanded ||
            !screenIsBrowser ||
            menuVisible ||
            siteInfoVisible ||
            translateVisible ||
            linkSheetVisible ||
            savePromptVisible ||
            autofillVisible ||
            promptsCount > 0 ||
            customViewActive
}

data class HeaderScrollThresholds(
    val downCollapsePx: Int = 64,
    val compactExpandUpPx: Int = 40,
    val hiddenRevealUpPx: Int = 68,
    val hiddenExpandUpPx: Int = 180,
    val hiddenInstantExpandDy: Int = 110,
    val topRevealY: Int = 8,
    val minCollapseY: Int = 48,
    val scrollCooldownMs: Long = 320L,
    val layoutTransitionCooldownMs: Long = 340L,
    val autoHideDelayMs: Long = 2_600L,
)

/**
 * Coordinates 3-state Dynamic Island header transitions, scroll hysteresis,
 * cancellable inactivity auto-hide timers, touch-pause protection, and manual toggles.
 */
class AdaptiveHeaderController(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = System::currentTimeMillis,
    val thresholds: HeaderScrollThresholds = HeaderScrollThresholds(),
) {
    var mode by mutableStateOf(HeaderMode.EXPANDED)
        private set

    var scrollAccum: Int = 0
        private set

    var isTouchingHeader: Boolean = false
        private set

    var manualHideActive: Boolean = false
        private set

    private var ignoreScrollUntilMs: Long = 0L
    private var autoHideJob: Job? = null
    private var blockers: HeaderInteractionState = HeaderInteractionState()

    fun suppressScrollForLayoutTransition(durationMs: Long = thresholds.layoutTransitionCooldownMs) {
        ignoreScrollUntilMs = maxOf(ignoreScrollUntilMs, clockMs() + durationMs)
        scrollAccum = 0
    }

    fun onScroll(
        dy: Int,
        scrollY: Int,
        isHome: Boolean = blockers.isHome,
        scrollBlockedByUi: Boolean = blockers.editing || blockers.findActive,
        isPullRefreshing: Boolean = false,
    ) {
        if (isHome || scrollBlockedByUi) {
            scrollAccum = 0
            if (mode != HeaderMode.EXPANDED) {
                manualHideActive = false
                setMode(HeaderMode.EXPANDED, applyScrollCooldown = false)
            } else {
                cancelAutoHide()
            }
            return
        }

        if (isPullRefreshing) {
            scrollAccum = 0
            if (mode != HeaderMode.EXPANDED) {
                manualHideActive = false
                setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
            }
            return
        }

        // At top of page: restore expanded toolbar (respecting active manual hide unless scrolling up).
        if (scrollY <= thresholds.topRevealY) {
            scrollAccum = 0
            val shouldRevealAtTop = when {
                mode == HeaderMode.EXPANDED -> false
                manualHideActive -> dy < -thresholds.topRevealY
                dy <= 0 -> true
                else -> mode == HeaderMode.COMPACT
            }
            if (shouldRevealAtTop) {
                manualHideActive = false
                setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
            }
            return
        }

        val now = clockMs()
        if (dy == 0 || now < ignoreScrollUntilMs) {
            if (now < ignoreScrollUntilMs) scrollAccum = 0
            return
        }

        val nextAccum = if ((dy > 0) != (scrollAccum > 0)) dy else scrollAccum + dy
        when (mode) {
            HeaderMode.EXPANDED -> {
                if (scrollY >= thresholds.minCollapseY && nextAccum > thresholds.downCollapsePx) {
                    scrollAccum = 0
                    manualHideActive = false
                    setMode(HeaderMode.COMPACT, applyScrollCooldown = true)
                    return
                }
            }
            HeaderMode.COMPACT -> {
                if (nextAccum < -thresholds.compactExpandUpPx) {
                    scrollAccum = 0
                    manualHideActive = false
                    setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
                    return
                }
            }
            HeaderMode.HIDDEN -> {
                if (dy <= -thresholds.hiddenInstantExpandDy || nextAccum <= -thresholds.hiddenExpandUpPx) {
                    scrollAccum = 0
                    manualHideActive = false
                    setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
                    return
                } else if (nextAccum <= -thresholds.hiddenRevealUpPx) {
                    scrollAccum = 0
                    manualHideActive = false
                    setMode(HeaderMode.COMPACT, applyScrollCooldown = true)
                    return
                }
            }
        }
        scrollAccum = nextAccum
    }

    fun expand() {
        manualHideActive = false
        scrollAccum = 0
        setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
    }

    fun collapseToCompact() {
        if (blockers.forcesExpanded) return
        manualHideActive = false
        scrollAccum = 0
        setMode(HeaderMode.COMPACT, applyScrollCooldown = true)
    }

    fun hide(manual: Boolean = false) {
        if (blockers.forcesExpanded) return
        manualHideActive = manual
        scrollAccum = 0
        setMode(HeaderMode.HIDDEN, applyScrollCooldown = true)
    }

    /** Double-tapping the header/island interaction zone manually toggles visibility. */
    fun toggleManualVisibility() {
        if (blockers.forcesExpanded) return
        if (mode == HeaderMode.HIDDEN) {
            expand()
        } else {
            hide(manual = true)
        }
    }

    fun onFullscreenChanged(fullscreen: Boolean) {
        blockers = blockers.copy(fullscreen = fullscreen)
        scrollAccum = 0
        manualHideActive = false
        if (fullscreen && !blockers.forcesExpanded) {
            setMode(HeaderMode.HIDDEN, applyScrollCooldown = true)
        } else {
            setMode(HeaderMode.EXPANDED, applyScrollCooldown = true)
        }
    }

    fun onHeaderTouchChanged(touching: Boolean) {
        if (isTouchingHeader == touching) return
        isTouchingHeader = touching
        if (touching) {
            cancelAutoHide()
        } else if (mode == HeaderMode.COMPACT && !blockers.pausesAutoHide) {
            scheduleAutoHide()
        }
    }

    fun syncUiBlockers(state: HeaderInteractionState) {
        val prev = blockers
        blockers = state
        when {
            state.forcesExpanded -> {
                cancelAutoHide()
                if (mode != HeaderMode.EXPANDED) {
                    manualHideActive = false
                    scrollAccum = 0
                    setMode(HeaderMode.EXPANDED, applyScrollCooldown = false)
                }
            }
            state.pausesAutoHide -> {
                cancelAutoHide()
            }
            mode == HeaderMode.COMPACT && (autoHideJob?.isActive != true || prev.pausesAutoHide) && !isTouchingHeader -> {
                scheduleAutoHide()
            }
        }
    }

    fun onTabOrNavigationReset() {
        scrollAccum = 0
        manualHideActive = false
        cancelAutoHide()
        val target = if (blockers.fullscreen && !blockers.forcesExpanded) HeaderMode.HIDDEN else HeaderMode.EXPANDED
        setMode(target, applyScrollCooldown = true)
    }

    fun dispose() {
        cancelAutoHide()
    }

    private fun setMode(next: HeaderMode, applyScrollCooldown: Boolean) {
        val changed = mode != next
        mode = next
        if (applyScrollCooldown && changed) {
            ignoreScrollUntilMs = maxOf(ignoreScrollUntilMs, clockMs() + thresholds.scrollCooldownMs)
        }
        when (next) {
            HeaderMode.COMPACT -> if (!isTouchingHeader && !blockers.pausesAutoHide) scheduleAutoHide() else cancelAutoHide()
            HeaderMode.EXPANDED, HeaderMode.HIDDEN -> cancelAutoHide()
        }
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        if (mode != HeaderMode.COMPACT || isTouchingHeader || blockers.pausesAutoHide) return
        autoHideJob = scope.launch {
            delay(thresholds.autoHideDelayMs)
            if (mode == HeaderMode.COMPACT && !isTouchingHeader && !blockers.pausesAutoHide) {
                manualHideActive = false
                scrollAccum = 0
                mode = HeaderMode.HIDDEN
            }
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }
}
