package app.bitbybit.sim

/**
 * Hard mode: the player places and wires the parts themselves. The board has fixed terminals
 * (input switches and one or more output LEDs); the player adds parts from a limited palette and wires them.
 *
 * Before the truth table is run, [HardMode.problem] checks the build rules, so a shortcut can't pass:
 * - every required part must be on the board (e.g. both transistors in Levels 1–2),
 * - switches may only drive a part's control input: a transistor's base B, or any gate input — never the LED or a collector,
 * - every LED must be driven by a placed part, never straight from a switch.
 * Then every input combination is simulated from a clean start and each LED is read.
 */
data class HardSpec(
    /** Cell ids of the fixed input switches, in input order (A, B, …). */
    val inputs: List<Int>,
    /** Cell ids of the fixed output LEDs, in output order (e.g. C, S for a half adder). */
    val leds: List<Int>,
    /** Parts the player must place, e.g. TRANSISTOR × 2. Empty = any mix from the palette. */
    val required: Map<PartKind, Int>,
    /** Wanted LED values for the given input values, in [leds] order. */
    val target: (List<Boolean>) -> List<Boolean>,
) {
    /** Row keys in table order: "0 0", "0 1", … (input A first). */
    val keys: List<String>
        get() = (0 until (1 shl inputs.size)).map { combo -> bits(combo).joinToString(" ") { if (it) "1" else "0" } }

    fun bits(combo: Int): List<Boolean> = List(inputs.size) { i -> (combo shr (inputs.size - 1 - i)) and 1 == 1 }
}

object HardMode {

    /** The first rule the build breaks, as a sentence for the player, or null if it may be tested. */
    fun problem(spec: HardSpec, parts: List<CircuitPart>, wires: List<CircuitWire>): String? {
        val byId = parts.associateBy { it.id }
        for ((kind, n) in spec.required) {
            val placed = parts.count { it.kind == kind }
            if (placed < n) return "Place ${n - placed} more ${kind.display(n - placed)} first."
        }
        val switches = spec.inputs.toSet()
        for (w in wires) {
            if (w.fromPart !in switches) continue
            val to = byId[w.toPart] ?: continue
            val ok = when (to.kind) {
                PartKind.TRANSISTOR -> w.toPin == 0
                PartKind.LED -> false
                else -> true
            }
            if (!ok) return if (to.kind == PartKind.TRANSISTOR) "Switches drive a transistor's base (B), not its collector or the LED."
            else "Drive every LED from a part, not straight from a switch."
        }
        for (led in spec.leds) {
            val drivers = wires.filter { it.toPart == led }
            if (drivers.isEmpty()) return if (spec.leds.size == 1) "Connect something to the LED." else "Connect something to every LED."
        }
        return null
    }

    /** Runs every input combination. Keys like "0 1"; values = each LED, in [HardSpec.leds] order. Null if a rule is broken. */
    fun run(spec: HardSpec, parts: List<CircuitPart>, wires: List<CircuitWire>): Map<String, List<Boolean>>? {
        if (problem(spec, parts, wires) != null) return null
        return spec.keys.indices.associate { combo ->
            val values = spec.bits(combo)
            val set = parts.map { p ->
                val idx = spec.inputs.indexOf(p.id)
                if (idx >= 0) p.copy(switchOn = values[idx]) else p
            }
            val result = Circuit(set, wires).simulate()
            spec.keys[combo] to spec.leds.map { result.input(it, 0) }
        }
    }

    /** Wanted LED values for a row key produced by [run]. */
    fun want(spec: HardSpec, key: String): List<Boolean> = spec.target(key.split(" ").map { it == "1" })

    private fun PartKind.display(n: Int) = when (this) {
        PartKind.TRANSISTOR -> if (n == 1) "transistor" else "transistors"
        else -> name
    }
}
