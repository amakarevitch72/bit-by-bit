package app.bitbybit.sim

import app.bitbybit.sim.PartKind.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HardModeTest {
    // Board: A = cell 0, B = cell 15, LED = cell 14, parts placed at 6 and 11.
    private val A = 0; private val B = 15; private val OUT = 14; private val Q1 = 6; private val Q2 = 11
    private val terminals = listOf(CircuitPart(A, SWITCH), CircuitPart(B, SWITCH), CircuitPart(OUT, LED))
    private val twoQ = terminals + listOf(CircuitPart(Q1, TRANSISTOR), CircuitPart(Q2, TRANSISTOR))
    private fun w(from: Int, to: Int, toPin: Int = 0, fromPin: Int = 0) = CircuitWire(from, fromPin, to, toPin)

    private val andSpec = HardSpec(listOf(A, B), listOf(OUT), mapOf(TRANSISTOR to 2)) { listOf(it[0] && it[1]) }
    private val orSpec = HardSpec(listOf(A, B), listOf(OUT), mapOf(TRANSISTOR to 2)) { listOf(it[0] || it[1]) }

    private fun allPass(spec: HardSpec, parts: List<CircuitPart>, wires: List<CircuitWire>): Boolean {
        val rows = HardMode.run(spec, parts, wires) ?: return false
        return rows.all { (k, v) -> v == HardMode.want(spec, k) }
    }

    private val series = listOf(w(A, Q1), w(B, Q2), w(Q1, Q2, 1), w(Q2, OUT))
    private val parallel = listOf(w(A, Q1), w(B, Q2), w(Q1, OUT), w(Q2, OUT))

    @Test fun seriesBuildPassesAnd() = assertTrue(allPass(andSpec, twoQ, series))
    @Test fun parallelBuildPassesOr() = assertTrue(allPass(orSpec, twoQ, parallel))

    @Test fun wrongBuildRunsButFails() {
        val rows = HardMode.run(orSpec, twoQ, series)
        assertNotNull(rows)
        assertEquals(listOf(false), rows["0 1"])               // series = AND, so 0 1 stays dark
        assertEquals(2, rows.count { (k, v) -> v == HardMode.want(orSpec, k) })
    }

    @Test fun missingTransistorsAreReported() {
        assertEquals("Place 2 more transistors first.", HardMode.problem(andSpec, terminals, emptyList()))
        assertEquals("Place 1 more transistor first.", HardMode.problem(andSpec, terminals + CircuitPart(Q1, TRANSISTOR), emptyList()))
    }

    @Test fun switchesStraightToTheLedAreRejected() {
        val shortcut = listOf(w(A, OUT), w(B, OUT)) // would pass OR without any transistor
        assertNull(HardMode.run(orSpec, twoQ, shortcut))
        assertNotNull(HardMode.problem(orSpec, twoQ, shortcut))
    }

    @Test fun switchIntoACollectorIsRejected() {
        val viaCollector = listOf(w(A, Q1), w(B, Q1, 1), w(Q1, OUT))
        assertEquals("Switches drive a transistor's base (B), not its collector or the LED.", HardMode.problem(andSpec, twoQ, viaCollector))
    }

    @Test fun ledWithNothingConnectedIsReported() =
        assertEquals("Connect something to the LED.", HardMode.problem(andSpec, twoQ, listOf(w(A, Q1), w(B, Q2))))

    // ---- Level 6: half adder from chips, two outputs (C at cell 9, S at cell 14) ----
    private val C = 9; private val S = 14; private val G1 = 6; private val G2 = 11
    private val haSpec = HardSpec(listOf(A, B), listOf(C, S), required = emptyMap()) { listOf(it[0] && it[1], it[0] != it[1]) }
    private fun haParts(g1: PartKind, g2: PartKind) = listOf(
        CircuitPart(A, SWITCH), CircuitPart(B, SWITCH), CircuitPart(C, LED), CircuitPart(S, LED),
        CircuitPart(G1, g1), CircuitPart(G2, g2),
    )
    // G1 → C, G2 → S; both gates read A and B.
    private val haWires = listOf(w(A, G1, 0), w(B, G1, 1), w(A, G2, 0), w(B, G2, 1), w(G1, C), w(G2, S))

    @Test fun halfAdderFromAndAndXorPasses() {
        assertTrue(allPass(haSpec, haParts(AND, XOR), haWires))
        assertEquals(listOf(true, false), HardMode.run(haSpec, haParts(AND, XOR), haWires)!!["1 1"]) // 1 + 1 = 10
    }

    @Test fun halfAdderWithOrForTheSumFailsOnlyOnePlusOne() {
        val rows = HardMode.run(haSpec, haParts(AND, OR), haWires)!!
        assertFalse(rows.getValue("1 1") == HardMode.want(haSpec, "1 1"))
        assertEquals(3, rows.count { (k, v) -> v == HardMode.want(haSpec, k) })
    }

    @Test fun halfAdderNeedsBothLedsConnected() =
        assertEquals("Connect something to every LED.", HardMode.problem(haSpec, haParts(AND, XOR), haWires.dropLast(1)))

    @Test fun switchStraightToAnLedIsRejectedWithSeveralLeds() =
        assertEquals("Drive every LED from a part, not straight from a switch.", HardMode.problem(haSpec, haParts(AND, XOR), haWires + w(A, S)))

    @Test fun keysAreInTableOrder() = assertEquals(listOf("0 0", "0 1", "1 0", "1 1"), haSpec.keys)
}
