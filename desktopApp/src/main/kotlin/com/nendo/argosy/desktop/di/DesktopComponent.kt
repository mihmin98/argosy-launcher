package com.nendo.argosy.desktop.di

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.desktop.platform.AppDirs
import dagger.BindsInstance
import dagger.Component
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Singleton
@Component(modules = [DesktopDatabaseModule::class, DesktopViewModelComponentModule::class])
interface DesktopComponent : DesktopEntryPoints {

    fun gameDao(): GameDao

    fun viewModelComponentFactory(): DesktopViewModelComponent.Factory

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance @ApplicationContext context: Context,
            @BindsInstance appDirs: AppDirs,
        ): DesktopComponent
    }
}
