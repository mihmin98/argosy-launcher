package com.nendo.argosy.desktop.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.migrations.MigrationRegistry
import com.nendo.argosy.desktop.platform.AppDirs
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DesktopDaoModule::class])
object DesktopDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(appDirs: AppDirs): ALauncherDatabase =
        Room.databaseBuilder<ALauncherDatabase>(name = appDirs.db("alauncher.db").path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*MigrationRegistry.ARRAY)
            .build()
}
