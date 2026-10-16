package com.nendo.argosy.desktop.di

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.BindsInstance
import dagger.Module
import dagger.Subcomponent
import javax.inject.Provider

interface ViewModelProviders {
    fun viewModels(): Map<Class<*>, @JvmSuppressWildcards Provider<ViewModel>>
}

@Subcomponent(modules = [DesktopViewModelModule::class])
interface DesktopViewModelComponent : ViewModelProviders {

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance savedStateHandle: SavedStateHandle): DesktopViewModelComponent
    }
}

@Module(subcomponents = [DesktopViewModelComponent::class])
object DesktopViewModelComponentModule
