package app.bitbybit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.data.LevelsFile
import app.bitbybit.ui.Routes
import app.bitbybit.ui.circuit.CircuitBoard
import app.bitbybit.ui.circuit.Editor
import app.bitbybit.ui.circuit.EditorActionBar
import app.bitbybit.ui.circuit.EditorActions
import app.bitbybit.ui.circuit.EditorState
import app.bitbybit.ui.circuit.SmallAction
import app.bitbybit.ui.palette.ComponentPalette
import app.bitbybit.ui.palette.buildPalette
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Free build: every unlocked part, a 6×5 board, wiring and live simulation (logic in ui/circuit/CircuitEditor.kt). */
class SandboxViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditorState(cols = 6, rows = 5))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private fun edit(block: (EditorState) -> EditorState) = _state.update { Editor.simulate(block(it)) }

    fun pickTool(id: String?) = edit { Editor.pickTool(it, id) }
    fun tapCell(cell: Int) = edit { Editor.tapCell(it, cell) }
    val actions = EditorActions(
        startWire = { pin -> edit { Editor.startWire(it, pin) } },
        chooseTargetPin = { pin -> edit { Editor.chooseTargetPin(it, pin) } },
        cancelWire = { edit(Editor::cancelWire) },
        unwire = { edit(Editor::unwireFocused) },
        remove = { edit(Editor::removeFocused) },
        donePlacing = { edit { Editor.pickTool(it, null) } },
    )
    fun clearBoard() = edit(Editor::clear)
}

@Composable
fun SandboxScreen(data: LevelsFile, completed: Set<Int>, onTab: (String) -> Unit, vm: SandboxViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val palette = remember(data, completed) { buildPalette(data, completed) }
    val byId = remember(palette) { palette.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("SANDBOX", style = BbType.Eyebrow)
                Text(
                    if (s.sim?.oscillating == true) "Free build · signals won't settle" else "Free build",
                    style = BbType.Title.copy(color = if (s.sim?.oscillating == true) Bb.Error else Bb.Text),
                )
            }
            if (s.placed.isNotEmpty()) SmallAction("Clear board", onClick = vm::clearBoard)
        }
        CircuitBoard(s, byId, vm::tapCell, Modifier.weight(1f).padding(horizontal = 16.dp))
        EditorActionBar(
            s, vm.actions,
            emptyHint = "Pick a component below, then tap the board.",
            idleHint = "Tap a part to wire it. Tap a switch to flip it.",
        )
        ComponentPalette(palette, s.tool, vm::pickTool)
        BottomTabs(selected = Routes.SANDBOX, onTab = onTab)
    }
}
