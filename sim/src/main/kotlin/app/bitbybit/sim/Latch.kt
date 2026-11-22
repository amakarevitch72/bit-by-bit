package app.bitbybit.sim

/**
 * Level 15 — SR latch built from two gate sockets with two feedback loops (design/SRLatch.dc.html).
 *
 * top gate: inputs R and (loop 2 ? Q̄ : 0) → Q
 * bottom gate: inputs (loop 1 ? Q : 0) and S → Q̄
 */
data class SrLatchBoard(
    val top: ChipId?,
    val bottom: ChipId?,
    val loop1: Boolean,
    val loop2: Boolean,
) {
    data class State(val q: Sig, val qBar: Sig, val oscillating: Boolean = false)

    val isComplete: Boolean get() = top != null && bottom != null

    /**
     * Re-evaluates both gates from the previous state until nothing changes.
     * Not settled after [MAX_PASSES] passes = oscillation (a race), e.g. S and R released together.
     */
    fun settle(s: Boolean, r: Boolean, prev: State): State {
        var q: Sig = prev.q
        var qn: Sig = prev.qBar
        repeat(MAX_PASSES) {
            val fbTop: Sig = if (loop2) qn else false
            val fbBot: Sig = if (loop1) q else false
            val nq = top?.let { Gates.eval(it, r, fbTop) }
            val nqn = bottom?.let { Gates.eval(it, fbBot, s) }
            if (nq == null || nqn == null) return State(nq, nqn)
            if (nq == q && nqn == qn) return State(q, qn)
            q = nq; qn = nqn
        }
        return State(q, qn, oscillating = true)
    }

    /** One step of a test sequence: inputs and the Q expected after it. */
    data class Step(val s: Boolean, val r: Boolean, val wantQ: Boolean)

    data class StepResult(val step: Step, val q: Boolean, val ok: Boolean)

    fun run(sequence: List<Step> = TEST_SEQUENCE): List<StepResult> {
        var state = State(q = false, qBar = false)
        return sequence.map { step ->
            state = settle(step.s, step.r, state)
            val q = state.q == true
            StepResult(step, q, q == step.wantQ)
        }
    }

    fun test(): TestResult {
        if (!isComplete) return TestResult.incomplete(TEST_SEQUENCE.size)
        val rows = run()
        val firstBad = rows.indexOfFirst { !it.ok }
        return TestResult(
            passed = rows.count { it.ok },
            total = rows.size,
            firstFailure = if (firstBad < 0) null else "step ${firstBad + 1}: Q = ${if (rows[firstBad].q) 1 else 0}, want ${if (rows[firstBad].step.wantQ) 1 else 0}",
        )
    }

    companion object {
        const val MAX_PASSES = 8
        const val PULSE_MS = 450L

        /** [S, R, expected Q] — same as levels.json level 15. */
        val TEST_SEQUENCE: List<Step> = listOf(
            intArrayOf(0, 1, 0), intArrayOf(0, 0, 0), intArrayOf(1, 0, 1), intArrayOf(0, 0, 1), intArrayOf(0, 0, 1),
            intArrayOf(0, 1, 0), intArrayOf(0, 0, 0), intArrayOf(0, 0, 0), intArrayOf(1, 0, 1), intArrayOf(0, 0, 1),
        ).map { Step(it[0] == 1, it[1] == 1, it[2] == 1) }
    }
}
