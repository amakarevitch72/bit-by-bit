package app.bitbybit.ui.circuit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.sim.Circuit
import app.bitbybit.sim.PartKind
import app.bitbybit.ui.palette.ChipGlyph
import app.bitbybit.ui.palette.PaletteItem
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

// The board as a live circuit board (design/HardBoardConcept.dc.html): dark PCB with pad dots, gold solder pads,
// right-angle copper traces, current shown as moving dashes on lit traces, an amber always-live IN (+5V) terminal.

private object Pcb {
    val Board = Color(0xFF0B1210)
    val Edge = Color(0xFF1E2B24)
    val Dot = Color(0xFF1C2A23)
    val Copper = Color(0xFF2F3A33)
    val Gold = Color(0xFFC9A44C)
    val GoldEdge = Color(0xFF7A5F22)
    val Silk = Color(0xFF7E9A88)
    val FlowGreen = Color(0xFFC8F7D9)
    val FlowAmber = Color(0xFFFFE2A3)
}

private val GAP = 6.dp
private val PAD = 8.dp
private val STUB = 12.dp

/** Where a pin sits on its cell, and which way its trace leaves. */
private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

private data class PinSpot(val x: Float, val y: Float, val side: Side)

/**
 * Pin positions inside a cell of size [w]×[h] (relative to the cell's top-left).
 * A transistor follows its symbol: base B on the left, collector C on top, emitter E at the bottom.
 * Everything else: inputs on the left, outputs on the right, spread evenly.
 */
private fun pinSpot(kind: PartKind, output: Boolean, pin: Int, w: Float, h: Float): PinSpot = when {
    kind == PartKind.TRANSISTOR && !output && pin == 0 -> PinSpot(0f, h / 2, Side.LEFT)
    kind == PartKind.TRANSISTOR && !output && pin == 1 -> PinSpot(w / 2, 0f, Side.TOP)
    kind == PartKind.TRANSISTOR && output -> PinSpot(w / 2, h, Side.BOTTOM)
    output -> PinSpot(w, h * (pin + 1) / (kind.outputs.size + 1), Side.RIGHT)
    else -> PinSpot(0f, h * (pin + 1) / (kind.inputs.size + 1), Side.LEFT)
}

private fun Side.dx() = when (this) { Side.LEFT -> -1f; Side.RIGHT -> 1f; else -> 0f }
private fun Side.dy() = when (this) { Side.TOP -> -1f; Side.BOTTOM -> 1f; else -> 0f }
private fun Side.vertical() = this == Side.TOP || this == Side.BOTTOM

/** A right-angle trace from pin a to pin b: a short stub out of each pad, then at most two bends between them. */
private fun trace(a: Offset, sa: Side, b: Offset, sb: Side, stub: Float): Path {
    val a1 = Offset(a.x + sa.dx() * stub, a.y + sa.dy() * stub)
    val b1 = Offset(b.x + sb.dx() * stub, b.y + sb.dy() * stub)
    val mid = when {
        !sa.vertical() && !sb.vertical() -> { val mx = (a1.x + b1.x) / 2; listOf(Offset(mx, a1.y), Offset(mx, b1.y)) }
        sa.vertical() && sb.vertical() -> { val my = (a1.y + b1.y) / 2; listOf(Offset(a1.x, my), Offset(b1.x, my)) }
        sa.vertical() -> listOf(Offset(a1.x, b1.y))
        else -> listOf(Offset(b1.x, a1.y))
    }
    return Path().apply {
        moveTo(a.x, a.y); lineTo(a1.x, a1.y)
        mid.forEach { lineTo(it.x, it.y) }
        lineTo(b1.x, b1.y); lineTo(b.x, b.y)
    }
}

/** The board: PCB, traces, parts and pads. Tapping a cell calls [onTap]; what happens depends on the tool. */
@Composable
fun CircuitBoard(
    s: EditorState,
    byId: Map<String, PaletteItem>,
    onTap: (Int) -> Unit,
    modifier: Modifier,
    /** Drag and drop: null = tapping only. [onDrop] gets what was dropped and the target cell (null = off the board). */
    drag: DragState? = null,
    onDrop: (DragPayload, Int?) -> Unit = { _, _ -> },
) {
    val flow by rememberInfiniteTransition(label = "current").animateFloat(
        0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "flow",
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pcb.Board)
            .border(1.dp, Pcb.Edge, RoundedCornerShape(18.dp)),
    ) {
        val cols = s.cols; val rows = s.rows
        val cellW: Dp = (maxWidth - PAD * 2 - GAP * (cols - 1)) / cols
        val cellH: Dp = (maxHeight - PAD * 2 - GAP * (rows - 1)) / rows
        fun x(c: Int) = PAD + (cellW + GAP) * c
        fun y(r: Int) = PAD + (cellH + GAP) * r
        val sim = s.sim
        val pending = s.pending
        val density = LocalDensity.current

        fun pinAt(cell: Int, kind: PartKind, output: Boolean, pin: Int): Pair<Offset, Side> = with(density) {
            val spot = pinSpot(kind, output, pin, cellW.toPx(), cellH.toPx())
            Offset(x(cell % cols).toPx() + spot.x, y(cell / cols).toPx() + spot.y) to spot.side
        }

        // 1 · PCB texture and copper traces (under the parts)
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var yy = step / 2
            while (yy < size.height) {
                var xx = step / 2
                while (xx < size.width) { drawCircle(Pcb.Dot, 1.4.dp.toPx(), Offset(xx, yy)); xx += step }
                yy += step
            }
            for (w in s.wires) {
                val from = s.placed[w.fromPart]?.kind ?: continue
                val to = s.placed[w.toPart]?.kind ?: continue
                val (a, sa) = pinAt(w.fromPart, from, true, w.fromPin)
                val (b, sb) = pinAt(w.toPart, to, false, w.toPin)
                val path = trace(a, sa, b, sb, STUB.toPx())
                val on = sim?.output(w.fromPart, w.fromPin) == true
                val power = from == PartKind.POWER
                val base = when { power -> Bb.Accent; on -> Bb.High; else -> Pcb.Copper }
                if (on) drawPath(path, base.copy(alpha = 0.25f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, base, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (on) {
                    val dash = 3.dp.toPx(); val gap = 9.dp.toPx()
                    drawPath(
                        path, if (power) Pcb.FlowAmber else Pcb.FlowGreen,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -flow * (dash + gap))),
                    )
                }
            }
        }

        // Drop target: turn a position on the board into the nearest cell (gaps snap to the closest cell).
        if (drag != null) {
            val padPx = with(density) { PAD.toPx() }; val gapPx = with(density) { GAP.toPx() }
            val cwPx = with(density) { cellW.toPx() }; val chPx = with(density) { cellH.toPx() }
            Box(
                Modifier.fillMaxSize().dropTarget(drag) { p ->
                    val c = ((p.x - padPx + gapPx / 2) / (cwPx + gapPx)).toInt()
                    val r = ((p.y - padPx + gapPx / 2) / (chPx + gapPx)).toInt()
                    if (p.x < 0 || p.y < 0 || c !in 0 until cols || r !in 0 until rows) null else r * cols + c
                },
            )
        }
        val hover = drag?.hoverCell

        // 2 · Cells: invisible until they matter (drop target, selection, wire target)
        for (i in 0 until cols * rows) {
            val part = s.placed[i]
            val item = part?.let { byId[it.id] }
            val focused = s.focused == i || pending?.fromCell == i || pending?.targetCell == i
            val canDrop = part == null && s.tool != null && pending == null && s.mode == BoardTool.PLACE
            val canTarget = pending != null && part != null && part.kind?.inputs?.isNotEmpty() == true
            // While dragging: the cell under the finger turns green if free, red if taken (the dragged part's own cell counts as free).
            val dragOrigin = (drag?.payload as? DragPayload.Existing)?.fromCell
            val hoverFree = hover == i && (part == null || dragOrigin == i)
            val hoverTaken = hover == i && !hoverFree
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.offset(x(i % cols), y(i / cols)).size(cellW, cellH).clip(shape)
                    .then(
                        when {
                            hoverFree -> Modifier.background(Bb.High.copy(alpha = 0.12f)).border(2.dp, Bb.High, shape)
                            hoverTaken -> Modifier.background(Bb.Error.copy(alpha = 0.10f)).border(2.dp, Bb.Error, shape)
                            dragOrigin == i -> Modifier.background(Bb.Bg.copy(alpha = 0.6f))
                            focused -> Modifier.background(Bb.Accent.copy(alpha = 0.10f)).border(1.5.dp, Bb.Accent, shape)
                            canTarget -> Modifier.border(1.5.dp, Bb.High.copy(alpha = 0.7f), shape)
                            canDrop -> Modifier.drawBehind {
                                drawRoundRect(
                                    Bb.AccentBorder, cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            }
                            else -> Modifier
                        },
                    )
                    // Long-press a part you placed and drag it: drop on a cell to move it, off the board to remove it.
                    .then(
                        if (drag != null && part != null && !part.fixed) Modifier.dragSource(
                            drag, payload = { DragPayload.Existing(i, item?.symbol ?: part.label) }, onDrop = onDrop,
                        ) else Modifier,
                    )
                    .clickable(enabled = part != null || s.tool != null || pending != null, role = Role.Button) { onTap(i) }
                    .semantics { contentDescription = cellDescription(s, i, part, item) },
                contentAlignment = Alignment.Center,
            ) {
                if (part != null && item != null) PartFace(part, item, i, sim)
            }
        }

        // 3 · Gold solder pads on every pin (above the parts)
        Canvas(Modifier.fillMaxSize()) {
            for ((cell, part) in s.placed) {
                val kind = part.kind ?: continue
                val pads = kind.inputs.indices.map { pinAt(cell, kind, false, it).first } +
                    kind.outputs.indices.map { pinAt(cell, kind, true, it).first }
                pads.forEach { p ->
                    drawCircle(Pcb.Gold, 4.5.dp.toPx(), p)
                    drawCircle(Pcb.GoldEdge, 4.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                }
                if (pending?.fromCell == cell) {
                    val (p, _) = pinAt(cell, kind, true, pending.fromPin)
                    drawCircle(Bb.Accent, 7.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun PartFace(part: PlacedPart, item: PaletteItem, cell: Int, sim: Circuit.Result?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when (part.id) {
            "SWITCH" -> SlideSwitch(part.switchOn)
            "POWER" -> PowerTerminal()
            "LED" -> Led(sim?.input(cell, 0) == true)
            "TRANSISTOR" -> TransistorSymbol(
                base = sim?.input(cell, 0) == true, collector = sim?.input(cell, 1) == true, emitter = sim?.output(cell, 0) == true,
            )
            else -> ChipGlyph(item.symbol, lit = part.kind != null, Modifier.padding(horizontal = 4.dp), compact = true)
        }
        if (part.id != "TRANSISTOR" && part.id != "POWER") {
            Text(
                part.label, fontFamily = BbFonts.Mono, fontSize = 10.sp, maxLines = 1,
                color = if (part.fixed) Bb.Text else Pcb.Silk, fontWeight = if (part.fixed) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** A slide toggle: knob up and green = 1. */
@Composable
private fun SlideSwitch(on: Boolean) {
    Box(
        Modifier.size(24.dp, 38.dp).clip(RoundedCornerShape(7.dp)).background(Bb.SurfaceSunken)
            .border(1.5.dp, if (on) Bb.High else Bb.Low, RoundedCornerShape(7.dp)),
    ) {
        Box(
            Modifier.padding(3.dp).offset(y = if (on) 0.dp else 15.dp).size(15.dp, 17.dp)
                .clip(RoundedCornerShape(4.dp)).background(if (on) Bb.High else Bb.PinOutline),
        )
    }
}

/** The permanent power input: an amber terminal block, always live. */
@Composable
private fun PowerTerminal() {
    Column(
        Modifier.clip(RoundedCornerShape(7.dp)).background(Bb.AccentBg).border(1.5.dp, Bb.Accent, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("IN", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Bb.Accent)
        Text("+5V", fontFamily = BbFonts.Mono, fontSize = 10.sp, color = Bb.TextSecondary)
    }
}

@Composable
private fun Led(lit: Boolean) {
    Canvas(Modifier.size(34.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        if (lit) drawCircle(Bb.High.copy(alpha = 0.28f), size.minDimension / 2, c)
        drawCircle(if (lit) Bb.High else Color(0xFF1B2420), 11.dp.toPx(), c)
        drawCircle(Bb.PinOutline, 11.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = if (lit) 0.7f else 0.15f), 3.dp.toPx(), Offset(c.x - 4.dp.toPx(), c.y - 4.dp.toPx()))
    }
}

/**
 * NPN symbol filling the cell: circle, base bar, base lead to the left pad, collector to the top pad,
 * emitter (with arrow) to the bottom pad. Each lead is green when it carries a 1.
 */
@Composable
private fun TransistorSymbol(base: Boolean, collector: Boolean, emitter: Boolean) {
    Canvas(Modifier.fillMaxSize()) { drawTransistor(base, collector, emitter) }
}

private fun DrawScope.drawTransistor(base: Boolean, collector: Boolean, emitter: Boolean) {
    val w = size.width; val h = size.height
    val c = Offset(w / 2, h / 2)
    val r = minOf(w, h) * 0.32f
    val bar = c.x - r * 0.42f
    val col = { on: Boolean -> if (on) Bb.High else Pcb.Copper }
    val sw = 2.5.dp.toPx()
    drawCircle(Bb.Surface, r, c)
    drawCircle(Color(0xFF7D8796), r, c, style = Stroke(2.dp.toPx()))
    drawLine(col(base), Offset(0f, c.y), Offset(bar, c.y), sw)                        // base lead
    drawLine(Bb.Text, Offset(bar, c.y - r * 0.55f), Offset(bar, c.y + r * 0.55f), 3.dp.toPx()) // base bar
    val cJoin = Offset(c.x, c.y - r * 0.62f)
    drawLine(col(collector), Offset(bar, c.y - r * 0.25f), cJoin, sw)                // collector
    drawLine(col(collector), cJoin, Offset(c.x, 0f), sw)
    val eJoin = Offset(c.x, c.y + r * 0.62f)
    drawLine(col(emitter), Offset(bar, c.y + r * 0.25f), eJoin, sw)                  // emitter
    drawLine(col(emitter), eJoin, Offset(c.x, h), sw)
    val arrow = Path().apply {                                                         // NPN arrow, pointing out
        moveTo(eJoin.x, eJoin.y)
        lineTo(eJoin.x - r * 0.38f, eJoin.y - r * 0.04f)
        lineTo(eJoin.x - r * 0.18f, eJoin.y - r * 0.34f)
        close()
    }
    drawPath(arrow, col(emitter))
}

private fun cellDescription(s: EditorState, i: Int, part: PlacedPart?, item: PaletteItem?): String {
    if (part == null) return "Cell ${s.cellName(i)}: empty"
    val state = when (part.id) {
        "SWITCH" -> if (part.switchOn) ", on" else ", off"
        "LED" -> if (s.sim?.input(i, 0) == true) ", lit" else ", dark"
        "TRANSISTOR" -> if (s.sim?.output(i, 0) == true) ", conducting" else ", off"
        "POWER" -> ", +5V"
        else -> ""
    }
    return "Cell ${s.cellName(i)}: ${part.label}, ${item?.name ?: part.id}$state"
}

/** Place / Wire / Probe / Erase: what a tap on the board does. */
@Composable
fun BoardToolBar(mode: BoardTool, onMode: (BoardTool) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(12.dp)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoardTool.entries.forEach { t ->
            val on = t == mode
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) Bb.AccentBg else Color.Transparent)
                    .then(if (on) Modifier.border(1.5.dp, Bb.Accent, RoundedCornerShape(9.dp)) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton) { onMode(t) },
                contentAlignment = Alignment.Center,
            ) { Text(t.label, color = if (on) Bb.Accent else Bb.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Context actions under the board. Buttons scroll sideways if they don't fit. */
@Composable
fun EditorActionBar(s: EditorState, actions: EditorActions, emptyHint: String, idleHint: String) {
    val focused = s.focused?.let { s.placed[it] }
    val pending = s.pending
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val (line, color) = when {
            pending?.targetCell != null ->
                "Connect ${Editor.pinLabel(s, pending.fromCell, pending.fromPin)} to which input of ${s.placed[pending.targetCell]?.label}?" to Bb.Accent
            pending != null -> "Wiring from ${Editor.pinLabel(s, pending.fromCell, pending.fromPin)}: tap the part to connect to" to Bb.Accent
            s.message != null -> s.message to Bb.TextSecondary
            s.mode == BoardTool.WIRE -> "Wire: tap the part the wire starts from." to Bb.TextMuted
            s.mode == BoardTool.PROBE -> "Probe: tap a part to read its pins." to Bb.TextMuted
            s.mode == BoardTool.ERASE -> "Erase: tap a part to remove it and its wires." to Bb.TextMuted
            focused != null && focused.kind == null -> "${focused.label} can't be wired yet." to Bb.TextMuted
            focused != null -> ("${focused.label} selected" + (if (focused.id == "SWITCH") " · tap it to flip" else "")) to Bb.Text
            s.tool != null -> "Placing ${s.tool.lowercase().replaceFirstChar { it.uppercase() }}: tap an empty cell" to Bb.Accent
            s.placed.values.none { !it.fixed } -> emptyHint to Bb.TextMuted
            else -> idleHint to Bb.TextMuted
        }
        Text(line, style = BbType.Body.copy(color = color), maxLines = 2)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                pending?.targetCell != null -> {
                    s.placed[pending.targetCell]?.kind?.inputs?.forEachIndexed { pin, name ->
                        SmallAction("Input $name", accent = true) { actions.chooseTargetPin(pin) }
                    }
                    SmallAction("Cancel", onClick = actions.cancelWire)
                }
                pending != null -> SmallAction("Cancel wire", onClick = actions.cancelWire)
                focused != null && s.mode != BoardTool.PROBE -> {
                    focused.kind?.outputs?.let { outs ->
                        outs.forEachIndexed { pin, name ->
                            SmallAction(if (outs.size == 1) "Start wire" else "Wire from $name", accent = true) { actions.startWire(pin) }
                        }
                    }
                    if (s.wires.any { it.fromPart == s.focused || it.toPart == s.focused }) SmallAction("Unwire", onClick = actions.unwire)
                    if (!focused.fixed) SmallAction("Remove", danger = true, onClick = actions.remove)
                }
                s.tool != null -> SmallAction("Done placing", onClick = actions.donePlacing)
            }
        }
    }
}

@Composable
fun SmallAction(text: String, danger: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    val (bg, border, fg) = when {
        danger -> Triple(Bb.ErrorBg, Bb.ErrorBorder, Bb.ErrorText)
        accent -> Triple(Bb.AccentBg, Bb.Accent, Bb.Accent)
        else -> Triple(Bb.Surface, Bb.Border, Bb.Text)
    }
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
