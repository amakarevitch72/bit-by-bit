package app.bitbybit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.data.LevelsFile
import app.bitbybit.sim.ChipId
import app.bitbybit.sim.Sig
import app.bitbybit.sim.SocketBoard
import app.bitbybit.sim.SocketLevels
import app.bitbybit.sim.SocketSpec
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.circuit.LogicAnalyzer
import app.bitbybit.ui.circuit.SmallAction
import app.bitbybit.ui.palette.ComponentPalette
import app.bitbybit.ui.palette.buildPalette
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Normal mode for chip levels whose wiring is fixed: the player picks which chip goes in each socket.
// One data-driven screen (like the transistor workbench): a level is just a SocketLevel config.

data class SocketLevel(
    val n: Int,
    val title: String,
    val goal: String,
    val spec: SocketSpec,
    val tray: List<ChipId>,
    /** Chips already in place when the level opens. */
    val start: Map<String, ChipId> = emptyMap(),
    val wrongHint: String,
    /** Shown on success when the build differs from the textbook answer, e.g. XOR joining the carries. */
    val bonus: (Map<String, ChipId?>) -> String? = { null },
)

val SOCKET_LEVELS: Map<Int, SocketLevel> = listOf(
    SocketLevel(
        4, "Build NAND and NOR",
        "Fill the sockets so the top LED is NAND (NOT of A AND B) and the bottom LED is NOR (NOT of A OR B).",
        SocketLevels.NAND_NOR, tray = listOf(ChipId.AND, ChipId.OR, ChipId.NOT), start = mapOf("g1" to ChipId.AND),
        wrongHint = "Each output is one gate followed by NOT. Which gate goes before each NOT?",
    ),
    SocketLevel(
        5, "Build an XOR gate",
        "X must be 1 when exactly one of A and B is on. Two gates read A and B; a third combines them.",
        SocketLevels.XOR, tray = listOf(ChipId.AND, ChipId.OR, ChipId.NAND, ChipId.NOR),
        wrongHint = "XOR is \"A or B, but not both\". Which gate says \"or\", which says \"not both\", and which joins them?",
    ),
    SocketLevel(
        7, "Build a full adder",
        "Add three bits: A, B and the carry-in Cin from the column before. Cout is the carry out, S the sum.",
        SocketLevels.FULL_ADDER, tray = listOf(ChipId.HA, ChipId.OR, ChipId.AND, ChipId.XOR), start = mapOf("h1" to ChipId.HA),
        wrongHint = "Add A + B first, then add Cin to that sum. The two carries meet in the last socket.",
        bonus = { chips -> if (chips["g"] == ChipId.XOR) "XOR works too: both carries are never 1 at the same time." else null },
    ),
).associateBy { it.n }

data class SocketUiState(
    val level: SocketLevel,
    val chips: Map<String, ChipId?> = level.start,
    val selected: ChipId? = null,
    val values: List<Boolean> = List(level.spec.inputs.size) { false },
    /** Step key → outputs, from the last Run test. Null = not tested since the last chip change. */
    val results: Map<String, List<Boolean>>? = null,
    val message: String? = null,
) {
    val board get() = SocketBoard(level.spec, chips)
    val signals: Map<String, Sig> get() = board.signals(values)
}

class SocketWorkbenchViewModel(level: SocketLevel) : ViewModel() {
    private val _state = MutableStateFlow(SocketUiState(level))
    val state: StateFlow<SocketUiState> = _state.asStateFlow()

    fun pick(id: String?) = _state.update { s ->
        val chip = id?.let { ChipId.valueOf(it) }
        s.copy(selected = chip?.takeIf { it != s.selected }, message = if (chip != null && chip != s.selected) "Now tap a socket." else null)
    }

    /** With a chip picked: put it in. Without: empty the socket. Either way it's a new circuit, so results reset. */
    fun tapSocket(id: String) = _state.update { s ->
        val chip = s.selected
        when {
            chip != null -> s.copy(chips = s.chips + (id to chip), selected = null, results = null, message = null)
            s.chips[id] != null -> s.copy(chips = s.chips + (id to null), results = null, message = "Socket emptied.")
            else -> s.copy(message = "Pick a chip below first, then tap the socket.")
        }
    }

    fun toggle(i: Int) = _state.update { s -> s.copy(values = s.values.toMutableList().also { it[i] = !it[i] }) }

    fun runTest() = _state.update { s ->
        val rows = s.board.run()
        if (rows == null) s.copy(message = "Fill every socket first.", results = null) else s.copy(results = rows, message = null)
    }
}

@Composable
fun SocketWorkbench(level: SocketLevel, data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit) {
    val vm: SocketWorkbenchViewModel = viewModel(key = "socket-${level.n}") { SocketWorkbenchViewModel(level) }
    val s by vm.state.collectAsStateWithLifecycle()
    val spec = level.spec
    val palette = remember(data, level) {
        val all = buildPalette(data, emptySet()).associateBy { it.id }
        level.tray.mapNotNull { all[it.name]?.copy(available = true) }   // the level hands you these chips
    }
    val sig = s.signals
    val got = s.results
    val allPass = got != null && spec.keys.all { got[it] == spec.want(it) }
    val anyWrong = got != null && !allPass
    val live = spec.outputs.joinToString(" · ") { (name, ref) -> "$name ${bitText(sig[ref] == true)}" }

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Workbench", level.title, onBack) {
            Text(
                live, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Bb.High,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Bb.HighBg).padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Live output: $live" },
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(level.goal, style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp))
            SocketBoardView(s, vm::tapSocket, Modifier.padding(horizontal = 16.dp))
            Text(
                s.message ?: if (s.selected != null) "Now tap a socket." else "Tap a chip, then a socket. Tap a filled socket to empty it.",
                style = BbType.Body.copy(color = if (s.selected != null) Bb.Accent else Bb.TextMuted),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ComponentPalette(palette, s.selected?.name, vm::pick, showFilters = false, caption = "Your chips")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.inputs.forEachIndexed { i, name -> InputToggle("Input $name", s.values[i], { vm.toggle(i) }, Modifier.weight(1f)) }
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                LogicAnalyzer(
                    inputNames = spec.inputs, outputNames = spec.outputs.map { it.first }, keys = spec.keys,
                    want = spec::want, got = got,
                    action = { SmallAction("Run test", accent = true, onClick = vm::runTest) },
                )
            }
            if (anyWrong) Text(level.wrongHint, style = BbType.Caption.copy(color = Bb.ErrorText), modifier = Modifier.padding(horizontal = 16.dp))
            if (allPass) level.bonus(s.chips)?.let { Text(it, style = BbType.Caption.copy(color = Bb.High), modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                !s.board.complete -> PrimaryButton("Fill every socket", ButtonTone.Disabled)
                anyWrong -> PrimaryButton("A step is wrong — swap a chip", ButtonTone.Error)
                else -> PrimaryButton("Tap Run test to check every step", ButtonTone.Disabled)
            }
        }
    }
}

/**
 * The fixed wiring, laid out left to right: inputs, then sockets in columns by depth, then the output LEDs.
 * Wires are right-angle traces coloured by their signal (dashed when unpowered because a socket upstream is empty).
 */
@Composable
private fun SocketBoardView(s: SocketUiState, onSocket: (String) -> Unit, modifier: Modifier) {
    val spec = s.level.spec
    val sig = s.signals
    val maxDepth = spec.sockets.maxOf { spec.depth(it.id) }
    val columns: List<List<String>> = (1..maxDepth).map { d -> spec.sockets.filter { spec.depth(it.id) == d }.map { it.id } }
    val rowsNeeded = maxOf(spec.inputs.size, spec.outputs.size, columns.maxOf { it.size })
    val boardH: Dp = 24.dp + 84.dp * rowsNeeded
    val sockW = 64.dp
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Bb.Text)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier.fillMaxWidth().height(boardH).clip(RoundedCornerShape(18.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(18.dp)),
    ) {
        val w = maxWidth; val h = maxHeight
        val cols = maxDepth + 2                     // inputs · sockets… · outputs
        fun colX(c: Int): Dp = 28.dp + (w - 56.dp) * c / (cols - 1)
        fun rowY(i: Int, n: Int): Dp = h * (i + 0.5f) / n
        fun socketHeight(id: String): Dp = if (s.chips[id] == ChipId.HA || s.chips[id] == ChipId.FA || id.startsWith("h")) 62.dp else 50.dp
        // Where each signal comes from (right edge of its source) and where sockets/outputs read it.
        val socketPos = columns.flatMapIndexed { c, ids -> ids.mapIndexed { i, id -> id to (colX(c + 1) to rowY(i, ids.size)) } }.toMap()
        fun source(ref: String): Pair<Dp, Dp> {
            val sock = spec.refSocket(ref)
            if (sock == null) { val i = spec.inputs.indexOf(ref); return colX(0) + 8.dp to rowY(i, spec.inputs.size) }
            val (x, y) = socketPos.getValue(sock)
            val pin = ref.substringAfter('.', "0").toInt()
            val hh = socketHeight(sock)
            val outs = if (ref.contains('.') || s.chips[sock] == ChipId.HA) 2 else 1
            return x + sockW / 2 to y - hh / 2 + hh * (pin + 1) / (outs + 1)
        }

        Canvas(Modifier.fillMaxSize()) {
            fun Dp.px() = with(density) { toPx() }
            fun wire(from: Pair<Dp, Dp>, toX: Dp, toY: Dp, v: Sig) {
                val ax = from.first.px(); val ay = from.second.px(); val bx = toX.px(); val by = toY.px()
                val mx = (ax + bx) / 2
                val path = Path().apply { moveTo(ax, ay); lineTo(mx, ay); lineTo(mx, by); lineTo(bx, by) }
                drawPath(
                    path, if (v == true) Bb.High else Bb.Low,
                    style = Stroke(3.dp.toPx(), pathEffect = if (v == null) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null),
                )
            }
            // Inputs
            spec.inputs.forEachIndexed { i, name ->
                val y = rowY(i, spec.inputs.size).px()
                drawCircle(if (sig[name] == true) Bb.High else Bb.Low, 8.dp.toPx(), Offset(colX(0).px(), y))
                drawText(measurer.measure(name, label), topLeft = Offset(colX(0).px() - 22.dp.toPx(), y - 30.dp.toPx()))
            }
            // Socket input wires
            for (def in spec.sockets) {
                val (x, y) = socketPos.getValue(def.id)
                val hh = socketHeight(def.id)
                def.inputs.forEachIndexed { k, ref ->
                    wire(source(ref), x - sockW / 2, y - hh / 2 + hh * (k + 1) / (def.inputs.size + 1), sig[ref])
                }
            }
            // Output LEDs
            spec.outputs.forEachIndexed { i, (name, ref) ->
                val ox = colX(cols - 1); val oy = rowY(i, spec.outputs.size)
                wire(source(ref), ox - 12.dp, oy, sig[ref])
                val on = sig[ref] == true
                if (on) drawCircle(Bb.HighGlow, 16.dp.toPx(), Offset(ox.px(), oy.px()))
                drawCircle(if (on) Bb.High else Bb.LedOff, 10.dp.toPx(), Offset(ox.px(), oy.px()))
                drawCircle(Bb.PinOutline, 10.dp.toPx(), Offset(ox.px(), oy.px()), style = Stroke(2.dp.toPx()))
                val m = measurer.measure(name, label)
                drawText(m, topLeft = Offset(ox.px() - m.size.width / 2f, oy.px() + 14.dp.toPx()))
            }
        }

        // Sockets (real buttons on top of the drawing)
        for (def in spec.sockets) {
            val (x, y) = socketPos.getValue(def.id)
            val chip = s.chips[def.id]
            val armed = s.selected != null
            val hh = socketHeight(def.id)
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.offset(x - sockW / 2, y - hh / 2).size(sockW, hh).clip(shape)
                    .background(if (chip != null) Bb.Surface else if (armed) Bb.AccentBg else Bb.SurfaceSunken)
                    .then(
                        if (chip != null) Modifier.border(2.dp, Bb.High, shape)
                        else Modifier.drawBehind {
                            drawRoundRect(
                                if (armed) Bb.Accent else Bb.PinOutline, cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                            )
                        },
                    )
                    .clickable(role = Role.Button, onClickLabel = if (chip != null && !armed) "Empty socket" else "Place chip") { onSocket(def.id) }
                    .semantics { contentDescription = if (chip != null) "Socket ${def.id}: ${chip.name}" else "Empty socket ${def.id}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    chip?.symbol ?: "+", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = if (chip != null) Bb.Text else if (armed) Bb.Accent else Bb.TextLocked,
                )
            }
        }
    }
}
