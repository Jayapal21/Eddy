package app.eddy.browser.ui.sakuga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable expandable card with smooth Sakuga spring expansion, spring-rotated chevron,
 * hover lift, press compression, and subtle lavender particle burst (Section C).
 */
@Composable
fun SakugaExpandableCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke? = null,
    preset: SakugaMotionPreset? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = preset ?: LocalSakugaMotionPreset.current
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = SakugaAnimationController.springSpec(SakugaSpringType.RELEASED, reduced),
        label = "expandChevronRotation",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .sakugaPressEffect(
                interactionSource = source,
                role = SakugaAnimationController.ComponentRole.CARD,
                particlePreset = SakugaParticlePreset.SUBTLE,
                state = if (expanded) SakugaComponentState.SELECTED else SakugaComponentState.IDLE,
                preset = resolvedPreset,
            )
            .animateContentSize(
                animationSpec = SakugaAnimationController.Cards.expandSpring.toComposeSpring(reduced),
            ),
        shape = shape,
        color = containerColor,
        border = border,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(
                        interactionSource = source,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = {
                            haptics.tick()
                            onToggle()
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(SakugaAnimationController.tweenSpec(SakugaAnimationController.Cards.surfaceColorAnimator, reduced)) +
                    expandVertically(SakugaAnimationController.Cards.expandSpring.toComposeSpring(reduced)),
                exit = fadeOut(SakugaAnimationController.tweenSpec(SakugaAnimationController.Cards.surfaceColorAnimator, reduced)) +
                    shrinkVertically(SakugaAnimationController.Cards.expandSpring.toComposeSpring(reduced)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * Reusable ModalBottomSheet wrapper with Sakuga spring settling entrance motion
 * driven from [SakugaAnimationController.SheetsAndDialogs] (Section C).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakugaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    preset: SakugaMotionPreset? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = preset ?: LocalSakugaMotionPreset.current
    val enterScale = remember { Animatable(if (reduced) 1f else 0.965f) }
    val enterOffsetY = remember { Animatable(if (reduced) 0f else 18f) }

    LaunchedEffect(Unit) {
        if (!reduced) {
            val spec = SakugaAnimationController.springSpec<Float>(SakugaSpringType.PREMIUM, false)
            enterScale.animateTo(1f, spec)
            enterOffsetY.animateTo(0f, spec)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.graphicsLayer {
                scaleX = enterScale.value
                scaleY = enterScale.value
                translationY = if (resolvedPreset == SakugaMotionPreset.SOFT) enterOffsetY.value * 0.5f else enterOffsetY.value
            },
            content = content,
        )
    }
}

/**
 * Standalone reusable elastic pill indicator where leading and trailing edges chase target
 * indices at staggered spring stiffnesses for responsive Sakuga smear motion (Section C).
 */
@Composable
fun SakugaElasticTabIndicator(
    slotCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    height: Dp = 40.dp,
    shape: Shape = CircleShape,
) {
    if (slotCount <= 0) return
    val reduced = LocalReducedMotion.current
    val clamped = selectedIndex.coerceIn(0, slotCount - 1).toFloat()

    val lead by animateFloatAsState(
        targetValue = clamped,
        animationSpec = SakugaAnimationController.Indicators.leadSpring.toComposeSpring(reduced),
        label = "elasticTabLead",
    )
    val trail by animateFloatAsState(
        targetValue = clamped,
        animationSpec = SakugaAnimationController.Indicators.trailSpring.toComposeSpring(reduced),
        label = "elasticTabTrail",
    )
    val startIndex = min(lead, trail)
    val endIndex = max(lead, trail) + 1f

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slotWidth = maxWidth / slotCount
        Box(
            Modifier
                .offset(x = slotWidth * startIndex)
                .width((slotWidth * (endIndex - startIndex)).coerceAtLeast(slotWidth))
                .height(height)
                .clip(shape)
                .background(indicatorColor),
        )
    }
}

/**
 * Reusable animated navigation selection container combining spring scale pop,
 * hover lift, and crisp lavender highlight sparks on selection (Section C).
 */
@Composable
fun SakugaNavSelectionItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preset: SakugaMotionPreset? = null,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }

    val iconScale by animateFloatAsState(
        targetValue = if (selected && !reduced) 1.08f else 1f,
        animationSpec = SakugaAnimationController.springSpec(SakugaSpringType.RELEASED, reduced),
        label = "navItemIconScale",
    )

    Row(
        modifier = modifier
            .heightIn(min = Dimens.touchTarget)
            .sakugaPressEffect(
                interactionSource = source,
                role = SakugaAnimationController.ComponentRole.TOGGLE,
                particlePreset = if (selected) SakugaParticlePreset.LAVENDER_BURST else SakugaParticlePreset.SUBTLE,
                state = if (selected) SakugaComponentState.SELECTED else SakugaComponentState.IDLE,
                preset = preset,
            )
            .clip(CircleShape)
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
