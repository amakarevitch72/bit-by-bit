package app.bitbybit.sim

/**
 * A signal on a wire: true = HIGH (1), false = LOW (0), null = unpowered
 * (something upstream is missing). Null propagates through every chip.
 */
typealias Sig = Boolean?

/** Every chip the player can own. Ids match `chips` in levels.json. */
enum class ChipId(val symbol: String) {
    AND("&"), OR("≥1"), XOR("=1"), NOT("1○"), NAND("&○"), NOR("≥1○"),
    HA("HA"), FA("FA");

    val isGate: Boolean get() = this != HA && this != FA
}

object Gates {
    /** Evaluates a single-output gate. NOT ignores [b]. */
    fun eval(gate: ChipId, a: Sig, b: Sig = false): Sig {
        if (a == null) return null
        if (gate == ChipId.NOT) return !a
        if (b == null) return null
        return when (gate) {
            ChipId.AND -> a && b
            ChipId.OR -> a || b
            ChipId.XOR -> a != b
            ChipId.NAND -> !(a && b)
            ChipId.NOR -> !(a || b)
            else -> error("$gate is not a gate")
        }
    }
}

/** Output of a two-output socket: [top] is the sum-like pin, [bot] the carry-like pin. */
data class TwoOut(val top: Sig, val bot: Sig)

object TwoOutChip {
    /**
     * HA: top = x XOR y, bot = x AND y.
     * A plain gate placed in a two-output socket drives only `top`; `bot` = 0.
     * An empty socket (chip = null) outputs null on both pins.
     */
    fun eval(chip: ChipId?, x: Sig, y: Sig): TwoOut {
        if (chip == null || x == null || y == null) return TwoOut(null, null)
        return when (chip) {
            ChipId.HA -> TwoOut(x != y, x && y)
            ChipId.FA -> error("FA needs three inputs")
            else -> TwoOut(Gates.eval(chip, x, y), false)
        }
    }
}

/** Full adder: s = a ^ b ^ cin, co = ab + cin(a ^ b). */
fun fullAdd(a: Boolean, b: Boolean, cin: Boolean): Pair<Boolean, Boolean> {
    val s = (a != b) != cin
    val co = (a && b) || (cin && (a != b))
    return s to co
}

fun Int.bit(k: Int): Boolean = (this shr k) and 1 == 1

fun Int.toBin(width: Int): String = Integer.toBinaryString(this and ((1 shl width) - 1)).padStart(width, '0')
