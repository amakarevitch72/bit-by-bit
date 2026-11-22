package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.data.Level
import app.bitbybit.data.LevelsFile
import app.bitbybit.sim.Sig
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

/**
 * Routes a level to its workbench. Levels 1 and 2 share [TransistorWorkbench].
 * TODO: port the other designed levels from design/<Mockup>.dc.html, reusing the pieces in this file.
 */
@Composable
fun WorkbenchScreen(level: Level, data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit) {
    val transistorLevel = TRANSISTOR_LEVELS[level.n]
    when {
        transistorLevel != null -> TransistorWorkbench(transistorLevel, onBack, onSolved)
        level.n == 3 -> NotWorkbench(onBack, onSolved)
        level.n == 6 -> HalfAdderWorkbench(data, onBack, onSolved)
        level.n in SOCKET_LEVELS -> SocketWorkbench(SOCKET_LEVELS.getValue(level.n), data, onBack, onSolved)
        level.n == 8 -> Adder4Workbench(data, onBack, onSolved)
        level.n == 9 -> SubtractorWorkbench(data, onBack, onSolved)
        else -> NotBuiltYet(level, onBack)
    }
}

@Composable
private fun NotBuiltYet(level: Level, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Workbench", level.title, onBack)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Not built yet", style = BbType.H2)
            Text(
                if (level.isDesigned) "Port it from ${level.mockup.joinToString()} — the simulation is ready in :sim."
                else "No design yet. Build it from docs/levels.json and SPEC §4.",
                style = BbType.Body,
            )
        }
    }
}

// ---------------------------------------------------------------- shared drawing pieces

/**
 * NPN transistor symbol in the 350×300 board space. The collector/emitter chain runs vertically at [chainX];
 * [dir] = 1 puts the base pin on the left at [pinX], -1 mirrors it to the right.
 * A null wire state is drawn dashed (unpowered / not connected).
 */
internal fun DrawScope.drawTransistor(
    p: (Float, Float) -> Offset, u: Float, chainX: Float, cy: Float, dir: Float, pinX: Float,
    base: Sig, collector: Sig, emitter: Sig,
) {
    val bar = chainX - 20f * dir
    wire(p, u, pinX + 14f * dir, cy, bar, cy, base)
    drawCircle(if (base == true) Bb.High else Bb.Low, 12 * u, p(pinX, cy))
    drawCircle(Bb.Surface, 26 * u, p(chainX - 10f * dir, cy))
    drawCircle(Bb.PinOutline, 26 * u, p(chainX - 10f * dir, cy), style = Stroke(2 * u))
    drawLine(Bb.Text, p(bar, cy - 16), p(bar, cy + 16), 3 * u)
    wire(p, u, bar, cy - 8, chainX, cy - 24, collector, width = 2.5f)
    wire(p, u, bar, cy + 8, chainX, cy + 24, emitter, width = 2.5f)
}

/** A wire segment: green = 1, gray = 0, dashed gray = null. */
internal fun DrawScope.wire(p: (Float, Float) -> Offset, u: Float, x1: Float, y1: Float, x2: Float, y2: Float, v: Sig, width: Float = 3f) {
    drawLine(
        color = if (v == true) Bb.High else Bb.Low,
        start = p(x1, y1), end = p(x2, y2), strokeWidth = width * u,
        pathEffect = if (v == null) PathEffect.dashPathEffect(floatArrayOf(6 * u, 6 * u)) else null,
    )
}

@Composable
internal fun InputToggle(label: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) Bb.HighBg else Bb.Surface)
            .border(2.dp, if (on) Bb.High else Bb.Border, RoundedCornerShape(14.dp))
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { stateDescription = if (on) "1" else "0" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = BbType.Caption)
        Text(if (on) "1" else "0", style = BbType.Bit.copy(fontSize = 22.sp))
    }
}

@Composable
internal fun OutputBox(on: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) Bb.High else Bb.SurfaceSunken)
            .border(2.dp, if (on) Bb.High else Bb.Border, RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Output ${if (on) 1 else 0}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Output", style = BbType.Caption.copy(color = if (on) Bb.OnHigh else Bb.TextMuted))
        Text(if (on) "1" else "0", fontFamily = BbFonts.Mono, fontSize = 22.sp, color = if (on) Bb.OnHigh else Bb.Text)
    }
}
