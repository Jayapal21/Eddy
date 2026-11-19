package app.eddy.browser.ui.sakuga

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight Canvas-based Sakuga particle renderer supporting circular bursts, luminous lavender/violet
 * highlights, directional streaks, puffs, expanding rings, and diamond chips with zero per-frame allocation.
 */
fun Modifier.sakugaOverlay(controller: SakugaMotionController): Modifier = composed {
    val diamondPath = remember { Path() }
    drawWithContent {
        controller.lastSize = size
        drawContent()
        // Frame tick invalidates only while active particles remain.
        val tick = controller.frameTick
        if (tick >= 0L && !controller.reducedMotion && controller.hasActiveParticles) {
            drawSakugaParticles(controller, diamondPath)
        }
    }
}

/**
 * Backwards-compatible squash-and-stretch press & hover modifier delegating directly
 * to [SakugaAnimationController] spring presets.
 */
fun Modifier.sakugaPressSquash(
    interactionSource: InteractionSource,
    pressX: Float = SakugaAnimationController.Buttons.PRESS_SCALE_X,
    pressY: Float = SakugaAnimationController.Buttons.PRESS_SCALE_Y,
    enabled: Boolean = true,
    role: SakugaAnimationController.ComponentRole = SakugaAnimationController.ComponentRole.BUTTON,
    preset: SakugaMotionPreset? = null,
): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val resolvedPreset = preset ?: LocalSakugaMotionPreset.current
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val profile = SakugaAnimationController.profileFor(resolvedPreset)

    val (targetX, targetY) = when {
        reduced || !enabled -> 1f to 1f
        pressed -> pressX to pressY
        hovered -> profile.hoverScale to profile.hoverScale
        else -> 1f to 1f
    }
    val spec = SakugaAnimationController.pressSpec<Float>(
        role = role,
        pressed = pressed,
        hovered = hovered,
        reducedMotion = reduced,
        preset = resolvedPreset,
    )
    val scaleX by animateFloatAsState(targetX, spec, label = "sakugaSquashX")
    val scaleY by animateFloatAsState(targetY, spec, label = "sakugaSquashY")

    graphicsLayer {
        this.scaleX = scaleX
        this.scaleY = scaleY
    }
}

internal fun DrawScope.drawSakugaParticles(
    controller: SakugaMotionController,
    scratchPath: Path,
) {
    for (p in controller.particles) {
        if (!p.active) continue
        val t = (p.age / p.lifetime).coerceIn(0f, 1f)
        val inv = 1f - t
        val easedOut = 1f - inv * inv

        when (p.kind) {
            ParticleKind.CIRCLE -> {
                val r = androidx.compose.ui.util.lerp(p.radius, p.endRadius, t * 0.85f)
                val alpha = (0.92f * inv * inv).coerceIn(0f, 1f)
                if (alpha > 0.01f && r > 0.4f) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(p.x, p.y),
                    )
                }
            }
            ParticleKind.PUFF -> {
                val r = androidx.compose.ui.util.lerp(p.radius, p.endRadius, easedOut)
                val alpha = (p.color.alpha * inv * inv).coerceIn(0f, 1f)
                if (alpha > 0.01f && r > 0.5f) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(p.x, p.y),
                    )
                }
            }
            ParticleKind.RING -> {
                val r = androidx.compose.ui.util.lerp(p.radius, p.endRadius, easedOut)
                val alpha = (p.color.alpha * inv).coerceIn(0f, 1f)
                val strokeWidth = (2.4.dp.toPx() * inv).coerceAtLeast(0.6f)
                if (alpha > 0.01f && r > 0.5f) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(p.x, p.y),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
            ParticleKind.SPARK -> {
                val len = androidx.compose.ui.util.lerp(p.radius, p.endRadius, t)
                val stroke = (2.6.dp.toPx() * inv).coerceAtLeast(0.75f)
                val alpha = (0.94f * inv).coerceIn(0f, 1f)
                if (alpha > 0.01f && len > 0.5f) {
                    val dx = cos(p.rotation) * len
                    val dy = sin(p.rotation) * len
                    drawLine(
                        color = p.color.copy(alpha = alpha),
                        start = Offset(p.x - dx * 0.35f, p.y - dy * 0.35f),
                        end = Offset(p.x + dx * 0.65f, p.y + dy * 0.65f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            ParticleKind.DROPLET -> {
                val r = androidx.compose.ui.util.lerp(p.radius, p.endRadius, t)
                val alpha = (0.90f * inv).coerceIn(0f, 1f)
                if (alpha > 0.01f && r > 0.4f) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = r,
                        center = Offset(p.x, p.y),
                    )
                }
            }
            ParticleKind.CHIP -> {
                val r = androidx.compose.ui.util.lerp(p.radius, p.endRadius, t)
                val alpha = (0.95f * inv).coerceIn(0f, 1f)
                if (alpha > 0.01f && r > 0.5f) {
                    scratchPath.rewind()
                    scratchPath.moveTo(0f, -r)
                    scratchPath.lineTo(r * 0.72f, 0f)
                    scratchPath.lineTo(0f, r)
                    scratchPath.lineTo(-r * 0.72f, 0f)
                    scratchPath.close()
                    withTransform({
                        translate(left = p.x, top = p.y)
                        rotate(degrees = Math.toDegrees(p.rotation.toDouble()).toFloat(), pivot = Offset.Zero)
                    }) {
                        drawPath(scratchPath, color = p.color.copy(alpha = alpha))
                    }
                }
            }
        }
    }
}
