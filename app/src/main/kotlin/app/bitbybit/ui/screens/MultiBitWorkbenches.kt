package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.data.LevelsFile
import app.bitbybit.sim.Adder4Board
import app.bitbybit.sim.ChipId
import app.bitbybit.sim.SubtractorBoard
import app.bitbybit.sim.TestResult
import app.bitbybit.sim.bit
import app.bitbybit.sim.toBin
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.Card
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
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

// Levels 8 (4-bit adder) and 9 (subtractor), ported from design/Adder4.dc.html and design/Subtractor.dc.html.
// Bits are laid out right to left like written addition: bit 3 (×8) on the left, bit 0 (×1) on the right.

private val BITS = listOf(3, 2, 1, 0)

// ---------------------------------------------------------------- shared pieces

/** A number in decimal with its binary underneath; [color] tints the result. */
@Composable
private fun NumberCell(dec: String, bin: String, color: Color = Bb.Text, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dec, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = color)
        Text(bin, fontFamily = BbFonts.Mono, fontSize = 12.sp, color = Bb.TextMuted)
    }
}

@Composable
private fun BitToggle(label: String, on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) Bb.HighBg else Bb.Surface)
            .border(2.dp, if (on) Bb.High else Bb.Border, RoundedCornerShape(10.dp))
            .toggleable(on, role = Role.Switch) { onToggle() }
            .semantics { contentDescription = label; stateDescription = bitText(on) },
        contentAlignment = Alignment.Center,
    ) { Text(bitText(on), fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Bb.Text) }
}

/** A chip socket: dashed when empty (amber when a chip is picked), green outline when filled. */
@Composable
private fun SocketButton(label: String, chip: ChipId?, armed: Boolean, height: Int = 52, onTap: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().height(height.dp).clip(shape)
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
            .clickable(role = Role.Button) { onTap() }
            .semantics { contentDescription = if (chip != null) "$label: ${chip.name}" else "Empty $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(chip?.symbol ?: "+", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            color = if (chip != null) Bb.Text else if (armed) Bb.Accent else Bb.TextLocked)
    }
}

@Composable
private fun LedDot(on: Boolean?, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (on == true) Bb.High else Bb.LedOff)
                .border(2.dp, Bb.PinOutline, CircleShape)
                .semantics { contentDescription = "$label ${if (on == null) "unpowered" else bitText(on)}" },
        )
        Text(label, fontFamily = BbFonts.Mono, fontSize = 11.sp, color = Bb.TextSecondary)
    }
}

@Composable
private fun TestCard(title: String, detail: String, detailColor: Color, ready: Boolean, onRun: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = BbType.Title.copy(fontSize = 13.sp))
                Text(detail, style = BbType.Caption.copy(color = detailColor))
            }
            if (ready) SmallAction("Test all 256", accent = true, onClick = onRun)
            else SmallAction("Test all 256", onClick = {})
        }
    }
}

private fun testDetail(t: TestResult?, ready: Boolean, idle: String, pass: String): Pair<String, Color> = when {
    !ready -> "Fill every socket first" to Bb.TextMuted
    t == null -> idle to Bb.TextMuted
    t.allPass -> pass to Bb.High
    else -> (t.firstFailure ?: "Some sums are wrong") to Bb.ErrorText
}

// ---------------------------------------------------------------- Level 8 · 4-bit adder

data class Adder4UiState(
    val a: Int = 5, val b: Int = 3,
    /** Indexed by bit (0 = ×1). Starts with bits 0–1 built and the bit 1 → 2 carry missing, as in the design. */
    val socks: List<ChipId?> = listOf(ChipId.FA, ChipId.FA, null, null),
    /** links[k] connects the carry from bit k to bit k+1. */
    val links: List<Boolean> = listOf(true, false, false),
    val selected: ChipId? = null,
    val test: TestResult? = null,
) {
    val board get() = Adder4Board(socks, links)
}

class Adder4ViewModel : ViewModel() {
    private val _s = MutableStateFlow(Adder4UiState())
    val state: StateFlow<Adder4UiState> = _s.asStateFlow()
    fun pick(id: String?) = _s.update { s -> val c = id?.let { ChipId.valueOf(it) }; s.copy(selected = c?.takeIf { it != s.selected }) }
    fun tapSocket(bit: Int) = _s.update { s ->
        val socks = s.socks.toMutableList()
        when {
            s.selected != null -> { socks[bit] = s.selected; s.copy(socks = socks, selected = null, test = null) }
            socks[bit] != null -> { socks[bit] = null; s.copy(socks = socks, test = null) }
            else -> s
        }
    }
    fun toggleLink(k: Int) = _s.update { s -> s.copy(links = s.links.toMutableList().also { it[k] = !it[k] }, test = null) }
    fun flipA(bit: Int) = _s.update { it.copy(a = it.a xor (1 shl bit)) }
    fun flipB(bit: Int) = _s.update { it.copy(b = it.b xor (1 shl bit)) }
    fun runAll() = _s.update { if (it.board.sockets.any { c -> c == null }) it else it.copy(test = it.board.test()) }
}

@Composable
fun Adder4Workbench(data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit, vm: Adder4ViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val board = s.board
    val ready = s.socks.all { it != null }
    val sum = board.eval(s.a, s.b)
    val cols = board.columns(s.a, s.b)
    val palette = remember(data) { trayItems(data, listOf(ChipId.FA, ChipId.HA, ChipId.OR)) }
    val allPass = s.test?.allPass == true
    val (detail, detailColor) = testDetail(s.test, ready, "Every A + B from 0 + 0 to 15 + 15",
        if (s.socks[0] == ChipId.HA) "Perfect. Bit 0 has no carry-in, so a half adder is enough there." else "Perfect. Carries ripple right to left.")

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level 8 · Workbench", "Build a 4-bit adder", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    NumberCell("${s.a}", s.a.toBin(4)); Text("+", fontSize = 24.sp, color = Bb.TextMuted)
                    NumberCell("${s.b}", s.b.toBin(4)); Text("=", fontSize = 24.sp, color = Bb.TextMuted)
                    NumberCell(sum?.toString() ?: "?", sum?.toBin(5) ?: "?????",
                        color = when { sum == null -> Bb.TextMuted; sum == s.a + s.b -> Bb.High; else -> Bb.Error })
                }
            }
            Text("Fill each column with a chip and connect the carries between columns.", style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp))
            // Bit columns, ×8 on the left
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                LedDot(cols[3].carry, "Cout")
                BITS.forEach { k ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("×${1 shl k}", fontFamily = BbFonts.Mono, fontSize = 11.sp, color = Bb.TextMuted)
                        BitToggle("A bit $k", s.a.bit(k)) { vm.flipA(k) }
                        BitToggle("B bit $k", s.b.bit(k)) { vm.flipB(k) }
                        SocketButton("Bit $k socket", s.socks[k], s.selected != null) { vm.tapSocket(k) }
                        LedDot(cols[k].sum, "S$k")
                    }
                }
            }
            // Carry links
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 1, 0).forEach { k ->
                    val on = s.links[k]
                    val carry = on && cols[k].carry == true
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Bb.HighBg else Bb.AccentBg)
                            .border(2.dp, if (on) Bb.High else Bb.Accent, RoundedCornerShape(10.dp))
                            .toggleable(on, role = Role.Switch) { vm.toggleLink(k) }
                            .semantics { contentDescription = "Carry link bit $k to bit ${k + 1}"; stateDescription = if (on) "connected" else "not connected" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("carry ${k}→${k + 1}" + if (carry) " · 1" else "", fontFamily = BbFonts.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Bb.High else Bb.Accent)
                    }
                }
            }
            ComponentPalette(palette, s.selected?.name, vm::pick, showFilters = false, caption = if (s.selected != null) "Now tap a socket" else "Tap a chip, then a socket")
            Box(Modifier.padding(horizontal = 16.dp)) {
                TestCard(s.test?.let { "${it.passed}/256 sums correct" } ?: "Test suite", detail, detailColor, ready, vm::runAll)
            }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Unlock the 4-bit ADD chip", ButtonTone.Success, onClick = onSolved)
                !ready -> PrimaryButton("Place a chip in every column", ButtonTone.Disabled)
                s.test != null -> PrimaryButton("Fix the circuit and test again", ButtonTone.Error)
                else -> PrimaryButton("Run the test suite to finish", ButtonTone.Disabled)
            }
        }
    }
}

// ---------------------------------------------------------------- Level 9 · subtractor

data class SubtractorUiState(
    val a: Int = 7, val b: Int = 5,
    /** Chips on the B lines, indexed by bit. Starts with one NOT and Cin = 0, as in the design. */
    val socks: List<ChipId?> = listOf(null, null, null, ChipId.NOT),
    val cin: Boolean = false,
    val signed: Boolean = false,
    val selected: ChipId? = null,
    val test: TestResult? = null,
) {
    val board get() = SubtractorBoard(socks, cin)
}

class SubtractorViewModel : ViewModel() {
    private val _s = MutableStateFlow(SubtractorUiState())
    val state: StateFlow<SubtractorUiState> = _s.asStateFlow()
    fun pick(id: String?) = _s.update { s -> val c = id?.let { ChipId.valueOf(it) }; s.copy(selected = c?.takeIf { it != s.selected }) }
    fun tapSocket(bit: Int) = _s.update { s ->
        val socks = s.socks.toMutableList()
        when {
            s.selected != null -> { socks[bit] = s.selected; s.copy(socks = socks, selected = null, test = null) }
            socks[bit] != null -> { socks[bit] = null; s.copy(socks = socks, test = null) }
            else -> s
        }
    }
    fun toggleCin() = _s.update { it.copy(cin = !it.cin, test = null) }
    fun toggleSigned() = _s.update { it.copy(signed = !it.signed) }
    fun flipA(bit: Int) = _s.update { it.copy(a = it.a xor (1 shl bit)) }
    fun flipB(bit: Int) = _s.update { it.copy(b = it.b xor (1 shl bit)) }
    fun runAll() = _s.update { if (it.socks.any { c -> c == null }) it else it.copy(test = it.board.test()) }
}

@Composable
fun SubtractorWorkbench(data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit, vm: SubtractorViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val out = s.board.eval(s.a, s.b)
    val ready = s.socks.all { it != null }
    val want = (s.a - s.b) and 15
    fun shown(n: Int) = if (s.signed) SubtractorBoard.signed4(n).let { if (it < 0) "−${-it}" else "$it" } else "$n"
    val palette = remember(data) { trayItems(data, listOf(ChipId.NOT, ChipId.XOR, ChipId.AND)) }
    val allPass = s.test?.allPass == true
    val (detail, detailColor) = testDetail(s.test, ready, "Every A − B from 0 − 0 to 15 − 15", "Perfect: flip every bit of B, add 1, then add.")

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level 9 · Workbench", "Subtract with an adder", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    NumberCell(shown(s.a), s.a.toBin(4)); Text("−", fontSize = 24.sp, color = Bb.TextMuted)
                    NumberCell(shown(s.b), s.b.toBin(4)); Text("=", fontSize = 24.sp, color = Bb.TextMuted)
                    NumberCell(out?.let { shown(it.result) } ?: "?", out?.result?.toBin(4) ?: "????",
                        color = when { out == null -> Bb.TextMuted; out.result == want -> Bb.High; else -> Bb.Error })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.signed) "Signed: the top bit counts as −8, so 1110 = −2." else "Unsigned: Cout = 0 means A was smaller and the result wrapped around.",
                        style = BbType.Caption, modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (s.signed) Bb.AccentBg else Bb.SurfaceSunken)
                            .border(2.dp, if (s.signed) Bb.Accent else Bb.Border, RoundedCornerShape(10.dp))
                            .toggleable(s.signed, role = Role.Switch) { vm.toggleSigned() }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("± Signed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (s.signed) Bb.Accent else Bb.TextSecondary) }
                }
            }
            Text("A − B = A + (every bit of B flipped) + 1. Put a chip on each B line, and set Cin.", style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BITS.forEach { k ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (s.signed && k == 3) "−8" else "${1 shl k}", fontFamily = BbFonts.Mono, fontSize = 11.sp,
                            color = if (s.signed && k == 3) Bb.Accent else Bb.TextMuted)
                        BitToggle("A bit $k", s.a.bit(k)) { vm.flipA(k) }
                        BitToggle("B bit $k", s.b.bit(k)) { vm.flipB(k) }
                        SocketButton("B line $k socket", s.socks[k], s.selected != null, height = 44) { vm.tapSocket(k) }
                    }
                }
            }
            // The fixed ADD block, with Cin on the right and Cout on the left
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot(out?.cout, "Cout")
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(Bb.Surface).border(2.dp, Bb.High, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("ADD · 4-bit · your chip", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Bb.Text) }
                Box(
                    Modifier.size(56.dp, 52.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (s.cin) Bb.HighBg else Bb.AccentBg)
                        .border(2.dp, if (s.cin) Bb.High else Bb.Accent, RoundedCornerShape(10.dp))
                        .toggleable(s.cin, role = Role.Switch) { vm.toggleCin() }
                        .semantics { contentDescription = "Carry-in"; stateDescription = bitText(s.cin) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cin", fontSize = 10.sp, color = Bb.TextMuted)
                        Text(bitText(s.cin), fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Bb.Text)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BITS.forEach { k ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { LedDot(out?.result?.bit(k), "R$k") }
                }
            }
            ComponentPalette(palette, s.selected?.name, vm::pick, showFilters = false, caption = if (s.selected != null) "Now tap a B-line socket" else "Tap a chip, then a socket")
            Box(Modifier.padding(horizontal = 16.dp)) {
                TestCard(s.test?.let { "${it.passed}/256 differences correct" } ?: "Test suite", detail, detailColor, ready, vm::runAll)
            }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton(if (s.board.usesXor) "XOR + a switch = add OR subtract!" else "Unlock the SUB chip", ButtonTone.Success, onClick = onSolved)
                !ready -> PrimaryButton("Put a chip on every B line", ButtonTone.Disabled)
                s.test != null -> PrimaryButton("Fix the circuit and test again", ButtonTone.Error)
                else -> PrimaryButton("Run the test suite to finish", ButtonTone.Disabled)
            }
        }
    }
}

/** Palette tiles for the given chips; the level hands them to you, so they're always available. */
private fun trayItems(data: LevelsFile, chips: List<ChipId>) =
    buildPalette(data, emptySet()).associateBy { it.id }.let { all -> chips.mapNotNull { all[it.name]?.copy(available = true) } }
