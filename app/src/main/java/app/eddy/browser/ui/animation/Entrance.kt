package app.eddy.browser.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.sakuga.SakugaAnimationController
import app.eddy.browser.ui.sakuga.SakugaSpringType
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/** Fade-and-rise entrance, staggered by [index]. Runs once per composition and is skipped with reduced motion. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else if (progress.value < 1f) {
            delay(index.coerceIn(0, 5) * 36L)
            progress.animateTo(1f, SakugaAnimationController.springSpec(SakugaSpringType.PREMIUM, false))
        }
    }
    return graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
    }
}
