package app.bitbybit.ui.circuit

import app.bitbybit.sim.CircuitWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The editor's tools on a Hard-mode-like board: A at 0, IN (+5V) at 2, B at 15, LED at 14. */
class EditorTest {
    private val start = Editor.simulate(
        EditorState(
            cols = 5, rows = 4,
            placed = mapOf(
                0 to PlacedPart("SWITCH", "A", fixed = true),
                2 to PlacedPart("POWER", "IN", fixed = true),
                15 to PlacedPart("SWITCH", "B", fixed = true),
                14 to PlacedPart("LED", "OUT", fixed = true),
            ),
        ),
    )
    private fun EditorState.tap(cell: Int) = Editor.simulate(Editor.tapCell(this, cell))
    private fun EditorState.mode(m: BoardTool) = Editor.setMode(this, m)

    @Test fun placeDropsThePickedPart() {
        val s = Editor.pickTool(start, "TRANSISTOR").tap(7)
        assertEquals("Q1", s.placed[7]?.label)
        assertEquals(BoardTool.PLACE, s.mode)
    }

    @Test fun pickingAPartSwitchesBackToPlace() {
        val s = Editor.pickTool(start.mode(BoardTool.ERASE), "TRANSISTOR")
        assertEquals(BoardTool.PLACE, s.mode)
    }

    @Test fun wireToolStartsAWireOnTapAndFinishesOnTheTarget() {
        val placed = Editor.pickTool(start, "TRANSISTOR").tap(7).mode(BoardTool.WIRE)
        val started = placed.tap(2)                          // tap IN: one output, so the wire starts at once
        assertEquals(PendingWire(2, 0), started.pending)
        val asking = started.tap(7)                          // Q1 has two inputs: B and C
        assertEquals(7, asking.pending?.targetCell)
        val wired = Editor.simulate(Editor.chooseTargetPin(asking, 1))
        assertEquals(listOf(CircuitWire(2, 0, 7, 1)), wired.wires)
        assertEquals("Wired IN → Q1.C.", wired.message)
    }

    @Test fun wireToolDoesNotFlipSwitches() {
        val s = start.mode(BoardTool.WIRE).tap(0)
        assertFalse(s.placed.getValue(0).switchOn)
        assertNotNull(s.pending)
    }

    @Test fun wireToolExplainsPartsWithoutOutputs() {
        val s = start.mode(BoardTool.WIRE).tap(14)
        assertNull(s.pending)
        assertTrue(s.message!!.startsWith("OUT has no outputs"))
    }

    @Test fun probeReadsPinsAndStillFlipsSwitches() {
        val s = start.mode(BoardTool.PROBE).tap(0)
        assertTrue(s.placed.getValue(0).switchOn)
        assertEquals("A · out 1", s.message)
        assertEquals("IN · +5V 1", start.mode(BoardTool.PROBE).tap(2).message)
    }

    @Test fun eraseRemovesPlayerPartsAndTheirWiresButNotFixedOnes() {
        var s = Editor.pickTool(start, "TRANSISTOR").tap(7).mode(BoardTool.WIRE)
        s = s.tap(0).tap(7); s = Editor.simulate(Editor.chooseTargetPin(s, 0))   // A → Q1.B
        s = s.mode(BoardTool.ERASE)
        val keptFixed = s.tap(2)
        assertTrue(2 in keptFixed.placed)
        assertEquals("IN is part of this level and can't be removed.", keptFixed.message)
        val erased = s.tap(7)
        assertFalse(7 in erased.placed)
        assertTrue(erased.wires.isEmpty())
    }

    @Test fun dropFromThePalettePlacesWhateverTheTool() {
        val s = start.mode(BoardTool.PROBE)
        val placed = Editor.placeAt(s, 7, "TRANSISTOR")
        assertEquals("Q1", placed.placed[7]?.label)
        assertEquals("Placed Q1.", placed.message)
        assertEquals("That spot is taken. Drop it on an empty cell.", Editor.placeAt(placed, 2, "TRANSISTOR").message)
        assertEquals("No.", Editor.placeAt(s, 8, "TRANSISTOR") { "No." }.message)
    }

    @Test fun movingAPartTakesItsWiresAlong() {
        var s = Editor.placeAt(start, 7, "TRANSISTOR").mode(BoardTool.WIRE)
        s = s.tap(2).tap(7); s = Editor.simulate(Editor.chooseTargetPin(s, 1))   // IN → Q1.C
        val moved = Editor.simulate(Editor.move(s, 7, 12))
        assertFalse(7 in moved.placed)
        assertEquals("Q1", moved.placed[12]?.label)
        assertEquals(listOf(CircuitWire(2, 0, 12, 1)), moved.wires)
        assertTrue(moved.sim!!.input(12, 1))                                     // still powered after the move
    }

    @Test fun fixedPartsDontMoveAndTakenCellsAreRefused() {
        val s = Editor.placeAt(start, 7, "TRANSISTOR")
        assertEquals("IN is part of this level and stays where it is.", Editor.move(s, 2, 8).message)
        assertTrue(2 in Editor.move(s, 2, 8).placed)
        assertEquals("That spot is taken. Drop Q1 on an empty cell.", Editor.move(s, 7, 14).message)
    }

    @Test fun draggingOffTheBoardRemovesAPartButNotAFixedOne() {
        val s = Editor.placeAt(start, 7, "TRANSISTOR")
        assertFalse(7 in Editor.dropOff(s, 7).placed)
        assertTrue(0 in Editor.dropOff(s, 0).placed)
    }

    @Test fun poweredTransistorConductsOnTheBoard() {
        var s = Editor.pickTool(start, "TRANSISTOR").tap(7).mode(BoardTool.WIRE)
        s = s.tap(2).tap(7); s = Editor.simulate(Editor.chooseTargetPin(s, 1))   // IN → Q1.C
        s = s.tap(0).tap(7); s = Editor.simulate(Editor.chooseTargetPin(s, 0))   // A → Q1.B
        s = s.tap(7).tap(14)                                                     // Q1 → OUT
        assertFalse(s.sim!!.input(14, 0))                                        // A is off
        s = s.mode(BoardTool.PLACE).tap(0)                                       // flip A on
        assertTrue(s.sim!!.input(14, 0))
    }
}
