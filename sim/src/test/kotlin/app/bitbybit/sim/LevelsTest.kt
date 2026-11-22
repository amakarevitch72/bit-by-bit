package app.bitbybit.sim

import app.bitbybit.sim.ChipId.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** For every designed level: the solution passes, alternatives pass, the start state fails. */
class LevelsTest {

    @Test fun gatesPropagateNull() {
        assertNull(Gates.eval(AND, null, true))
        assertNull(Gates.eval(OR, true, null))
        assertEquals(false, Gates.eval(NOT, true))
        assertEquals(true, Gates.eval(NAND, true, false))
        assertEquals(true, Gates.eval(NOR, false, false))
        assertEquals(true, Gates.eval(XOR, true, false))
    }

    @Test fun level1_andFromStackedTransistors() {
        assertTrue(TransistorPairBoard(Wiring.SERIES).test(TransistorPairBoard.AND_TARGET).allPass)
        assertEquals(false, TransistorPairBoard(Wiring.SERIES).eval(true, false).out)
        assertEquals(true, TransistorPairBoard(Wiring.SERIES).eval(true, false).q1Out) // current gets past Q1 only
        assertFalse(TransistorPairBoard(Wiring.PARALLEL).test(TransistorPairBoard.AND_TARGET).allPass)
    }

    @Test fun level2_orFromParallelTransistors() {
        assertTrue(TransistorPairBoard(Wiring.PARALLEL).test(TransistorPairBoard.OR_TARGET).allPass)
        assertTrue(TransistorPairBoard(null).test(TransistorPairBoard.OR_TARGET).incomplete) // start: not wired
        val series = TransistorPairBoard(Wiring.SERIES).test(TransistorPairBoard.OR_TARGET)
        assertFalse(series.allPass)
        assertEquals(2, series.passed) // 01 and 10 fail: series is AND
        assertEquals("0 1 gave 0, want 1", series.firstFailure)
        assertEquals(true, TransistorPairBoard(Wiring.PARALLEL).eval(false, true).out)
    }

    @Test fun level6_halfAdder() {
        assertTrue(HalfAdderBoard(XOR, AND).test().allPass)
        assertTrue(HalfAdderBoard(XOR, null).test().incomplete) // start state
        val wrong = HalfAdderBoard(OR, AND).test()
        assertFalse(wrong.allPass)
        assertEquals(3, wrong.passed) // only 1 + 1 differs
        assertNotNull(wrong.firstFailure)
    }

    @Test fun level7_fullAdder() {
        assertTrue(FullAdderBoard(HA, HA, OR).test().allPass)
        assertTrue(FullAdderBoard(HA, HA, XOR).test().allPass) // bonus answer
        assertTrue(FullAdderBoard(HA, null, null).test().incomplete) // start state
        assertFalse(FullAdderBoard(HA, HA, AND).test().allPass)
        assertNull(FullAdderBoard(HA, null, OR).eval(true, true, false).s) // unpowered downstream
    }

    @Test fun level8_adder4() {
        val solved = Adder4Board(listOf(FA, FA, FA, FA), listOf(true, true, true))
        assertTrue(solved.test().allPass)
        assertEquals(30, solved.eval(15, 15))
        assertTrue(Adder4Board(listOf(HA, FA, FA, FA), listOf(true, true, true)).test().allPass) // bonus
        assertTrue(Adder4Board(listOf(FA, FA, null, null), listOf(true, false, false)).test().incomplete) // start
        val missingLink = Adder4Board(listOf(FA, FA, FA, FA), listOf(true, false, true)).test()
        assertFalse(missingLink.allPass)
        assertNotNull(missingLink.firstFailure)
        assertFalse(Adder4Board(listOf(FA, HA, FA, FA), listOf(true, true, true)).test().allPass)
    }

    @Test fun level9_subtractor() {
        assertTrue(SubtractorBoard(listOf(NOT, NOT, NOT, NOT), cin = true).test().allPass)
        assertTrue(SubtractorBoard(listOf(XOR, XOR, NOT, XOR), cin = true).test().allPass) // bonus
        assertTrue(SubtractorBoard(listOf(null, null, null, NOT), cin = false).test().incomplete) // start
        val offByOne = SubtractorBoard(listOf(NOT, NOT, NOT, NOT), cin = false).test()
        assertFalse(offByOne.allPass)
        assertTrue(offByOne.firstFailure!!.endsWith("(off by one?)"))
        assertEquals(-2, SubtractorBoard.signed4(0b1110))
        val r = SubtractorBoard(listOf(NOT, NOT, NOT, NOT), cin = true).eval(3, 5)!!
        assertEquals(14, r.result)
        assertFalse(r.cout) // borrow
    }

    @Test fun level15_srLatch() {
        assertTrue(SrLatchBoard(NOR, NOR, loop1 = true, loop2 = true).test().allPass)
        assertTrue(SrLatchBoard(NOR, null, loop1 = true, loop2 = false).test().incomplete) // start
        assertFalse(SrLatchBoard(NOR, NOR, loop1 = false, loop2 = false).test().allPass) // forgets
        assertFalse(SrLatchBoard(OR, OR, loop1 = true, loop2 = true).test().allPass)
    }

    @Test fun level15_forbiddenStateAndRace() {
        val latch = SrLatchBoard(NOR, NOR, loop1 = true, loop2 = true)
        val both = latch.settle(s = true, r = true, prev = SrLatchBoard.State(q = true, qBar = false))
        assertEquals(false, both.q)
        assertEquals(false, both.qBar)
        val released = latch.settle(s = false, r = false, prev = both)
        assertTrue(released.oscillating)
    }

    @Test fun level14_calculatorChallenges() {
        assertEquals(Alu8.Display.Number(224), Alu8.compute(125, Alu8.Op.ADD, 99).display)
        assertEquals(Alu8.Display.Number(-58), Alu8.compute(42, Alu8.Op.SUB, 100).display)
        assertFalse(Alu8.compute(42, Alu8.Op.SUB, 100).cout)
        assertEquals(Alu8.Display.Overflow, Alu8.compute(200, Alu8.Op.ADD, 100).display)
    }

    @Test fun calculatorKeys() {
        var c = CalculatorState()
        c = c.digit(1).digit(2).digit(5).operator(Alu8.Op.ADD).digit(9).digit(9).equals()
        assertEquals(Alu8.Display.Number(224), c.shown)
        c = c.digit(2).digit(5).digit(6) // 256 rejected
        assertEquals(Alu8.Display.Number(25), c.shown)
        assertEquals(listOf(' ', ' ', 'O', 'F'), SevenSegment.places(Alu8.Display.Overflow))
        assertEquals(listOf('-', ' ', '5', '8'), SevenSegment.places(Alu8.Display.Number(-58)))
    }
}
