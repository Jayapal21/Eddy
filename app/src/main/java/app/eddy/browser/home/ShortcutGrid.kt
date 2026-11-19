package app.eddy.browser.home

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.data.models.ShortcutStyle
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.ShortcutColors
import app.eddy.browser.ui.components.ShortcutIcons
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.rememberSakugaColors
import app.eddy.browser.ui.sakuga.rememberSakugaController
import app.eddy.browser.ui.sakuga.sakugaOverlay
import app.eddy.browser.ui.sakuga.sakugaPressSquash
import app.eddy.browser.ui.shapes.EddyPolygons
import app.eddy.browser.ui.shapes.MorphShape
import app.eddy.browser.ui.shapes.rememberMorph
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.util.UrlUtils

private sealed interface ShortcutCell {
    data class Manual(val shortcut: Shortcut, val index: Int) : ShortcutCell
    data class Recommended(val shortcut: Shortcut, val index: Int) : ShortcutCell
    data object Add : ShortcutCell
}

/**
 * Shortcut grid with long-press-to-drag reordering for manual shortcuts. Recommended websites
 * share the exact same [ShortcutTile] component and visual styling in the same grid alongside
 * manual shortcuts and the Add (+) tile.
 */
@Composable
fun ShortcutGrid(
    shortcuts: List<Shortcut>,
    style: ShortcutStyle,
    columns: Int,
    tileSize: Dp,
    onOpen: (Shortcut) -> Unit,
    onEdit: (Shortcut) -> Unit,
    onRemove: (Shortcut) -> Unit,
    onReorder: (List<Shortcut>) -> Unit,
    onAdd: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    recommended: List<Shortcut> = emptyList(),
    onOpenNewTab: ((Shortcut) -> Unit)? = null,
    onPinRecommended: ((Shortcut) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var recMenuFor by remember { mutableStateOf<String?>(null) }
    // Reordering happens on a local copy so it is instant; the new order is persisted once when the drag ends.
    var order by remember { mutableStateOf(shortcuts) }
    var lastSwap by remember { mutableLongStateOf(0L) }
    LaunchedEffect(shortcuts) { if (draggingId == null) order = shortcuts }

    val cells: List<ShortcutCell> = order.mapIndexed { index, shortcut ->
        ShortcutCell.Manual(shortcut, index)
    } + recommended.mapIndexed { index, shortcut ->
        ShortcutCell.Recommended(shortcut, order.size + index)
    } + ShortcutCell.Add

    val safeColumns = columns.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        cells.chunked(safeColumns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { cell ->
                    // With no shortcuts and no recommendations the lone add tile takes the full row so it sits in the centre.
                    Box(if (order.isEmpty() && recommended.isEmpty()) Modifier.fillMaxWidth() else Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        when (cell) {
                            ShortcutCell.Add -> AddTile(tileSize, onAdd, onHide)
                            is ShortcutCell.Manual -> {
                                val item = cell.shortcut
                                val index = cell.index
                                val isDragging = draggingId == item.id
                                Box(
                                    Modifier
                                        .onGloballyPositioned { c -> bounds[item.id] = Rect(c.positionInRoot(), Size(c.size.width.toFloat(), c.size.height.toFloat())) }
                                        .pointerInput(item.id) {
                                            var total = Offset.Zero
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { local ->
                                                    haptics.longPress()
                                                    draggingId = item.id
                                                    grab = local
                                                    total = Offset.Zero
                                                    pointerRoot = (bounds[item.id]?.topLeft ?: Offset.Zero) + local
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    total += amount
                                                    pointerRoot = (bounds[item.id]?.topLeft ?: Offset.Zero) + change.position
                                                    val now = System.currentTimeMillis()
                                                    // A short cooldown lets layout catch up with a swap before the next one is considered.
                                                    if (now - lastSwap < SWAP_COOLDOWN_MS) return@detectDragGesturesAfterLongPress
                                                    val from = order.indexOfFirst { it.id == item.id }
                                                    val target = bounds.entries.firstOrNull { (id, r) -> id != item.id && r.contains(pointerRoot) }?.key
                                                    val to = order.indexOfFirst { it.id == target }
                                                    if (to >= 0 && from >= 0) {
                                                        haptics.tick()
                                                        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                                        lastSwap = now
                                                    }
                                                },
                                                onDragEnd = {
                                                    // Touch slop, not a few pixels: a finger always wobbles a little during a long press.
                                                    if (total.getDistance() < viewConfiguration.touchSlop) menuFor = item.id
                                                    draggingId = null
                                                    if (order.map { it.id } != shortcuts.map { it.id }) onReorder(order)
                                                },
                                                onDragCancel = {
                                                    // The tile's click consumes the release, so a still long press ends here, not in onDragEnd.
                                                    if (total.getDistance() < viewConfiguration.touchSlop) menuFor = item.id
                                                    draggingId = null
                                                    if (order.map { it.id } != shortcuts.map { it.id }) onReorder(order)
                                                },
                                            )
                                        },
                                ) {
                                    ShortcutTile(
                                        shortcut = item,
                                        index = index,
                                        style = style,
                                        tileSize = tileSize,
                                        lifted = isDragging,
                                        translation = {
                                            if (isDragging) pointerRoot - grab - (bounds[item.id]?.topLeft ?: pointerRoot - grab) else Offset.Zero
                                        },
                                        // A long press also ends in a click on release; it opens the menu, not the site.
                                        onClick = { if (draggingId != item.id) onOpen(item) },
                                    )
                                    DropdownMenu(expanded = menuFor == item.id, onDismissRequest = { menuFor = null }) {
                                        DropdownMenuItem(
                                            text = { Text(str(R.string.edit)) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                            onClick = { menuFor = null; onEdit(item) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(str(R.string.remove_2)) },
                                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                            onClick = { menuFor = null; onRemove(item) },
                                        )
                                        HideItem { menuFor = null; onHide() }
                                    }
                                }
                            }
                            is ShortcutCell.Recommended -> {
                                val item = cell.shortcut
                                Box {
                                    ShortcutTile(
                                        shortcut = item,
                                        index = cell.index,
                                        style = style,
                                        tileSize = tileSize,
                                        lifted = false,
                                        translation = { Offset.Zero },
                                        onClick = { onOpen(item) },
                                        onLongClick = { recMenuFor = item.id },
                                    )
                                    DropdownMenu(expanded = recMenuFor == item.id, onDismissRequest = { recMenuFor = null }) {
                                        if (onOpenNewTab != null) {
                                            DropdownMenuItem(
                                                text = { Text(str(R.string.open_in_new_tab)) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                                onClick = { recMenuFor = null; onOpenNewTab(item) },
                                            )
                                        }
                                        if (onPinRecommended != null) {
                                            DropdownMenuItem(
                                                text = { Text(str(R.string.add_to_start_page)) },
                                                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                                onClick = { recMenuFor = null; onPinRecommended(item) },
                                            )
                                        }
                                        HideItem { recMenuFor = null; onHide() }
                                    }
                                }
                            }
                        }
                    }
                }
                // Keep the last row's tiles aligned with the rows above.
                repeat((safeColumns - row.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    index: Int,
    style: ShortcutStyle,
    tileSize: Dp,
    lifted: Boolean,
    translation: () -> Offset,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val polygon = EddyPolygons.forShortcut(style, shortcut.shape.takeIf { style == ShortcutStyle.MIXED } ?: index)
    val morph = rememberMorph(polygon, EddyPolygons.all.last())
    val reduced = LocalReducedMotion.current
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Pressing softens the silhouette towards a circle; the tile also squashes and springs back on release.
    val morphProgress by animateFloatAsState(
        if (pressed || lifted) 1f else 0f,
        if (reduced) tween(0) else spatialSpring(),
        label = "morph",
    )
    val targetScaleX = when {
        reduced -> 1f
        lifted -> 1.14f
        pressed -> 1.06f
        else -> 1f
    }
    val targetScaleY = when {
        reduced -> 1f
        lifted -> 1.14f
        pressed -> 0.90f
        else -> 1f
    }
    val scaleX by animateFloatAsState(
        targetScaleX,
        if (pressed) SakugaPhysics.pressSpring(reduced) else SakugaPhysics.overshootSpring(reduced),
        label = "tileScaleX",
    )
    val scaleY by animateFloatAsState(
        targetScaleY,
        if (pressed) SakugaPhysics.pressSpring(reduced) else SakugaPhysics.overshootSpring(reduced),
        label = "tileScaleY",
    )
    val tileAlpha by animateFloatAsState(
        if (pressed) 0.90f else 1f,
        tween(if (reduced) 0 else 160, easing = FastOutSlowInEasing),
        label = "tileAlpha",
    )
    val container = ShortcutColors.container(shortcut.color, scheme)
    val content = ShortcutColors.content(shortcut.color, scheme)
    val clampedMorph = morphProgress.coerceIn(0f, 1f)
    val shape = remember(morph, clampedMorph) { MorphShape(morph, clampedMorph) }
    val host = UrlUtils.displayHost(shortcut.url)

    val triggerClick = {
        controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.85f)
        onClick()
    }

    Column(
        Modifier.widthIn(max = tileSize + 20.dp)
            .graphicsLayer {
                val t = translation()
                translationX = t.x
                translationY = t.y
                this.scaleX = scaleX
                this.scaleY = scaleY
                alpha = tileAlpha
                shadowElevation = if (lifted) 24f else 0f
            }
            .sakugaOverlay(controller)
            .testTag("shortcut_tile_$host")
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = str(R.string.open_2, shortcut.title),
                        onClick = triggerClick,
                        onLongClickLabel = str(R.string.shortcut_options),
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = str(R.string.open_2, shortcut.title),
                        onClick = triggerClick,
                    )
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(tileSize).clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = ShortcutIcons.all[shortcut.icon]
            if (glyph != null) Icon(glyph, null, Modifier.size(tileSize * 0.44f), tint = content)
            else SiteIcon(host, size = tileSize * 0.62f, fetch = true, background = androidx.compose.ui.graphics.Color.Transparent)
        }
        Text(
            shortcut.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HideItem(onClick: () -> Unit) = DropdownMenuItem(
    text = { Text(str(R.string.hide_shortcuts)) },
    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
    onClick = onClick,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddTile(tileSize: Dp, onClick: () -> Unit, onHide: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (pressed) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        animationSpec = tween(if (reduced) 0 else 180, easing = FastOutSlowInEasing),
        label = "addBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (pressed) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(if (reduced) 0 else 180, easing = FastOutSlowInEasing),
        label = "addTint",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(tileSize)
                .sakugaPressSquash(
                    interactionSource = source,
                    pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                    pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
                )
                .sakugaOverlay(controller)
                .clip(CircleShape)
                .background(bgColor)
                .border(BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.28f)), CircleShape)
                .testTag("add_shortcut_button")
                .combinedClickable(
                    interactionSource = source,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    role = Role.Button,
                    onClickLabel = str(R.string.add_shortcut),
                    onClick = {
                        controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.85f)
                        onClick()
                    },
                    onLongClickLabel = str(R.string.shortcut_options),
                    onLongClick = { menu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = str(R.string.add_shortcut),
                modifier = Modifier.size(22.dp),
                tint = iconTint,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { HideItem { menu = false; onHide() } }
        }
        Text(
            str(R.string.add),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val SWAP_COOLDOWN_MS = 140L
