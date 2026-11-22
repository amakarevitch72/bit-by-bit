package app.bitbybit.sim

/** The 8-bit ALU behind the calculator milestone: two of the player's 4-bit ALUs chained (design/Calculator.dc.html). */
object Alu8 {
    enum class Op(val symbol: String) { ADD("+"), SUB("−") }

    sealed interface Display {
        data class Number(val value: Int) : Display
        data object Overflow : Display
    }

    data class Result(
        val a: Int,
        val b: Int,
        val op: Op,
        /** Raw 8-bit result on the bus. */
        val r: Int,
        val cout: Boolean,
        /** Comparator flag A < B (drives the minus sign). */
        val lessThan: Boolean,
        val display: Display,
    )

    fun compute(a: Int, op: Op, b: Int): Result {
        val a8 = a and 255
        val b8 = b and 255
        val s = when (op) {
            Op.ADD -> a8 + b8
            Op.SUB -> a8 + (b8.inv() and 255) + 1 // two's complement
        }
        val r = s and 255
        val cout = s > 255
        val display = when (op) {
            Op.ADD -> if (cout) Display.Overflow else Display.Number(r)
            Op.SUB -> if (cout) Display.Number(r) else Display.Number(-(256 - r)) // no Cout = borrow
        }
        return Result(a8, b8, op, r, cout, a8 < b8, display)
    }
}

/**
 * Calculator key handling (0–9, + − = C ⌫), entry limited to 0–255.
 * Immutable: every key returns a new state.
 */
data class CalculatorState(
    val current: Int? = 0,
    val accumulator: Int? = null,
    val pendingOp: Alu8.Op? = null,
    val justEvaluated: Boolean = false,
    val fresh: Boolean = true,
    val shown: Alu8.Display = Alu8.Display.Number(0),
    val last: Alu8.Result? = null,
    val lastKey: String? = null,
) {
    fun digit(d: Int): CalculatorState {
        val next = if (fresh || current == null) d else current * 10 + d
        if (next > 255) return this
        val base = if (justEvaluated) copy(accumulator = null, pendingOp = null, last = null, justEvaluated = false) else this
        return base.copy(current = next, shown = Alu8.Display.Number(next), fresh = false, lastKey = d.toString())
    }

    fun operator(op: Alu8.Op): CalculatorState {
        if (accumulator != null && pendingOp != null && fresh) return copy(pendingOp = op, lastKey = op.symbol)
        return if (accumulator != null && pendingOp != null) {
            val res = Alu8.compute(accumulator, pendingOp, current ?: 0)
            copy(accumulator = res.r, pendingOp = op, fresh = true, last = res, shown = res.display, lastKey = op.symbol, justEvaluated = false)
        } else {
            val acc = current ?: when (val s = shown) {
                is Alu8.Display.Number -> s.value and 255 // a negative result continues as its 8-bit pattern
                Alu8.Display.Overflow -> last?.r ?: 0
            }
            copy(accumulator = acc, pendingOp = op, fresh = true, lastKey = op.symbol, justEvaluated = false)
        }
    }

    fun equals(): CalculatorState {
        val acc = accumulator ?: return this
        val op = pendingOp ?: return this
        val res = Alu8.compute(acc, op, current ?: 0)
        return copy(last = res, shown = res.display, accumulator = null, pendingOp = null, current = null, fresh = true, justEvaluated = true, lastKey = "=")
    }

    fun clear(): CalculatorState = CalculatorState(lastKey = "C")

    fun backspace(): CalculatorState {
        if (fresh || current == null) return this
        val next = current / 10
        return copy(current = next, shown = Alu8.Display.Number(next), lastKey = "⌫")
    }
}

/** 7-segment patterns, segments a–g. */
object SevenSegment {
    private val MAP = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcfgd",
        '-' to "g", 'O' to "abcdef", 'F' to "aefg", ' ' to "",
    )

    /** Which of the 7 segments (a..g) are lit for [ch]. */
    fun segments(ch: Char): BooleanArray {
        val on = MAP[ch] ?: ""
        return BooleanArray(7) { i -> on.contains('a' + i) }
    }

    /** The 4 display places: sign + 3 digits, or "  OF". */
    fun places(display: Alu8.Display): List<Char> = when (display) {
        Alu8.Display.Overflow -> listOf(' ', ' ', 'O', 'F')
        is Alu8.Display.Number -> {
            val mag = kotlin.math.abs(display.value).toString().padStart(3, ' ')
            listOf(if (display.value < 0) '-' else ' ') + mag.toList()
        }
    }
}
