package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.BuildConfig
import app.bitbybit.data.Difficulty
import app.bitbybit.data.Level
import app.bitbybit.data.LevelsFile
import app.bitbybit.data.currentLevel
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.Routes
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

private enum class LevelStatus { Done, Current, Locked }

/** Home: progress header, chapters with a level rail, bottom tabs (design/Main.dc.html). */
@Composable
fun JourneyScreen(
    data: LevelsFile,
    completed: Set<Int>,
    completedHard: Set<Int> = emptySet(),
    difficulty: Difficulty? = null,
    onOpenLevel: (Int) -> Unit,
    onTab: (String) -> Unit,
) {
    val total = data.levels.size
    val current = currentLevel(completed, total)
    fun status(l: Level) = when {
        l.n in completed -> LevelStatus.Done
        l.n == current -> LevelStatus.Current
        else -> LevelStatus.Locked
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BIT BY BIT", style = BbType.Eyebrow.copy(color = Bb.High))
                    Text("From transistor\nto computer", style = BbType.H1.copy(fontSize = 30.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${completed.size} of $total levels", style = BbType.Caption.copy(fontSize = 14.sp))
                        difficulty?.let { DifficultyChip(it) }
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Bb.BorderSubtle)) {
                        Box(Modifier.fillMaxWidth(completed.size / total.toFloat()).height(8.dp).background(Bb.High))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            data.chapters.forEach { chapter ->
                val levels = data.levels.filter { it.chapter == chapter.id }
                // Debug builds list every chapter's levels so locked ones can be opened for testing.
                val unlocked = BuildConfig.DEBUG || levels.any { status(it) != LevelStatus.Locked }
                item(key = "ch${chapter.id}") {
                    Column(Modifier.padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "CHAPTER ${chapter.id}" + if (chapter.milestone) " · MILESTONE" else "",
                            style = BbType.Eyebrow.copy(color = if (unlocked && levels.any { it.n == current }) Bb.Accent else Bb.TextMuted),
                        )
                        Text(chapter.title, style = BbType.H2)
                        Text(chapter.blurb, style = BbType.Body.copy(color = Bb.TextMuted))
                    }
                }
                if (unlocked) {
                    items(levels, key = { it.id }) { level ->
                        LevelRow(level, status(level), hardDone = level.n in completedHard, onOpen = { onOpenLevel(level.n) })
                    }
                } else {
                    item(key = "locked${chapter.id}") {
                        Text(
                            levels.joinToString(" · ") { "${it.n} ${it.title}" + if (it.milestone) " ★" else "" },
                            style = BbType.Caption,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bb.Surface)
                                .border(1.dp, Bb.Border, RoundedCornerShape(14.dp)).padding(14.dp),
                        )
                    }
                }
            }
        }
        BottomTabs(selected = Routes.JOURNEY, onTab = onTab)
    }
}

@Composable
private fun LevelRow(level: Level, status: LevelStatus, hardDone: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val (nodeBg, nodeFg) = when (status) {
            LevelStatus.Done -> Bb.High to Bb.Bg
            LevelStatus.Current -> Bb.Accent to Bb.Bg
            LevelStatus.Locked -> Bb.Surface to Bb.TextLocked
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(nodeBg)
                .border(2.dp, if (status == LevelStatus.Locked) Bb.Border else nodeBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (status) { LevelStatus.Done -> "✓"; LevelStatus.Current -> "${level.n}"; LevelStatus.Locked -> "${level.n}" }, // TODO(icon): lock line icon
                color = nodeFg, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold,
            )
        }
        if (status == LevelStatus.Current) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Bb.AccentBg)
                    .border(1.5.dp, Bb.Accent, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("NEXT UP · L${level.n}", style = BbType.Eyebrow.copy(color = Bb.Accent))
                Text(level.title, style = BbType.H2.copy(fontSize = 20.sp))
                level.desc?.let { Text(it, style = BbType.Body) }
                PrimaryButton("Start level", ButtonTone.Accent, onClick = onOpen)
            }
        } else {
            Column(
                Modifier.weight(1f).padding(top = 8.dp)
                    .then(
                        when {
                            status == LevelStatus.Done -> Modifier.clickable(role = Role.Button, onClickLabel = "Replay") { onOpen() }
                            // Debug builds only: open locked levels to test them without playing everything before.
                            BuildConfig.DEBUG && status == LevelStatus.Locked -> Modifier.clickable(role = Role.Button, onClickLabel = "Open (debug)") { onOpen() }
                            else -> Modifier
                        },
                    )
                    .semantics { contentDescription = "Level ${level.n}, ${level.title}, ${status.name.lowercase()}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(level.title + if (level.milestone) " ★" else "", style = BbType.Title.copy(color = if (status == LevelStatus.Locked) Bb.TextSecondary else Bb.Text))
                    if (hardDone) Text(
                        "HARD ★", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Bb.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Bb.AccentBg).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                level.desc?.let { Text(it, style = BbType.Caption) }
            }
        }
    }
}

@Composable
fun BottomTabs(selected: String, onTab: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp).background(Bb.SurfaceSunken)) {
        listOf(Routes.JOURNEY to "Journey", Routes.TOOLBOX to "Chips", Routes.SANDBOX to "Sandbox").forEach { (route, label) ->
            val active = route == selected
            Box(
                Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab) { onTab(route) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Bb.Accent else Bb.TextMuted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}
