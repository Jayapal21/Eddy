package app.eddy.browser

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import app.eddy.browser.ui.sakuga.SakugaAnimationController
import app.eddy.browser.ui.sakuga.SakugaColors
import app.eddy.browser.ui.sakuga.SakugaComponentState
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaMotionController
import app.eddy.browser.ui.sakuga.SakugaMotionPreset
import app.eddy.browser.ui.sakuga.SakugaParticleConfig
import app.eddy.browser.ui.sakuga.SakugaParticlePreset
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.SakugaSpringType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SakugaMotionTest {

    private val testColors = SakugaColors.from(darkColorScheme())

    @Test
    fun `all standardized spring archetypes expose valid immutable physics constants`() {
        for (type in SakugaSpringType.entries) {
            val preset = SakugaAnimationController.Springs.byType(type)
            assertTrue("Stiffness must be positive for $type", preset.stiffness > 0f)
            assertTrue("Damping ratio must be positive for $type", preset.dampingRatio > 0f)
            assertTrue(preset.toComposeSpring<Float>(reducedMotion = false) is SpringSpec<Float>)
            assertTrue(preset.toComposeSpring<Float>(reducedMotion = true) is SnapSpec<Float>)
        }
        assertTrue(SakugaAnimationController.Springs.PLAYFUL.dampingRatio < SakugaAnimationController.Springs.PREMIUM.dampingRatio)
        assertTrue(SakugaAnimationController.Springs.SNAPPY.stiffness > SakugaAnimationController.Springs.PREMIUM.stiffness)
    }

    @Test
    fun `motion presets Soft Premium Playful Snappy scale expressiveness consistently`() {
        val soft = SakugaAnimationController.profileFor(SakugaMotionPreset.SOFT)
        val premium = SakugaAnimationController.profileFor(SakugaMotionPreset.PREMIUM)
        val playful = SakugaAnimationController.profileFor(SakugaMotionPreset.PLAYFUL)
        val snappy = SakugaAnimationController.profileFor(SakugaMotionPreset.SNAPPY)

        assertTrue(playful.pressScaleX > premium.pressScaleX)
        assertTrue(premium.pressScaleX > soft.pressScaleX)
        assertTrue(playful.defaultParticleCount > soft.defaultParticleCount)
        assertTrue(snappy.durationScale < soft.durationScale)
        assertTrue(snappy.pressSpring.stiffness > soft.pressSpring.stiffness)
    }

    @Test
    fun `state transition engine resolves distinct transforms for all component states`() {
        for (state in SakugaComponentState.entries) {
            val spec = SakugaAnimationController.stateTransformFor(state)
            assertTrue(spec.springPreset.stiffness > 0f)
            when (state) {
                SakugaComponentState.PRESSED -> {
                    assertTrue(spec.scaleX > 1f)
                    assertTrue(spec.scaleY < 1f)
                }
                SakugaComponentState.HOVERED -> {
                    assertTrue(spec.scaleX > 1f)
                    assertTrue(spec.elevationDeltaDp > 0f)
                }
                SakugaComponentState.DISABLED -> {
                    assertTrue(spec.alpha < 0.5f)
                    assertEquals(1f, spec.scaleX, 0.0001f)
                }
                SakugaComponentState.SUCCESS -> assertTrue(spec.scaleX > 1f)
                SakugaComponentState.IDLE, SakugaComponentState.RELEASED -> {
                    assertEquals(1f, spec.scaleX, 0.0001f)
                    assertEquals(1f, spec.scaleY, 0.0001f)
                    assertEquals(1f, spec.alpha, 0.0001f)
                }
                else -> Unit
            }
        }
    }

    @Test
    fun `SakugaAnimationController unifies button toggle card sheet and dialog roles`() {
        for (role in SakugaAnimationController.ComponentRole.entries) {
            val press = SakugaAnimationController.springPresetFor(role, pressed = true)
            val release = SakugaAnimationController.springPresetFor(role, pressed = false)
            assertTrue(press.stiffness > 0f)
            assertTrue(release.stiffness > 0f)
            assertTrue(press.dampingRatio > 0f)
            assertTrue(release.dampingRatio > 0f)

            val (sx, sy) = SakugaAnimationController.pressScaleFor(role)
            if (role !in setOf(
                    SakugaAnimationController.ComponentRole.INDICATOR,
                    SakugaAnimationController.ComponentRole.SHEET,
                    SakugaAnimationController.ComponentRole.DIALOG,
                )
            ) {
                assertTrue(sx > 1f)
                assertTrue(sy < 1f)
            } else {
                assertEquals(1f to 1f, sx to sy)
            }

            val animatorPreset = SakugaAnimationController.valueAnimatorPresetFor(role)
            assertTrue(animatorPreset.durationMs > 0L)

            val composeSpring = release.toComposeSpring<Float>(reducedMotion = false)
            assertTrue(composeSpring is SpringSpec<Float>)
            val reducedSpring = release.toComposeSpring<Float>(reducedMotion = true)
            assertTrue(reducedSpring is SnapSpec<Float>)

            val normalTween = animatorPreset.toComposeTween<Float>(reducedMotion = false)
            val reducedTween: TweenSpec<Float> = animatorPreset.toComposeTween(reducedMotion = true)
            assertEquals(animatorPreset.durationMs.toInt(), normalTween.durationMillis)
            assertEquals(0, reducedTween.durationMillis)
        }
    }

    @Test
    fun `SakugaPhysics constants and specs stay synced with SakugaAnimationController`() {
        assertEquals(SakugaAnimationController.Buttons.PRESS_SCALE_X, SakugaPhysics.PRESS_BUTTON_SCALE_X, 0.0001f)
        assertEquals(SakugaAnimationController.Buttons.PRESS_SCALE_Y, SakugaPhysics.PRESS_BUTTON_SCALE_Y, 0.0001f)
        assertEquals(SakugaAnimationController.Cards.PRESS_SCALE_X, SakugaPhysics.PRESS_CARD_SCALE_X, 0.0001f)
        assertEquals(SakugaAnimationController.Cards.PRESS_SCALE_Y, SakugaPhysics.PRESS_CARD_SCALE_Y, 0.0001f)
        assertEquals(SakugaAnimationController.Toggles.MAX_SMEAR_STRETCH, SakugaPhysics.MAX_SMEAR_STRETCH, 0.0001f)
    }

    @Test
    fun `computePressSquash widens X and compresses Y only when pressed and motion enabled`() {
        val (sx, sy) = SakugaPhysics.computePressSquash(pressed = true, reducedMotion = false)
        assertTrue(sx > 1f)
        assertTrue(sy < 1f)

        assertEquals(1f to 1f, SakugaPhysics.computePressSquash(pressed = false, reducedMotion = false))
        assertEquals(1f to 1f, SakugaPhysics.computePressSquash(pressed = true, reducedMotion = true))
    }

    @Test
    fun `computeTravelSmear peaks at midpoint and resets at endpoints or reduced motion`() {
        val start = SakugaPhysics.computeTravelSmear(0f, reducedMotion = false)
        val mid = SakugaPhysics.computeTravelSmear(0.5f, reducedMotion = false)
        val end = SakugaPhysics.computeTravelSmear(1f, reducedMotion = false)

        assertEquals(1f, start, 0.0001f)
        assertEquals(SakugaPhysics.MAX_SMEAR_STRETCH, mid, 0.001f)
        assertEquals(1f, end, 0.0001f)

        assertEquals(1f, SakugaPhysics.computeTravelSmear(-0.5f, reducedMotion = false), 0.0001f)
        assertEquals(1f, SakugaPhysics.computeTravelSmear(1.5f, reducedMotion = false), 0.0001f)
        assertEquals(1f, SakugaPhysics.computeTravelSmear(0.5f, reducedMotion = true), 0.0001f)
    }

    @Test
    fun `computeVelocityDeform preserves clamped bounds and respects reduced motion`() {
        val (sx0, sy0) = SakugaPhysics.computeVelocityDeform(0f, reducedMotion = false)
        assertEquals(1f, sx0, 0.0001f)
        assertEquals(1f, sy0, 0.0001f)

        val (sxMax, syMax) = SakugaPhysics.computeVelocityDeform(2.5f, reducedMotion = false)
        assertEquals(1.28f, sxMax, 0.0001f)
        assertTrue(syMax in 0.76f..1f)

        val (sxNeg, syNeg) = SakugaPhysics.computeVelocityDeform(-0.5f, reducedMotion = false)
        assertEquals(1.14f, sxNeg, 0.0001f)
        assertTrue(syNeg < 1f)

        assertEquals(1f to 1f, SakugaPhysics.computeVelocityDeform(1f, reducedMotion = true))
    }

    @Test
    fun `controller emits for every effect and configured particle preset and expires cleanly`() {
        val controller = SakugaMotionController(reducedMotion = false)
        for (effect in SakugaEffectType.entries) {
            controller.clear()
            assertFalse(controller.hasActiveParticles)
            controller.emit(effect, Offset(120f, 80f), testColors)
            assertTrue("Expected active particles for $effect", controller.hasActiveParticles)
            var steps = 0
            while (controller.step(0.016f) && steps < 60) steps++
            assertFalse("Particles should expire within 1s for $effect", controller.hasActiveParticles)
        }

        for (preset in SakugaParticlePreset.entries) {
            controller.clear()
            controller.emitConfigured(
                origin = Offset(60f, 60f),
                colors = testColors,
                config = SakugaParticleConfig(
                    preset = preset,
                    particleCount = 12,
                    burstRadiusScale = 1.4f,
                    directionAngleRad = -1.57f,
                    directionalSpreadRad = 1.2f,
                ),
            )
            assertTrue("Expected active particles for preset $preset", controller.hasActiveParticles)
            assertTrue(controller.activeParticleCount <= SakugaMotionController.MAX_PARTICLES)
        }
    }

    @Test
    fun `controller suppresses emission under reducedMotion`() {
        val controller = SakugaMotionController(reducedMotion = true)
        for (effect in SakugaEffectType.entries) {
            controller.emit(effect, Offset(50f, 50f), testColors)
            assertFalse(controller.hasActiveParticles)
        }
        controller.emitConfigured(Offset(50f, 50f), testColors, SakugaParticleConfig(preset = SakugaParticlePreset.LAVENDER_BURST))
        assertFalse(controller.hasActiveParticles)
    }

    @Test
    fun `rapid bursts enforce MAX_PARTICLES cap and clear cleanly`() {
        val controller = SakugaMotionController(reducedMotion = false)
        repeat(20) { i ->
            controller.emit(SakugaEffectType.CLOUD_BURST, Offset(i * 10f, 40f), testColors, intensity = 1.2f)
        }
        assertTrue(controller.hasActiveParticles)
        assertTrue(controller.activeParticleCount <= SakugaMotionController.MAX_PARTICLES)
        controller.clear()
        assertFalse(controller.hasActiveParticles)
        assertEquals(0, controller.activeParticleCount)
    }

    @Test
    fun `SakugaColors provides distinct lavender violet success and delete accents for light and dark themes`() {
        val dark = SakugaColors.from(darkColorScheme())
        val light = SakugaColors.from(lightColorScheme())
        assertTrue(dark.smokePuff.alpha > light.smokePuff.alpha)
        assertEquals(darkColorScheme().primary, dark.primaryAccent)
        assertEquals(lightColorScheme().primary, light.primaryAccent)
        assertTrue(dark.lavenderAccent != dark.deleteAccent)
        assertTrue(light.violetAccent != light.successAccent)
    }
}
