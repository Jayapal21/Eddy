package app.eddy.browser.ui.sakuga

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.WeakHashMap

/**
 * Global Sakuga motion preset levels (Section F).
 * Defaults to [PREMIUM] for subtle, responsive motion across Eddy Browser.
 */
enum class SakugaMotionPreset {
    SOFT,
    PREMIUM,
    PLAYFUL,
    SNAPPY,
}

/** Provides the active app-wide [SakugaMotionPreset] with optional per-component overrides. */
val LocalSakugaMotionPreset = staticCompositionLocalOf { SakugaMotionPreset.PREMIUM }

/**
 * Standardized interactive component states for the Sakuga State Transition Engine (Section E).
 */
enum class SakugaComponentState {
    IDLE,
    PRESSED,
    RELEASED,
    HOVERED,
    SELECTED,
    DISABLED,
    LOADING,
    SUCCESS,
    ERROR,
}

/**
 * Standardized semantic spring archetypes (Section A).
 */
enum class SakugaSpringType {
    PRESSED,
    RELEASED,
    HOVERED,
    SNAPPY,
    PLAYFUL,
    PREMIUM,
}

/**
 * Centralized Sakuga animation configuration controller encapsulating immutable [SpringForce] and
 * [ValueAnimator] presets, View-based [SpringAnimation] builders with lifecycle tracking, and
 * Jetpack Compose [FiniteAnimationSpec] bridges.
 *
 * Mutable [SpringAnimation] and [ValueAnimator] instances are never shared across targets:
 * every target receives its own independent animator built from immutable configurations.
 */
object SakugaAnimationController {

    enum class ComponentRole {
        BUTTON,
        ICON_BUTTON,
        TOGGLE,
        CARD,
        INDICATOR,
        SHEET,
        DIALOG,
    }

    /** Immutable spring physics preset shared across dynamic View springs and Compose animations. */
    @Immutable
    data class SpringPreset(
        val dampingRatio: Float,
        val stiffness: Float,
        val minimumVisibleChange: Float = DEFAULT_MIN_VISIBLE_CHANGE_SCALE,
    ) {
        fun createSpringForce(finalPosition: Float = 1f): SpringForce =
            SpringForce(finalPosition).apply {
                dampingRatio = this@SpringPreset.dampingRatio
                stiffness = this@SpringPreset.stiffness
            }

        fun <T> toComposeSpring(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
            if (reducedMotion) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    /** Immutable timing + bezier preset shared across Android [ValueAnimator] and Compose tweens. */
    @Immutable
    data class ValueAnimatorPreset(
        val durationMs: Long,
        val c1x: Float = 0.2f,
        val c1y: Float = 0f,
        val c2x: Float = 0f,
        val c2y: Float = 1f,
    ) {
        fun createInterpolator(): TimeInterpolator = PathInterpolator(c1x, c1y, c2x, c2y)

        fun toComposeEasing(): Easing = CubicBezierEasing(c1x, c1y, c2x, c2y)

        fun <T> toComposeTween(reducedMotion: Boolean = false, durationScale: Float = 1f): TweenSpec<T> =
            tween(
                durationMillis = if (reducedMotion) 0 else (durationMs * durationScale).toInt().coerceAtLeast(1),
                easing = toComposeEasing(),
            )
    }

    /** Immutable state-driven target transform/visual spec resolved by the State Transition Engine. */
    @Immutable
    data class StateTransformSpec(
        val scaleX: Float,
        val scaleY: Float,
        val alpha: Float,
        val elevationDeltaDp: Float,
        val translationYDp: Float = 0f,
        val springPreset: SpringPreset,
    )

    /** Full preset profile combining springs, deformations, and particle budgets (Section F). */
    @Immutable
    data class MotionProfile(
        val preset: SakugaMotionPreset,
        val pressSpring: SpringPreset,
        val releaseSpring: SpringPreset,
        val hoverSpring: SpringPreset,
        val toggleSpring: SpringPreset,
        val cardSpring: SpringPreset,
        val sheetSpring: SpringPreset,
        val pressScaleX: Float,
        val pressScaleY: Float,
        val cardPressScaleX: Float,
        val cardPressScaleY: Float,
        val hoverScale: Float,
        val hoverElevationDp: Float,
        val defaultParticleCount: Int,
        val defaultBurstRadiusDp: Float,
        val durationScale: Float,
    )

    private const val DEFAULT_MIN_VISIBLE_CHANGE_SCALE = 0.002f
    private const val DEFAULT_MIN_VISIBLE_CHANGE_ALPHA = 0.004f
    private const val DEFAULT_MIN_VISIBLE_CHANGE_PIXELS = 0.5f

    /** Section A: Standardized Spring Presets. */
    object Springs {
        /** Subtle squash with responsive feedback. */
        val PRESSED = SpringPreset(dampingRatio = 0.72f, stiffness = 920f)

        /** Smooth spring-back with controlled overshoot. */
        val RELEASED = SpringPreset(dampingRatio = 0.54f, stiffness = 560f)

        /** Gentle scale-up and elevation. */
        val HOVERED = SpringPreset(dampingRatio = 0.78f, stiffness = 440f)

        /** Quick response for small controls. */
        val SNAPPY = SpringPreset(dampingRatio = 0.68f, stiffness = 860f)

        /** Expressive elastic movement. */
        val PLAYFUL = SpringPreset(dampingRatio = 0.44f, stiffness = 480f)

        /** Subtle and sophisticated movement. */
        val PREMIUM = SpringPreset(dampingRatio = 0.76f, stiffness = 420f)

        fun byType(type: SakugaSpringType): SpringPreset = when (type) {
            SakugaSpringType.PRESSED -> PRESSED
            SakugaSpringType.RELEASED -> RELEASED
            SakugaSpringType.HOVERED -> HOVERED
            SakugaSpringType.SNAPPY -> SNAPPY
            SakugaSpringType.PLAYFUL -> PLAYFUL
            SakugaSpringType.PREMIUM -> PREMIUM
        }
    }

    /** Role-specific defaults mapped over [Springs] and [ValueAnimatorPreset]s. */
    object Buttons {
        const val PRESS_SCALE_X = 1.06f
        const val PRESS_SCALE_Y = 0.90f
        const val ICON_PRESS_SCALE_X = 1.09f
        const val ICON_PRESS_SCALE_Y = 0.87f

        val pressSpring = Springs.PRESSED
        val releaseOvershootSpring = Springs.RELEASED
        val hoverSpring = Springs.HOVERED
        val colorAnimator = ValueAnimatorPreset(durationMs = 170L)
    }

    object Toggles {
        const val PRESS_SCALE_X = 1.05f
        const val PRESS_SCALE_Y = 0.92f
        const val MAX_SMEAR_STRETCH = 1.44f

        val slideSpring = SpringPreset(dampingRatio = 0.54f, stiffness = 460f)
        val selectionPopSpring = Springs.PLAYFUL
        val trackColorAnimator = ValueAnimatorPreset(durationMs = 200L)
        val checkDrawAnimator = ValueAnimatorPreset(durationMs = 240L)
        val checkFillAnimator = ValueAnimatorPreset(durationMs = 170L)
        val ringColorAnimator = ValueAnimatorPreset(durationMs = 180L)
    }

    object Cards {
        const val PRESS_SCALE_X = 1.018f
        const val PRESS_SCALE_Y = 0.976f

        val pressSpring = Springs.PRESSED
        val releaseSpring = Springs.PREMIUM
        val hoverSpring = Springs.HOVERED
        val entranceSpring = SpringPreset(dampingRatio = 0.62f, stiffness = 420f)
        val expandSpring = Springs.PREMIUM
        val surfaceColorAnimator = ValueAnimatorPreset(durationMs = 180L)
    }

    object Indicators {
        val leadSpring = Springs.SNAPPY
        val trailSpring = SpringPreset(dampingRatio = 0.72f, stiffness = 260f)
        val indeterminateSweepAnimator = ValueAnimatorPreset(durationMs = 1150L)
    }

    object SheetsAndDialogs {
        val sheetTransitionSpring = Springs.PREMIUM
        val dialogEntranceSpring = Springs.PLAYFUL
        val dialogExitAnimator = ValueAnimatorPreset(durationMs = 150L)
        val scrimAnimator = ValueAnimatorPreset(durationMs = 180L)
    }

    private val SOFT_PROFILE = MotionProfile(
        preset = SakugaMotionPreset.SOFT,
        pressSpring = SpringPreset(dampingRatio = 0.85f, stiffness = 620f),
        releaseSpring = SpringPreset(dampingRatio = 0.82f, stiffness = 360f),
        hoverSpring = Springs.HOVERED,
        toggleSpring = SpringPreset(dampingRatio = 0.78f, stiffness = 380f),
        cardSpring = SpringPreset(dampingRatio = 0.86f, stiffness = 340f),
        sheetSpring = Springs.PREMIUM,
        pressScaleX = 1.03f,
        pressScaleY = 0.95f,
        cardPressScaleX = 1.01f,
        cardPressScaleY = 0.988f,
        hoverScale = 1.014f,
        hoverElevationDp = 2f,
        defaultParticleCount = 6,
        defaultBurstRadiusDp = 24f,
        durationScale = 1.15f,
    )

    private val PREMIUM_PROFILE = MotionProfile(
        preset = SakugaMotionPreset.PREMIUM,
        pressSpring = Springs.PRESSED,
        releaseSpring = Springs.RELEASED,
        hoverSpring = Springs.HOVERED,
        toggleSpring = Toggles.slideSpring,
        cardSpring = Springs.PREMIUM,
        sheetSpring = Springs.PREMIUM,
        pressScaleX = Buttons.PRESS_SCALE_X,
        pressScaleY = Buttons.PRESS_SCALE_Y,
        cardPressScaleX = Cards.PRESS_SCALE_X,
        cardPressScaleY = Cards.PRESS_SCALE_Y,
        hoverScale = 1.022f,
        hoverElevationDp = 3f,
        defaultParticleCount = 10,
        defaultBurstRadiusDp = 30f,
        durationScale = 1.0f,
    )

    private val PLAYFUL_PROFILE = MotionProfile(
        preset = SakugaMotionPreset.PLAYFUL,
        pressSpring = SpringPreset(dampingRatio = 0.64f, stiffness = 840f),
        releaseSpring = Springs.PLAYFUL,
        hoverSpring = SpringPreset(dampingRatio = 0.62f, stiffness = 460f),
        toggleSpring = Springs.PLAYFUL,
        cardSpring = Springs.RELEASED,
        sheetSpring = Springs.RELEASED,
        pressScaleX = 1.09f,
        pressScaleY = 0.87f,
        cardPressScaleX = 1.028f,
        cardPressScaleY = 0.964f,
        hoverScale = 1.032f,
        hoverElevationDp = 4f,
        defaultParticleCount = 14,
        defaultBurstRadiusDp = 38f,
        durationScale = 1.0f,
    )

    private val SNAPPY_PROFILE = MotionProfile(
        preset = SakugaMotionPreset.SNAPPY,
        pressSpring = SpringPreset(dampingRatio = 0.78f, stiffness = 1150f),
        releaseSpring = Springs.SNAPPY,
        hoverSpring = Springs.SNAPPY,
        toggleSpring = Springs.SNAPPY,
        cardSpring = Springs.SNAPPY,
        sheetSpring = Springs.SNAPPY,
        pressScaleX = 1.045f,
        pressScaleY = 0.92f,
        cardPressScaleX = 1.014f,
        cardPressScaleY = 0.980f,
        hoverScale = 1.018f,
        hoverElevationDp = 2.5f,
        defaultParticleCount = 8,
        defaultBurstRadiusDp = 26f,
        durationScale = 0.78f,
    )

    fun profileFor(preset: SakugaMotionPreset): MotionProfile = when (preset) {
        SakugaMotionPreset.SOFT -> SOFT_PROFILE
        SakugaMotionPreset.PREMIUM -> PREMIUM_PROFILE
        SakugaMotionPreset.PLAYFUL -> PLAYFUL_PROFILE
        SakugaMotionPreset.SNAPPY -> SNAPPY_PROFILE
    }

    fun pressScaleFor(
        role: ComponentRole,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): Pair<Float, Float> {
        val p = profileFor(preset)
        return when (role) {
            ComponentRole.BUTTON -> p.pressScaleX to p.pressScaleY
            ComponentRole.ICON_BUTTON -> {
                val boostX = p.pressScaleX + 0.03f
                val dropY = (p.pressScaleY - 0.03f).coerceAtLeast(0.82f)
                boostX to dropY
            }
            ComponentRole.TOGGLE -> Toggles.PRESS_SCALE_X to Toggles.PRESS_SCALE_Y
            ComponentRole.CARD -> p.cardPressScaleX to p.cardPressScaleY
            ComponentRole.INDICATOR, ComponentRole.SHEET, ComponentRole.DIALOG -> 1f to 1f
        }
    }

    fun springPresetFor(
        role: ComponentRole,
        pressed: Boolean = false,
        hovered: Boolean = false,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): SpringPreset {
        val profile = profileFor(preset)
        if (pressed) return profile.pressSpring
        if (hovered) return profile.hoverSpring
        return when (role) {
            ComponentRole.BUTTON, ComponentRole.ICON_BUTTON -> profile.releaseSpring
            ComponentRole.TOGGLE -> profile.toggleSpring
            ComponentRole.CARD -> profile.cardSpring
            ComponentRole.INDICATOR -> Indicators.leadSpring
            ComponentRole.SHEET -> profile.sheetSpring
            ComponentRole.DIALOG -> if (preset == SakugaMotionPreset.SOFT) Springs.PREMIUM else profile.releaseSpring
        }
    }

    fun valueAnimatorPresetFor(role: ComponentRole): ValueAnimatorPreset = when (role) {
        ComponentRole.BUTTON, ComponentRole.ICON_BUTTON -> Buttons.colorAnimator
        ComponentRole.TOGGLE -> Toggles.trackColorAnimator
        ComponentRole.CARD -> Cards.surfaceColorAnimator
        ComponentRole.INDICATOR -> Indicators.indeterminateSweepAnimator
        ComponentRole.SHEET, ComponentRole.DIALOG -> SheetsAndDialogs.scrimAnimator
    }

    /** Section E: State Transition Engine target resolution. */
    fun stateTransformFor(
        state: SakugaComponentState,
        role: ComponentRole = ComponentRole.BUTTON,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): StateTransformSpec {
        val profile = profileFor(preset)
        val (pressX, pressY) = pressScaleFor(role, preset)
        return when (state) {
            SakugaComponentState.IDLE, SakugaComponentState.RELEASED -> StateTransformSpec(
                scaleX = 1f,
                scaleY = 1f,
                alpha = 1f,
                elevationDeltaDp = 0f,
                springPreset = profile.releaseSpring,
            )
            SakugaComponentState.PRESSED -> StateTransformSpec(
                scaleX = pressX,
                scaleY = pressY,
                alpha = 0.96f,
                elevationDeltaDp = -1f,
                springPreset = profile.pressSpring,
            )
            SakugaComponentState.HOVERED -> StateTransformSpec(
                scaleX = profile.hoverScale,
                scaleY = profile.hoverScale,
                alpha = 1f,
                elevationDeltaDp = profile.hoverElevationDp,
                translationYDp = -1f,
                springPreset = profile.hoverSpring,
            )
            SakugaComponentState.SELECTED -> StateTransformSpec(
                scaleX = 1.03f,
                scaleY = 1.03f,
                alpha = 1f,
                elevationDeltaDp = 2f,
                springPreset = profile.releaseSpring,
            )
            SakugaComponentState.DISABLED -> StateTransformSpec(
                scaleX = 1f,
                scaleY = 1f,
                alpha = 0.38f,
                elevationDeltaDp = 0f,
                springPreset = Springs.PREMIUM,
            )
            SakugaComponentState.LOADING -> StateTransformSpec(
                scaleX = 0.985f,
                scaleY = 0.985f,
                alpha = 0.85f,
                elevationDeltaDp = 0f,
                springPreset = Springs.PREMIUM,
            )
            SakugaComponentState.SUCCESS -> StateTransformSpec(
                scaleX = 1.045f,
                scaleY = 1.045f,
                alpha = 1f,
                elevationDeltaDp = 3f,
                springPreset = Springs.PLAYFUL,
            )
            SakugaComponentState.ERROR -> StateTransformSpec(
                scaleX = 1.02f,
                scaleY = 0.97f,
                alpha = 1f,
                elevationDeltaDp = 1f,
                springPreset = Springs.SNAPPY,
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Android View-Based DynamicAnimation & ValueAnimator Utilities (Sections A, C, G, H)
    // -----------------------------------------------------------------------------------------

    private class ActiveViewAnimators {
        var scaleX: SpringAnimation? = null
        var scaleY: SpringAnimation? = null
        var translationY: SpringAnimation? = null
        var elevation: SpringAnimation? = null
        var valueAnimator: Animator? = null
        var attachListener: View.OnAttachStateChangeListener? = null

        fun cancelAll() {
            scaleX?.cancel()
            scaleY?.cancel()
            translationY?.cancel()
            elevation?.cancel()
            valueAnimator?.cancel()
            scaleX = null
            scaleY = null
            translationY = null
            elevation = null
            valueAnimator = null
        }
    }

    // WeakHashMap guarantees zero leaks if a View is GC'd; OnAttachStateChangeListener cancels on detach.
    private val activeViewAnimators = WeakHashMap<View, ActiveViewAnimators>()

    private fun trackHolder(view: View): ActiveViewAnimators {
        synchronized(activeViewAnimators) {
            return activeViewAnimators.getOrPut(view) {
                val holder = ActiveViewAnimators()
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) {
                        cancelViewAnimations(v)
                    }
                }
                holder.attachListener = listener
                view.addOnAttachStateChangeListener(listener)
                holder
            }
        }
    }

    /** Cancels all active Sakuga springs and animators owned by [view] cleanly. */
    fun cancelViewAnimations(view: View) {
        synchronized(activeViewAnimators) {
            activeViewAnimators[view]?.cancelAll()
        }
    }

    /** Creates a brand-new [SpringAnimation] for a single target [view] from an immutable [SpringPreset]. */
    fun createViewSpring(
        view: View,
        property: FloatPropertyCompat<View>,
        preset: SpringPreset,
        finalPosition: Float,
    ): SpringAnimation = SpringAnimation(view, property).apply {
        minimumVisibleChange = preset.minimumVisibleChange
        spring = preset.createSpringForce(finalPosition)
    }

    fun createScaleSpringPair(
        view: View,
        preset: SpringPreset,
        targetScaleX: Float,
        targetScaleY: Float,
    ): Pair<SpringAnimation, SpringAnimation> =
        createViewSpring(view, DynamicAnimation.SCALE_X, preset, targetScaleX) to
            createViewSpring(view, DynamicAnimation.SCALE_Y, preset, targetScaleY)

    fun createFloatValueSpring(
        initialValue: Float,
        preset: SpringPreset,
        finalPosition: Float,
        onUpdate: (Float) -> Unit,
    ): SpringAnimation = SpringAnimation(FloatValueHolder(initialValue)).apply {
        minimumVisibleChange = preset.minimumVisibleChange
        spring = preset.createSpringForce(finalPosition)
        addUpdateListener { _, value, _ -> onUpdate(value) }
    }

    private val VIEW_ELEVATION_PROPERTY = object : FloatPropertyCompat<View>("elevation") {
        override fun getValue(view: View): Float = view.elevation
        override fun setValue(view: View, value: Float) {
            view.elevation = value
        }
    }

    /**
     * Drives unified state transitions (IDLE, PRESSED, HOVERED, SELECTED, DISABLED, etc.) on a traditional
     * Android [View], cancelling prior conflicting scale/translation/elevation springs automatically.
     */
    fun animateViewState(
        view: View,
        state: SakugaComponentState,
        role: ComponentRole = ComponentRole.BUTTON,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
        baseElevationPx: Float = view.elevation,
        reducedMotion: Boolean = false,
    ) {
        val holder = trackHolder(view)
        holder.scaleX?.cancel()
        holder.scaleY?.cancel()
        holder.translationY?.cancel()
        holder.elevation?.cancel()
        holder.valueAnimator?.cancel()

        val spec = stateTransformFor(state, role, preset)
        val density = view.resources.displayMetrics.density
        val targetTyPx = spec.translationYDp * density
        val targetElevationPx = (baseElevationPx + spec.elevationDeltaDp * density).coerceAtLeast(0f)

        if (reducedMotion) {
            view.scaleX = spec.scaleX
            view.scaleY = spec.scaleY
            view.alpha = spec.alpha
            view.translationY = targetTyPx
            view.elevation = targetElevationPx
            return
        }

        holder.scaleX = createViewSpring(view, DynamicAnimation.SCALE_X, spec.springPreset, spec.scaleX).also { it.start() }
        holder.scaleY = createViewSpring(view, DynamicAnimation.SCALE_Y, spec.springPreset, spec.scaleY).also { it.start() }
        holder.translationY = createViewSpring(
            view,
            DynamicAnimation.TRANSLATION_Y,
            spec.springPreset.copy(minimumVisibleChange = DEFAULT_MIN_VISIBLE_CHANGE_PIXELS),
            targetTyPx,
        ).also { it.start() }
        holder.elevation = createViewSpring(
            view,
            VIEW_ELEVATION_PROPERTY,
            spec.springPreset.copy(minimumVisibleChange = DEFAULT_MIN_VISIBLE_CHANGE_PIXELS),
            targetElevationPx,
        ).also { it.start() }

        if (view.alpha != spec.alpha) {
            holder.valueAnimator = createFloatAnimator(
                preset = valueAnimatorPresetFor(role),
                view.alpha,
                spec.alpha,
                reducedMotion = false,
            ) { a -> view.alpha = a }.also { it.start() }
        }
    }

    /** Convenience press helper for traditional Android [View]s. */
    fun animateViewPress(
        view: View,
        role: ComponentRole,
        pressed: Boolean,
        reducedMotion: Boolean = false,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): Pair<SpringAnimation, SpringAnimation> {
        val state = if (pressed) SakugaComponentState.PRESSED else SakugaComponentState.RELEASED
        val spec = stateTransformFor(state, role, preset)
        val holder = trackHolder(view)
        holder.scaleX?.cancel()
        holder.scaleY?.cancel()
        val pair = createScaleSpringPair(view, spec.springPreset, spec.scaleX, spec.scaleY)
        holder.scaleX = pair.first
        holder.scaleY = pair.second
        if (reducedMotion) {
            view.scaleX = spec.scaleX
            view.scaleY = spec.scaleY
        } else {
            pair.first.start()
            pair.second.start()
        }
        return pair
    }

    /** Binds press & hover Sakuga touch feedback onto any traditional Android [View], auto-cleaning on detach. */
    @SuppressLint("ClickableViewAccessibility")
    fun attachViewTouchFeedback(
        view: View,
        role: ComponentRole = ComponentRole.BUTTON,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
        reducedMotion: Boolean = false,
    ) {
        val baseElevation = view.elevation
        trackHolder(view)
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    animateViewState(v, SakugaComponentState.PRESSED, role, preset, baseElevation, reducedMotion)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    animateViewState(v, SakugaComponentState.RELEASED, role, preset, baseElevation, reducedMotion)
            }
            false
        }
        view.setOnHoverListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER ->
                    animateViewState(v, SakugaComponentState.HOVERED, role, preset, baseElevation, reducedMotion)
                MotionEvent.ACTION_HOVER_EXIT ->
                    animateViewState(v, SakugaComponentState.IDLE, role, preset, baseElevation, reducedMotion)
            }
            false
        }
    }

    /** Creates an independent entrance spring set (scale + alpha fade) for View dialogs/sheets. */
    fun createDialogEntranceAnimator(
        view: View,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
        reducedMotion: Boolean = false,
    ): Animator {
        val holder = trackHolder(view)
        holder.cancelAll()
        if (reducedMotion) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            return ValueAnimator.ofFloat(1f, 1f).apply { duration = 0L }
        }
        val springPreset = springPresetFor(ComponentRole.DIALOG, preset = preset)
        view.scaleX = 0.88f
        view.scaleY = 0.88f
        view.alpha = 0f
        holder.scaleX = createViewSpring(view, DynamicAnimation.SCALE_X, springPreset, 1f).also { it.start() }
        holder.scaleY = createViewSpring(view, DynamicAnimation.SCALE_Y, springPreset, 1f).also { it.start() }
        val alphaAnim = createFloatAnimator(
            preset = SheetsAndDialogs.scrimAnimator,
            0f,
            1f,
            reducedMotion = false,
        ) { view.alpha = it }
        holder.valueAnimator = alphaAnim
        return AnimatorSet().apply { playTogether(alphaAnim) }
    }

    fun createFloatAnimator(
        preset: ValueAnimatorPreset,
        vararg values: Float,
        reducedMotion: Boolean = false,
        durationScale: Float = 1f,
        onUpdate: ((Float) -> Unit)? = null,
    ): ValueAnimator = ValueAnimator.ofFloat(*values).apply {
        duration = if (reducedMotion) 0L else (preset.durationMs * durationScale).toLong().coerceAtLeast(1L)
        interpolator = preset.createInterpolator()
        if (onUpdate != null) {
            addUpdateListener { onUpdate(it.animatedValue as Float) }
        }
    }

    fun createFloatAnimator(
        role: ComponentRole,
        vararg values: Float,
        reducedMotion: Boolean = false,
        onUpdate: ((Float) -> Unit)? = null,
    ): ValueAnimator = createFloatAnimator(
        preset = valueAnimatorPresetFor(role),
        values = values,
        reducedMotion = reducedMotion,
        onUpdate = onUpdate,
    )

    fun createColorAnimator(
        preset: ValueAnimatorPreset,
        startColor: Int,
        endColor: Int,
        reducedMotion: Boolean = false,
        onUpdate: ((Int) -> Unit)? = null,
    ): ValueAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor).apply {
        duration = if (reducedMotion) 0L else preset.durationMs
        interpolator = preset.createInterpolator()
        if (onUpdate != null) {
            addUpdateListener { onUpdate(it.animatedValue as Int) }
        }
    }

    fun createColorAnimator(
        role: ComponentRole,
        startColor: Int,
        endColor: Int,
        reducedMotion: Boolean = false,
        onUpdate: ((Int) -> Unit)? = null,
    ): ValueAnimator = createColorAnimator(
        preset = valueAnimatorPresetFor(role),
        startColor = startColor,
        endColor = endColor,
        reducedMotion = reducedMotion,
        onUpdate = onUpdate,
    )

    // -----------------------------------------------------------------------------------------
    // Compose Spec Bridges
    // -----------------------------------------------------------------------------------------

    fun <T> pressSpec(
        role: ComponentRole,
        pressed: Boolean,
        reducedMotion: Boolean,
        hovered: Boolean = false,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): FiniteAnimationSpec<T> =
        springPresetFor(role, pressed = pressed, hovered = hovered, preset = preset).toComposeSpring(reducedMotion)

    fun <T> springSpec(
        type: SakugaSpringType,
        reducedMotion: Boolean,
    ): FiniteAnimationSpec<T> = Springs.byType(type).toComposeSpring(reducedMotion)

    fun <T> toggleSlideSpec(
        reducedMotion: Boolean,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): FiniteAnimationSpec<T> = profileFor(preset).toggleSpring.toComposeSpring(reducedMotion)

    fun <T> entranceSpec(
        reducedMotion: Boolean,
        preset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): FiniteAnimationSpec<T> = when (preset) {
        SakugaMotionPreset.PLAYFUL -> Springs.PLAYFUL
        SakugaMotionPreset.SNAPPY -> Springs.SNAPPY
        else -> Cards.entranceSpring
    }.toComposeSpring(reducedMotion)

    fun <T> tweenSpec(
        preset: ValueAnimatorPreset,
        reducedMotion: Boolean,
        motionPreset: SakugaMotionPreset = SakugaMotionPreset.PREMIUM,
    ): TweenSpec<T> = preset.toComposeTween(reducedMotion, profileFor(motionPreset).durationScale)
}
