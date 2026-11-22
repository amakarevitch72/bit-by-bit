package app.bitbybit.sim

// Simulations for the designed levels. Each mirrors the reference logic in design/<Mockup>.dc.html.

/**
 * Levels 1 and 2 — two transistors between +5V and the LED.
 * Level 1 fixes the wiring to SERIES: AND (both switches must close), as in design/Workbench.dc.html.
 * Level 2 lets the player choose; PARALLEL is the answer: OR (either switch is enough).
 */
enum class Wiring { SERIES, PARALLEL }

data class TransistorPairBoard(val wiring: Wiring?) {
    /** Wire states for drawing. [q1Out]/[q2Out] = current leaving each transistor's emitter. */
    data class Wires(val q1Out: Sig, val q2Out: Sig, val out: Sig)

    fun eval(a: Boolean, b: Boolean): Wires = when (wiring) {
        Wiring.SERIES -> Wires(q1Out = a, q2Out = a && b, out = a && b)
        Wiring.PARALLEL -> Wires(q1Out = a, q2Out = b, out = a || b)
        null -> Wires(null, null, null)
    }

    /** Tests against [target], e.g. `{ a, b -> a || b }` for the OR level. */
    fun test(target: (Boolean, Boolean) -> Boolean): TestResult {
        if (wiring == null) return TestResult.incomplete(4)
        var pass = 0
        var fail: String? = null
        for (a in listOf(false, true)) for (b in listOf(false, true)) {
            val got = eval(a, b).out
            val want = target(a, b)
            if (got == want) pass++ else if (fail == null) fail = "${a.d} ${b.d} gave ${got.d}, want ${want.d}"
        }
        return TestResult(pass, 4, fail)
    }

    companion object {
        val AND_TARGET: (Boolean, Boolean) -> Boolean = { a, b -> a && b }
        val OR_TARGET: (Boolean, Boolean) -> Boolean = { a, b -> a || b }
    }
}

/** Level 6 — half adder: one socket drives S, one drives C (design/HalfAdder.dc.html). */
data class HalfAdderBoard(val sumSocket: ChipId?, val carrySocket: ChipId?) {
    fun eval(a: Boolean, b: Boolean): Pair<Sig, Sig> {
        val s = sumSocket?.let { Gates.eval(it, a, b) }
        val c = carrySocket?.let { Gates.eval(it, a, b) }
        return s to c
    }

    fun test(): TestResult {
        if (sumSocket == null || carrySocket == null) return TestResult.incomplete(4)
        var pass = 0
        var fail: String? = null
        for (a in listOf(false, true)) for (b in listOf(false, true)) {
            val (s, c) = eval(a, b)
            if (s == (a != b) && c == (a && b)) pass++
            else if (fail == null) fail = "${a.d} ${b.d}: got C S = ${c.d} ${s.d}, want ${(a && b).d} ${(a != b).d}"
        }
        return TestResult(pass, 4, fail)
    }
}

/** Level 7 — full adder from two two-output sockets plus a carry-joining socket (design/FullAdder.dc.html). */
data class FullAdderBoard(val h1: ChipId?, val h2: ChipId?, val joiner: ChipId?) {
    data class Wires(val s1: Sig, val c1: Sig, val s: Sig, val c2: Sig, val cout: Sig)

    fun eval(a: Boolean, b: Boolean, cin: Boolean): Wires {
        val u1 = TwoOutChip.eval(h1, a, b)
        val u2 = TwoOutChip.eval(h2, u1.top, cin)
        val cout = if (joiner == null || u1.bot == null || u2.bot == null) null else Gates.eval(joiner, u1.bot, u2.bot)
        return Wires(u1.top, u1.bot, u2.top, u2.bot, cout)
    }

    fun test(): TestResult {
        if (h1 == null || h2 == null || joiner == null) return TestResult.incomplete(8)
        var pass = 0
        var fail: String? = null
        for (i in 0 until 8) {
            val a = i.bit(2); val b = i.bit(1); val c = i.bit(0)
            val w = eval(a, b, c)
            val (s, co) = fullAdd(a, b, c)
            if (w.s == s && w.cout == co) pass++
            else if (fail == null) fail = "${a.d}${b.d}${c.d} gave ${w.cout.d}${w.s.d}, want ${co.d}${s.d}"
        }
        return TestResult(pass, 8, fail)
    }

    /** True when the player found the alternative XOR carry joiner. */
    val usesXorJoiner: Boolean get() = joiner == ChipId.XOR
}

/**
 * Level 8 — 4-bit ripple adder (design/Adder4.dc.html).
 * [sockets] is indexed by bit (0 = LSB). [links][k] connects the carry from bit k to bit k+1.
 * Bit 0 has carry-in 0. Cout of bit 3 is always wired to the ×16 output.
 */
data class Adder4Board(val sockets: List<ChipId?>, val links: List<Boolean>) {
    init {
        require(sockets.size == 4) { "4 sockets" }
        require(links.size == 3) { "3 links" }
    }

    data class Column(val sum: Sig, val carry: Sig)

    fun columns(a: Int, b: Int): List<Column> {
        val out = ArrayList<Column>(4)
        var prevCo = false
        for (k in 0 until 4) {
            val ak = a.bit(k); val bk = b.bit(k)
            val cin = if (k == 0) false else links[k - 1] && prevCo
            val col = when (sockets[k]) {
                ChipId.FA -> fullAdd(ak, bk, cin).let { Column(it.first, it.second) }
                ChipId.HA -> Column(ak != bk, ak && bk)
                null -> Column(null, null)
                else -> Column(Gates.eval(sockets[k]!!, ak, bk), false)
            }
            out += col
            prevCo = col.carry == true
        }
        return out
    }

    /** The 5-bit result, or null if any socket is empty. */
    fun eval(a: Int, b: Int): Int? {
        if (sockets.any { it == null }) return null
        val cols = columns(a, b)
        var sum = 0
        cols.forEachIndexed { k, c -> if (c.sum == true) sum = sum or (1 shl k) }
        if (cols[3].carry == true) sum = sum or 16
        return sum
    }

    fun test(): TestResult {
        if (sockets.any { it == null }) return TestResult.incomplete(256)
        var pass = 0
        var fail: String? = null
        for (a in 0..15) for (b in 0..15) {
            val got = eval(a, b)!!
            if (got == a + b) pass++ else if (fail == null) fail = "e.g. $a + $b gave $got, want ${a + b}"
        }
        return TestResult(pass, 256, fail)
    }
}

/**
 * Level 9 — subtractor: chips on the four B lines feed a fixed ADD4, plus a Cin switch (design/Subtractor.dc.html).
 * XOR counts as an inverter here because its second pin is tied to 1 (the future SUB control line).
 */
data class SubtractorBoard(val bSockets: List<ChipId?>, val cin: Boolean) {
    init { require(bSockets.size == 4) }

    data class Out(val bPrime: Int, val result: Int, val cout: Boolean)

    fun bPrime(b: Int): Int? {
        var out = 0
        for (k in 0 until 4) {
            val g = bSockets[k] ?: return null
            val v = when (g) {
                ChipId.NOT, ChipId.XOR -> !b.bit(k)
                ChipId.AND -> b.bit(k)
                else -> Gates.eval(g, b.bit(k), true) ?: return null
            }
            if (v) out = out or (1 shl k)
        }
        return out
    }

    fun eval(a: Int, b: Int): Out? {
        val bp = bPrime(b) ?: return null
        val s = a + bp + if (cin) 1 else 0
        return Out(bp, s and 15, s > 15)
    }

    fun test(): TestResult {
        if (bSockets.any { it == null }) return TestResult.incomplete(256)
        var pass = 0
        var fail: String? = null
        for (a in 0..15) for (b in 0..15) {
            val got = eval(a, b)!!.result
            val want = (a - b) and 15
            if (got == want) pass++
            else if (fail == null) fail = "e.g. $a − $b gave $got, want $want" + if (!cin) " (off by one?)" else ""
        }
        return TestResult(pass, 256, fail)
    }

    val usesXor: Boolean get() = bSockets.any { it == ChipId.XOR }

    companion object {
        /** Reads a 4-bit pattern as two's complement: the top bit is worth −8. */
        fun signed4(n: Int): Int = if (n and 8 != 0) (n and 15) - 16 else n and 15
    }
}

private val Boolean.d: String get() = if (this) "1" else "0"
private val Boolean?.d: String get() = when (this) { true -> "1"; false -> "0"; null -> "–" }
