package app.eddy.browser.ui.sakuga

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlin.math.abs

/**
 * Sakuga-style Switch with elastic smearing thumb during slide transitions,
 * smooth track morphing, and a droplet impact splash at the landing side.
 */
@Composable
fun SakugaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()

    val slideProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = SakugaAnimationController.toggleSlideSpec(reduced),
        label = "sakugaSwitchPos",
    )
    val hoverScale by animateFloatAsState(
        targetValue = if (hovered && !pressed && !reduced && enabled) 1.04f else 1f,
        animationSpec = SakugaAnimationController.springSpec(SakugaSpringType.HOVERED, reduced),
        label = "sakugaSwitchHover",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHighest,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.trackColorAnimator, reduced),
        label = "sakugaSwitchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.outline,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.trackColorAnimator, reduced),
        label = "sakugaSwitchBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else scheme.outline,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.trackColorAnimator, reduced),
        label = "sakugaSwitchThumb",
    )

    var lastChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != lastChecked) {
            lastChecked = checked
            val size = controller.lastSize
            if (size.width > 0f && size.height > 0f) {
                val targetX = if (checked) size.width * 0.72f else size.width * 0.28f
                controller.emit(SakugaEffectType.IMPACT_SPLASH, Offset(targetX, size.height * 0.5f), sakugaColors)
            }
        }
    }

    val clickableModifier = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp),
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .graphicsLayer {
                scaleX = hoverScale
                scaleY = hoverScale
            }
            .alpha(if (enabled) 1f else 0.38f)
            .sakugaOverlay(controller),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 52.dp, height = 32.dp)) {
            val w = size.width
            val h = size.height
            val trackRadius = h / 2f
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
            if (!checked) {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            val baseRadius = if (checked) 12.dp.toPx() else if (pressed) 10.dp.toPx() else 8.dp.toPx()
            val smear = SakugaPhysics.computeTravelSmear(slideProgress, reduced)
            val thumbScaleX = if (pressed && !reduced) smear * 1.10f else smear
            val thumbScaleY = if (pressed && !reduced) 0.90f else (1f / smear).coerceAtLeast(0.78f)
            val startX = 16.dp.toPx()
            val endX = w - 16.dp.toPx()
            val cx = androidx.compose.ui.util.lerp(startX, endX, slideProgress.coerceIn(0f, 1f))
            val cy = h / 2f

            withTransform({
                translate(cx, cy)
                scale(thumbScaleX, thumbScaleY, pivot = Offset.Zero)
            }) {
                drawCircle(
                    color = thumbColor,
                    radius = baseRadius,
                    center = Offset.Zero,
                )
            }
        }
    }
}

/**
 * Sakuga-style Checkbox featuring box bounce, animated self-drawing checkmark via [PathMeasure],
 * and crystal-chip burst when checked.
 */
@Composable
fun SakugaCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()

    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.checkDrawAnimator, reduced),
        label = "sakugaCheckDraw",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.checkFillAnimator, reduced),
        label = "sakugaCheckFill",
    )
    val popScale = remember { Animatable(1f) }
    var lastChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != lastChecked) {
            lastChecked = checked
            if (checked) {
                controller.emitCenterConfigured(
                    sakugaColors,
                    SakugaParticleConfig(preset = SakugaParticlePreset.LAVENDER_BURST, particleCount = 8, burstRadiusScale = 0.85f),
                )
            }
            if (!reduced) {
                popScale.snapTo(if (checked) 1.18f else 0.90f)
                popScale.animateTo(1f, SakugaAnimationController.Toggles.selectionPopSpring.toComposeSpring(false))
            }
        }
    }

    val fullPath = remember { Path() }
    val drawnPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    val clickableModifier = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 22.dp),
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .alpha(if (enabled) 1f else 0.38f)
            .sakugaOverlay(controller),
        contentAlignment = Alignment.Center,
    ) {
        val pressMul = when {
            reduced -> 1f
            pressed -> 0.88f
            hovered -> 1.08f
            else -> 1f
        }
        Canvas(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    val s = popScale.value * pressMul
                    scaleX = s
                    scaleY = s
                },
        ) {
            val boxSize = size.minDimension
            val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            if (fillAlpha > 0f) {
                drawRoundRect(
                    color = scheme.primary.copy(alpha = fillAlpha),
                    cornerRadius = corner,
                )
            }
            if (fillAlpha < 1f) {
                drawRoundRect(
                    color = scheme.onSurfaceVariant.copy(alpha = 1f - fillAlpha),
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            if (checkProgress > 0.01f) {
                fullPath.rewind()
                fullPath.moveTo(boxSize * 0.24f, boxSize * 0.52f)
                fullPath.lineTo(boxSize * 0.43f, boxSize * 0.71f)
                fullPath.lineTo(boxSize * 0.77f, boxSize * 0.31f)

                pathMeasure.setPath(fullPath, false)
                drawnPath.rewind()
                pathMeasure.getSegment(0f, pathMeasure.length * checkProgress, drawnPath, true)
                drawPath(
                    path = drawnPath,
                    color = scheme.onPrimary,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * Sakuga-style RadioButton with bouncy inner-dot pop and crystal burst on selection.
 */
@Composable
fun SakugaRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = SakugaAnimationController.Toggles.selectionPopSpring.toComposeSpring(reduced),
        label = "sakugaRadioDot",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Toggles.ringColorAnimator, reduced),
        label = "sakugaRadioRing",
    )

    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected) {
            controller.emitCenter(SakugaEffectType.CRYSTAL_BURST, sakugaColors, intensity = 0.85f)
        }
        wasSelected = selected
    }

    val boxModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 22.dp),
                onValueChange = {
                    haptics.tick()
                    onClick()
                },
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(boxModifier)
            .alpha(if (enabled) 1f else 0.38f)
            .sakugaOverlay(controller),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = ringColor,
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
            if (dotScale > 0.01f) {
                drawCircle(
                    color = scheme.primary,
                    radius = (radius * 0.52f) * dotScale,
                )
            }
        }
    }
}

/**
 * Sakuga-enhanced Slider: thumb dynamically stretches horizontally based on scrub delta
 * with spring recovery and emits soft cumulus puffs on fast drags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakugaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val source = remember { MutableInteractionSource() }

    var dragging by remember { mutableStateOf(false) }
    var lastVal by remember { mutableFloatStateOf(value) }
    var velocityFraction by remember { mutableFloatStateOf(0f) }
    val hovered by source.collectIsHoveredAsState()

    LaunchedEffect(source) {
        source.interactions.collect { interaction: Interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    velocityFraction = 0f
                }
            }
        }
    }

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    LaunchedEffect(value) {
        val delta = abs(value - lastVal) / span
        lastVal = value
        if (dragging && delta > 0.01f) {
            velocityFraction = (delta * 8f).coerceIn(0f, 1f)
            if (delta > 0.045f && controller.lastSize.width > 0f) {
                val thumbX = normalized * controller.lastSize.width
                controller.emit(
                    SakugaEffectType.PUFF_TRAIL,
                    Offset(thumbX, controller.lastSize.height * 0.5f),
                    sakugaColors,
                    intensity = 0.7f,
                )
            }
        } else if (!dragging) {
            velocityFraction = 0f
        }
    }

    val (targetSx, targetSy) = SakugaPhysics.computeVelocityDeform(
        velocityFraction = if (dragging) velocityFraction else 0f,
        reducedMotion = reduced,
    )
    val thumbSx by animateFloatAsState(targetSx, SakugaAnimationController.springSpec(SakugaSpringType.RELEASED, reduced), label = "sliderThumbSx")
    val thumbSy by animateFloatAsState(targetSy, SakugaAnimationController.springSpec(SakugaSpringType.RELEASED, reduced), label = "sliderThumbSy")
    val dragScale by animateFloatAsState(
        targetValue = when {
            reduced -> 1f
            dragging -> 1.18f
            hovered -> 1.08f
            else -> 1f
        },
        animationSpec = SakugaAnimationController.springSpec(SakugaSpringType.SNAPPY, reduced),
        label = "sliderDragScale",
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.sakugaOverlay(controller),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = {
            velocityFraction = 0f
            onValueChangeFinished?.invoke()
        },
        interactionSource = source,
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = thumbSx * dragScale
                        scaleY = thumbSy * dragScale
                    }
                    .background(
                        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
                        shape = CircleShape,
                    ),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                enabled = enabled,
            )
        },
    )
}

/**
 * Sakuga linear progress bar with smooth animated progress, bright energy leading-edge glow,
 * and clean Material 3 fallback under reduced motion.
 */
@Composable
fun SakugaLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 4.dp,
) {
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val raw = progress().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = raw,
        animationSpec = if (reduced) tween(0) else SakugaPhysics.indicatorLeadSpring(false),
        label = "sakugaLinearProgress",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val fillW = size.width * animated
        if (fillW > 0.5f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(color, sakugaColors.secondaryAccent, color),
                    endX = fillW,
                ),
                size = Size(fillW, h),
                cornerRadius = radius,
            )
            if (!reduced && animated < 0.995f) {
                drawCircle(
                    color = sakugaColors.highlightCore,
                    radius = (h * 0.95f).coerceAtLeast(2.dp.toPx()),
                    center = Offset(fillW, h / 2f),
                )
            }
        }
    }
}

/** Indeterminate Sakuga linear progress bar with sweeping comet glow. */
@Composable
fun SakugaIndeterminateLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 4.dp,
) {
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val phase = if (reduced) {
        0.5f
    } else {
        val infinite = rememberInfiniteTransition(label = "sakugaIndeterminateBar")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = SakugaAnimationController.tweenSpec(SakugaAnimationController.Indicators.indeterminateSweepAnimator, reduced),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sweep",
        ).value
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val segWidth = size.width * 0.34f
        val startX = -segWidth + (size.width + segWidth) * phase
        val clampedStart = startX.coerceAtLeast(0f)
        val clampedEnd = (startX + segWidth).coerceAtMost(size.width)
        val drawW = (clampedEnd - clampedStart).coerceAtLeast(0f)
        if (drawW > 0.5f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(color, sakugaColors.secondaryAccent),
                    startX = clampedStart,
                    endX = clampedEnd,
                ),
                topLeft = Offset(clampedStart, 0f),
                size = Size(drawW, h),
                cornerRadius = radius,
            )
        }
    }
}

/** Sakuga circular progress wrapper preserving standard deterministic/indeterminate signatures cleanly. */
@Composable
fun SakugaCircularProgress(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
) {
    val reduced = LocalReducedMotion.current
    if (progress != null) {
        val animated by animateFloatAsState(
            targetValue = progress().coerceIn(0f, 1f),
            animationSpec = if (reduced) tween(0) else SakugaPhysics.indicatorLeadSpring(false),
            label = "sakugaCircleProgress",
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = modifier.defaultMinSize(24.dp, 24.dp),
            color = color,
            strokeWidth = strokeWidth,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.defaultMinSize(24.dp, 24.dp),
            color = color,
            strokeWidth = strokeWidth,
        )
    }
}
