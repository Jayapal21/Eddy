package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.plural
import app.eddy.browser.util.str
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.graphicsLayer
import app.eddy.browser.ui.animation.effectSpring
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.sakuga.SakugaAnimationController
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.SakugaSpringType
import app.eddy.browser.ui.sakuga.rememberSakugaColors
import app.eddy.browser.ui.sakuga.rememberSakugaController
import app.eddy.browser.ui.sakuga.sakugaOverlay
import app.eddy.browser.ui.sakuga.sakugaPressSquash
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.FrostHeaderColors
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.ui.theme.UrlTextStyle
import app.eddy.browser.ui.theme.rememberFrostHeaderColors
import app.eddy.browser.util.UrlUtils

/**
 * Floating frost-glass WebView control bar: Home coin, flexible URL capsule, Tab switcher coin,
 * and 3-dot Menu coin. Morphs smoothly into a slim Dynamic Island capsule on scroll down and
 * auto-hides cleanly after inactivity.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserBar(
    tab: BrowserTab?,
    tabCount: Int,
    headerMode: HeaderMode,
    onHome: () -> Unit,
    onEdit: () -> Unit,
    onExpand: () -> Unit,
    onToggleHide: () -> Unit,
    onHeaderTouchChanged: (Boolean) -> Unit,
    onSiteInfo: () -> Unit,
    onStop: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    swipeModifier: Modifier = Modifier,
    onToggleReader: () -> Unit = {},
    overrideFrostColors: FrostHeaderColors? = null,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val defaultFrost = rememberFrostHeaderColors(isDark)
    val frost = overrideFrostColors ?: defaultFrost
    val home = tab == null || tab.isHome
    val isHidden = headerMode == HeaderMode.HIDDEN
    val showReaderToggle = tab != null && !home && !tab.isLoading && tab.error == null && (tab.readerAvailable || tab.readerActive)
    val label = when {
        tab == null || tab.isHome -> str(R.string.search_or_type_url)
        tab.error != null -> UrlUtils.displayHost(tab.url).ifEmpty { str(R.string.page_unavailable) }
        tab.host.isNotEmpty() -> tab.host
        else -> tab.url.ifEmpty { str(R.string.search_or_type_url) }
    }
    val compactLabel = when {
        tab == null || tab.isHome -> str(R.string.search_or_type_url)
        tab.error != null -> UrlUtils.compactDomain(tab.url, str(R.string.page_unavailable))
        else -> UrlUtils.compactDomain(tab.url, label).ifEmpty { label }
    }

    // Track expanded-vs-pill representation separately from HIDDEN so hiding collapses directly into top origin.
    var lastVisibleIsCompact by remember { mutableStateOf(false) }
    LaunchedEffect(headerMode) {
        when (headerMode) {
            HeaderMode.EXPANDED -> lastVisibleIsCompact = false
            HeaderMode.COMPACT -> lastVisibleIsCompact = true
            HeaderMode.HIDDEN -> Unit
        }
    }
    val isCompactLayout = when (headerMode) {
        HeaderMode.EXPANDED -> false
        HeaderMode.COMPACT -> true
        HeaderMode.HIDDEN -> lastVisibleIsCompact
    }

    val slideDistancePx = with(density) { 44.dp.toPx() }
    val motionSpring = SakugaAnimationController.springSpec<Float>(SakugaSpringType.PREMIUM, reduced)
    val hiddenAlpha by animateFloatAsState(
        targetValue = if (isHidden) 0f else 1f,
        animationSpec = if (reduced) snap() else tween(if (isHidden) 170 else 210, easing = FastOutSlowInEasing),
        label = "headerHiddenAlpha",
    )
    val hiddenScaleX by animateFloatAsState(
        targetValue = if (isHidden) (if (isCompactLayout) 0.74f else 0.90f) else 1f,
        animationSpec = motionSpring,
        label = "headerHiddenScaleX",
    )
    val hiddenScaleY by animateFloatAsState(
        targetValue = if (isHidden) (if (isCompactLayout) 0.68f else 0.84f) else 1f,
        animationSpec = motionSpring,
        label = "headerHiddenScaleY",
    )
    val hiddenTranslationY by animateFloatAsState(
        targetValue = if (isHidden) -slideDistancePx else 0f,
        animationSpec = motionSpring,
        label = "headerHiddenTranslationY",
    )

    DisposableEffect(Unit) {
        onDispose { onHeaderTouchChanged(false) }
    }

    val touchPauseObserver = Modifier.pointerInput(isHidden) {
        if (isHidden) return@pointerInput
        awaitPointerEventScope {
            var active = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedNow = event.changes.any { it.pressed }
                if (pressedNow != active) {
                    active = pressedNow
                    onHeaderTouchChanged(pressedNow)
                }
            }
        }
    }

    val visibilityLayer = Modifier.graphicsLayer {
        alpha = hiddenAlpha
        scaleX = hiddenScaleX
        scaleY = hiddenScaleY
        translationY = hiddenTranslationY
        transformOrigin = TransformOrigin(0.5f, 0f)
    }

    if (hiddenAlpha <= 0.005f && isHidden) {
        return
    }

    AnimatedContent(
        targetState = isCompactLayout,
        modifier = modifier
            .fillMaxWidth()
            .then(visibilityLayer)
            .then(touchPauseObserver),
        transitionSpec = {
            if (reduced) {
                (fadeIn(snap()) togetherWith fadeOut(snap()))
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            } else {
                val scaleSpring = spring<Float>(
                    dampingRatio = 0.84f,
                    stiffness = 440f,
                )
                val origin = TransformOrigin(0.5f, 0f)
                val enter = if (targetState) {
                    fadeIn(tween(180, delayMillis = 20, easing = FastOutSlowInEasing)) +
                        scaleIn(scaleSpring, initialScale = 1.08f, transformOrigin = origin)
                } else {
                    fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        scaleIn(scaleSpring, initialScale = 0.84f, transformOrigin = origin) +
                        expandVertically(
                            spring(dampingRatio = 0.86f, stiffness = 420f),
                            expandFrom = Alignment.Top,
                        )
                }
                val exit = if (targetState) {
                    fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                        scaleOut(tween(150, easing = FastOutSlowInEasing), targetScale = 0.80f, transformOrigin = origin)
                } else {
                    fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                        scaleOut(tween(140, easing = FastOutSlowInEasing), targetScale = 0.90f, transformOrigin = origin) +
                        shrinkVertically(tween(140, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
                }
                (enter togetherWith exit).using(SizeTransform(clip = false))
            }
        },
        label = "browserBarMode",
    ) { isCompact ->
        if (isCompact) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CompactDynamicIslandPill(
                    tab = tab,
                    domainLabel = compactLabel,
                    frost = frost,
                    interactive = !isHidden,
                    onExpand = onExpand,
                    onDoubleTapHide = {
                        haptics.tick()
                        onToggleHide()
                    },
                    modifier = if (isHidden) Modifier else swipeModifier,
                )
            }
        } else {
            val expandedSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isHidden) Modifier else swipeModifier)
                    .combinedClickable(
                        enabled = !isHidden,
                        interactionSource = expandedSource,
                        indication = null,
                        onClick = {},
                        onDoubleClick = {
                            haptics.tick()
                            onToggleHide()
                        },
                    )
                    .testTag("header_expanded_bar"),
                horizontalArrangement = Arrangement.spacedBy(Dimens.headerElementGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeCoinButton(frost = frost, onClick = onHome)
                UrlCapsule(
                    tab = tab,
                    label = label,
                    home = home,
                    showReaderToggle = showReaderToggle,
                    frost = frost,
                    onEdit = onEdit,
                    onSiteInfo = onSiteInfo,
                    onStop = onStop,
                    onToggleReader = onToggleReader,
                    modifier = Modifier.weight(1f),
                )
                TabCoinButton(
                    count = tabCount,
                    incognito = tab?.incognito == true,
                    frost = frost,
                    onClick = onTabs,
                )
                MenuCoinButton(frost = frost, onClick = onMenu)
            }
        }
    }
}

/** Dynamic Island-inspired compact frosted pill shown while scrolling down. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactDynamicIslandPill(
    tab: BrowserTab?,
    domainLabel: String,
    frost: FrostHeaderColors,
    interactive: Boolean,
    onExpand: () -> Unit,
    onDoubleTapHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val controller = rememberSakugaController()
    val sakugaColors = rememberSakugaColors()
    val source = remember { MutableInteractionSource() }
    val loading = tab?.isLoading == true && tab.error == null
    val animatedProgress by animateFloatAsState(
        targetValue = if (loading) tab!!.progress.coerceIn(6, 100) / 100f else 0f,
        animationSpec = tween(if (reduced) 0 else 220, easing = FastOutSlowInEasing),
        label = "islandLoadingProgress",
    )

    Box(
        modifier = modifier
            .heightIn(min = Dimens.touchTarget)
            .sakugaOverlay(controller)
            .combinedClickable(
                enabled = interactive,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = str(R.string.show_toolbar),
                onLongClickLabel = str(R.string.hide_toolbar),
                onClick = {
                    controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.75f)
                    haptics.tick()
                    onExpand()
                },
                onDoubleClick = onDoubleTapHide,
            )
            .testTag("header_compact_pill"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .height(Dimens.omniboxHeightCompact)
                .widthIn(min = 132.dp, max = 240.dp)
                .animateContentSize(
                    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.84f, stiffness = 430f),
                )
                .sakugaPressSquash(
                    interactionSource = source,
                    pressX = SakugaPhysics.PRESS_CARD_SCALE_X,
                    pressY = SakugaPhysics.PRESS_CARD_SCALE_Y,
                ),
            shape = CircleShape,
            color = frost.islandSurface,
            border = BorderStroke(1.dp, frost.islandBorder),
            shadowElevation = 10.dp,
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .drawWithContent {
                        drawContent()
                        // Specular frost edge along the top rim
                        drawRect(
                            color = frost.islandTopHighlight,
                            topLeft = Offset.Zero,
                            size = Size(size.width, 1.dp.toPx()),
                        )
                        // Subtle bottom progress filament while the page is loading
                        if (loading && animatedProgress > 0.01f) {
                            val barH = 2.dp.toPx()
                            val barW = size.width * animatedProgress.coerceIn(0f, 1f)
                            drawRect(
                                color = frost.accent.copy(alpha = 0.22f),
                                topLeft = Offset(0f, size.height - barH),
                                size = Size(size.width, barH),
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        frost.accent.copy(alpha = 0.75f),
                                        frost.accent,
                                    ),
                                    startX = 0f,
                                    endX = barW,
                                ),
                                topLeft = Offset(0f, size.height - barH),
                                size = Size(barW, barH),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        tab?.error != null || tab?.security == Security.ERROR -> {
                            Icon(
                                imageVector = Icons.Rounded.GppBad,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        tab?.security == Security.INSECURE -> {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        tab != null && !tab.isHome -> {
                            if (tab.favicon != null) {
                                SiteIcon(
                                    host = tab.host.ifEmpty { domainLabel },
                                    size = 18.dp,
                                    live = tab.favicon,
                                    fetch = false,
                                    shape = CircleShape,
                                    background = Color.White.copy(alpha = 0.14f),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = frost.islandSecondaryText,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = domainLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = frost.islandPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tab?.incognito == true) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = str(R.string.shortcut_incognito),
                            modifier = Modifier.size(14.dp),
                            tint = frost.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostCoin(
    frost: FrostHeaderColors,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraScale: Float = 1f,
    content: @Composable (tint: Color) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val controller = rememberSakugaController()
    val sakugaColors = rememberSakugaColors()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) frost.coinSurfacePressed else frost.coinSurface,
        animationSpec = tween(if (reduced) 0 else 160, easing = FastOutSlowInEasing),
        label = "coinSurfaceColor",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = Dimens.touchTarget)
            .sakugaOverlay(controller)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 23.dp),
                role = Role.Button,
                onClickLabel = description,
                onClick = {
                    controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.8f)
                    haptics.tick()
                    onClick()
                },
            )
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(Dimens.coinSize)
                .sakugaPressSquash(
                    interactionSource = source,
                    pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                    pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
                )
                .scale(extraScale),
            shape = CircleShape,
            color = bg,
            border = BorderStroke(1.dp, frost.border),
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            color = frost.topHighlight,
                            topLeft = Offset.Zero,
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                content(frost.primaryText)
            }
        }
    }
}

@Composable
private fun HomeCoinButton(
    frost: FrostHeaderColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostCoin(
        frost = frost,
        description = str(R.string.home),
        testTag = "header_home_button",
        onClick = onClick,
        modifier = modifier,
    ) { tint ->
        Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = tint,
        )
    }
}

@Composable
private fun TabCoinButton(
    count: Int,
    incognito: Boolean,
    frost: FrostHeaderColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val slide = spatialSpring<IntOffset>()
    val fade = effectSpring<Float>()
    val pulse = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != lastCount) {
            lastCount = count
            if (!reduced) {
                pulse.animateTo(1.12f, tween(95, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(135, easing = FastOutSlowInEasing))
            }
        }
    }
    val strokeColor = if (incognito) frost.accent else frost.primaryText

    FrostCoin(
        frost = frost,
        description = plural(R.plurals.tab_count, count),
        testTag = "header_tabs_button",
        onClick = onClick,
        modifier = modifier,
        extraScale = pulse.value,
    ) {
        Box(
            Modifier
                .size(19.dp)
                .border(1.7.dp, strokeColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith
                        (slideOutVertically(slide) { -it } + fadeOut(fade))
                },
                label = "headerTabCount",
            ) { n ->
                Text(
                    text = if (n > 99) ":D" else "$n",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = strokeColor,
                )
            }
        }
    }
}

@Composable
private fun MenuCoinButton(
    frost: FrostHeaderColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostCoin(
        frost = frost,
        description = str(R.string.menu),
        testTag = "header_menu_button",
        onClick = onClick,
        modifier = modifier,
    ) { tint ->
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@Composable
private fun UrlCapsule(
    tab: BrowserTab?,
    label: String,
    home: Boolean,
    showReaderToggle: Boolean,
    frost: FrostHeaderColors,
    onEdit: () -> Unit,
    onSiteInfo: () -> Unit,
    onStop: () -> Unit,
    onToggleReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) frost.capsuleSurfacePressed else frost.capsuleSurface,
        animationSpec = tween(if (reduced) 0 else 160, easing = FastOutSlowInEasing),
        label = "urlCapsuleBg",
    )

    Surface(
        modifier = modifier
            .heightIn(min = Dimens.urlCapsuleHeight)
            .sakugaPressSquash(
                interactionSource = source,
                pressX = SakugaPhysics.PRESS_CARD_SCALE_X,
                pressY = SakugaPhysics.PRESS_CARD_SCALE_Y,
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClickLabel = str(R.string.edit_address),
                onClick = onEdit,
            )
            .testTag("header_url_capsule"),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, frost.border),
        shadowElevation = 6.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = frost.topHighlight,
                        topLeft = Offset.Zero,
                        size = Size(size.width, 1.dp.toPx()),
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.urlCapsuleHeight)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab != null && !home) {
                    SecurityButton(tab, tab.incognito, frost, onSiteInfo)
                } else {
                    Icon(
                        imageVector = if (tab?.incognito == true) Icons.Rounded.VisibilityOff else Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                        tint = frost.secondaryText,
                    )
                }

                AnimatedContent(
                    targetState = label,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                                slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 5 }) togetherWith
                                (fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 5 })
                        }
                    },
                    label = "headerDomainLabel",
                ) { shownLabel ->
                    Text(
                        text = shownLabel,
                        style = UrlTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (home) frost.secondaryText else frost.primaryText,
                    )
                }

                AnimatedVisibility(
                    visible = showReaderToggle,
                    enter = fadeIn(tween(if (reduced) 0 else 180)) +
                        scaleIn(tween(if (reduced) 0 else 180), initialScale = 0.84f),
                    exit = fadeOut(tween(if (reduced) 0 else 140)) +
                        scaleOut(tween(if (reduced) 0 else 140), targetScale = 0.84f),
                ) {
                    ReaderToggleButton(
                        active = tab?.readerActive == true,
                        frost = frost,
                        onClick = onToggleReader,
                    )
                }

                if (tab != null && !home && tab.shieldShown) {
                    BlockedBadge(tab.blockedCount, tab.siteBlockingOff, frost, onSiteInfo)
                }

                // Only shown while loading; reload lives in the menu and pull-to-refresh.
                if (tab?.isLoading == true) {
                    Box(
                        Modifier
                            .sizeIn(minWidth = 36.dp, minHeight = Dimens.urlCapsuleHeight)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = str(R.string.stop_loading),
                                onClick = onStop,
                            )
                            .semantics { contentDescription = str(R.string.stop_loading) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = frost.secondaryText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderToggleButton(
    active: Boolean,
    frost: FrostHeaderColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val controller = rememberSakugaController()
    val sakugaColors = rememberSakugaColors()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pillBg by animateColorAsState(
        targetValue = when {
            active -> frost.activePillContainer
            pressed -> frost.coinSurfacePressed
            else -> Color.Transparent
        },
        animationSpec = tween(if (reduced) 0 else 200, easing = FastOutSlowInEasing),
        label = "readerToggleBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) frost.accent else frost.secondaryText,
        animationSpec = tween(if (reduced) 0 else 200, easing = FastOutSlowInEasing),
        label = "readerToggleTint",
    )
    val label = if (active) str(R.string.exit_reader_mode) else str(R.string.enter_reader_mode)

    Box(
        modifier
            .sizeIn(minWidth = 38.dp, minHeight = Dimens.urlCapsuleHeight)
            .sakugaOverlay(controller)
            .sakugaPressSquash(
                interactionSource = source,
                pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClickLabel = label,
                onClick = {
                    controller.emitCenter(SakugaEffectType.CRYSTAL_BURST, sakugaColors, intensity = 0.85f)
                    onClick()
                },
            )
            .semantics {
                contentDescription = label
                selected = active
            }
            .testTag("address_bar_reader_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(pillBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) Icons.AutoMirrored.Rounded.ChromeReaderMode else Icons.AutoMirrored.Outlined.ChromeReaderMode,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconTint,
            )
        }
    }
}

@Composable
private fun SecurityButton(
    tab: BrowserTab,
    incognito: Boolean,
    frost: FrostHeaderColors,
    onClick: () -> Unit,
) {
    val (icon, description) = when {
        incognito && tab.security == Security.NONE -> Icons.Rounded.VisibilityOff to str(R.string.shortcut_incognito)
        tab.security == Security.SECURE -> Icons.Rounded.Lock to str(R.string.secure_connection_open_site_information)
        tab.security == Security.INSECURE -> Icons.Rounded.Warning to str(R.string.not_secure_open_site_information)
        tab.security == Security.ERROR -> Icons.Rounded.GppBad to str(R.string.connection_problem_open_site_information)
        else -> Icons.Rounded.Search to str(R.string.site_information)
    }
    val tint = when (tab.security) {
        Security.SECURE -> frost.accent
        Security.INSECURE, Security.ERROR -> MaterialTheme.colorScheme.error
        Security.NONE -> frost.secondaryText
    }
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Dimens.urlCapsuleHeight)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab.favicon != null && tab.security == Security.SECURE) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                SiteIcon(
                    host = tab.host,
                    size = 20.dp,
                    live = tab.favicon,
                    modifier = Modifier.semantics { contentDescription = description },
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(frost.capsuleSurface)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = frost.accent,
                    )
                }
            }
        } else {
            Icon(icon, description, Modifier.size(18.dp), tint = tint)
        }
        // A word next to the icon so "not secure" never depends on colour alone.
        if (tab.security == Security.INSECURE) {
            Text(
                text = str(R.string.not_secure),
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BlockedBadge(
    count: Int,
    off: Boolean,
    frost: FrostHeaderColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = str(R.string.blocked_requests), onClick = onClick)
            .heightIn(min = 34.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // On: muted frost secondary tint so it stays quiet. Off: error tint + crossed shield icon.
        val tint = if (off) MaterialTheme.colorScheme.error else frost.secondaryText.copy(alpha = 0.85f)
        if (off) {
            Icon(Icons.Rounded.RemoveModerator, str(R.string.blocking_off_for_site), Modifier.size(15.dp), tint = tint)
        } else {
            Icon(Icons.Rounded.Shield, plural(R.plurals.requests_blocked, count), Modifier.size(15.dp), tint = tint)
            if (count > 0) {
                Text(
                    text = if (count > 99) "99+" else "$count",
                    modifier = Modifier.padding(start = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
        }
    }
}

@Composable
internal fun TabCountButton(count: Int, incognito: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val controller = rememberSakugaController()
    val sakugaColors = rememberSakugaColors()
    val slide = spatialSpring<IntOffset>()
    val fade = effectSpring<Float>()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressBg by animateColorAsState(
        targetValue = if (pressed) scheme.surfaceContainerHighest.copy(alpha = 0.72f) else Color.Transparent,
        animationSpec = tween(if (reduced) 0 else 160, easing = FastOutSlowInEasing),
        label = "tabCountBg",
    )
    val strokeColor = if (incognito) scheme.tertiary else scheme.onSurface.copy(alpha = 0.88f)

    Box(
        modifier.size(Dimens.touchTarget)
            .sakugaOverlay(controller)
            .sakugaPressSquash(
                interactionSource = source,
                pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
            )
            .clip(CircleShape)
            .background(pressBg)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClickLabel = str(R.string.open_tab_switcher),
            ) {
                controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.8f)
                haptics.tick()
                onClick()
            }
            .semantics { contentDescription = plural(R.plurals.tab_count, count) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).border(1.75.dp, strokeColor, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                count,
                transitionSpec = { (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { -it } + fadeOut(fade)) },
                label = "tabCount",
            ) { n ->
                Text(
                    if (n > 99) ":D" else "$n",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = strokeColor,
                )
            }
        }
    }
}

/** Displays the active search engine's dynamically loaded favicon with a smooth crossfade on engine changes. */
@Composable
internal fun SearchEngineIcon(
    engineHost: String,
    incognito: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    badgeBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(size + 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = engineHost,
            transitionSpec = {
                val enter = fadeIn(tween(if (reduced) 0 else 220)) +
                    scaleIn(tween(if (reduced) 0 else 220), initialScale = 0.86f)
                val exit = fadeOut(tween(if (reduced) 0 else 150))
                enter togetherWith exit
            },
            label = "searchEngineFavicon",
        ) { host ->
            if (host.isNotEmpty()) {
                SiteIcon(
                    host = host,
                    size = size,
                    fetch = true,
                    shape = CircleShape,
                    background = scheme.surfaceContainerHighest,
                )
            } else {
                Box(
                    Modifier.size(size).clip(CircleShape).background(scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.primary,
                    )
                }
            }
        }
        if (incognito) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(badgeBackground)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = scheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Expanded editing state: text field plus suggestions in one rounded surface that grows with its
 * content. Suggestions sit next to the keyboard when the toolbar is at the bottom.
 */
@Composable
fun OmniboxEditor(
    initialText: String,
    suggestions: List<Suggestion>,
    incognito: Boolean,
    atTop: Boolean,
    engineName: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPick: (Suggestion) -> Unit,
    onFill: (Suggestion) -> Unit,
    dictate: Boolean = false,
    onDictateStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
    engineHost: String = "",
) {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val voice = rememberVoiceState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Dictation streams partial transcripts, and every one of them requeries the suggestions. Letting the
    // list resize mid-hold would slide the field, and the finger would come off the mic button.
    val frozenSuggestions = remember(voice.listening) { suggestions }
    val shown = if (voice.listening) frozenSuggestions else suggestions
    val editorBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val list = @Composable {
        if (shown.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState(), reverseScrolling = !atTop)) {
                val ordered = (if (atTop) shown else shown.reversed()).distinctBy { it.kind to it.text }
                ordered.forEach { item ->
                    key(item.kind, item.text) {
                        SuggestionRow(
                            s = item,
                            onPick = { onPick(item) },
                            onFill = {
                                onFill(item)
                                value = TextFieldValue(item.text, TextRange(item.text.length))
                            },
                            engineHost = engineHost,
                            incognito = incognito,
                            badgeBorderColor = editorBg,
                        )
                    }
                }
            }
        }
    }
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val inputSource = remember { MutableInteractionSource() }
    val focused by inputSource.collectIsFocusedAsState()
    val pulse = remember { Animatable(1f) }
    var prevLen by remember { mutableIntStateOf(value.text.length) }
    LaunchedEffect(value.text.length) {
        if (!reduced && value.text.length > prevLen && focused) {
            pulse.snapTo(1.01f)
            pulse.animateTo(1f, SakugaPhysics.pressSpring(false))
        }
        prevLen = value.text.length
    }
    LaunchedEffect(focused) {
        if (focused && controller.lastSize.width > 0f) {
            controller.emit(
                SakugaEffectType.SUBTLE_POP,
                Offset(32f, 26f),
                sakugaColors,
                intensity = 0.68f,
            )
        }
    }
    val field = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.omniboxHeight).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchEngineIcon(
                engineHost = engineHost,
                incognito = incognito,
                size = 26.dp,
                badgeBackground = editorBg,
            )
            BasicTextField(
                value = value,
                onValueChange = { value = it; onTextChange(it.text) },
                interactionSource = inputSource,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp).voiceTrace(voice).focusRequester(focus),
                singleLine = true,
                textStyle = UrlTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                if (voice.listening) str(R.string.listening) else str(R.string.search_or_type_a_url),
                                style = UrlTextStyle,
                                color = if (voice.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            VoiceInputButton({ spoken -> value = TextFieldValue(spoken, TextRange(spoken.length)); onTextChange(spoken) }, voice, autoStart = dictate, onAutoStarted = onDictateStarted)
            // The slot stays put while dictating: a button appearing beside the mic would shift it too.
            AnimatedVisibility(
                visible = value.text.isNotEmpty() || voice.listening,
                enter = fadeIn(tween(if (reduced) 0 else 160)) + scaleIn(tween(if (reduced) 0 else 160), initialScale = 0.85f),
                exit = fadeOut(tween(if (reduced) 0 else 140)) + scaleOut(tween(if (reduced) 0 else 140), targetScale = 0.85f),
            ) {
                EddyIconButton(
                    Icons.Rounded.Close,
                    str(R.string.clear_text),
                    { value = TextFieldValue(""); onTextChange("") },
                    iconSize = 20.dp,
                )
            }
        }
    }

    val focusBorderColor by animateColorAsState(
        if (focused) sakugaColors.primaryAccent.copy(alpha = 0.45f) else Color.Transparent,
        tween(if (reduced) 0 else 180),
        label = "omniboxFocusBorder",
    )
    Surface(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .sakugaOverlay(controller)
            .animateContentSize(tween(150)), // a spring here overshoots and makes the field bounce
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.2.dp, focusBorderColor),
        shadowElevation = 6.dp,
    ) {
        Column {
            if (!atTop) list()
            field()
            if (atTop) list()
        }
    }
}

@Composable
internal fun SuggestionRow(
    s: Suggestion,
    onPick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
    engineHost: String = "",
    incognito: Boolean = false,
    badgeBorderColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val scheme = MaterialTheme.colorScheme
    val isNavigableUrl = s.kind == SuggestionKind.HISTORY || s.kind == SuggestionKind.BOOKMARK
    val rowSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .sakugaPressSquash(rowSource, pressX = 1.015f, pressY = 0.975f)
            .clickable(
                interactionSource = rowSource,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClick = onPick,
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SuggestionLeadingIcon(
            s = s,
            engineHost = engineHost,
            incognito = incognito,
            badgeBorderColor = badgeBorderColor,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.subtitle.isNotEmpty()) {
                Text(
                    s.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNavigableUrl) scheme.primary.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EddyIconButton(Icons.Rounded.NorthWest, str(R.string.use_in_address_bar, s.title), onFill, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionLeadingIcon(
    s: Suggestion,
    engineHost: String,
    incognito: Boolean,
    badgeBorderColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val host = s.host
    val fetchFavicons = !incognito
    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        when (s.kind) {
            SuggestionKind.SEARCH -> {
                if (host.isNotEmpty()) {
                    SiteIcon(
                        host = host,
                        size = 34.dp,
                        fetch = fetchFavicons,
                        background = scheme.primaryContainer.copy(alpha = 0.7f),
                    )
                    SuggestionCornerBadge(
                        icon = Icons.Rounded.Search,
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        borderColor = badgeBorderColor,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = scheme.onPrimaryContainer)
                    }
                    if (engineHost.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(badgeBorderColor)
                                .padding(1.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon(
                                host = engineHost,
                                size = 13.dp,
                                fetch = fetchFavicons,
                                background = scheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            }
            SuggestionKind.HISTORY -> {
                if (host.isNotEmpty()) {
                    SiteIcon(
                        host = host,
                        size = 34.dp,
                        fetch = fetchFavicons,
                        background = scheme.secondaryContainer,
                    )
                    SuggestionCornerBadge(
                        icon = Icons.Rounded.History,
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        borderColor = badgeBorderColor,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = scheme.onSecondaryContainer)
                    }
                }
            }
            SuggestionKind.BOOKMARK -> {
                if (host.isNotEmpty()) {
                    SiteIcon(
                        host = host,
                        size = 34.dp,
                        fetch = fetchFavicons,
                        background = scheme.primaryContainer.copy(alpha = 0.75f),
                    )
                    SuggestionCornerBadge(
                        icon = Icons.Rounded.Bookmark,
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        borderColor = badgeBorderColor,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp), tint = scheme.primary)
                    }
                }
            }
            SuggestionKind.DOMAIN -> {
                if (host.isNotEmpty()) {
                    SiteIcon(
                        host = host,
                        size = 34.dp,
                        fetch = fetchFavicons,
                        background = scheme.tertiaryContainer.copy(alpha = 0.65f),
                    )
                    SuggestionCornerBadge(
                        icon = Icons.Rounded.Public,
                        containerColor = scheme.tertiaryContainer,
                        contentColor = scheme.onTertiaryContainer,
                        borderColor = badgeBorderColor,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Public, null, Modifier.size(18.dp), tint = scheme.onTertiaryContainer)
                    }
                }
            }
            SuggestionKind.CLIPBOARD -> {
                if (host.isNotEmpty()) {
                    SiteIcon(
                        host = host,
                        size = 34.dp,
                        fetch = fetchFavicons,
                        background = scheme.surfaceContainerHighest,
                    )
                    SuggestionCornerBadge(
                        icon = Icons.Rounded.ContentPaste,
                        containerColor = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurfaceVariant,
                        borderColor = badgeBorderColor,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCornerBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(borderColor)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(10.dp), tint = contentColor)
    }
}

/** Compact find-in-page bar that floats next to the chrome without covering the page. */
@Composable
fun FindBar(state: FindState, onQuery: (String) -> Unit, onNext: (Boolean) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext(true) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) Text(str(R.string.find_in_page), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                Text(
                    if (state.total == 0) str(R.string.no_results) else str(R.string.find_match_of_total, state.active, state.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            EddyIconButton(Icons.Rounded.KeyboardArrowUp, str(R.string.previous_result), { onNext(false) }, enabled = state.total > 0)
            EddyIconButton(Icons.Rounded.KeyboardArrowDown, str(R.string.next_result), { onNext(true) }, enabled = state.total > 0)
            EddyIconButton(Icons.Rounded.Close, str(R.string.close_find), onClose)
        }
    }
}
