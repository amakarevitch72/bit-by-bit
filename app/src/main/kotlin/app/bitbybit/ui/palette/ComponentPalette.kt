package app.bitbybit.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.data.LevelsFile
import app.bitbybit.ui.circuit.DragPayload
import app.bitbybit.ui.circuit.DragState
import app.bitbybit.ui.circuit.dragSource
import app.bitbybit.ui.screens.unlockIds
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

enum class PaletteCategory(val label: String) {
    IO("Inputs & outputs"), PARTS("Parts"), GATES("Gates"), ARITHMETIC("Arithmetic"), CALCULATOR("Calculator"), MEMORY("Memory");

    companion object {
        fun of(id: String): PaletteCategory = when (id) {
            "SWITCH", "LED", "POWER" -> IO
            "TRANSISTOR" -> PARTS
            "AND", "OR", "NOT", "NAND", "NOR", "XOR" -> GATES
            "HA", "FA", "ADD4", "SUB4" -> ARITHMETIC
            "CMP4", "SEG7", "ALU4", "KEYENC" -> CALCULATOR
            else -> MEMORY
        }
    }
}

/** One entry in the palette. [unlockLevel] is the level that unlocks it (null = always available). */
data class PaletteItem(
    val id: String,
    val symbol: String,
    val name: String,
    val category: PaletteCategory,
    val available: Boolean,
    val unlockLevel: Int?,
)

/**
 * Everything the player could place: the switch and LED (always available), the starting transistor,
 * and every chip in levels.json. A chip is available once the level that unlocks it is complete.
 * Order: by category, then by the level that unlocks it.
 */
fun buildPalette(data: LevelsFile, completed: Set<Int>): List<PaletteItem> {
    val unlockedBy = buildMap { data.levels.forEach { l -> l.unlockIds().forEach { put(it, l.n) } } }
    val fixed = listOf(
        PaletteItem("SWITCH", "0|1", "Switch", PaletteCategory.IO, true, null),
        PaletteItem("LED", "●", "LED", PaletteCategory.IO, true, null),
        // Permanent +5V supply. Transistors only conduct when their C pin is wired to power.
        PaletteItem("POWER", "+5V", "Power", PaletteCategory.IO, true, null),
    )
    val chips = data.chips.map { (id, def) ->
        val level = unlockedBy[id]
        PaletteItem(
            id = id,
            symbol = def.symbol,
            name = def.name ?: id,
            category = PaletteCategory.of(id),
            available = level == null || level in completed,
            unlockLevel = level,
        )
    }
    return (fixed + chips).sortedWith(compareBy({ it.category.ordinal }, { it.unlockLevel ?: 0 }))
}

/**
 * Bottom palette: category filter + a scrolling row of component tiles.
 * Available tiles can be selected (amber); locked tiles are dashed and say which level unlocks them.
 */
@Composable
fun ComponentPalette(
    items: List<PaletteItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Category filter row; Hard mode hides it because the level only offers a few parts. */
    showFilters: Boolean = true,
    /** Right-hand caption; defaults to "N of M available". */
    caption: String? = null,
    /** Small line under an available tile, e.g. "2 left" in Hard mode. */
    badge: ((PaletteItem) -> String?)? = null,
    /** Drag and drop onto a board: long-press an available tile and drag it. Null = tap only. */
    drag: DragState? = null,
    onDrop: (DragPayload, Int?) -> Unit = { _, _ -> },
) {
    var filter by rememberSaveable { mutableStateOf<PaletteCategory?>(null) }
    val shown = items.filter { !showFilters || filter == null || it.category == filter }
    val availableCount = items.count { it.available }

    Column(
        modifier.fillMaxWidth().background(Bb.SurfaceSunken).padding(top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("COMPONENTS", style = BbType.Eyebrow, modifier = Modifier.weight(1f))
            Text(caption ?: "$availableCount of ${items.size} available", style = BbType.Caption)
        }

        // Category filter. Categories with nothing unlocked are still listed, so players see what's ahead.
        if (showFilters) LazyRow(
            Modifier.selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val options = listOf<PaletteCategory?>(null) + PaletteCategory.entries
            items(options, key = { it?.name ?: "ALL" }) { cat ->
                val on = filter == cat
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (on) Bb.AccentBg else Bb.Surface)
                        .border(1.5.dp, if (on) Bb.Accent else Bb.Border, RoundedCornerShape(999.dp))
                        .selectable(selected = on, role = Role.Tab) { filter = cat }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cat?.label ?: "All", color = if (on) Bb.Accent else Bb.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyRow(
            Modifier.selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { item ->
                PaletteTile(item, selected = item.id == selectedId, badge = badge?.invoke(item), drag = drag, onDrop = onDrop, onClick = {
                    if (item.available) onSelect(if (item.id == selectedId) null else item.id)
                })
            }
        }
    }
}

@Composable
private fun PaletteTile(
    item: PaletteItem, selected: Boolean, badge: String?,
    drag: DragState?, onDrop: (DragPayload, Int?) -> Unit, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val border = when {
        selected -> Modifier.border(2.dp, Bb.Accent, shape)
        item.available -> Modifier.border(1.dp, Bb.Border, shape)
        else -> Modifier.drawBehind {
            drawRoundRect(
                Bb.Border, cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
            )
        }
    }
    Column(
        Modifier.width(78.dp).height(100.dp).clip(shape)
            .background(if (selected) Bb.AccentBg else if (item.available) Bb.Surface else Color.Transparent)
            .then(border)
            .then(
                if (drag != null && item.available) Modifier.dragSource(drag, payload = { DragPayload.New(item.id, item.symbol) }, onDrop = onDrop)
                else Modifier,
            )
            .selectable(selected = selected, enabled = item.available, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = item.name
                stateDescription = when {
                    selected -> "selected"
                    item.available -> "available"
                    else -> "locked, unlocks in level ${item.unlockLevel}"
                }
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChipGlyph(item.symbol, item.available, Modifier.size(width = 52.dp, height = 36.dp))
        Text(
            item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
            color = if (item.available) Bb.Text else Bb.TextLocked,
        )
        when {
            badge != null -> Text(badge, maxLines = 1, fontSize = 11.sp, color = if (item.available) Bb.Accent else Bb.TextLocked)
            !item.available -> Text("Level ${item.unlockLevel}", maxLines = 1, fontSize = 11.sp, color = Bb.TextLocked)
        }
    }
}

/** A chip drawn as a small box with pins and its IEC symbol. Also used for parts placed on the board. */
@Composable
fun ChipGlyph(symbol: String, lit: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val pin = if (lit) Bb.High else Bb.Low
    Box(
        modifier.drawBehind {
            val w = 6.dp.toPx(); val y1 = size.height * 0.33f; val y2 = size.height * 0.67f
            drawLine(pin, androidx.compose.ui.geometry.Offset(0f, y1), androidx.compose.ui.geometry.Offset(w, y1), 2.dp.toPx())
            drawLine(pin, androidx.compose.ui.geometry.Offset(0f, y2), androidx.compose.ui.geometry.Offset(w, y2), 2.dp.toPx())
            drawLine(pin, androidx.compose.ui.geometry.Offset(size.width - w, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 2.dp.toPx())
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(if (compact) 24.dp else 32.dp).clip(RoundedCornerShape(6.dp))
                .background(Bb.SurfaceSunken)
                .border(2.dp, if (lit) Bb.High else Bb.Low, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (lit) Bb.Text else Bb.TextLocked, maxLines = 1)
        }
    }
}
