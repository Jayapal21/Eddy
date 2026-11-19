package app.eddy.browser.ui.sakuga

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

/**
 * Configuration for [Modifier.sakugaImpactEffect] and [Modifier.sakugaPressEffect].
 * Every visual layer can be independently enabled or disabled per component.
 */
@Immutable
data class SakugaImpactEffectConfig(
    val enableTouchRipple: Boolean = true,
    val enableImpactRing: Boolean = true,
    val enableScaleCompression: Boolean = true,
    val enableHoverLift: Boolean = true,
    val enableParticles: Boolean = true,
    val particleConfig: SakugaParticleConfig = SakugaParticleConfig(),
    val role: SakugaAnimationController.ComponentRole = SakugaAnimationController.ComponentRole.BUTTON,
    val preset: SakugaMotionPreset? = null,
)

@Stable
data class SakugaStateValues(
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
    val elevationDp: Dp,
    val translationYDp: Dp,
    val errorShakePx: Float,
)

/**
 * State Transition Engine composable resolving IDLE, PRESSED, RELEASED, HOVERED, SELECTED,
 * DISABLED, LOADING, SUCCESS, and ERROR into unified non-conflicting animated values.
 */
@Composable
fun rememberSakugaStateValues(
    state: SakugaComponentState,
    role: SakugaAnimationController.ComponentRole = SakugaAnimationController.ComponentRole.BUTTON,
    preset: SakugaMotionPreset? = null,
): SakugaStateValues {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = preset ?: LocalSakugaMotionPreset.current
    val target = SakugaAnimationController.stateTransformFor(state, role, resolvedPreset)
    val spec = target.springPreset.toComposeSpring<Float>(reduced)
    val dpSpec = target.springPreset.toComposeSpring<Dp>(reduced)

    val scaleX by animateFloatAsState(target.scaleX, spec, label = "sakugaStateScaleX")
    val scaleY by animateFloatAsState(target.scaleY, spec, label = "sakugaStateScaleY")
    val alpha by animateFloatAsState(
        targetValue = target.alpha,
        animationSpec = SakugaAnimationController.tweenSpec(
            SakugaAnimationController.valueAnimatorPresetFor(role),
            reduced,
            resolvedPreset,
        ),
        label = "sakugaStateAlpha",
    )
    val elevationDp by animateDpAsState(target.elevationDeltaDp.dp, dpSpec, label = "sakugaStateElevation")
    val translationYDp by animateDpAsState(target.translationYDp.dp, dpSpec, label = "sakugaStateTy")

    // Subtle breathing pulse only while LOADING; gentle dampened shake on ERROR.
    val loadingPulse = if (state == SakugaComponentState.LOADING && !reduced) {
        val inf = rememberInfiniteTransition(label = "sakugaLoadingPulse")
        inf.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(680, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loadingScale",
        ).value
    } else {
        1f
    }

    val errorShake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == SakugaComponentState.ERROR && !reduced) {
            errorShake.snapTo(-10f)
            errorShake.animateTo(0f, SakugaAnimationController.Springs.PLAYFUL.toComposeSpring(false))
        } else {
            errorShake.snapTo(0f)
        }
    }

    return SakugaStateValues(
        scaleX = scaleX * loadingPulse,
        scaleY = scaleY * loadingPulse,
        alpha = alpha,
        elevationDp = elevationDp,
        translationYDp = translationYDp,
        errorShakePx = errorShake.value,
    )
}

/**
 * Reusable Sakuga touch/impact feedback modifier (Sections D, E, G, H):
 * - Captures exact pointer touch origin from [interactionSource]
 * - Renders bounded touch-origin ripple + expanding impact ring
 * - Drives scale compression while pressed, overshoot spring-back on release, and gentle hover lift
 * - Emits configurable circular/lavender/success/delete particle bursts on release
 * - Cleans up all active animations & particles automatically when disposed.
 */
fun Modifier.sakugaImpactEffect(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    state: SakugaComponentState = SakugaComponentState.IDLE,
    config: SakugaImpactEffectConfig = SakugaImpactEffectConfig(),
    controller: SakugaMotionController? = null,
): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = config.preset ?: LocalSakugaMotionPreset.current
    val sakugaColors = rememberSakugaColors()
    val ownedController = controller ?: rememberSakugaController()
    val profile = SakugaAnimationController.profileFor(resolvedPreset)

    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    val effectiveState = when {
        !enabled -> SakugaComponentState.DISABLED
        pressed -> SakugaComponentState.PRESSED
        state != SakugaComponentState.IDLE -> state
        hovered && config.enableHoverLift -> SakugaComponentState.HOVERED
        else -> SakugaComponentState.IDLE
    }

    val stateValues = rememberSakugaStateValues(
        state = effectiveState,
        role = config.role,
        preset = resolvedPreset,
    )

    var touchOrigin by remember { mutableStateOf(Offset.Zero) }
    val rippleProgress = remember { Animatable(0f) }
    val impactRingProgress = remember { Animatable(0f) }
    val scratchPath = remember { Path() }

    LaunchedEffect(interactionSource, enabled, reduced, config) {
        if (!enabled) return@LaunchedEffect
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val sz = ownedController.lastSize
                    touchOrigin = if (interaction.pressPosition != Offset.Zero) {
                        interaction.pressPosition
                    } else {
                        Offset(sz.width * 0.5f, sz.height * 0.5f)
                    }
                    if (!reduced && config.enableTouchRipple) {
                        launch {
                            rippleProgress.snapTo(0.05f)
                            rippleProgress.animateTo(
                                1f,
                                tween((280 * profile.durationScale).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing),
                            )
                            rippleProgress.snapTo(0f)
                        }
                    }
                }
                is PressInteraction.Release -> {
                    val sz = ownedController.lastSize
                    val origin = if (touchOrigin != Offset.Zero) {
                        touchOrigin
                    } else {
                        Offset(sz.width * 0.5f, sz.height * 0.5f)
                    }
                    if (!reduced) {
                        if (config.enableImpactRing) {
                            launch {
                                impactRingProgress.snapTo(0.04f)
                                impactRingProgress.animateTo(
                                    1f,
                                    tween((260 * profile.durationScale).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing),
                                )
                                impactRingProgress.snapTo(0f)
                            }
                        }
                        if (config.enableParticles) {
                            val mergedConfig = config.particleConfig.copy(
                                particleCount = config.particleConfig.particleCount ?: profile.defaultParticleCount,
                                burstRadiusScale = config.particleConfig.burstRadiusScale * (profile.defaultBurstRadiusDp / 30f),
                            )
                            ownedController.emitConfigured(origin, sakugaColors, mergedConfig)
                        }
                    }
                }
                is PressInteraction.Cancel -> {
                    launch { rippleProgress.snapTo(0f) }
                }
            }
        }
    }

    this
        .graphicsLayer {
            if (config.enableScaleCompression || effectiveState == SakugaComponentState.HOVERED) {
                scaleX = stateValues.scaleX
                scaleY = stateValues.scaleY
            }
            if (config.enableHoverLift && effectiveState == SakugaComponentState.HOVERED) {
                translationY = stateValues.translationYDp.toPx()
            }
            if (stateValues.errorShakePx != 0f) {
                translationX = stateValues.errorShakePx
            }
        }
        .drawWithContent {
            ownedController.lastSize = size
            drawContent()

            val origin = if (touchOrigin != Offset.Zero) touchOrigin else Offset(size.width * 0.5f, size.height * 0.5f)
            val rp = rippleProgress.value
            if (!reduced && config.enableTouchRipple && rp > 0.001f && size.width > 0f && size.height > 0f) {
                val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 0.72f
                val alpha = ((1f - rp) * 0.18f).coerceIn(0f, 0.22f)
                val rippleColor = when (config.particleConfig.preset) {
                    SakugaParticlePreset.SUCCESS -> sakugaColors.successAccent
                    SakugaParticlePreset.DELETE -> sakugaColors.deleteAccent
                    SakugaParticlePreset.LAVENDER_BURST -> sakugaColors.lavenderAccent
                    else -> sakugaColors.lavenderAccent
                }
                clipRect {
                    drawCircle(
                        color = rippleColor.copy(alpha = alpha),
                        radius = maxR * rp,
                        center = origin,
                    )
                }
            }

            val irp = impactRingProgress.value
            if (!reduced && config.enableImpactRing && irp > 0.001f) {
                val ringMax = (profile.defaultBurstRadiusDp.dp.toPx() * config.particleConfig.burstRadiusScale).coerceAtLeast(16.dp.toPx())
                val alpha = ((1f - irp) * 0.55f).coerceIn(0f, 0.6f)
                val ringColor = when (config.particleConfig.preset) {
                    SakugaParticlePreset.SUCCESS -> sakugaColors.successAccent
                    SakugaParticlePreset.DELETE -> sakugaColors.deleteAccent
                    SakugaParticlePreset.LAVENDER_BURST -> sakugaColors.violetAccent
                    else -> sakugaColors.impactRing
                }
                drawCircle(
                    color = ringColor.copy(alpha = alpha),
                    radius = (6.dp.toPx() + ringMax * irp),
                    center = origin,
                    style = Stroke(width = (2.2.dp.toPx() * (1f - irp)).coerceAtLeast(0.6f)),
                )
            }

            val tick = ownedController.frameTick
            if (tick >= 0L && !reduced && config.enableParticles && ownedController.hasActiveParticles) {
                drawSakugaParticles(ownedController, scratchPath)
            }
        }
}

/**
 * Primary Compose integration modifier required by Sakuga Motion Engine (Section H).
 * Connects press, hover, touch-origin ripple, impact ring, and configurable particles.
 */
fun Modifier.sakugaPressEffect(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    role: SakugaAnimationController.ComponentRole = SakugaAnimationController.ComponentRole.BUTTON,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.PRIMARY,
    enableParticles: Boolean = true,
    enableTouchRipple: Boolean = true,
    enableImpactRing: Boolean = true,
    enableHoverLift: Boolean = true,
    state: SakugaComponentState = SakugaComponentState.IDLE,
    preset: SakugaMotionPreset? = null,
    controller: SakugaMotionController? = null,
): Modifier = sakugaImpactEffect(
    interactionSource = interactionSource,
    enabled = enabled,
    state = state,
    config = SakugaImpactEffectConfig(
        enableTouchRipple = enableTouchRipple,
        enableImpactRing = enableImpactRing,
        enableScaleCompression = true,
        enableHoverLift = enableHoverLift,
        enableParticles = enableParticles,
        particleConfig = SakugaParticleConfig(preset = particlePreset),
        role = role,
        preset = preset,
    ),
    controller = controller,
)
