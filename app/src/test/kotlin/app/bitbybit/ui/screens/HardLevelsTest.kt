package app.bitbybit.ui.screens

import app.bitbybit.sim.CircuitPart
import app.bitbybit.sim.CircuitWire
import app.bitbybit.sim.HardMode
import app.bitbybit.sim.PartKind
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every Hard level can be solved on its real board layout (fixed cells and palette from HARD_LEVELS). */
class HardLevelsTest {
    private fun solve(n: Int, placed: Map<Int, PartKind>, wires: List<CircuitWire>): Boolean {
        val level = HARD_LEVELS.getValue(n)
        val spec = level.spec
        val fixed = spec.inputs.map { CircuitPart(it, PartKind.SWITCH) } + spec.leds.map { CircuitPart(it, PartKind.LED) } +
            listOfNotNull(spec.power?.let { CircuitPart(it, PartKind.POWER) })
        val parts = fixed + placed.map { (cell, kind) -> CircuitPart(cell, kind) }
        // Stay within the level's palette.
        placed.values.groupingBy { it }.eachCount().forEach { (kind, count) -> assertTrue(count <= (level.palette[kind.name] ?: 0), "$kind × $count") }
        assertNull(HardMode.problem(spec, parts, wires))
        val rows = HardMode.run(spec, parts, wires)!!
        return rows.all { (k, v) -> v == HardMode.want(spec, k) }
    }
    private fun w(from: Int, to: Int, toPin: Int = 0, fromPin: Int = 0) = CircuitWire(from, fromPin, to, toPin)

    @Test fun level4_nandAndNor() {
        // A at 0, B at 15, NAND LED at 9, NOR LED at 14. AND at 6 → NOT at 7 → NAND; OR at 11 → NOT at 12 → NOR.
        val placed = mapOf(6 to PartKind.AND, 7 to PartKind.NOT, 11 to PartKind.OR, 12 to PartKind.NOT)
        val wires = listOf(w(0, 6, 0), w(15, 6, 1), w(6, 7), w(7, 9), w(0, 11, 0), w(15, 11, 1), w(11, 12), w(12, 14))
        assertTrue(solve(4, placed, wires))
    }

    @Test fun level5_xorFromOrNandAnd() {
        // X LED at 14. OR at 6, NAND at 11, AND at 12.
        val placed = mapOf(6 to PartKind.OR, 11 to PartKind.NAND, 12 to PartKind.AND)
        val wires = listOf(w(0, 6, 0), w(15, 6, 1), w(0, 11, 0), w(15, 11, 1), w(6, 12, 0), w(11, 12, 1), w(12, 14))
        assertTrue(solve(5, placed, wires))
    }

    @Test fun level7_fullAdderFromTwoHalfAdders() {
        // A at 0, B at 5, Cin at 15; Cout LED at 9, S LED at 14. HA1 at 6, HA2 at 12, OR at 8.
        val placed = mapOf(6 to PartKind.HA, 12 to PartKind.HA, 8 to PartKind.OR)
        val wires = listOf(
            w(0, 6, 0), w(5, 6, 1),                 // A, B → HA1
            w(6, 12, 0, fromPin = 0), w(15, 12, 1), // HA1.S, Cin → HA2
            w(6, 8, 0, fromPin = 1), w(12, 8, 1, fromPin = 1), // both carries → OR
            w(8, 9), w(12, 14, 0, fromPin = 0),      // OR → Cout, HA2.S → S
        )
        assertTrue(solve(7, placed, wires))
    }

    @Test fun levels1and2StillSolvable() {
        // IN at 2. AND: IN→Q1.C, A→Q1.B, Q1.E→Q2.C, B→Q2.B, Q2.E→OUT.
        assertTrue(solve(1, mapOf(7 to PartKind.TRANSISTOR, 12 to PartKind.TRANSISTOR),
            listOf(w(2, 7, 1), w(0, 7, 0), w(7, 12, 1), w(15, 12, 0), w(12, 14))))
        assertTrue(solve(2, mapOf(7 to PartKind.TRANSISTOR, 8 to PartKind.TRANSISTOR),
            listOf(w(2, 7, 1), w(2, 8, 1), w(0, 7, 0), w(15, 8, 0), w(7, 14), w(8, 14))))
    }
}
