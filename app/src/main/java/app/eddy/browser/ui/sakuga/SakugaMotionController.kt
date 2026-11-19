package app.eddy.browser.ui.sakuga

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.eddy.browser.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Extended Sakuga VFX color tokens including lavender/violet burst accents alongside
 * semantic success and destructive action hues harmonized with the active [ColorScheme].
 */
@Stable
data class SakugaColors(
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val lavenderAccent: Color,
    val violetAccent: Color,
    val successAccent: Color,
    val deleteAccent: Color,
    val highlightCore: Color,
    val smokePuff: Color,
    val impactRing: Color,
) {
    companion object {
        val SakugaLavender = Color(0xFFC49BFF)
        val SakugaViolet = Color(0xFF8B5CF6)
        val SakugaPink = Color(0xFFFF6EB4)
        private val SakugaEmerald = Color(0xFF2DD4BF)
        private val SakugaCoral = Color(0xFFF87171)

        fun from(scheme: ColorScheme): SakugaColors {
            val dark = (scheme.background.red + scheme.background.green + scheme.background.blue) / 3f < 0.45f
            val secondary = lerp(scheme.tertiary, if (dark) SakugaPink else SakugaViolet, 0.36f)
            val lavender = lerp(SakugaLavender, scheme.primary, 0.22f)
            val violet = lerp(SakugaViolet, scheme.tertiary, 0.25f)
            return SakugaColors(
                primaryAccent = scheme.primary,
                secondaryAccent = secondary,
                lavenderAccent = lavender,
                violetAccent = violet,
                successAccent = lerp(SakugaEmerald, scheme.primary, 0.25f),
                deleteAccent = lerp(scheme.error, SakugaCoral, 0.35f),
                highlightCore = if (dark) Color.White.copy(alpha = 0.92f) else lerp(lavender, Color.White, 0.45f),
                smokePuff = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.28f else 0.20f),
                impactRing = lerp(lavender, scheme.primary, 0.40f),
            )
        }
    }
}

@Composable
fun rememberSakugaColors(): SakugaColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { SakugaColors.from(scheme) }
}

/**
 * Pure animation physics constants and math helpers inspired by sakuga-vfx.
 * All spring and timing specifications delegate directly to [SakugaAnimationController].
 */
object SakugaPhysics {
    const val PRESS_BUTTON_SCALE_X = SakugaAnimationController.Buttons.PRESS_SCALE_X
    const val PRESS_BUTTON_SCALE_Y = SakugaAnimationController.Buttons.PRESS_SCALE_Y
    const val PRESS_CARD_SCALE_X = SakugaAnimationController.Cards.PRESS_SCALE_X
    const val PRESS_CARD_SCALE_Y = SakugaAnimationController.Cards.PRESS_SCALE_Y
    const val PRESS_ICON_SCALE_X = SakugaAnimationController.Buttons.ICON_PRESS_SCALE_X
    const val PRESS_ICON_SCALE_Y = SakugaAnimationController.Buttons.ICON_PRESS_SCALE_Y
    const val MAX_SMEAR_STRETCH = SakugaAnimationController.Toggles.MAX_SMEAR_STRETCH

    fun <T> overshootSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Buttons.releaseOvershootSpring.toComposeSpring(reducedMotion)

    fun <T> pressSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Buttons.pressSpring.toComposeSpring(reducedMotion)

    fun <T> toggleSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Toggles.slideSpring.toComposeSpring(reducedMotion)

    fun <T> indicatorLeadSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Indicators.leadSpring.toComposeSpring(reducedMotion)

    fun <T> indicatorTrailSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Indicators.trailSpring.toComposeSpring(reducedMotion)

    fun <T> toastEntranceSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        SakugaAnimationController.Cards.entranceSpring.toComposeSpring(reducedMotion)

    fun computePressSquash(
        pressed: Boolean,
        reducedMotion: Boolean,
        pressX: Float = PRESS_BUTTON_SCALE_X,
        pressY: Float = PRESS_BUTTON_SCALE_Y,
    ): Pair<Float, Float> {
        if (!pressed || reducedMotion) return 1f to 1f
        return pressX to pressY
    }

    fun computeTravelSmear(progress: Float, reducedMotion: Boolean): Float {
        if (reducedMotion) return 1f
        val p = progress.coerceIn(0f, 1f)
        val arc = sin(p * PI).toFloat().coerceIn(0f, 1f)
        return (1f + arc * (MAX_SMEAR_STRETCH - 1f)).coerceIn(1f, MAX_SMEAR_STRETCH)
    }

    fun computeVelocityDeform(velocityFraction: Float, reducedMotion: Boolean): Pair<Float, Float> {
        if (reducedMotion) return 1f to 1f
        val v = kotlin.math.abs(velocityFraction).coerceIn(0f, 1f)
        val sx = 1f + v * 0.28f
        val sy = (1f / sx).coerceAtLeast(0.76f)
        return sx to sy
    }
}

enum class SakugaEffectType {
    CLOUD_BURST,
    SUBTLE_POP,
    IMPACT_SPLASH,
    CRYSTAL_BURST,
    WEDGE_POP,
    PUFF_TRAIL,
    LAVENDER_BURST,
    SUCCESS_BURST,
    DELETE_BURST,
}

/** Semantic presets for configurable click/action particle emissions (Section B). */
enum class SakugaParticlePreset {
    PRIMARY,
    LAVENDER_BURST,
    SUCCESS,
    DELETE,
    SUBTLE,
    IMPACT,
}

/**
 * Per-component configurable particle burst descriptor supporting count, burst radius,
 * directional angle/spread, and semantic preset.
 */
data class SakugaParticleConfig(
    val preset: SakugaParticlePreset = SakugaParticlePreset.PRIMARY,
    val particleCount: Int? = null,
    val burstRadiusScale: Float = 1f,
    val directionAngleRad: Float? = null,
    val directionalSpreadRad: Float = 1.25f,
    val intensity: Float = 1f,
)

internal enum class ParticleKind {
    CIRCLE,
    PUFF,
    SPARK,
    RING,
    CHIP,
    DROPLET,
}

internal class SakugaParticle {
    var active: Boolean = false
    var kind: ParticleKind = ParticleKind.CIRCLE
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var radius: Float = 4f
    var endRadius: Float = 0f
    var rotation: Float = 0f
    var angularVel: Float = 0f
    var age: Float = 0f
    var lifetime: Float = 0.35f
    var drag: Float = 0.90f
    var color: Color = Color.Unspecified
}

/**
 * Fixed-capacity particle pool & frame-driven ticker for Sakuga VFX bursts.
 * Zero per-frame heap allocations; enforces an active particle cap ([MAX_PARTICLES]);
 * and clears automatically on disposal or when lifetimes finish.
 */
@Stable
class SakugaMotionController(
    internal val reducedMotion: Boolean,
) {
    internal val particles = Array(MAX_PARTICLES) { SakugaParticle() }
    private var cursor = 0
    internal var frameTick by mutableLongStateOf(0L)
        private set
    internal var lastSize = Size.Zero

    val hasActiveParticles: Boolean
        get() = particles.any { it.active }

    val activeParticleCount: Int
        get() = particles.count { it.active }

    fun emit(
        type: SakugaEffectType,
        origin: Offset,
        colors: SakugaColors,
        intensity: Float = 1f,
    ) {
        val mapped = when (type) {
            SakugaEffectType.CLOUD_BURST -> SakugaParticleConfig(SakugaParticlePreset.PRIMARY, intensity = intensity)
            SakugaEffectType.SUBTLE_POP -> SakugaParticleConfig(SakugaParticlePreset.SUBTLE, intensity = intensity)
            SakugaEffectType.IMPACT_SPLASH -> SakugaParticleConfig(SakugaParticlePreset.IMPACT, directionAngleRad = -HALF_PI, directionalSpreadRad = 2.5f, intensity = intensity)
            SakugaEffectType.CRYSTAL_BURST -> SakugaParticleConfig(SakugaParticlePreset.LAVENDER_BURST, particleCount = 6, intensity = intensity)
            SakugaEffectType.WEDGE_POP -> SakugaParticleConfig(SakugaParticlePreset.SUBTLE, particleCount = 5, intensity = intensity)
            SakugaEffectType.PUFF_TRAIL -> {
                emitPuffTrail(origin, colors, intensity)
                return
            }
            SakugaEffectType.LAVENDER_BURST -> SakugaParticleConfig(SakugaParticlePreset.LAVENDER_BURST, intensity = intensity)
            SakugaEffectType.SUCCESS_BURST -> SakugaParticleConfig(SakugaParticlePreset.SUCCESS, directionAngleRad = -HALF_PI, directionalSpreadRad = 2.2f, intensity = intensity)
            SakugaEffectType.DELETE_BURST -> SakugaParticleConfig(SakugaParticlePreset.DELETE, intensity = intensity)
        }
        emitConfigured(origin, colors, mapped)
    }

    /**
     * Emits directional or radial particles with randomized sizes, velocities, fading, and shrinking
     * governed by [config].
     */
    fun emitConfigured(
        origin: Offset,
        colors: SakugaColors,
        config: SakugaParticleConfig,
    ) {
        if (reducedMotion) return
        val scale = config.intensity.coerceIn(0.35f, 1.6f)
        val radiusScale = config.burstRadiusScale.coerceIn(0.45f, 2.2f)
        val count = (config.particleCount ?: defaultCountFor(config.preset)).coerceIn(2, MAX_BURST_EMISSION)

        val ringColor = when (config.preset) {
            SakugaParticlePreset.PRIMARY -> colors.impactRing.copy(alpha = 0.55f)
            SakugaParticlePreset.LAVENDER_BURST -> colors.lavenderAccent.copy(alpha = 0.65f)
            SakugaParticlePreset.SUCCESS -> colors.successAccent.copy(alpha = 0.62f)
            SakugaParticlePreset.DELETE -> colors.deleteAccent.copy(alpha = 0.60f)
            SakugaParticlePreset.SUBTLE -> colors.impactRing.copy(alpha = 0.38f)
            SakugaParticlePreset.IMPACT -> colors.primaryAccent.copy(alpha = 0.58f)
        }
        spawnRing(
            origin = origin,
            startRadius = 5f * scale,
            endRadius = (if (config.preset == SakugaParticlePreset.SUBTLE) 22f else 34f) * scale * radiusScale,
            lifetime = 0.25f,
            color = ringColor,
        )

        val puffCount = when (config.preset) {
            SakugaParticlePreset.PRIMARY, SakugaParticlePreset.DELETE -> (count / 3).coerceIn(2, 5)
            SakugaParticlePreset.LAVENDER_BURST -> 2
            else -> 0
        }
        repeat(puffCount) { i ->
            val angle = sampleAngle(i, puffCount, config.directionAngleRad, config.directionalSpreadRad)
            val speed = rand(55f, 115f) * scale * radiusScale
            val puffColor = when (config.preset) {
                SakugaParticlePreset.LAVENDER_BURST -> colors.lavenderAccent.copy(alpha = 0.24f)
                SakugaParticlePreset.DELETE -> colors.deleteAccent.copy(alpha = 0.22f)
                else -> colors.smokePuff
            }
            spawnParticle(
                kind = ParticleKind.PUFF,
                origin = origin,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radius = rand(4.5f, 8.5f) * scale,
                endRadius = rand(10f, 15f) * scale * radiusScale,
                lifetime = rand(0.22f, 0.32f),
                drag = 0.87f,
                color = puffColor,
            )
        }

        for (i in 0 until count) {
            val angle = sampleAngle(i, count, config.directionAngleRad, config.directionalSpreadRad)
            val speed = rand(95f, 215f) * scale * radiusScale
            val kind = when (config.preset) {
                SakugaParticlePreset.PRIMARY -> if (i % 2 == 0) ParticleKind.CIRCLE else ParticleKind.SPARK
                SakugaParticlePreset.LAVENDER_BURST -> when (i % 3) {
                    0 -> ParticleKind.CIRCLE
                    1 -> ParticleKind.CHIP
                    else -> ParticleKind.SPARK
                }
                SakugaParticlePreset.SUCCESS -> if (i % 3 == 0) ParticleKind.CHIP else ParticleKind.CIRCLE
                SakugaParticlePreset.DELETE -> if (i % 2 == 0) ParticleKind.CHIP else ParticleKind.CIRCLE
                SakugaParticlePreset.SUBTLE -> if (i % 2 == 0) ParticleKind.CIRCLE else ParticleKind.SPARK
                SakugaParticlePreset.IMPACT -> ParticleKind.DROPLET
            }
            val color = when (config.preset) {
                SakugaParticlePreset.PRIMARY -> when (i % 3) {
                    0 -> colors.lavenderAccent
                    1 -> colors.primaryAccent
                    else -> colors.violetAccent
                }
                SakugaParticlePreset.LAVENDER_BURST -> if (i % 2 == 0) colors.lavenderAccent else colors.violetAccent
                SakugaParticlePreset.SUCCESS -> when (i % 3) {
                    0 -> colors.successAccent
                    1 -> colors.lavenderAccent
                    else -> colors.primaryAccent
                }
                SakugaParticlePreset.DELETE -> when (i % 3) {
                    0 -> colors.deleteAccent
                    1 -> colors.violetAccent
                    else -> colors.lavenderAccent
                }
                SakugaParticlePreset.SUBTLE -> if (i % 2 == 0) colors.lavenderAccent else colors.primaryAccent
                SakugaParticlePreset.IMPACT -> if (i % 2 == 0) colors.primaryAccent else colors.lavenderAccent
            }
            val startR = when (kind) {
                ParticleKind.CIRCLE, ParticleKind.DROPLET -> rand(2.4f, 5.4f) * scale
                ParticleKind.CHIP -> rand(3.2f, 5.5f) * scale
                ParticleKind.SPARK -> rand(6.0f, 11.5f) * scale
                else -> 4f * scale
            }
            spawnParticle(
                kind = kind,
                origin = origin,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radius = startR,
                endRadius = 0.4f, // Shrinks cleanly toward zero while fading out.
                lifetime = rand(0.19f, 0.31f),
                drag = 0.86f,
                color = color,
                angularVel = if (kind == ParticleKind.CHIP) rand(-8f, 8f) else 0f,
            )
        }
        frameTick++
    }

    private fun emitPuffTrail(origin: Offset, colors: SakugaColors, intensity: Float) {
        if (reducedMotion) return
        val scale = intensity.coerceIn(0.4f, 1.5f)
        repeat(2) {
            val angle = rand(0f, TWO_PI)
            val speed = rand(25f, 60f) * scale
            spawnParticle(
                kind = ParticleKind.PUFF,
                origin = origin,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 18f,
                radius = rand(3.5f, 5.5f) * scale,
                endRadius = rand(8f, 11f) * scale,
                lifetime = 0.22f,
                drag = 0.89f,
                color = colors.smokePuff,
            )
        }
        frameTick++
    }

    fun emitCenter(type: SakugaEffectType, colors: SakugaColors, intensity: Float = 1f) {
        val center = if (lastSize.width > 0f && lastSize.height > 0f) {
            Offset(lastSize.width * 0.5f, lastSize.height * 0.5f)
        } else {
            Offset.Zero
        }
        emit(type, center, colors, intensity)
    }

    fun emitCenterConfigured(colors: SakugaColors, config: SakugaParticleConfig) {
        val center = if (lastSize.width > 0f && lastSize.height > 0f) {
            Offset(lastSize.width * 0.5f, lastSize.height * 0.5f)
        } else {
            Offset.Zero
        }
        emitConfigured(center, colors, config)
    }

    fun step(dtSeconds: Float): Boolean {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        var anyAlive = false
        for (p in particles) {
            if (!p.active) continue
            p.age += dt
            if (p.age >= p.lifetime) {
                p.active = false
                continue
            }
            anyAlive = true
            val decay = p.drag.toDouble().pow((dt * 60f).toDouble()).toFloat()
            p.vx *= decay
            p.vy *= decay
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.angularVel * dt
        }
        frameTick++
        return anyAlive
    }

    fun clear() {
        for (p in particles) p.active = false
        frameTick++
    }

    private fun defaultCountFor(preset: SakugaParticlePreset): Int = when (preset) {
        SakugaParticlePreset.PRIMARY -> 8
        SakugaParticlePreset.LAVENDER_BURST -> 10
        SakugaParticlePreset.SUCCESS -> 10
        SakugaParticlePreset.DELETE -> 9
        SakugaParticlePreset.SUBTLE -> 5
        SakugaParticlePreset.IMPACT -> 5
    }

    private fun sampleAngle(
        index: Int,
        total: Int,
        directionalAngle: Float?,
        spreadRad: Float,
    ): Float {
        if (directionalAngle != null) {
            val frac = if (total <= 1) 0f else (index / (total - 1f)) - 0.5f
            return directionalAngle + frac * spreadRad + rand(-0.18f, 0.18f)
        }
        return (index / total.toFloat()) * TWO_PI + rand(-0.28f, 0.28f)
    }

    private fun spawnRing(origin: Offset, startRadius: Float, endRadius: Float, lifetime: Float, color: Color) {
        spawnParticle(
            kind = ParticleKind.RING,
            origin = origin,
            vx = 0f,
            vy = 0f,
            radius = startRadius,
            endRadius = endRadius,
            lifetime = lifetime,
            drag = 1f,
            color = color,
        )
    }

    private fun spawnParticle(
        kind: ParticleKind,
        origin: Offset,
        vx: Float,
        vy: Float,
        radius: Float,
        endRadius: Float,
        lifetime: Float,
        drag: Float,
        color: Color,
        angularVel: Float = 0f,
    ) {
        val slot = particles[cursor]
        cursor = (cursor + 1) % MAX_PARTICLES
        slot.active = true
        slot.kind = kind
        slot.x = origin.x
        slot.y = origin.y
        slot.vx = vx
        slot.vy = vy
        slot.radius = radius
        slot.endRadius = endRadius
        slot.rotation = if (vx != 0f || vy != 0f) kotlin.math.atan2(vy, vx) else 0f
        slot.angularVel = angularVel
        slot.age = 0f
        slot.lifetime = lifetime.coerceAtLeast(0.05f)
        slot.drag = drag
        slot.color = color
    }

    private fun rand(min: Float, max: Float): Float = min + Random.nextFloat() * (max - min)

    companion object {
        const val MAX_PARTICLES = 64
        const val MAX_BURST_EMISSION = 20
        private const val TWO_PI = (PI * 2.0).toFloat()
        private const val HALF_PI = (PI / 2.0).toFloat()
    }
}

@Composable
fun rememberSakugaController(): SakugaMotionController {
    val reduced = LocalReducedMotion.current
    val controller = remember(reduced) { SakugaMotionController(reduced) }
    DisposableEffect(controller) {
        onDispose { controller.clear() }
    }
    val activeTrigger = controller.frameTick
    LaunchedEffect(controller, activeTrigger > 0L && controller.hasActiveParticles) {
        if (!controller.hasActiveParticles) return@LaunchedEffect
        var lastNanos = 0L
        while (controller.hasActiveParticles) {
            val stillRunning = withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                    true
                } else {
                    val dt = (now - lastNanos) / 1_000_000_000f
                    lastNanos = now
                    controller.step(dt)
                }
            }
            if (!stillRunning) break
        }
    }
    return controller
}
