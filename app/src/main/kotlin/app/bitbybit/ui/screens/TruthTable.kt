package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.ui.Card
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

/**
 * One row of a want/got truth table. [inputs] like "0 1". [want] / [got] are the output bits as text,
 * e.g. "1" for one LED or "10" for C S of a half adder. [got] null = not tested yet; [now] = the current input row.
 */
data class WantGotRow(val inputs: String, val want: String, val got: String?, val now: Boolean = false) {
    val ok: Boolean? get() = got?.let { it == want }
}

fun bitsText(values: List<Boolean>) = values.joinToString("") { if (it) "1" else "0" }
fun bitText(v: Boolean) = if (v) "1" else "0"

/**
 * The truth table every workbench uses: inputs, the wanted output, and ✓ or a red "got X".
 * [outputs] names the output columns when there are several ("C S"). [action] is an optional header button ("Run test").
 */
@Composable
fun WantGotTable(rows: List<WantGotRow>, hint: String? = null, outputs: String? = null, action: (@Composable () -> Unit)? = null) {
    val pass = rows.count { it.ok == true }
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Truth table" + (outputs?.let { " · A B → $it" } ?: ""), style = BbType.Title.copy(fontSize = 13.sp))
                Text("$pass/${rows.size} correct", style = BbType.Caption)
            }
            action?.invoke()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { r ->
                val ok = r.ok
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                r.now -> Bb.AccentBg
                                ok == false -> Bb.ErrorBg
                                else -> Bb.SurfaceSunken
                            },
                        )
                        .border(1.5.dp, if (r.now) Bb.Accent else Bb.SurfaceSunken, RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp)
                        .semantics {
                            contentDescription = "Inputs ${r.inputs}, want ${r.want}, " +
                                when (ok) { true -> "correct"; false -> "wrong, got ${r.got}"; null -> "not tested" }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(r.inputs, style = BbType.Bit.copy(fontSize = 14.sp))
                    Text("want ${r.want}", style = BbType.Caption)
                    Text(
                        when (ok) { true -> "✓"; false -> "got ${r.got}"; null -> "○" },
                        color = when (ok) { true -> Bb.High; false -> Bb.Error; null -> Bb.PinOutline },
                        fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
        if (hint != null) Text(hint, style = BbType.Caption.copy(color = Bb.ErrorText))
    }
}
