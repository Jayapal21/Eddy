package app.eddy.browser.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.webkit.ScriptHandler

/**
 * WebView with two extras the browser chrome needs: scroll-direction callbacks (to collapse the
 * toolbar) and a pull-to-refresh gesture that only starts when the page is scrolled to the top.
 */
class EddyWebView(private val ctx: MutableContextWrapper) : WebView(ctx) {
    /** Called with the vertical scroll delta in px and the resulting scrollY. */
    var onScrolled: ((dy: Int, scrollY: Int) -> Unit)? = null
    /** Dampened pull distance in px while pulling down at the top of the page. */
    var onPull: ((Int) -> Unit)? = null
    var onPullRelease: ((triggered: Boolean) -> Unit)? = null
    /** Returns true when edge navigation in [direction] (-1 = back, +1 = forward) is currently available. */
    var canSwipeEdge: ((direction: Int) -> Boolean)? = null
    /** Reports horizontal edge-swipe navigation progress (0f..1.35f) while dragging from left/right edge. */
    var onEdgeSwipe: ((direction: Int, progress: Float) -> Unit)? = null
    /** Fired when edge swipe completes or cancels ([commit] = true triggers navigation). */
    var onEdgeSwipeRelease: ((direction: Int, commit: Boolean) -> Unit)? = null
    var dntScript: ScriptHandler? = null
    var desktopScript: ScriptHandler? = null
    var cosmeticScript: ScriptHandler? = null
    var youtubeScript: ScriptHandler? = null
    var incognito = false
    @Volatile var isDestroyed = false
        private set
    @Volatile var failedDarkModePageKey: String? = null
    /** Uptime of the last tap or key press, the native side of "user activation" for bridges a page could call directly. */
    var lastInteraction = 0L
        private set
    /** Cancels the in-flight prerender, if any, when a new one starts or the omnibox closes. */
    var prerender: android.os.CancellationSignal? = null
    private var appliedBackgroundColor: Int? = null
    private val pendingCallbacks = mutableSetOf<Runnable>()

    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val refreshThreshold = resources.displayMetrics.density * 72
    private val edgeZonePx = resources.displayMetrics.density * 28f
    private val edgeCommitThresholdPx = resources.displayMetrics.density * 88f
    private var pullStartY = -1f
    private var pull = 0f
    /** Set once Chromium reports an unconsumed downward drag at the top. Pages that scroll an inner
     *  element keep scrollY at 0, so scrollY alone can't tell a pull from a normal scroll. */
    private var overscrolledTop = false

    private var edgeDownX = 0f
    private var edgeDownY = 0f
    private var edgeCandidateDir = 0
    private var edgeSwipeActiveDir = 0
    private var edgeSwipeProgress = 0f

    /** The activity context is swapped in/out so dialogs work but the Activity is never leaked. */
    fun attachTo(context: Context) { ctx.baseContext = context }
    fun detachFromActivity(appContext: Context) { ctx.baseContext = appContext }

    /** Posts a delayed task that automatically cancels if the WebView is destroyed before firing. */
    fun postSafeDelayed(delayMillis: Long, block: () -> Unit) {
        if (isDestroyed) return
        lateinit var task: Runnable
        task = Runnable {
            pendingCallbacks.remove(task)
            if (!isDestroyed) block()
        }
        pendingCallbacks.add(task)
        if (!postDelayed(task, delayMillis)) {
            pendingCallbacks.remove(task)
        }
    }

    /** Evaluates JS only when the WebView is alive, preventing destroyed-WebView execution crashes. */
    fun safeEvaluateJavascript(script: String, callback: ((String?) -> Unit)? = null): Boolean {
        if (isDestroyed) {
            callback?.invoke(null)
            return false
        }
        return runCatching {
            evaluateJavascript(script) { value ->
                if (!isDestroyed) callback?.invoke(value)
            }
            true
        }.getOrElse {
            callback?.invoke(null)
            false
        }
    }

    override fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        pendingCallbacks.toList().forEach { removeCallbacks(it) }
        pendingCallbacks.clear()
        runCatching { dntScript?.remove() }; dntScript = null
        runCatching { desktopScript?.remove() }; desktopScript = null
        runCatching { cosmeticScript?.remove() }; cosmeticScript = null
        runCatching { youtubeScript?.remove() }; youtubeScript = null
        onScrolled = null
        onPull = null
        onPullRelease = null
        canSwipeEdge = null
        onEdgeSwipe = null
        onEdgeSwipeRelease = null
        runCatching { stopLoading() }
        super.destroy()
    }

    /** Syncs the WebView surface color to the browser backdrop so loading/resize regions never flash white. */
    fun applyBackgroundColor(color: Int) {
        if (isDestroyed || appliedBackgroundColor == color) return
        appliedBackgroundColor = color
        setBackgroundColor(color)
    }

    // Leaving the app or turning the screen off hides the window, and sites like YouTube pause on the
    // resulting visibilitychange. While media plays, keep the page believing it is still on screen.
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (MediaService.isPlaying(context)) VISIBLE else visibility)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t - oldt, t)
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        if (deltaY < 0 && scrollY == 0) overscrolledTop = true
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_UP) lastInteraction = android.os.SystemClock.uptimeMillis()
        return super.dispatchKeyEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) lastInteraction = android.os.SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullStartY = if (scrollY == 0 && !canScrollVertically(-1)) event.y else -1f
                pull = 0f
                overscrolledTop = false
                edgeDownX = event.x
                edgeDownY = event.y
                edgeCandidateDir = resolveEdgeCandidate(
                    x = event.x,
                    viewWidth = width,
                    edgeZonePx = edgeZonePx,
                    canGoBack = canSwipeEdge?.invoke(-1) == true,
                    canGoForward = canSwipeEdge?.invoke(1) == true,
                )
                edgeSwipeActiveDir = 0
                edgeSwipeProgress = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (edgeSwipeActiveDir != 0) {
                    onEdgeSwipeRelease?.invoke(edgeSwipeActiveDir, false)
                    edgeSwipeActiveDir = 0
                    edgeSwipeProgress = 0f
                }
                edgeCandidateDir = 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (edgeCandidateDir != 0 && event.pointerCount == 1 && pull == 0f) {
                    val dx = event.x - edgeDownX
                    val dy = event.y - edgeDownY
                    if (edgeSwipeActiveDir == 0) {
                        val scrollDir = if (edgeCandidateDir == -1) -1 else 1
                        when (
                            resolveEdgeSwipeDecision(
                                candidateDir = edgeCandidateDir,
                                dx = dx,
                                dy = dy,
                                touchSlop = touchSlop.toFloat(),
                                canPageScrollHorizontally = canScrollHorizontally(scrollDir),
                            )
                        ) {
                            EdgeSwipeDecision.ACTIVATE -> {
                                edgeSwipeActiveDir = edgeCandidateDir
                                pullStartY = -1f
                                MotionEvent.obtain(event).also { cancel ->
                                    cancel.action = MotionEvent.ACTION_CANCEL
                                    super.onTouchEvent(cancel)
                                    cancel.recycle()
                                }
                            }
                            EdgeSwipeDecision.YIELD_TO_PAGE -> edgeCandidateDir = 0
                            EdgeSwipeDecision.PENDING -> Unit
                        }
                    }
                    if (edgeSwipeActiveDir != 0) {
                        val travel = if (edgeSwipeActiveDir == -1) dx else -dx
                        edgeSwipeProgress = (travel.coerceAtLeast(0f) / edgeCommitThresholdPx).coerceIn(0f, 1.35f)
                        onEdgeSwipe?.invoke(edgeSwipeActiveDir, edgeSwipeProgress)
                        return true
                    }
                }
                if (pullStartY >= 0 && event.pointerCount == 1 && edgeSwipeActiveDir == 0) {
                    // Measure the pull from where the page stopped consuming the drag.
                    if (!overscrolledTop) { pullStartY = event.y; return super.onTouchEvent(event) }
                    val dy = event.y - pullStartY
                    if (dy > touchSlop * 2) {
                        edgeCandidateDir = 0
                        pull = (dy - touchSlop * 2) * 0.5f
                        onPull?.invoke(pull.toInt())
                    } else if (dy < -touchSlop) {
                        pullStartY = -1f
                        if (pull > 0f) onPull?.invoke(0)
                        pull = 0f
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (edgeSwipeActiveDir != 0) {
                    val commit = event.actionMasked == MotionEvent.ACTION_UP && edgeSwipeProgress >= 1f
                    onEdgeSwipeRelease?.invoke(edgeSwipeActiveDir, commit)
                    edgeSwipeActiveDir = 0
                    edgeCandidateDir = 0
                    edgeSwipeProgress = 0f
                    return true
                }
                edgeCandidateDir = 0
                if (pull > 0f) {
                    onPullRelease?.invoke(event.actionMasked == MotionEvent.ACTION_UP && pull >= refreshThreshold)
                    onPull?.invoke(0)
                }
                pullStartY = -1f
                pull = 0f
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        /** Finds the Activity behind a wrapped context (needed for dialogs). */
        fun unwrap(context: Context): Context? {
            var c: Context? = context
            while (c is ContextWrapper) c = c.baseContext
            return c
        }
    }
}

internal enum class EdgeSwipeDecision { PENDING, ACTIVATE, YIELD_TO_PAGE }

internal fun resolveEdgeCandidate(
    x: Float,
    viewWidth: Int,
    edgeZonePx: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
): Int = when {
    viewWidth <= 0 -> 0
    x <= edgeZonePx && canGoBack -> -1
    x >= viewWidth - edgeZonePx && canGoForward -> 1
    else -> 0
}

internal fun resolveEdgeSwipeDecision(
    candidateDir: Int,
    dx: Float,
    dy: Float,
    touchSlop: Float,
    canPageScrollHorizontally: Boolean,
): EdgeSwipeDecision {
    if (candidateDir == 0) return EdgeSwipeDecision.YIELD_TO_PAGE
    val absX = kotlin.math.abs(dx)
    val absY = kotlin.math.abs(dy)
    val minActivation = touchSlop * 1.5f
    if (absY > minActivation && absY >= absX) return EdgeSwipeDecision.YIELD_TO_PAGE
    val movedInward = (candidateDir == -1 && dx > minActivation) || (candidateDir == 1 && dx < -minActivation)
    if (!movedInward) {
        val movedOutward = (candidateDir == -1 && dx < -minActivation) || (candidateDir == 1 && dx > minActivation)
        return if (movedOutward) EdgeSwipeDecision.YIELD_TO_PAGE else EdgeSwipeDecision.PENDING
    }
    if (canPageScrollHorizontally) return EdgeSwipeDecision.YIELD_TO_PAGE
    return if (absX > absY * 1.35f) EdgeSwipeDecision.ACTIVATE else EdgeSwipeDecision.YIELD_TO_PAGE
}
