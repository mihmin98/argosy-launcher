package androidx.hilt.navigation.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nendo.argosy.desktop.di.LocalDesktopViewModelFactory

/**
 * Desktop stand-in for Hilt's `hiltViewModel()`: the [VM] scoped to [viewModelStoreOwner], built
 * by the [LocalDesktopViewModelFactory] in composition.
 */
@Composable
inline fun <reified VM : ViewModel> hiltViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    },
    key: String? = null,
): VM = viewModel(
    viewModelStoreOwner = viewModelStoreOwner,
    key = key,
    factory = LocalDesktopViewModelFactory.current,
)
