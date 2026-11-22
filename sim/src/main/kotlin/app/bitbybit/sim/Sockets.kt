package app.bitbybit.sim

/**
 * A board of chip sockets with fixed wiring: the player only chooses which chip goes in each socket.
 * Used by Normal mode for Levels 4 (NAND & NOR), 5 (XOR) and 7 (full adder).
 *
 * References ("refs") name a signal: an input ("A", "Cin"), a socket's first output ("g1"), or a socket's
 * n-th output ("h1.1" = second output, e.g. a half adder's carry). An empty socket makes everything downstream null.
 */
data class SocketDef(val id: String, val inputs: List<String>)

data class SocketSpec(
    val inputs: List<String>,
    val sockets: List<SocketDef>,
    /** Output name → ref, in the order the analyzer shows them (e.g. Cout then S). */
    val outputs: List<Pair<String, String>>,
    val target: (List<Boolean>) -> List<Boolean>,
) {
    /** Step keys in table order: "0 0", "0 1", … (first input first). */
    val keys: List<String> = (0 until (1 shl inputs.size)).map { combo -> bits(combo).joinToString(" ") { if (it) "1" else "0" } }

    fun bits(combo: Int): List<Boolean> = List(inputs.size) { i -> (combo shr (inputs.size - 1 - i)) and 1 == 1 }
    fun want(key: String): List<Boolean> = target(key.split(" ").map { it == "1" })

    /** How many chips deep a socket sits (inputs are 0), for drawing left-to-right columns. */
    fun depth(id: String): Int {
        val def = sockets.first { it.id == id }
        return 1 + (def.inputs.maxOfOrNull { ref -> refSocket(ref)?.let { depth(it) } ?: 0 } ?: 0)
    }

    fun refSocket(ref: String): String? = ref.substringBefore('.').takeIf { s -> sockets.any { it.id == s } }
}

/** Outputs of any chip for the given inputs (missing inputs read 0). Null if the socket is empty or an input is null. */
fun chipOutputs(chip: ChipId?, ins: List<Sig>): List<Sig>? {
    if (chip == null || ins.any { it == null }) return null
    val i = ins.map { it!! }
    fun at(n: Int) = i.getOrElse(n) { false }
    return when (chip) {
        ChipId.HA -> listOf(at(0) != at(1), at(0) && at(1))
        ChipId.FA -> fullAdd(at(0), at(1), at(2)).let { (s, c) -> listOf(s, c) }
        ChipId.NOT -> listOf(!at(0))
        else -> listOf(Gates.eval(chip, at(0), at(1)))
    }
}

data class SocketBoard(val spec: SocketSpec, val chips: Map<String, ChipId?>) {
    val complete: Boolean get() = spec.sockets.all { chips[it.id] != null }

    /** Every signal on the board for one set of input values: ref → value (null = unpowered). */
    fun signals(values: List<Boolean>): Map<String, Sig> {
        val out = HashMap<String, Sig>()
        spec.inputs.forEachIndexed { i, n -> out[n] = values[i] }
        // Sockets only read earlier signals, so evaluating in depth order is enough.
        for (def in spec.sockets.sortedBy { spec.depth(it.id) }) {
            val ins = def.inputs.map { out[it] }
            val res = chipOutputs(chips[def.id], ins)
            out[def.id] = res?.getOrNull(0)
            res?.forEachIndexed { k, v -> out["${def.id}.$k"] = v } ?: run { out["${def.id}.0"] = null; out["${def.id}.1"] = null }
        }
        return out
    }

    fun outputs(values: List<Boolean>): List<Sig> = signals(values).let { s -> spec.outputs.map { (_, ref) -> s[ref] } }

    /** Every step: key → output values. Null if a socket is empty. */
    fun run(): Map<String, List<Boolean>>? {
        if (!complete) return null
        return spec.keys.indices.associate { c -> spec.keys[c] to outputs(spec.bits(c)).map { it == true } }
    }

    fun test(): TestResult {
        val rows = run() ?: return TestResult.incomplete(spec.keys.size)
        val bad = spec.keys.firstOrNull { rows[it] != spec.want(it) }
        return TestResult(spec.keys.count { rows[it] == spec.want(it) }, spec.keys.size, bad?.let { "$it gave ${rows[it]?.bits()}, want ${spec.want(it).bits()}" })
    }

    private fun List<Boolean>.bits() = joinToString("") { if (it) "1" else "0" }
}

/** The three socket levels. */
object SocketLevels {
    val NAND_NOR = SocketSpec(
        inputs = listOf("A", "B"),
        sockets = listOf(SocketDef("g1", listOf("A", "B")), SocketDef("n1", listOf("g1")), SocketDef("g2", listOf("A", "B")), SocketDef("n2", listOf("g2"))),
        outputs = listOf("NAND" to "n1", "NOR" to "n2"),
        target = { (a, b) -> listOf(!(a && b), !(a || b)) },
    )

    /** XOR from three gates: two read A and B, the third combines them. (A OR B) AND (A NAND B) is one answer. */
    val XOR = SocketSpec(
        inputs = listOf("A", "B"),
        sockets = listOf(SocketDef("g1", listOf("A", "B")), SocketDef("g2", listOf("A", "B")), SocketDef("g3", listOf("g1", "g2"))),
        outputs = listOf("X" to "g3"),
        target = { (a, b) -> listOf(a != b) },
    )

    /** Full adder: HA(A, B) → HA(sum, Cin) → join the two carries. */
    val FULL_ADDER = SocketSpec(
        inputs = listOf("A", "B", "Cin"),
        sockets = listOf(SocketDef("h1", listOf("A", "B")), SocketDef("h2", listOf("h1.0", "Cin")), SocketDef("g", listOf("h1.1", "h2.1"))),
        outputs = listOf("Cout" to "g", "S" to "h2.0"),
        target = { (a, b, c) -> fullAdd(a, b, c).let { (s, co) -> listOf(co, s) } },
    )
}

/**
 * Level 3 — NOT from one transistor. The transistor sits between the output node and ground, with a resistor from
 * +5V feeding the node. The player chooses where the LED goes:
 * - EMITTER: after the transistor's emitter, so it lights when the transistor conducts (a "buffer": OUT = A). Wrong.
 * - COLLECTOR: on the node above the transistor. When A is on, the transistor drains the node to ground and the LED
 *   goes dark; when A is off, current flows through the resistor into the LED (OUT = NOT A). Right.
 */
enum class LedSpot { EMITTER, COLLECTOR }

data class InverterBoard(val spot: LedSpot?) {
    /** null = LED not placed yet. */
    fun out(a: Boolean): Sig = when (spot) {
        LedSpot.EMITTER -> a
        LedSpot.COLLECTOR -> !a
        null -> null
    }

    fun test(): TestResult {
        if (spot == null) return TestResult.incomplete(2)
        val pass = listOf(false, true).count { out(it) == !it }
        return TestResult(pass, 2, if (pass < 2) "LED follows A instead of flipping it" else null)
    }
}
