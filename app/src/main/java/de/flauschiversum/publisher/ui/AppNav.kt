package de.flauschiversum.publisher.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.ui.editor.EditorScreen
import de.flauschiversum.publisher.ui.editor.EditorViewModel
import de.flauschiversum.publisher.ui.home.HomeScreen
import de.flauschiversum.publisher.ui.home.HomeViewModel
import de.flauschiversum.publisher.ui.settings.SettingsScreen
import de.flauschiversum.publisher.ui.settings.SettingsViewModel
import de.flauschiversum.publisher.ui.setup.SetupScreen
import de.flauschiversum.publisher.ui.setup.SetupViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{dir}"
    private const val NEW = "-"
    fun editor(dir: String?) = "editor/" + if (dir == null) NEW else URLEncoder.encode(dir, "UTF-8")
    fun decodeDir(arg: String?): String? = if (arg == null || arg == NEW) null else URLDecoder.decode(arg, "UTF-8")
}

@Composable
fun AppNav(container: AppContainer, nav: NavHostController, start: String) {
    val factory = AppViewModelFactory(container)
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            val vm: SetupViewModel = viewModel(factory = factory)
            SetupScreen(vm, onFinished = { nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } })
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(vm, onOpenPost = { nav.navigate(Routes.editor(it)) }, onSettings = { nav.navigate(Routes.SETTINGS) },
                onFixAccess = { nav.navigate(Routes.SETUP) })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(vm, onBack = { nav.popBackStack() }, onSetup = { nav.navigate(Routes.SETUP) { popUpTo(Routes.HOME) } })
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("dir") { type = NavType.StringType })) { entry ->
            val dir = Routes.decodeDir(entry.arguments?.getString("dir"))
            val vm: EditorViewModel = viewModel(key = "editor-$dir", factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = EditorViewModel(container, SavedStateHandle(), dir) as T
            })
            val scale = container.settingsSnapshot.editorFontScale
            EditorScreen(vm, scale, onDone = { nav.popBackStack() })
        }
    }
}
