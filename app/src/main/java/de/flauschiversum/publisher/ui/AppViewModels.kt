package de.flauschiversum.publisher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import de.flauschiversum.publisher.app.AppContainer

/** Erzeugt ViewModels mit dem [AppContainer]. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        modelClass.getConstructor(AppContainer::class.java).newInstance(container)
}
