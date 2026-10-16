package com.nendo.argosy.desktop.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * Builds `@HiltViewModel` classes the way Hilt's view-model factory does: one fresh set of
 * [ViewModelProviders] per view model, bound to a [SavedStateHandle] from the owner's creation
 * extras. A class absent from the providers' map fails with its name.
 */
class DesktopViewModelFactory(
    private val providers: (SavedStateHandle) -> ViewModelProviders,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
        val provider = providers(extras.createSavedStateHandle()).viewModels()[modelClass.java]
            ?: throw IllegalArgumentException("desktop: ${modelClass.qualifiedName} is not a compiled @HiltViewModel")
        return modelClass.java.cast(provider.get())
    }
}

val LocalDesktopViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("desktop: no DesktopViewModelFactory provided")
}
