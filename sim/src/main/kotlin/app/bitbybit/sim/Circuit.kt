package app.bitbybit.sim

/**
 * Free-form circuits for the Sandbox: parts joined by wires, simulated until signals settle.
 *
 * Rules (kept simple and close to the game's transistor model):
 * - An input with no wire reads 0. There is no hidden power: a transistor only conducts if its collector C is wired
 *   to something that is on, normally the POWER part (+5V, always on).
 * - Several wires into one input are joined as OR ("wired OR"), which is how two transistors side by side make OR.
 * - Feedback loops are allowed. Signals are re-evaluated from the previous state until nothing changes
 *   (at most [MAX_PASSES] passes); if they never settle, the result is marked [Result.oscillating].
 */
enum class PartKind(val inputs: List<String>, val outputs: List<String>) {
    SWITCH(emptyList(), listOf("out")),
    /** Permanent power input (+5V, shown as "IN"): always 1. Feeds transistor collectors. */
    POWER(emptyList(), listOf("+5V")),
    LED(listOf("in"), emptyList()),
    /** Simplified NPN: current passes from collector C to emitter E while base B is on. C must be wired to power. */
    TRANSISTOR(listOf("B", "C"), listOf("E")),
    AND(listOf("A", "B"), listOf("Y")),
    OR(listOf("A", "B"), listOf("Y")),
    NOT(listOf("A"), listOf("Y")),
    NAND(listOf("A", "B"), listOf("Y")),
    NOR(listOf("A", "B"), listOf("Y")),
    XOR(listOf("A", "B"), listOf("Y")),
    HA(listOf("A", "B"), listOf("S", "C")),
    FA(listOf("A", "B", "Cin"), listOf("S", "Cout"));

    /** Every unwired input reads 0 (kept as a function so a part could override it later). */
    @Suppress("UNUSED_PARAMETER") fun defaultInput(pin: Int): Boolean = false

    fun eval(i: BooleanArray, switchOn: Boolean): BooleanArray = when (this) {
        SWITCH -> booleanArrayOf(switchOn)
        POWER -> booleanArrayOf(true)
        LED -> BooleanArray(0)
        TRANSISTOR -> booleanArrayOf(i[0] && i[1])
        AND -> booleanArrayOf(i[0] && i[1])
        OR -> booleanArrayOf(i[0] || i[1])
        NOT -> booleanArrayOf(!i[0])
        NAND -> booleanArrayOf(!(i[0] && i[1]))
        NOR -> booleanArrayOf(!(i[0] || i[1]))
        XOR -> booleanArrayOf(i[0] != i[1])
        HA -> booleanArrayOf(i[0] != i[1], i[0] && i[1])
        FA -> fullAdd(i[0], i[1], i[2]).let { (s, co) -> booleanArrayOf(s, co) }
    }

    companion object {
        /** Palette/chip id → kind, or null for chips that can't be wired yet (ADD4, ALU4, RAM16…). */
        fun forId(id: String): PartKind? = entries.firstOrNull { it.name == id }
    }
}

/** A part on the board. [id] is any stable key (the Sandbox uses the grid cell). */
data class CircuitPart(val id: Int, val kind: PartKind, val switchOn: Boolean = false)

/** A wire from an output pin of one part to an input pin of another. */
data class CircuitWire(val fromPart: Int, val fromPin: Int, val toPart: Int, val toPin: Int)

class Circuit(val parts: List<CircuitPart>, val wires: List<CircuitWire>) {

    data class Result(
        /** part id → output pin values */
        val outputs: Map<Int, BooleanArray>,
        /** part id → input pin values (after wired-OR and defaults) */
        val inputs: Map<Int, BooleanArray>,
        val oscillating: Boolean,
    ) {
        fun output(part: Int, pin: Int): Boolean = outputs[part]?.getOrNull(pin) ?: false
        fun input(part: Int, pin: Int): Boolean = inputs[part]?.getOrNull(pin) ?: false
    }

    private val byId = parts.associateBy { it.id }
    private val validWires = wires.filter { w ->
        val from = byId[w.fromPart]; val to = byId[w.toPart]
        from != null && to != null && w.fromPin in from.kind.outputs.indices && w.toPin in to.kind.inputs.indices
    }
    private val incoming = validWires.groupBy { it.toPart to it.toPin }

    /**
     * Settles the circuit. Pass the previous [Result.outputs] as [previous] so memory circuits
     * (latches) keep their state between edits; without it every output starts at 0.
     */
    fun simulate(previous: Map<Int, BooleanArray> = emptyMap()): Result {
        var outputs: Map<Int, BooleanArray> = parts.associate { p ->
            p.id to (previous[p.id]?.takeIf { it.size == p.kind.outputs.size } ?: BooleanArray(p.kind.outputs.size))
        }
        var inputs = readInputs(outputs)
        repeat(MAX_PASSES) {
            val next = parts.associate { p -> p.id to p.kind.eval(inputs.getValue(p.id), p.switchOn) }
            val nextInputs = readInputs(next)
            if (same(next, outputs)) return Result(next, nextInputs, oscillating = false)
            outputs = next
            inputs = nextInputs
        }
        return Result(outputs, inputs, oscillating = true)
    }

    private fun readInputs(outputs: Map<Int, BooleanArray>): Map<Int, BooleanArray> = parts.associate { p ->
        p.id to BooleanArray(p.kind.inputs.size) { pin ->
            val drivers = incoming[p.id to pin]
            if (drivers.isNullOrEmpty()) p.kind.defaultInput(pin)
            else drivers.any { w -> outputs[w.fromPart]?.getOrNull(w.fromPin) == true }
        }
    }

    private fun same(a: Map<Int, BooleanArray>, b: Map<Int, BooleanArray>) =
        a.keys == b.keys && a.all { (k, v) -> v.contentEquals(b.getValue(k)) }

    companion object {
        const val MAX_PASSES = 32
    }
}
