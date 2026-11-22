package app.bitbybit.sim

/**
 * Outcome of a level test.
 * [firstFailure] is the one example shown to the player, e.g. "5 + 3 gave 0, want 8".
 * A test on an incomplete circuit returns [incomplete] = true and passes nothing.
 */
data class TestResult(
    val passed: Int,
    val total: Int,
    val firstFailure: String? = null,
    val incomplete: Boolean = false,
) {
    val allPass: Boolean get() = !incomplete && passed == total

    companion object {
        fun incomplete(total: Int) = TestResult(0, total, incomplete = true)
    }
}
