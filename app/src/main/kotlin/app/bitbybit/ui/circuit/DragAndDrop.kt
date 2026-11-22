package app.bitbybit.ui.circuit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import kotlin.math.roundToInt

// Drag and drop for the board. Long-press a palette tile or a placed part, then drag; a normal swipe still scrolls.
// Positions are kept in root coordinates so the palette, the board and the ghost (all separate composables) agree.

/** What is being dragged: a new part from the palette, or a part already on the board. */
sealed interface DragPayload {
    val symbol: String
    data class New(val id: String, override val symbol: String) : DragPayload
    data class Existing(val fromCell: Int, override val symbol: String) : DragPayload
}

/** The board publishes where its cells are, so a root position can be turned into a cell. */
class BoardGeometry(val bounds: Rect, val cellAt: (Offset) -> Int?)

@Stable
class DragState {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    /** Finger position, in root coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set
    var board: BoardGeometry? = null

    val hoverCell: Int? get() = payload?.let { board?.cellAt?.invoke(position) }
    val overBoard: Boolean get() = board?.bounds?.contains(position) == true

    fun start(p: DragPayload, at: Offset) { payload = p; position = at }
    fun moveBy(delta: Offset) { position += delta }
    fun cancel() { payload = null }

    /** Ends the drag and returns what was dropped and where (cell, or null if dropped off the board). */
    fun finish(): Pair<DragPayload, Int?>? {
        val p = payload ?: return null
        val cell = hoverCell
        payload = null
        return p to cell
    }
}

@Composable
fun rememberDragState() = remember { DragState() }

/**
 * Makes a composable draggable after a long press. [payload] is asked at the moment the drag starts
 * (null = not draggable now). [onDrop] gets the payload and the target cell (null = dropped off the board).
 */
@Composable
fun Modifier.dragSource(drag: DragState, payload: () -> DragPayload?, onDrop: (DragPayload, Int?) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val p = payload() ?: return@detectDragGesturesAfterLongPress
                    val origin = coords?.positionInRoot() ?: Offset.Zero
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag.start(p, origin + local)
                },
                onDrag = { change, amount -> if (drag.payload != null) { change.consume(); drag.moveBy(amount) } },
                onDragEnd = { drag.finish()?.let { (p, cell) -> onDrop(p, cell) } },
                onDragCancel = { drag.cancel() },
            )
        }
}

/** Records the board's position and cell grid in [drag], so drops land on the right cell. */
fun Modifier.dropTarget(drag: DragState, cellAtLocal: (Offset) -> Int?): Modifier = onGloballyPositioned { c ->
    val bounds = c.boundsInRoot()
    drag.board = BoardGeometry(bounds) { root -> if (bounds.contains(root)) cellAtLocal(root - bounds.topLeft) else null }
}

/**
 * The part following your finger. Put it last in the screen's root Box so it draws over everything.
 * Green outline over a free cell, red over a taken one, grey off the board (drop = cancel, or remove for a placed part).
 */
@Composable
fun DragGhost(drag: DragState, isFree: (Int) -> Boolean) {
    val p = drag.payload ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cell = drag.hoverCell
    val (edge, hint) = when {
        cell != null && isFree(cell) -> Bb.High to "Drop here"
        cell != null -> Bb.Error to "Taken"
        p is DragPayload.Existing -> Bb.ErrorText to "Drop to remove"
        else -> Bb.PinOutline to "Drop on the board"
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val local = drag.position - origin
        Column(
            Modifier.offset { IntOffset((local.x - 36.dp.toPx()).roundToInt(), (local.y - 72.dp.toPx()).roundToInt()) }
                .shadow(10.dp, RoundedCornerShape(12.dp))
                .background(Bb.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, edge, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(p.symbol, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Bb.Text)
            Text(hint, fontSize = 10.sp, color = edge)
        }
    }
}
