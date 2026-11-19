package app.eddy.browser.ui.shapes

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import app.eddy.browser.data.models.ShortcutStyle

/** The tasteful set of silhouettes used for shortcuts and accents. Every entry is roughly square. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object EddyPolygons {
    val squircle: RoundedPolygon = RoundedPolygon.rectangle(
        width = 1f, height = 1f, rounding = CornerRounding(radius = 0.38f, smoothing = 0.9f),
    ).normalized()

    val all: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            squircle,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Diamond,
            MaterialShapes.Flower,
            MaterialShapes.SoftBurst,
            MaterialShapes.Circle,
        )
    }

    fun forShortcut(style: ShortcutStyle, index: Int): RoundedPolygon = when (style) {
        ShortcutStyle.MIXED -> all[index.mod(all.size)]
        ShortcutStyle.SQUIRCLE -> squircle
        ShortcutStyle.CIRCLE -> MaterialShapes.Circle
        ShortcutStyle.FLOWER -> MaterialShapes.Cookie9Sided
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** A [Shape] that morphs between two polygons; [progress] 0 is [Morph.start], 1 is the target. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f || !size.width.isFinite() || !size.height.isFinite()) {
            return Outline.Rectangle(Rect.Zero)
        }
        val t = progress.coerceIn(0f, 1f)
        val path = morph.toPath(t)
        val b = path.getBounds()
        if (b.width <= 0f || b.height <= 0f || !b.width.isFinite() || !b.height.isFinite()) {
            return Outline.Rectangle(size.toRect())
        }
        val m = Matrix().apply {
            scale(size.width / b.width, size.height / b.height)
            translate(-b.left, -b.top)
        }
        path.transform(m)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is MorphShape && morph === other.morph && progress == other.progress)

    override fun hashCode(): Int = 31 * System.identityHashCode(morph) + progress.hashCode()
}

@Composable
fun rememberMorph(from: RoundedPolygon, to: RoundedPolygon): Morph = remember(from, to) { Morph(from, to) }
