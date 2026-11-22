package app.bitbybit.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.bitbybit.data.Difficulty
import app.bitbybit.data.LevelRepository
import app.bitbybit.data.ProgressStore
import app.bitbybit.data.SaveGame
import app.bitbybit.ui.screens.CompleteScreen
import app.bitbybit.ui.screens.HARD_LEVELS
import app.bitbybit.ui.screens.HardWorkbench
import app.bitbybit.ui.screens.JourneyScreen
import app.bitbybit.ui.screens.LESSONS
import app.bitbybit.ui.screens.LessonScreen
import app.bitbybit.ui.screens.NewGameScreen
import app.bitbybit.ui.screens.SandboxScreen
import app.bitbybit.ui.screens.StartScreen
import app.bitbybit.ui.screens.ToolboxScreen
import app.bitbybit.ui.screens.WorkbenchScreen
import app.bitbybit.ui.theme.Bb
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: level data and the saved game. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = LevelRepository(app)
    private val progress = ProgressStore(app)

    /** null until the save has loaded, so screens never flash an empty game and send a tap to the wrong place. */
    val save: StateFlow<SaveGame?> = progress.save.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete(level: Int, hard: Boolean = false) {
        viewModelScope.launch { progress.markComplete(level, hard) }
    }

    fun newGame(difficulty: Difficulty, then: () -> Unit) {
        viewModelScope.launch { progress.newGame(difficulty); then() }
    }
}

object Routes {
    const val START = "start"
    const val NEW_GAME = "new-game"
    const val JOURNEY = "journey"
    const val TOOLBOX = "toolbox"
    const val SANDBOX = "sandbox"
    fun lesson(n: Int) = "lesson/$n"
    fun workbench(n: Int) = "workbench/$n"
    fun hard(n: Int) = "hard/$n"
    fun complete(n: Int, hard: Boolean = false) = "complete/$n?hard=$hard"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val loaded by vm.save.collectAsStateWithLifecycle()
    val save = loaded ?: SaveGame(null, emptySet(), emptySet())
    val completed = save.completed
    val levelArg = listOf(navArgument("n") { type = NavType.IntType })

    fun tab(route: String) = nav.navigate(route) {
        popUpTo(Routes.JOURNEY)
        launchSingleTop = true
    }
    fun solved(n: Int, hard: Boolean) {
        vm.complete(n, hard)
        nav.navigate(Routes.complete(n, hard)) { popUpTo(Routes.JOURNEY) }
    }
    /** The mode a level opens in: the game's difficulty, unless the level has no Hard version yet. */
    fun open(n: Int) = nav.navigate(
        when {
            n in LESSONS -> Routes.lesson(n)
            save.difficulty == Difficulty.HARD && n in HARD_LEVELS -> Routes.hard(n)
            else -> Routes.workbench(n)
        },
    )

    Box(Modifier.fillMaxSize().background(Bb.Bg).safeDrawingPadding()) {
        // Back from Journey returns to Start, which stays at the bottom of the stack.
        NavHost(nav, startDestination = Routes.START) {
            composable(Routes.START) {
                if (loaded == null) return@composable // background only, for the few ms the save takes to load
                StartScreen(
                    data = vm.repo.data, save = save,
                    onContinue = { nav.navigate(Routes.JOURNEY) },
                    onNewGame = { nav.navigate(Routes.NEW_GAME) },
                )
            }
            composable(Routes.NEW_GAME) {
                NewGameScreen(save, onBack = { nav.popBackStack() }) { difficulty ->
                    vm.newGame(difficulty) { nav.navigate(Routes.JOURNEY) { popUpTo(Routes.START) } }
                }
            }
            composable(Routes.JOURNEY) {
                if (loaded == null) return@composable
                JourneyScreen(
                    data = vm.repo.data,
                    completed = completed,
                    completedHard = save.completedHard,
                    difficulty = save.difficulty,
                    onOpenLevel = ::open,
                    onTab = ::tab,
                )
            }
            composable("lesson/{n}", levelArg) { entry ->
                val n = entry.arguments!!.getInt("n")
                val hasHard = n in HARD_LEVELS
                LessonScreen(
                    vm.repo.level(n),
                    onBack = { nav.popBackStack() },
                    onStart = { nav.navigate(Routes.workbench(n)) },
                    onStartHard = if (hasHard) ({ nav.navigate(Routes.hard(n)) }) else null,
                    preferHard = save.difficulty == Difficulty.HARD && hasHard,
                    doneHard = n in save.completedHard,
                )
            }
            composable("workbench/{n}", levelArg) { entry ->
                val n = entry.arguments!!.getInt("n")
                WorkbenchScreen(level = vm.repo.level(n), data = vm.repo.data, onBack = { nav.popBackStack() }, onSolved = { solved(n, hard = false) })
            }
            composable("hard/{n}", levelArg) { entry ->
                val n = entry.arguments!!.getInt("n")
                HardWorkbench(HARD_LEVELS.getValue(n), vm.repo.data, onBack = { nav.popBackStack() }, onSolved = { solved(n, hard = true) })
            }
            composable(
                "complete/{n}?hard={hard}",
                levelArg + navArgument("hard") { type = NavType.BoolType; defaultValue = false },
            ) { entry ->
                val n = entry.arguments!!.getInt("n")
                CompleteScreen(
                    level = vm.repo.level(n),
                    data = vm.repo.data,
                    hard = entry.arguments!!.getBoolean("hard"),
                    onNext = { nav.popBackStack(Routes.JOURNEY, inclusive = false) },
                    onToolbox = { tab(Routes.TOOLBOX) },
                )
            }
            composable(Routes.TOOLBOX) { ToolboxScreen(vm.repo.data, completed, onTab = ::tab) }
            composable(Routes.SANDBOX) { SandboxScreen(vm.repo.data, completed, onTab = ::tab) }
        }
    }
}
