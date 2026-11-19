package app.eddy.browser.ui.components

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.rememberSakugaColors
import app.eddy.browser.ui.sakuga.rememberSakugaController
import app.eddy.browser.ui.sakuga.sakugaOverlay
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion

/** Title bar with a back arrow, used by every full-screen page (history, bookmarks, downloads, settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { EddyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back), onBack) },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val focus = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    var prevLength by remember { mutableIntStateOf(query.length) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(query.length) {
        if (!reduced && query.length > prevLength && focused) {
            pulse.snapTo(1.012f)
            pulse.animateTo(1f, SakugaPhysics.pressSpring(false))
        }
        prevLength = query.length
    }

    var wasFocused by remember { mutableStateOf(focused) }
    LaunchedEffect(focused) {
        if (focused && !wasFocused && controller.lastSize.width > 0f) {
            controller.emit(
                SakugaEffectType.SUBTLE_POP,
                Offset(28f, controller.lastSize.height * 0.5f),
                sakugaColors,
                intensity = 0.7f,
            )
        }
        wasFocused = focused
    }

    val borderColor by animateColorAsState(
        if (focused) sakugaColors.primaryAccent.copy(alpha = 0.65f) else scheme.outlineVariant.copy(alpha = 0.25f),
        tween(if (reduced) 0 else 180),
        label = "searchFieldBorder",
    )
    val bgColor by animateColorAsState(
        if (focused) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        tween(if (reduced) 0 else 180),
        label = "searchFieldBg",
    )
    val elevation by animateDpAsState(if (focused) 4.dp else 0.dp, tween(if (reduced) 0 else 180), label = "searchFieldElev")
    val focusScale by animateFloatAsState(if (focused && !reduced) 1.008f else 1f, SakugaPhysics.pressSpring(reduced), label = "searchFieldScale")

    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = 6.dp)
            .graphicsLayer {
                val s = focusScale * pulse.value
                scaleX = s
                scaleY = s
            }
            .sakugaOverlay(controller),
        shape = RoundedCornerShape(28.dp),
        color = bgColor,
        shadowElevation = elevation,
        border = BorderStroke(if (focused) 1.5.dp else 1.dp, borderColor),
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = if (focused) sakugaColors.primaryAccent else scheme.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        inner()
                    }
                },
            )
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(if (reduced) 0 else 150)) + scaleIn(SakugaPhysics.overshootSpring(reduced), initialScale = 0.7f),
                exit = fadeOut(tween(if (reduced) 0 else 120)) + scaleOut(targetScale = 0.7f),
            ) {
                EddyIconButton(Icons.Rounded.Close, str(R.string.clear_search), { onChange("") })
            }
        }
    }
}
