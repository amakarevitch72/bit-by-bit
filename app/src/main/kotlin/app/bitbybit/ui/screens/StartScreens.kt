package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.data.Difficulty
import app.bitbybit.data.LevelsFile
import app.bitbybit.data.SaveGame
import app.bitbybit.data.currentLevel
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.Card
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

fun Difficulty.label() = when (this) { Difficulty.NORMAL -> "Normal"; Difficulty.HARD -> "Hard" }

/** First screen: continue the saved game, or start a new one. */
@Composable
fun StartScreen(data: LevelsFile, save: SaveGame, onContinue: () -> Unit, onNewGame: () -> Unit) {
    val total = data.levels.size
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.weight(1f))
        Text("BIT BY BIT", style = BbType.Eyebrow.copy(color = Bb.High, fontSize = 14.sp))
        Text("From transistor\nto computer", style = BbType.H1.copy(fontSize = 36.sp, lineHeight = 40.sp))
        Text("Build a computer one chip at a time: gates, adders, a calculator, memory, then a tiny CPU.", style = BbType.Body)
        Spacer(Modifier.weight(1f))

        if (save.exists) {
            val current = data.levels.firstOrNull { it.n == currentLevel(save.completed, total) }
            Card {
                Text("YOUR GAME", style = BbType.Eyebrow)
                Text(if (save.completed.size == total) "All levels done" else "Level ${current?.n} · ${current?.title}", style = BbType.Title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${save.completed.size} of $total levels", style = BbType.Caption)
                    DifficultyChip(save.difficulty!!)
                    if (save.completedHard.isNotEmpty()) Text("★ ${save.completedHard.size} in Hard", style = BbType.Caption.copy(color = Bb.Accent))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Bb.BorderSubtle)) {
                    Box(Modifier.fillMaxWidth(save.completed.size / total.toFloat()).height(6.dp).background(Bb.High))
                }
            }
            PrimaryButton("Continue", ButtonTone.Accent, onClick = onContinue)
            PrimaryButton("New game", ButtonTone.Ghost, onClick = onNewGame)
        } else {
            PrimaryButton("New game", ButtonTone.Accent, onClick = onNewGame)
            Text("Your progress is saved on this phone.", style = BbType.Caption, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DifficultyChip(d: Difficulty) {
    Text(
        d.label().uppercase(), fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        color = if (d == Difficulty.HARD) Bb.Accent else Bb.High,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (d == Difficulty.HARD) Bb.AccentBg else Bb.HighBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** New game: choose the difficulty. Starting over a saved game asks first, because it erases the progress. */
@Composable
fun NewGameScreen(save: SaveGame, onBack: () -> Unit, onStart: (Difficulty) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(Difficulty.NORMAL) }
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("New game", "Choose your difficulty", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DifficultyCard(
                Difficulty.NORMAL, choice == Difficulty.NORMAL, "Guided",
                "The parts are already on the board. You decide how they're wired, or which chip goes in each socket.",
            ) { choice = Difficulty.NORMAL }
            DifficultyCard(
                Difficulty.HARD, choice == Difficulty.HARD, "From scratch",
                "An empty board and a small box of parts. Place and wire everything yourself. Levels without a Hard version yet play in Normal.",
            ) { choice = Difficulty.HARD }
            Text("You can still switch any single level to the other mode from its lesson.", style = BbType.Caption)
            if (save.exists) {
                Text(
                    "Starting a new game erases your saved progress (${save.completed.size} levels done).",
                    style = BbType.Body.copy(color = Bb.ErrorText),
                )
            }
        }
        Column(Modifier.padding(20.dp)) {
            PrimaryButton("Start ${choice.label()} game", ButtonTone.Accent) {
                if (save.exists && save.completed.isNotEmpty()) confirm = true else onStart(choice)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Bb.Surface,
            title = { Text("Erase your progress?", style = BbType.Title) },
            text = { Text("Your current game (${save.completed.size} levels done) will be replaced by a new ${choice.label()} game. This can't be undone.", style = BbType.Body) },
            confirmButton = { TextButton(onClick = { confirm = false; onStart(choice) }) { Text("Erase and start", color = Bb.Error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep my game", color = Bb.Text) } },
        )
    }
}

@Composable
private fun DifficultyCard(d: Difficulty, selected: Boolean, tagline: String, body: String, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Bb.AccentBg else Bb.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Bb.Accent else Bb.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape)
                .border(2.dp, if (selected) Bb.Accent else Bb.PinOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Bb.Accent)) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.label(), style = BbType.H2.copy(fontSize = 20.sp))
                Text(tagline, style = BbType.Caption)
            }
            Text(body, style = BbType.Body)
        }
    }
}
