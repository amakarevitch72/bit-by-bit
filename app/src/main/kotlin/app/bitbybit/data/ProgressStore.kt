package app.bitbybit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.progressDataStore by preferencesDataStore(name = "progress")

/** The difficulty chosen when a game starts. Lessons start in this mode; the other mode stays one tap away. */
enum class Difficulty { NORMAL, HARD }

/** One saved game. [difficulty] is null when no game has been started yet (first launch). */
data class SaveGame(val difficulty: Difficulty?, val completed: Set<Int>, val completedHard: Set<Int>) {
    val exists: Boolean get() = difficulty != null
}

/**
 * Local-only, single save slot. [SaveGame.completed] = levels finished in any mode (unlocks the next level and the chip);
 * [SaveGame.completedHard] = levels also finished in Hard mode (a badge on Journey). Current level = lowest unfinished one.
 */
class ProgressStore(private val context: Context) {
    private val completedKey = stringSetPreferencesKey("completed_levels")
    private val completedHardKey = stringSetPreferencesKey("completed_levels_hard")
    private val difficultyKey = stringPreferencesKey("difficulty")

    val save: Flow<SaveGame> = context.progressDataStore.data.map { prefs ->
        // Saves from before the Start screen existed have progress but no difficulty: treat them as Normal games.
        val completed = prefs[completedKey].toLevels()
        val difficulty = prefs[difficultyKey]?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
            ?: if (completed.isNotEmpty()) Difficulty.NORMAL else null
        SaveGame(difficulty, completed, prefs[completedHardKey].toLevels())
    }

    /** Finishing in Hard mode also counts as finishing the level. */
    suspend fun markComplete(level: Int, hard: Boolean = false) {
        context.progressDataStore.edit { prefs ->
            prefs[completedKey] = prefs[completedKey].orEmpty() + level.toString()
            if (hard) prefs[completedHardKey] = prefs[completedHardKey].orEmpty() + level.toString()
        }
    }

    /** Wipes all progress and starts over at Level 1 with [difficulty]. */
    suspend fun newGame(difficulty: Difficulty) {
        context.progressDataStore.edit { prefs ->
            prefs.remove(completedKey)
            prefs.remove(completedHardKey)
            prefs[difficultyKey] = difficulty.name
        }
    }

    private fun Set<String>?.toLevels(): Set<Int> = orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
}

fun currentLevel(completed: Set<Int>, total: Int): Int = (1..total).firstOrNull { it !in completed } ?: total
