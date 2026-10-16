package com.nendo.argosy.desktop.di

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.bundle.Bundle
import androidx.core.bundle.bundleOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.ui.components.LaunchOverlayViewModel
import com.nendo.argosy.ui.components.recap.LaunchRecapCursor
import com.nendo.argosy.ui.components.recap.RecapCursor
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalTestApi::class)
class DesktopViewModelTest {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LaunchProbeEntryPoint {
        fun launchProgressTracker(): LaunchProgressTracker
        fun launchRecapCursor(): LaunchRecapCursor
    }

    @Singleton
    @Component(modules = [DesktopViewModelComponentModule::class])
    interface ViewModelTestComponent : DesktopEntryPoints, LaunchProbeEntryPoint {
        fun viewModelComponentFactory(): DesktopViewModelComponent.Factory
    }

    class HandleProbeViewModel @Inject constructor(val handle: SavedStateHandle) : ViewModel()

    class UnboundViewModel : ViewModel()

    @Module
    abstract class HandleProbeModule {
        @Binds
        @IntoMap
        @ClassKey(HandleProbeViewModel::class)
        abstract fun handleProbe(viewModel: HandleProbeViewModel): ViewModel
    }

    @Component(modules = [HandleProbeModule::class])
    interface HandleProbeComponent : ViewModelProviders {
        @Component.Factory
        interface Factory {
            fun create(@BindsInstance savedStateHandle: SavedStateHandle): HandleProbeComponent
        }
    }

    private class OwnerContext(override val component: Any) : Context(), DesktopComponentOwner {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = throw UnsupportedOperationException()
        override fun <T> getSystemService(serviceClass: Class<T>): T? = null
    }

    private class EntryOwner(args: Bundle? = null) :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {

        private val registry = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = throw UnsupportedOperationException("the desktop factory is passed explicitly")
        override val defaultViewModelCreationExtras: CreationExtras = MutableCreationExtras().apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@EntryOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@EntryOwner)
            if (args != null) set(DEFAULT_ARGS_KEY, args)
        }

        init {
            controller.performAttach()
            controller.performRestore(null)
            enableSavedStateHandles()
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    private val handleProbeFactory = DesktopViewModelFactory { handle ->
        DaggerDesktopViewModelTest_HandleProbeComponent.factory().create(handle)
    }

    @Test
    fun `hiltViewModel builds an upstream view model whose init runs on the component singletons`() =
        runComposeUiTest {
            val component = DaggerDesktopViewModelTest_ViewModelTestComponent.create()
            val entryPoint = EntryPointAccessors.fromApplication(OwnerContext(component), LaunchProbeEntryPoint::class.java)
            val cursor = entryPoint.launchRecapCursor()
            cursor.scrollBody(1)
            assertNotEquals(RecapCursor(), cursor.state.value)
            val owner = EntryOwner()
            val obtained = mutableListOf<LaunchOverlayViewModel>()

            setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalDesktopViewModelFactory provides DesktopViewModelFactory(component.viewModelComponentFactory()::create),
                ) {
                    obtained += hiltViewModel<LaunchOverlayViewModel>()
                    obtained += hiltViewModel<LaunchOverlayViewModel>()
                }
            }

            waitForIdle()
            assertEquals(RecapCursor(), cursor.state.value)
            assertSame(obtained.first(), obtained.last())
        }

    @Test
    fun `each view model receives a saved state handle carrying its owner's arguments`() = runComposeUiTest {
        val genre = EntryOwner(bundleOf("type" to "genre"))
        val year = EntryOwner(bundleOf("type" to "year"))
        val obtained = mutableMapOf<String, HandleProbeViewModel>()

        setContent {
            CompositionLocalProvider(LocalDesktopViewModelFactory provides handleProbeFactory) {
                obtained["genre"] = hiltViewModel(viewModelStoreOwner = genre)
                obtained["year"] = hiltViewModel(viewModelStoreOwner = year)
            }
        }

        waitForIdle()
        assertEquals("genre", obtained.getValue("genre").handle.get<String>("type"))
        assertEquals("year", obtained.getValue("year").handle.get<String>("type"))
        assertNotSame(obtained.getValue("genre").handle, obtained.getValue("year").handle)
    }

    @Test
    fun `a view model the component does not bind fails with its name`() {
        val extras = MutableCreationExtras(EntryOwner().defaultViewModelCreationExtras).apply {
            set(ViewModelProvider.VIEW_MODEL_KEY, "unbound")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            handleProbeFactory.create(UnboundViewModel::class, extras)
        }

        assertTrue(error.message!!.contains(UnboundViewModel::class.qualifiedName!!))
    }

    @Test
    fun `entry point lookup fails when the application context holds no component`() {
        val plain = object : Context() {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = throw UnsupportedOperationException()
            override fun <T> getSystemService(serviceClass: Class<T>): T? = null
        }

        assertThrows(IllegalStateException::class.java) {
            EntryPointAccessors.fromApplication(plain, LaunchProbeEntryPoint::class.java)
        }
    }

    @Test
    fun `entry point lookup fails when the component does not implement the entry point`() {
        val context = OwnerContext(DaggerDesktopViewModelTest_HandleProbeComponent.factory().create(SavedStateHandle()))

        val error = assertThrows(IllegalStateException::class.java) {
            EntryPointAccessors.fromApplication(context, LaunchProbeEntryPoint::class.java)
        }

        assertTrue(error.message!!.contains(LaunchProbeEntryPoint::class.java.name))
    }
}
