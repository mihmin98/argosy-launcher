package com.nendo.argosy.desktop.di

/**
 * The application context's hold on its Dagger component, the desktop counterpart of Hilt's
 * generated component manager. `EntryPointAccessors.fromApplication` casts [component] to the
 * requested entry point.
 */
interface DesktopComponentOwner {
    val component: Any
}
