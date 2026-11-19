package app.eddy.browser.ui.sakuga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Clickable Surface card with Sakuga press squash, bouncy release rebound,
 * and optional impact particle burst.
 */
@Composable
fun SakugaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    burstOnClick: Boolean = false,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.LAVENDER_BURST,
    state: SakugaComponentState = SakugaComponentState.IDLE,
    preset: SakugaMotionPreset? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.CARD,
            particlePreset = particlePreset,
            enableParticles = burstOnClick,
            enableImpactRing = burstOnClick,
            state = state,
            preset = preset,
        ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        shadowElevation = shadowElevation,
        tonalElevation = tonalElevation,
        interactionSource = source,
        content = content,
    )
}

/**
 * Sakuga-enhanced OutlinedTextField with smooth focus border glow, micro-lift on focus,
 * and subtle keystroke kinetic pulse.
 */
@Composable
fun SakugaOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val pulse = remember { Animatable(1f) }
    var prevLength by remember { mutableIntStateOf(value.length) }
    LaunchedEffect(value.length) {
        if (!reduced && value.length > prevLength && focused) {
            pulse.snapTo(1.012f)
            pulse.animateTo(1f, SakugaPhysics.pressSpring(false))
        }
        prevLength = value.length
    }

    var wasFocused by remember { mutableStateOf(focused) }
    LaunchedEffect(focused) {
        if (focused && !wasFocused && controller.lastSize.width > 0f) {
            controller.emit(
                SakugaEffectType.SUBTLE_POP,
                Offset(controller.lastSize.width * 0.14f, controller.lastSize.height * 0.5f),
                sakugaColors,
                intensity = 0.65f,
            )
        }
        wasFocused = focused
    }

    val inputState = when {
        isError -> SakugaComponentState.ERROR
        focused -> SakugaComponentState.SELECTED
        else -> SakugaComponentState.IDLE
    }
    val stateValues = rememberSakugaStateValues(
        state = inputState,
        role = SakugaAnimationController.ComponentRole.CARD,
    )
    val focusScale by animateFloatAsState(
        targetValue = if (focused && !reduced) 1.008f else 1f,
        animationSpec = SakugaAnimationController.springSpec(SakugaSpringType.SNAPPY, reduced),
        label = "inputFocusScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (focused) scheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = SakugaAnimationController.tweenSpec(SakugaAnimationController.Cards.surfaceColorAnimator, reduced),
        label = "inputFocusContainer",
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .graphicsLayer {
                val s = focusScale * pulse.value
                scaleX = s
                scaleY = s
                translationX = stateValues.errorShakePx
            }
            .sakugaOverlay(controller),
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedBorderColor = sakugaColors.primaryAccent,
        ),
    )
}

data class SakugaTabItem(
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * Elastic segmented tab selector: the pill's leading edge springs ahead of its trailing edge,
 * creating classic Sakuga horizontal smear/stretch motion between options plus an impact pop.
 */
@Composable
fun SakugaSegmentedRow(
    items: List<SakugaTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val haptics = rememberHaptics()

    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    var previousIndex by remember { mutableIntStateOf(clampedIndex) }
    LaunchedEffect(clampedIndex) {
        if (clampedIndex != previousIndex) {
            previousIndex = clampedIndex
            val w = controller.lastSize.width
            val h = controller.lastSize.height
            if (w > 0f && h > 0f) {
                val targetX = ((clampedIndex + 0.5f) / items.size) * w
                controller.emit(SakugaEffectType.WEDGE_POP, Offset(targetX, h * 0.5f), sakugaColors, intensity = 0.85f)
            }
        }
    }

    val fastPos by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = SakugaPhysics.indicatorLeadSpring(reduced),
        label = "tabLeadEdge",
    )
    val slowPos by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = SakugaPhysics.indicatorTrailSpring(reduced),
        label = "tabTrailEdge",
    )
    val startIndex = min(fastPos, slowPos)
    val endIndex = max(fastPos, slowPos) + 1f

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .sakugaOverlay(controller),
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val slotWidth = maxWidth / items.size
            val leftOffset = slotWidth * startIndex
            val indicatorWidth = (slotWidth * (endIndex - startIndex)).coerceAtLeast(slotWidth)

            SakugaElasticTabIndicator(
                slotCount = items.size,
                selectedIndex = clampedIndex,
                indicatorColor = scheme.primaryContainer,
                height = 40.dp,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == clampedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        animationSpec = tween(if (reduced) 0 else 180),
                        label = "tabTextColor",
                    )
                    val source = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .sakugaPressEffect(
                                interactionSource = source,
                                role = SakugaAnimationController.ComponentRole.TOGGLE,
                                particlePreset = SakugaParticlePreset.SUBTLE,
                                enableParticles = false,
                                enableImpactRing = false,
                                state = if (selected) SakugaComponentState.SELECTED else SakugaComponentState.IDLE,
                            )
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = source,
                                indication = LocalIndication.current,
                                role = Role.Tab,
                                onClick = {
                                    haptics.tick()
                                    onSelect(index)
                                },
                            )
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.icon != null) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp).size(17.dp),
                                tint = textColor,
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * FilterChip enhanced with Sakuga squash-press, spring overshoot pop on selection,
 * and subtle wedge particles.
 */
@Composable
fun SakugaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    val popScale = remember { Animatable(1f) }
    var lastSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != lastSelected) {
            lastSelected = selected
            if (selected) controller.emitCenter(SakugaEffectType.WEDGE_POP, sakugaColors, intensity = 0.78f)
            if (!reduced) {
                popScale.snapTo(1.08f)
                popScale.animateTo(1f, SakugaPhysics.overshootSpring(false))
            }
        }
    }

    FilterChip(
        selected = selected,
        onClick = {
            haptics.tick()
            onClick()
        },
        label = label,
        leadingIcon = leadingIcon,
        interactionSource = source,
        modifier = modifier
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            }
            .sakugaPressSquash(
                source,
                pressX = SakugaAnimationController.Toggles.PRESS_SCALE_X,
                pressY = SakugaAnimationController.Toggles.PRESS_SCALE_Y,
                role = SakugaAnimationController.ComponentRole.TOGGLE,
            )
            .sakugaOverlay(controller),
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(),
    )
}

/**
 * Toast/Snackbar presentation with spring bounce-in, subtle leading accent bar,
 * and compact burst spark on entry.
 */
@Composable
fun SakugaSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val scheme = MaterialTheme.colorScheme
    val enterScale = remember { Animatable(if (reduced) 1f else 0.88f) }

    LaunchedEffect(data) {
        if (!reduced) {
            enterScale.snapTo(0.88f)
            enterScale.animateTo(1f, SakugaPhysics.toastEntranceSpring(false))
        }
        val size = controller.lastSize
        if (size.width > 0f && size.height > 0f) {
            controller.emit(SakugaEffectType.SUBTLE_POP, Offset(22f, size.height * 0.5f), sakugaColors, intensity = 0.75f)
        }
    }

    Snackbar(
        snackbarData = data,
        modifier = modifier
            .graphicsLayer {
                val s = enterScale.value
                scaleX = s
                scaleY = if (reduced) 1f else 1f / (s * 0.98f + 0.02f)
            }
            .sakugaOverlay(controller)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = sakugaColors.primaryAccent,
                    topLeft = Offset(6.dp.toPx(), 10.dp.toPx()),
                    size = Size(3.dp.toPx(), (size.height - 20.dp.toPx()).coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            },
        shape = RoundedCornerShape(20.dp),
        containerColor = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        actionColor = sakugaColors.primaryAccent,
    )
}

/**
 * AlertDialog wrapper providing a snappy Sakuga spring entrance & alpha transition
 * driven by [SakugaAnimationController.SheetsAndDialogs].
 */
@Composable
fun SakugaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    preset: SakugaMotionPreset? = null,
) {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = preset ?: LocalSakugaMotionPreset.current
    val pop = remember { Animatable(if (reduced) 1f else 0.88f) }
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            alpha.animateTo(
                1f,
                SakugaAnimationController.tweenSpec(SakugaAnimationController.SheetsAndDialogs.scrimAnimator, false, resolvedPreset),
            )
            pop.animateTo(
                1f,
                SakugaAnimationController.springSpec(
                    if (resolvedPreset == SakugaMotionPreset.SOFT) SakugaSpringType.PREMIUM else SakugaSpringType.RELEASED,
                    false,
                ),
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            this.alpha = alpha.value
        },
    )
}

/**
 * Interactive EmptyState with Sakuga entrance and playful crystal burst + bounce if tapped.
 */
@Composable
fun SakugaEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pop = remember { Animatable(1f) }
    var tapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(tapCount) {
        if (tapCount > 0 && !reduced) {
            pop.snapTo(1.18f)
            pop.animateTo(1f, SakugaPhysics.overshootSpring(false))
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(Dimens.gutterLarge * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .sakugaPressSquash(source, pressX = 1.12f, pressY = 0.86f)
                .sakugaOverlay(controller)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    onClick = {
                        tapCount++
                        controller.emitCenter(SakugaEffectType.CRYSTAL_BURST, sakugaColors, intensity = 0.95f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = scheme.primary.copy(alpha = 0.85f))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}
