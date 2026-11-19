package app.eddy.browser

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import app.eddy.browser.data.models.ShortcutStyle
import app.eddy.browser.ui.shapes.EddyPolygons
import app.eddy.browser.ui.shapes.MorphShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveShapesTest {

    @Test
    fun `degenerate or zero sizes return empty rectangle outline without crashing`() {
        val morph = Morph(EddyPolygons.squircle, EddyPolygons.all.last())
        val density = Density(1f)
        val invalidSizes = listOf(
            Size.Zero,
            Size(0f, 100f),
            Size(100f, 0f),
            Size(-10f, 50f),
            Size(Float.NaN, 50f),
            Size(50f, Float.POSITIVE_INFINITY),
        )
        for (size in invalidSizes) {
            for (progress in listOf(-0.25f, 0f, 0.5f, 1f, 1.25f)) {
                val outline = MorphShape(morph, progress).createOutline(size, LayoutDirection.Ltr, density)
                assertEquals(Outline.Rectangle(Rect.Zero), outline)
            }
        }
    }

    @Test
    fun `all shortcut styles resolve valid polygons and morph targets`() {
        val target = EddyPolygons.all.last()
        for (style in ShortcutStyle.entries) {
            for (index in 0..12) {
                val poly = EddyPolygons.forShortcut(style, index)
                val morph = Morph(poly, target)
                val shape = MorphShape(morph, 0.5f)
                assertTrue(shape == MorphShape(morph, 0.5f))
            }
        }
    }
}
