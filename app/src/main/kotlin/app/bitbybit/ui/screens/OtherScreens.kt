package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.data.Level
import app.bitbybit.data.LevelsFile
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.Card
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.Routes
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Chip ids a level unlocks ("unlocks" is a string or an array in levels.json). */
fun Level.unlockIds(): List<String> = when (val u = unlocks) {
    is JsonPrimitive -> listOf(u.content)
    is JsonArray -> u.map { it.jsonPrimitive.content }
    else -> emptyList()
}

/** One lesson: a headline, explanation cards, and the goal. Levels without an entry open the workbench directly. */
data class Lesson(val headline: String, val cards: List<Pair<String, String>>, val goal: String)

val LESSONS: Map<Int, Lesson> = mapOf(
    1 to Lesson(
        headline = "A transistor is a switch you flip with electricity",
        cards = listOf(
            "Base on → current flows" to "Put voltage on the base and current passes from top to bottom. Remove it and the path is cut.",
            "Two switches in a row" to "Stack transistor A on top of transistor B. Current reaches the LED only if A and B are both on.",
        ),
        goal = "Wire two transistors so the LED lights only when A and B are both on. Test all 4 input combinations.",
    ),
    // Poses the puzzle without giving the answer away: the player has to discover "side by side".
    2 to Lesson(
        headline = "Same two parts, a new rule",
        cards = listOf(
            "What you know" to "In Level 1, stacked transistors needed both A and B before current could reach the LED.",
            "The new rule" to "Now the LED should light if A is on, if B is on, or if both are. Either switch must be enough.",
        ),
        goal = "Find a way to wire the same two transistors so either one can light the LED.",
    ),
    6 to Lesson(
        headline = "Making bits add up",
        cards = listOf(
            "Adding in binary" to "0 + 0 = 0, 0 + 1 = 1, 1 + 0 = 1, and 1 + 1 = 10: there's no digit \"2\", so the 1 carries to the next column.",
            "Two outputs" to "S is the sum bit (the right digit). C is the carry (the left digit). Each one is a gate you already built.",
            "Chips, not transistors" to "From now on you build with your chips: XOR, AND, OR, NAND. Each counts as one block.",
        ),
        goal = "Find which chip makes S and which makes C, so A + B comes out right for all four inputs.",
    ),
)

/** Lesson intro (design/Lesson.dc.html). */
@Composable
fun LessonScreen(
    level: Level,
    onBack: () -> Unit,
    onStart: () -> Unit,
    /** Null when the level has no Hard mode. */
    onStartHard: (() -> Unit)? = null,
    doneHard: Boolean = false,
    /** True in a Hard game: the main button starts Hard mode. */
    preferHard: Boolean = false,
) {
    val lesson = LESSONS[level.n]
    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Lesson", level.title, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (lesson == null) {
                Text(level.title, style = BbType.H1)
            } else {
                Text(lesson.headline, style = BbType.H1)
                lesson.cards.forEach { (title, body) ->
                    Card {
                        Text(title, style = BbType.Title)
                        Text(body, style = BbType.Body)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bb.AccentBg)
                        .border(1.dp, Bb.AccentBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                ) {
                    Text("Your goal: " + lesson.goal, style = BbType.Body.copy(color = Bb.Text))
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The game's difficulty decides the main button; the other mode stays one tap away.
            val hardLabel = if (doneHard) "Hard mode ★ · build it again" else "Hard mode · build it yourself"
            when {
                onStartHard == null -> PrimaryButton("Start building", ButtonTone.Accent, onClick = onStart)
                preferHard -> {
                    PrimaryButton("Start · Hard", ButtonTone.Accent, onClick = onStartHard)
                    PrimaryButton("Play this level in Normal", ButtonTone.Ghost, onClick = onStart)
                }
                else -> {
                    PrimaryButton("Start · Normal", ButtonTone.Accent, onClick = onStart)
                    PrimaryButton(hardLabel, ButtonTone.Ghost, onClick = onStartHard)
                }
            }
        }
    }
}

/** Level complete (design/Complete.dc.html). */
@Composable
fun CompleteScreen(level: Level, data: LevelsFile, hard: Boolean = false, onNext: () -> Unit, onToolbox: () -> Unit) {
    val chips = level.unlockIds()
    val next = data.levels.firstOrNull { it.n == level.n + 1 }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("LEVEL ${level.n} COMPLETE" + if (hard) " · HARD MODE ★" else "", style = BbType.Eyebrow.copy(color = Bb.High))
        chips.forEach { id ->
            val symbol = data.chips[id]?.symbol ?: id
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Bb.Surface).border(3.dp, Bb.High, RoundedCornerShape(10.dp))
                    .padding(horizontal = 36.dp, vertical = 28.dp),
            ) { Text(symbol, fontFamily = BbFonts.Mono, fontSize = 40.sp, color = Bb.Text) }
        }
        Text(chips.joinToString(" + ") + " chip unlocked", style = BbType.H1)
        Text(
            if (hard) "You placed and wired every part yourself. From now on you use it as one block."
            else "From now on you use it as one block in the next levels.",
            style = BbType.Body,
        )
        next?.let {
            Card {
                Text("NEXT · LEVEL ${it.n}", style = BbType.Eyebrow.copy(color = Bb.Accent))
                Text(it.title, style = BbType.Title)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Next level", ButtonTone.Accent, onClick = onNext)
        PrimaryButton("See it in your toolbox", ButtonTone.Ghost, onClick = onToolbox)
    }
}

/** Chip toolbox (design/Toolbox.dc.html). */
@Composable
fun ToolboxScreen(data: LevelsFile, completed: Set<Int>, onTab: (String) -> Unit) {
    val owned = buildSet {
        add("TRANSISTOR")
        data.levels.filter { it.n in completed }.forEach { addAll(it.unlockIds()) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your chips", style = BbType.H1)
            Text("${owned.size} unlocked. Each chip is built from the ones before it.", style = BbType.Body.copy(color = Bb.TextMuted))
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(data.chips.entries.toList(), key = { it.key }) { (id, chip) ->
                val on = id in owned
                Column(
                    Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Bb.Surface else Bb.SurfaceSunken)
                        .border(1.dp, Bb.Border, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(chip.symbol, fontFamily = BbFonts.Mono, fontSize = 16.sp, color = if (on) Bb.High else Bb.TextLocked)
                    Text(chip.name ?: id, style = BbType.Title.copy(color = if (on) Bb.Text else Bb.TextSecondary))
                    Text(chip.builtFrom ?: "", style = BbType.Caption)
                }
            }
        }
        BottomTabs(selected = Routes.TOOLBOX, onTab = onTab)
    }
}

