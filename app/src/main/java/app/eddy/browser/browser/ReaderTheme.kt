package app.eddy.browser.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.eddy.browser.R
import app.eddy.browser.data.models.ReaderThemeMode
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.sakuga.SakugaCircularProgress
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.rememberSakugaColors
import app.eddy.browser.ui.sakuga.rememberSakugaController
import app.eddy.browser.ui.sakuga.sakugaOverlay
import app.eddy.browser.ui.sakuga.sakugaPressSquash
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.FrostHeaderColors
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.util.str
import kotlin.math.roundToInt

/**
 * Shared color tokens for Reader Mode. Both the native Compose chrome/floating controls
 * and the injected WebView article stylesheet derive from this palette.
 */
data class ReaderThemePalette(
    val background: Color,
    val readingSurface: Color,
    val toolbarBackdrop: Color,
    val toolbarSurface: Color,
    val toolbarSurfacePressed: Color,
    val capsuleSurface: Color,
    val capsuleSurfacePressed: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val dividers: Color,
    val accent: Color,
    val accentContainer: Color,
    val codeSurface: Color,
    val quoteSurface: Color,
    val topHighlight: Color,
)

/** Centralized manager for Reader Mode themes, CSS variable generation, and coordinated animations. */
object ReaderThemeManager {
    const val TRANSITION_DURATION_MS = 480
    val TransitionEasing = EaseInOut

    val DarkPalette = ReaderThemePalette(
        background = Color(0xFF080B0D),
        readingSurface = Color(0xFF11161B),
        toolbarBackdrop = Color(0xFF080B0D),
        toolbarSurface = Color(0xFF171D24).copy(alpha = 0.94f),
        toolbarSurfacePressed = Color(0xFF222B35).copy(alpha = 0.96f),
        capsuleSurface = Color(0xFF1B222B).copy(alpha = 0.94f),
        capsuleSurfacePressed = Color(0xFF242D39).copy(alpha = 0.96f),
        primaryText = Color(0xFFF5F7FA),
        secondaryText = Color(0xFFA7B0BC),
        dividers = Color(0xFF29313A),
        accent = Color(0xFF36D6AE),
        accentContainer = Color(0xFF36D6AE).copy(alpha = 0.18f),
        codeSurface = Color(0xFF171E25),
        quoteSurface = Color(0xFF151C23),
        topHighlight = Color.White.copy(alpha = 0.06f),
    )

    val LightPalette = ReaderThemePalette(
        background = Color(0xFFF8F9FA),
        readingSurface = Color(0xFFFFFFFF),
        toolbarBackdrop = Color(0xFFF8F9FA),
        toolbarSurface = Color(0xFFFFFFFF).copy(alpha = 0.96f),
        toolbarSurfacePressed = Color(0xFFEEF1F4).copy(alpha = 0.98f),
        capsuleSurface = Color(0xFFFFFFFF).copy(alpha = 0.96f),
        capsuleSurfacePressed = Color(0xFFF1F3F5),
        primaryText = Color(0xFF171B22),
        secondaryText = Color(0xFF626B78),
        dividers = Color(0xFFE5E7EB),
        accent = Color(0xFF059669),
        accentContainer = Color(0xFF059669).copy(alpha = 0.14f),
        codeSurface = Color(0xFFF1F3F5),
        quoteSurface = Color(0xFFF3F6F5),
        topHighlight = Color.White.copy(alpha = 0.85f),
    )

    fun paletteFor(mode: ReaderThemeMode): ReaderThemePalette = when (mode) {
        ReaderThemeMode.DARK -> DarkPalette
        ReaderThemeMode.LIGHT -> LightPalette
    }

    fun toHex(color: Color): String {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun cssVariables(mode: ReaderThemeMode): Map<String, String> {
        val p = paletteFor(mode)
        return linkedMapOf(
            "--er-bg" to toHex(p.background),
            "--er-surface" to toHex(p.readingSurface),
            "--er-text" to toHex(p.primaryText),
            "--er-muted" to toHex(p.secondaryText),
            "--er-divider" to toHex(p.dividers),
            "--er-accent" to toHex(p.accent),
            "--er-code-bg" to toHex(p.codeSurface),
            "--er-quote-bg" to toHex(p.quoteSurface),
        )
    }

    fun toFrostHeaderColors(palette: ReaderThemePalette): FrostHeaderColors = FrostHeaderColors(
        backdrop = palette.toolbarBackdrop,
        coinSurface = palette.toolbarSurface,
        coinSurfacePressed = palette.toolbarSurfacePressed,
        capsuleSurface = palette.capsuleSurface,
        capsuleSurfacePressed = palette.capsuleSurfacePressed,
        border = palette.dividers,
        topHighlight = palette.topHighlight,
        primaryText = palette.primaryText,
        secondaryText = palette.secondaryText,
        accent = palette.accent,
        activePillContainer = palette.accentContainer,
        islandSurface = Color(0xFF10151C).copy(alpha = 0.88f),
        islandBorder = Color.White.copy(alpha = 0.14f),
        islandTopHighlight = Color.White.copy(alpha = 0.12f),
        islandPrimaryText = Color(0xFFF5F7FA),
        islandSecondaryText = Color(0xFFB3BCC8),
    )
}

/**
 * Returns a [ReaderThemePalette] whose colors smoothly animate in lockstep whenever [mode] changes,
 * matching the CSS ease-in-out interpolation inside the Reader Mode WebView.
 */
@Composable
fun rememberAnimatedReaderPalette(mode: ReaderThemeMode): ReaderThemePalette {
    val target = ReaderThemeManager.paletteFor(mode)
    val reduced = LocalReducedMotion.current
    val spec = remember(reduced) {
        tween<Color>(
            durationMillis = if (reduced) 0 else ReaderThemeManager.TRANSITION_DURATION_MS,
            easing = ReaderThemeManager.TransitionEasing,
        )
    }
    val background by animateColorAsState(target.background, spec, label = "readerBg")
    val readingSurface by animateColorAsState(target.readingSurface, spec, label = "readerSurface")
    val toolbarBackdrop by animateColorAsState(target.toolbarBackdrop, spec, label = "readerBackdrop")
    val toolbarSurface by animateColorAsState(target.toolbarSurface, spec, label = "readerToolbar")
    val toolbarSurfacePressed by animateColorAsState(target.toolbarSurfacePressed, spec, label = "readerToolbarPressed")
    val capsuleSurface by animateColorAsState(target.capsuleSurface, spec, label = "readerCapsule")
    val capsuleSurfacePressed by animateColorAsState(target.capsuleSurfacePressed, spec, label = "readerCapsulePressed")
    val primaryText by animateColorAsState(target.primaryText, spec, label = "readerText")
    val secondaryText by animateColorAsState(target.secondaryText, spec, label = "readerMuted")
    val dividers by animateColorAsState(target.dividers, spec, label = "readerDivider")
    val accent by animateColorAsState(target.accent, spec, label = "readerAccent")
    val accentContainer by animateColorAsState(target.accentContainer, spec, label = "readerAccentContainer")
    val codeSurface by animateColorAsState(target.codeSurface, spec, label = "readerCode")
    val quoteSurface by animateColorAsState(target.quoteSurface, spec, label = "readerQuote")
    val topHighlight by animateColorAsState(target.topHighlight, spec, label = "readerTopHighlight")

    return ReaderThemePalette(
        background = background,
        readingSurface = readingSurface,
        toolbarBackdrop = toolbarBackdrop,
        toolbarSurface = toolbarSurface,
        toolbarSurfacePressed = toolbarSurfacePressed,
        capsuleSurface = capsuleSurface,
        capsuleSurfacePressed = capsuleSurfacePressed,
        primaryText = primaryText,
        secondaryText = secondaryText,
        dividers = dividers,
        accent = accent,
        accentContainer = accentContainer,
        codeSurface = codeSurface,
        quoteSurface = quoteSurface,
        topHighlight = topHighlight,
    )
}

/**
 * Floating frost-glass Reader Mode toolbar containing:
 * 1. TTS playback control (Listen / Pause / Resume + Stop while active)
 * 2. Animated Dark/Light Reading Theme toggle
 * 3. Reader Mode menu (Offline Save, Saved Articles, etc.)
 */
@Composable
fun ReaderControlBar(
    mode: ReaderThemeMode,
    palette: ReaderThemePalette,
    ttsState: TtsState,
    ttsProgress: Float,
    articleSaved: Boolean,
    savingOffline: Boolean,
    savedArticlesCount: Int,
    onToggleTts: () -> Unit,
    onStopTts: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleOfflineSave: () -> Unit,
    onOpenSavedArticles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    val sakugaColors = rememberSakugaColors()
    val themeController = rememberSakugaController()
    val ttsController = rememberSakugaController()
    val menuController = rememberSakugaController()

    val themeSource = remember { MutableInteractionSource() }
    val iconRotation by animateFloatAsState(
        targetValue = if (mode == ReaderThemeMode.DARK) 0f else 180f,
        animationSpec = tween(
            durationMillis = if (reduced) 0 else 440,
            easing = ReaderThemeManager.TransitionEasing,
        ),
        label = "readerThemeIconRotation",
    )

    val ttsSource = remember { MutableInteractionSource() }
    val stopSource = remember { MutableInteractionSource() }
    val menuSource = remember { MutableInteractionSource() }

    val isDark = mode == ReaderThemeMode.DARK
    val themeA11y = str(
        if (isDark) R.string.switch_to_light_reader_theme else R.string.switch_to_dark_reader_theme,
    )
    val themeChipLabel = str(if (isDark) R.string.reader_theme_dark else R.string.reader_theme_light)

    val ttsActive = ttsState != TtsState.IDLE
    val ttsA11y = when (ttsState) {
        TtsState.PLAYING -> str(R.string.pause_reading)
        TtsState.PAUSED -> str(R.string.resume_reading)
        TtsState.PREPARING -> str(R.string.stop_reading)
        TtsState.IDLE -> str(R.string.listen)
    }

    Surface(
        modifier = modifier.heightIn(min = Dimens.touchTarget),
        shape = CircleShape,
        color = palette.toolbarSurface,
        border = BorderStroke(1.dp, palette.dividers),
        shadowElevation = 8.dp,
    ) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                drawRect(
                    color = palette.topHighlight,
                    topLeft = Offset.Zero,
                    size = Size(size.width, 1.dp.toPx()),
                )
                if (ttsActive && ttsProgress > 0f) {
                    drawRect(
                        color = palette.accent,
                        topLeft = Offset(0f, size.height - 2.dp.toPx()),
                        size = Size(size.width * ttsProgress.coerceIn(0f, 1f), 2.dp.toPx()),
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 1. TTS playback control button
                Row(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .sakugaOverlay(ttsController)
                        .sakugaPressSquash(ttsSource)
                        .clip(CircleShape)
                        .background(if (ttsActive) palette.accentContainer else Color.Transparent)
                        .clickable(
                            interactionSource = ttsSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            role = Role.Button,
                            onClickLabel = ttsA11y,
                            onClick = {
                                ttsController.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.85f)
                                haptics.tick()
                                onToggleTts()
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = ttsA11y }
                        .testTag("reader_tts_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (ttsState == TtsState.PREPARING) {
                        SakugaCircularProgress(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent,
                        )
                    } else {
                        Icon(
                            imageVector = when (ttsState) {
                                TtsState.PLAYING -> Icons.Rounded.Pause
                                TtsState.PAUSED -> Icons.Rounded.PlayArrow
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (ttsActive) palette.accent else palette.primaryText,
                        )
                    }
                    AnimatedContent(
                        targetState = ttsState,
                        transitionSpec = {
                            val dur = if (reduced) 0 else 180
                            fadeIn(tween(dur)) togetherWith fadeOut(tween(if (reduced) 0 else 120))
                        },
                        label = "ttsButtonLabel",
                    ) { s ->
                        if (s == TtsState.IDLE || s == TtsState.PAUSED) {
                            Text(
                                text = str(if (s == TtsState.PAUSED) R.string.tts_paused else R.string.listen),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (s == TtsState.PAUSED) palette.accent else palette.primaryText,
                            )
                        }
                    }
                }

                // Stop TTS control while playing/paused
                AnimatedVisibility(
                    visible = ttsActive,
                    enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(180)) + expandHorizontally(tween(180)),
                    exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(140)) + shrinkHorizontally(tween(140)),
                ) {
                    Box(
                        Modifier
                            .sizeIn(minWidth = 40.dp, minHeight = 42.dp)
                            .sakugaPressSquash(
                                interactionSource = stopSource,
                                pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                                pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
                            )
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = stopSource,
                                indication = androidx.compose.material3.ripple(bounded = true),
                                role = Role.Button,
                                onClickLabel = str(R.string.stop_reading),
                                onClick = {
                                    haptics.tick()
                                    onStopTts()
                                },
                            )
                            .semantics { contentDescription = str(R.string.stop_reading) }
                            .testTag("reader_tts_stop_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = palette.primaryText,
                        )
                    }
                }

                ToolbarHairline(palette.dividers)

                // 2. Animated Dark / Light Reading Theme toggle
                Row(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .sakugaOverlay(themeController)
                        .sakugaPressSquash(themeSource)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = themeSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            role = Role.Button,
                            onClickLabel = themeA11y,
                            onClick = {
                                themeController.emitCenter(SakugaEffectType.CRYSTAL_BURST, sakugaColors, intensity = 0.95f)
                                haptics.tick()
                                onToggleTheme()
                            },
                        )
                        .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                        .semantics { contentDescription = themeA11y }
                        .testTag("reader_theme_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.accentContainer)
                            .rotate(iconRotation),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = {
                                val enterDur = if (reduced) 0 else 280
                                val exitDur = if (reduced) 0 else 180
                                (fadeIn(tween(enterDur)) +
                                    scaleIn(tween(enterDur), initialScale = 0.72f)) togetherWith
                                    (fadeOut(tween(exitDur)) +
                                        scaleOut(tween(exitDur), targetScale = 0.72f))
                            },
                            label = "readerThemeIconSwap",
                        ) { targetMode ->
                            Icon(
                                imageVector = if (targetMode == ReaderThemeMode.DARK) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(17.dp)
                                    .rotate(if (targetMode == ReaderThemeMode.DARK) 0f else -180f),
                                tint = palette.accent,
                            )
                        }
                    }
                    AnimatedContent(
                        targetState = themeChipLabel,
                        transitionSpec = {
                            val dur = if (reduced) 0 else 220
                            fadeIn(tween(dur)) togetherWith fadeOut(tween(if (reduced) 0 else 150))
                        },
                        label = "readerThemeLabel",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText,
                        )
                    }
                }

                ToolbarHairline(palette.dividers)

                // 3. Reader Mode Menu (Offline Save + Saved Articles + TTS)
                Box {
                    Box(
                        Modifier
                            .sizeIn(minWidth = 42.dp, minHeight = 42.dp)
                            .sakugaOverlay(menuController)
                            .sakugaPressSquash(
                                interactionSource = menuSource,
                                pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                                pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
                            )
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = menuSource,
                                indication = androidx.compose.material3.ripple(bounded = true),
                                role = Role.Button,
                                onClickLabel = str(R.string.reader_menu),
                                onClick = {
                                    menuController.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.78f)
                                    haptics.tick()
                                    menuOpen = true
                                },
                            )
                            .semantics { contentDescription = str(R.string.reader_menu) }
                            .testTag("reader_menu_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (savingOffline) {
                            SakugaCircularProgress(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.accent,
                            )
                        } else {
                            Icon(
                                imageVector = if (articleSaved) Icons.Rounded.OfflinePin else Icons.Rounded.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = if (articleSaved) palette.accent else palette.primaryText,
                            )
                        }
                    }

                    val itemColors = MenuDefaults.itemColors(
                        textColor = palette.primaryText,
                        leadingIconColor = palette.primaryText,
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = palette.toolbarSurface,
                        border = BorderStroke(1.dp, palette.dividers),
                    ) {
                        DropdownMenuItem(
                            modifier = Modifier.testTag("reader_menu_offline_save"),
                            text = {
                                Text(
                                    text = when {
                                        savingOffline -> str(R.string.saving_offline)
                                        articleSaved -> str(R.string.saved_offline_remove)
                                        else -> str(R.string.offline_save)
                                    },
                                    fontWeight = if (articleSaved) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (articleSaved) Icons.Rounded.OfflinePin else Icons.Rounded.DownloadForOffline,
                                    contentDescription = null,
                                    tint = if (articleSaved) palette.accent else palette.primaryText,
                                )
                            },
                            enabled = !savingOffline,
                            colors = itemColors,
                            onClick = {
                                menuOpen = false
                                onToggleOfflineSave()
                            },
                        )
                        DropdownMenuItem(
                            modifier = Modifier.testTag("reader_menu_saved_articles"),
                            text = {
                                Text(
                                    if (savedArticlesCount > 0) {
                                        str(R.string.saved_articles_count, savedArticlesCount)
                                    } else {
                                        str(R.string.saved_articles)
                                    },
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryBooks,
                                    contentDescription = null,
                                    tint = palette.secondaryText,
                                )
                            },
                            colors = itemColors,
                            onClick = {
                                menuOpen = false
                                onOpenSavedArticles()
                            },
                        )
                        HorizontalDivider(color = palette.dividers)
                        DropdownMenuItem(
                            text = { Text(ttsA11y) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (ttsState) {
                                        TtsState.PLAYING -> Icons.Rounded.Pause
                                        TtsState.PAUSED -> Icons.Rounded.PlayArrow
                                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                                    },
                                    contentDescription = null,
                                    tint = if (ttsActive) palette.accent else palette.secondaryText,
                                )
                            },
                            colors = itemColors,
                            onClick = {
                                menuOpen = false
                                onToggleTts()
                            },
                        )
                        if (ttsActive) {
                            DropdownMenuItem(
                                text = { Text(str(R.string.stop_reading)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = null,
                                        tint = palette.secondaryText,
                                    )
                                },
                                colors = itemColors,
                                onClick = {
                                    menuOpen = false
                                    onStopTts()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(themeA11y) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = null,
                                    tint = palette.accent,
                                )
                            },
                            colors = itemColors,
                            onClick = {
                                menuOpen = false
                                onToggleTheme()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarHairline(color: Color) {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(color),
    )
}
