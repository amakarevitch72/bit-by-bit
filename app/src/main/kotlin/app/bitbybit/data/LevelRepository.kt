package app.bitbybit.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Parsed form of assets/levels.json (a copy of docs/levels.json). Level-specific fields stay as raw JSON. */
@Serializable
data class LevelsFile(
    val version: Int,
    val chips: Map<String, ChipDef>,
    val chapters: List<Chapter>,
    val levels: List<Level>,
)

@Serializable
data class ChipDef(val symbol: String, val name: String? = null, val builtFrom: String? = null)

@Serializable
data class Chapter(val id: Int, val title: String, val blurb: String, val milestone: Boolean = false)

@Serializable
data class Level(
    val n: Int,
    val chapter: Int,
    val id: String,
    val title: String,
    val desc: String? = null,
    val status: String,
    val milestone: Boolean = false,
    val mockup: List<String> = emptyList(),
    val kind: String? = null,
    val tray: List<String> = emptyList(),
    val unlocks: JsonElement? = null,
    val start: JsonObject? = null,
    val solution: JsonElement? = null,
) {
    val isDesigned: Boolean get() = status == "designed"
}

class LevelRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val data: LevelsFile by lazy {
        context.assets.open("levels.json").bufferedReader().use { json.decodeFromString(LevelsFile.serializer(), it.readText()) }
    }

    fun level(n: Int): Level = data.levels.first { it.n == n }
}
