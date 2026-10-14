package de.flauschiversum.publisher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import de.flauschiversum.publisher.ui.photos.PhotosScreen
import de.flauschiversum.publisher.ui.photos.PhotosViewModel
import de.flauschiversum.publisher.ui.share.ShareScreen
import de.flauschiversum.publisher.ui.share.ShareViewModel
import de.flauschiversum.publisher.ui.setup.SetupScreen
import de.flauschiversum.publisher.ui.setup.SetupViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SHARE = "share"
    const val PREVIEW = "preview/{dir}"
    fun preview(dir: String) = "preview/" + URLEncoder.encode(dir, "UTF-8")
    const val PHOTOS = "photos/{dir}"
    fun photos(dir: String) = "photos/" + URLEncoder.encode(dir, "UTF-8")
    const val EDITOR = "editor/{dir}?props={props}"
    private const val NEW = "-"
    fun editor(dir: String?, properties: Boolean = false) = "editor/" + (if (dir == null) NEW else URLEncoder.encode(dir, "UTF-8")) + "?props=$properties"
    fun decodeDir(arg: String?): String? = if (arg == null || arg == NEW) null else URLDecoder.decode(arg, "UTF-8")
}

@Composable
fun AppNav(container: AppContainer, nav: NavHostController, start: String) {
    val factory = AppViewModelFactory(container)
    // Geteilte Fotos öffnen die Zuordnung, sobald die Einrichtung abgeschlossen ist
    val pending by container.incoming.pending.collectAsState()
    LaunchedEffect(pending.isNotEmpty(), start) {
        if (pending.isNotEmpty() && start != Routes.SETUP) nav.navigate(Routes.SHARE) { launchSingleTop = true }
    }
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            val vm: SetupViewModel = viewModel(factory = factory)
            SetupScreen(vm, onFinished = { nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } })
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            var photoTarget by remember { mutableStateOf<String?>(null) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                val dir = photoTarget; photoTarget = null
                if (uris.isNotEmpty()) container.appScope.launch(Dispatchers.IO) { container.incoming.receiveUris(uris, dir) }
            }
            HomeScreen(vm, onOpenPost = { nav.navigate(Routes.editor(it)) }, onSettings = { nav.navigate(Routes.SETTINGS) },
                onFixAccess = { nav.navigate(Routes.SETUP) }, onOpenProperties = { nav.navigate(Routes.editor(it, properties = true)) }, onOpenPhotos = { nav.navigate(Routes.photos(it)) }, onOpenPreview = { nav.navigate(Routes.preview(it)) },
                onAddPhoto = { photoTarget = it; picker.launch("image/*") })
        }
        composable(Routes.SHARE) {
            val vm: ShareViewModel = viewModel(factory = factory)
            ShareScreen(vm, onClose = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) })
        }
        composable(Routes.PHOTOS, arguments = listOf(navArgument("dir") { type = NavType.StringType })) { entry ->
            val dir = Routes.decodeDir(entry.arguments?.getString("dir")) ?: return@composable
            val vm: PhotosViewModel = viewModel(key = "photos-$dir", factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = PhotosViewModel(container, dir) as T
            })
            PhotosScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.PREVIEW, arguments = listOf(navArgument("dir") { type = NavType.StringType })) { entry ->
            val dir = Routes.decodeDir(entry.arguments?.getString("dir")) ?: return@composable
            de.flauschiversum.publisher.ui.preview.PreviewScreen(container, dir, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(vm, onBack = { nav.popBackStack() }, onSetup = { nav.navigate(Routes.SETUP) { popUpTo(Routes.HOME) } })
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("dir") { type = NavType.StringType }, navArgument("props") { type = NavType.BoolType; defaultValue = false })) { entry ->
            val dir = Routes.decodeDir(entry.arguments?.getString("dir"))
            val vm: EditorViewModel = viewModel(key = "editor-$dir", factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = EditorViewModel(container, SavedStateHandle(), dir, entry.arguments?.getBoolean("props") == true) as T
            })
            val scale = container.settingsSnapshot.editorFontScale
            EditorScreen(vm, scale, onDone = { nav.popBackStack() }, onPhotos = { d -> nav.navigate(Routes.photos(d)) })
        }
    }
}
