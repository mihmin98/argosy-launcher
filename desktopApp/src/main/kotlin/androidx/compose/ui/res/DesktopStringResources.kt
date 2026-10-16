package androidx.compose.ui.res

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int): String = resources().getString(id)

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String = resources().getString(id, *formatArgs)

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int): String = resources().getQuantityString(id, count)

@Composable
@ReadOnlyComposable
fun pluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    resources().getQuantityString(id, count, *formatArgs)

@Composable
@ReadOnlyComposable
private fun resources(): Resources = LocalContext.current.resources
