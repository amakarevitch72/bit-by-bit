package app.bitbybit.sim

import app.bitbybit.sim.PartKind.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircuitTest {
    private fun sw(id: Int, on: Boolean) = CircuitPart(id, SWITCH, on)
    private fun w(from: Int, to: Int, toPin: Int = 0, fromPin: Int = 0) = CircuitWire(from, fromPin, to, toPin)

    /** Level 1 rebuilt by hand: A → Q1.B, B → Q2.B, Q1.E → Q2.C, Q2.E → LED. */
    private fun seriesAnd(a: Boolean, b: Boolean): Boolean {
        val parts = listOf(sw(1, a), sw(2, b), CircuitPart(3, TRANSISTOR), CircuitPart(4, TRANSISTOR), CircuitPart(5, LED))
        val wires = listOf(w(1, 3, 0), w(2, 4, 0), w(3, 4, 1), w(4, 5))
        return Circuit(parts, wires).simulate().input(5, 0)
    }

    /** Level 2 rebuilt by hand: both emitters into the same LED input (wired OR). */
    private fun parallelOr(a: Boolean, b: Boolean): Boolean {
        val parts = listOf(sw(1, a), sw(2, b), CircuitPart(3, TRANSISTOR), CircuitPart(4, TRANSISTOR), CircuitPart(5, LED))
        val wires = listOf(w(1, 3, 0), w(2, 4, 0), w(3, 5), w(4, 5))
        return Circuit(parts, wires).simulate().input(5, 0)
    }

    @Test fun transistorsInSeriesMakeAnd() {
        for (a in listOf(false, true)) for (b in listOf(false, true)) assertEquals(a && b, seriesAnd(a, b), "$a $b")
    }

    @Test fun transistorsInParallelMakeOr() {
        for (a in listOf(false, true)) for (b in listOf(false, true)) assertEquals(a || b, parallelOr(a, b), "$a $b")
    }

    @Test fun unconnectedInputsReadZeroButCollectorReadsFiveVolts() {
        val r = Circuit(listOf(sw(1, true), CircuitPart(2, TRANSISTOR), CircuitPart(3, AND)), listOf(w(1, 2, 0), w(1, 3, 0))).simulate()
        assertTrue(r.output(2, 0))  // C defaults to +5V
        assertFalse(r.output(3, 0)) // AND.B unconnected = 0
    }

    @Test fun halfAdderChip() {
        val r = Circuit(listOf(sw(1, true), sw(2, true), CircuitPart(3, HA)), listOf(w(1, 3, 0), w(2, 3, 1))).simulate()
        assertFalse(r.output(3, 0)) // S
        assertTrue(r.output(3, 1))  // C
    }

    /** SR latch from two NORs: Q = NOR(R, Q̄), Q̄ = NOR(Q, S). Q must stay 1 after S goes back to 0. */
    @Test fun norLatchRemembers() {
        fun latch(s: Boolean, r: Boolean) = Circuit(
            listOf(sw(1, s), sw(2, r), CircuitPart(3, NOR), CircuitPart(4, NOR)),
            listOf(w(2, 3, 0), w(4, 3, 1), w(3, 4, 0), w(1, 4, 1)),
        )
        var prev = latch(s = false, r = true).simulate().outputs // reset
        assertFalse(prev.getValue(3)[0])
        prev = latch(s = true, r = false).simulate(prev).outputs // set
        assertTrue(prev.getValue(3)[0])
        val held = latch(s = false, r = false).simulate(prev)    // release
        assertTrue(held.output(3, 0))
        assertFalse(held.oscillating)
    }

    @Test fun notFeedingItselfOscillates() {
        val r = Circuit(listOf(CircuitPart(1, NOT)), listOf(w(1, 1))).simulate()
        assertTrue(r.oscillating)
    }

    @Test fun wiresToMissingPartsOrPinsAreIgnored() {
        val r = Circuit(listOf(sw(1, true), CircuitPart(2, LED)), listOf(w(1, 2), w(9, 2), CircuitWire(1, 5, 2, 0))).simulate()
        assertTrue(r.input(2, 0))
    }
}
