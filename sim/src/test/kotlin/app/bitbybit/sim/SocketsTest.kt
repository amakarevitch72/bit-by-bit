package app.bitbybit.sim

import app.bitbybit.sim.ChipId.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Levels 3, 4, 5 and 7: each solution passes, alternatives pass, the start state fails. */
class SocketsTest {
    private fun board(spec: SocketSpec, vararg chips: Pair<String, ChipId?>) = SocketBoard(spec, chips.toMap())

    @Test fun level3_notNeedsTheLedOnTheCollector() {
        assertTrue(InverterBoard(LedSpot.COLLECTOR).test().allPass)
        assertFalse(InverterBoard(LedSpot.EMITTER).test().allPass)       // that's a buffer: OUT = A
        assertTrue(InverterBoard(null).test().incomplete)
        assertEquals(true, InverterBoard(LedSpot.COLLECTOR).out(false))
    }

    @Test fun level4_nandAndNorFromAndOrPlusNot() {
        val spec = SocketLevels.NAND_NOR
        assertTrue(board(spec, "g1" to AND, "n1" to NOT, "g2" to OR, "n2" to NOT).test().allPass)
        assertTrue(board(spec, "g1" to AND).test().incomplete)                                  // start: one chip placed
        assertFalse(board(spec, "g1" to OR, "n1" to NOT, "g2" to AND, "n2" to NOT).test().allPass) // swapped
        assertNull(board(spec, "g1" to AND, "g2" to OR, "n2" to NOT).outputs(listOf(true, true))[0]) // n1 empty: unpowered
    }

    @Test fun level5_xorFromThreeGates() {
        val spec = SocketLevels.XOR
        assertTrue(board(spec, "g1" to OR, "g2" to NAND, "g3" to AND).test().allPass)
        assertTrue(board(spec, "g1" to NAND, "g2" to OR, "g3" to AND).test().allPass)          // order of the first two doesn't matter
        assertFalse(board(spec, "g1" to OR, "g2" to AND, "g3" to AND).test().allPass)
        assertEquals(listOf("0 0", "0 1", "1 0", "1 1"), spec.keys)
    }

    @Test fun level7_fullAdderFromTwoHalfAdders() {
        val spec = SocketLevels.FULL_ADDER
        assertTrue(board(spec, "h1" to HA, "h2" to HA, "g" to OR).test().allPass)
        assertTrue(board(spec, "h1" to HA, "h2" to HA, "g" to XOR).test().allPass)             // both carries are never 1 together
        assertFalse(board(spec, "h1" to HA, "h2" to HA, "g" to AND).test().allPass)
        assertTrue(board(spec, "h1" to HA).test().incomplete)
        assertEquals(8, spec.keys.size)
        assertEquals(listOf(true, true), spec.want("1 1 1"))                                     // 1 + 1 + 1 = 11
    }

    @Test fun depthsLayOutLeftToRight() {
        val spec = SocketLevels.FULL_ADDER
        assertEquals(1, spec.depth("h1")); assertEquals(2, spec.depth("h2")); assertEquals(3, spec.depth("g"))
    }
}
