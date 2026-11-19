package app.eddy.browser.ui.sakuga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion

/**
 * Primary action button powered by the Advanced Sakuga Motion Engine:
 * - Touch-origin ripple + impact ring + press squash & overshoot release
 * - Hover scale-up + lift
 * - Action-specific particle presets ([SakugaParticlePreset.PRIMARY], [SakugaParticlePreset.SUCCESS], [SakugaParticlePreset.DELETE], etc.)
 * - State Transition Engine support including animated success confirmation.
 */
@Composable
fun SakugaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    burstOnClick: Boolean = true,
    successConfirmed: Boolean = false,
    particlePreset: SakugaParticlePreset = if (successConfirmed) SakugaParticlePreset.SUCCESS else SakugaParticlePreset.PRIMARY,
    state: SakugaComponentState = if (successConfirmed) SakugaComponentState.SUCCESS else SakugaComponentState.IDLE,
    preset: SakugaMotionPreset? = null,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val controller = rememberSakugaController()
    val sakugaColors = rememberSakugaColors()
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }

    LaunchedEffect(successConfirmed) {
        if (successConfirmed && burstOnClick) {
            controller.emitCenterConfigured(sakugaColors, SakugaParticleConfig(preset = SakugaParticlePreset.SUCCESS))
        }
    }

    Button(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.BUTTON,
            particlePreset = particlePreset,
            enableParticles = burstOnClick,
            state = state,
            preset = preset,
            controller = controller,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
    ) {
        AnimatedVisibility(
            visible = successConfirmed,
            enter = fadeIn() + scaleIn(
                SakugaAnimationController.springSpec(SakugaSpringType.PLAYFUL, reduced),
                initialScale = 0.4f,
            ),
            exit = fadeOut() + scaleOut(),
        ) {
            Row {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
        }
        content()
    }
}

/** Tonal button with Sakuga touch-origin impact, hover lift, press squash, and subtle particles. */
@Composable
fun SakugaFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.LAVENDER_BURST,
    preset: SakugaMotionPreset? = null,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    FilledTonalButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.BUTTON,
            particlePreset = particlePreset,
            preset = preset,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** Outlined button with Sakuga touch impact, release overshoot, and subtle particle bursts. */
@Composable
fun SakugaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.SUBTLE,
    preset: SakugaMotionPreset? = null,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    OutlinedButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.BUTTON,
            particlePreset = particlePreset,
            preset = preset,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** Text button with responsive press squash, hover lift, spring overshoot, and optional burst. */
@Composable
fun SakugaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    burstOnClick: Boolean = false,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.SUBTLE,
    preset: SakugaMotionPreset? = null,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }

    TextButton(
        onClick = onClick,
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.BUTTON,
            particlePreset = particlePreset,
            enableParticles = burstOnClick,
            enableImpactRing = burstOnClick,
            preset = preset,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** Icon button with 48dp minimum touch target, hover scale, press squash, and semantic burst preset. */
@Composable
fun SakugaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 22.dp,
    burstOnClick: Boolean = true,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.SUBTLE,
    preset: SakugaMotionPreset? = null,
) {
    val source = remember { MutableInteractionSource() }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier
            .sizeIn(minWidth = Dimens.touchTarget, minHeight = Dimens.touchTarget)
            .sakugaPressEffect(
                interactionSource = source,
                enabled = enabled,
                role = SakugaAnimationController.ComponentRole.ICON_BUTTON,
                particlePreset = particlePreset,
                enableParticles = burstOnClick,
                preset = preset,
            ),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

/** Tonal icon button with Sakuga squash, hover lift, overshoot rebound, and lavender spark burst. */
@Composable
fun SakugaFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.LAVENDER_BURST,
    preset: SakugaMotionPreset? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            enabled = enabled,
            role = SakugaAnimationController.ComponentRole.ICON_BUTTON,
            particlePreset = particlePreset,
            preset = preset,
        ),
        content = content,
    )
}

/** Extended FAB with Sakuga touch-origin ripple, squash-and-stretch press, hover lift, and burst particles. */
@Composable
fun SakugaExtendedFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    particlePreset: SakugaParticlePreset = SakugaParticlePreset.LAVENDER_BURST,
    preset: SakugaMotionPreset? = null,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    ExtendedFloatingActionButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        icon = icon,
        text = text,
        modifier = modifier.sakugaPressEffect(
            interactionSource = source,
            role = SakugaAnimationController.ComponentRole.BUTTON,
            particlePreset = particlePreset,
            preset = preset,
        ),
        containerColor = containerColor,
        interactionSource = source,
    )
}
