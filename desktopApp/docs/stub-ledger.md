# Desktop Stub Ledger

Every T1 compat stub and T2 dropped-feature stand-in member on the desktop classpath,
with the upstream sites that use it (`desktopApp/AGENTS.md`, "Covering upstream code").
A member is added here in the same change that adds it to the stub.

| Tier | FQN | Member | Behaviour | Used by (upstream) | Added in |
|---|---|---|---|---|---|
| T1 | `android.content.Context` | `getFilesDir()` | abstract; desktop context implements | `data/repository/DocumentHighlightStore` | P0-06 |
| T1 | `dagger.hilt.android.qualifiers.ApplicationContext` | annotation | `@Qualifier`, same targets as Hilt's | 152 upstream injection sites (1 compiled so far: `DocumentHighlightStore`) | P0-06 |
| T1 | `android.content.Context` | `getApplicationContext()` | abstract; desktop context returns itself (P1-02) | the 4 `@EntryPoint` sites pass `context.applicationContext` (0 compiled); read by the desktop `EntryPointAccessors` | P0-07 |
| T1 | `android.content.Context` | `getSystemService(Class)` | abstract; desktop context returns `null` for every service (P1-02). Android's is final over `getSystemServiceName`; reshape when a caller needs the name form | `androidx.core.content.getSystemService` → `ui/input/HapticFeedback` | P0-07 |
| T1 | `androidx.core.content` | `Context.getSystemService<T>()` | delegates to `Context.getSystemService(Class)` | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.util.Log` | `d`/`i`/`w`/`e`, with and without `Throwable` | `L/tag: message` line (plus stack trace) on stderr; returns the line length | `util/Logger` | P0-07 |
| T1 | `android.content.Intent` | `getAction`, `getPackage`, `getComponent`, `getData`, `getExtras` | no instance exists (private constructor); members throw | `util/Logger.describeIntent` | P0-07 |
| T1 | `android.content.ComponentName` | `getShortClassName()` | no instance exists; throws | `util/Logger.describeIntent` | P0-07 |
| T1 | `android.net.Uri` | `getScheme()`, `getPath()` | no instance exists; members throw. The plan's `java.net.URI`-backed `Uri` replaces this when a caller creates one | `util/Logger.describeIntent` | P0-07 |
| T1 | `android.os.Bundle` | `isEmpty()`, `keySet()` | no instance exists; members throw | `util/Logger.describeIntent` | P0-07 |
| T1 | `android.os.Build` | `VERSION.SDK_INT`; `VERSION_CODES.Q`/`S`/`TIRAMISU` | `SDK_INT = 0` (every API gate takes its oldest path); codes are the SDK's 29/31/33 | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.os.SystemClock` | `uptimeMillis()` | `System.nanoTime() / 1_000_000` | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.os.Vibrator` | `hasVibrator()`, `hasAmplitudeControl()`, `vibrate(effect)`, `vibrate(effect, attributes)` | abstract; no implementation yet, P2-08 maps it to Jamepad rumble | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.os.VibratorManager` | `getDefaultVibrator()` | abstract; no implementation yet (P2-08) | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.os.VibrationEffect` | `DEFAULT_AMPLITUDE`, `EFFECT_CLICK`/`DOUBLE_CLICK`/`TICK`/`HEAVY_CLICK`; `createOneShot`, `createWaveform(long[], int[], int)`, `createPredefined` | constants are the SDK's; factories throw (unreachable while no `Vibrator` exists) | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.os.VibrationAttributes` | `USAGE_TOUCH`; `createForUsage` | constant is the SDK's 18; factory throws (unreachable at `SDK_INT` 0) | `ui/input/HapticFeedback` | P0-07 |
| T1 | `android.view.KeyEvent` | `KEYCODE_UNKNOWN`, `KEYCODE_BUTTON_C`, `KEYCODE_BUTTON_Z` | the SDK's 0/98/101 | `data/preferences/ControlsPreferencesRepository` | P0-07 |
| T1 | `dagger.hilt.android.lifecycle.HiltViewModel` | annotation | marker read by `scripts/desktop/gen-vm-module.py` | 33 upstream view models (1 compiled: `LaunchOverlayViewModel`) | P0-07 |
| T1 | `dagger.hilt.android.EntryPointAccessors` | `fromApplication(Context, Class)` | casts the application context's `DesktopComponentOwner.component`; `IllegalStateException` when the context holds no component or the component lacks the entry point | 4 upstream sites (0 compiled) | P0-07 |
| T1 | `androidx.hilt.navigation.compose` | `hiltViewModel(viewModelStoreOwner, key)` | `viewModel(factory = LocalDesktopViewModelFactory.current)`: one `DesktopViewModelComponent` per view model, handle from the owner's creation extras | 31 upstream calls, all no-argument (0 compiled) | P0-07 |

T1 stubs of Java APIs are written in Java (`desktopApp/src/main/java`), so upstream Kotlin
keeps its property syntax over Java getters (`context.filesDir`) and explicit getter calls
both resolve, as on Android. Stubs of Kotlin APIs (Hilt's `EntryPointAccessors`,
`hiltViewModel`, androidx.core extensions) are written in Kotlin
(`desktopApp/src/main/kotlin`).

P0-04 (2026-10-14): the narrowed data core (`data/local/{converter,dao,entity}`,
`ALauncherDatabase`, `data/model`, `PlatformDefinitions`, `SearchNormalizer`; 161 files)
imports only Room, coroutines and `java.*`, so it compiled with no stubs.

P0-07 (2026-10-16): `LaunchOverlayViewModel`'s closure (17 more files) needed the Logger
and haptics stubs above. `HapticPattern` lives in `ui/input/HapticFeedback.kt`, which
`InputHandler` (every screen) references, so the haptics manager compiles here; with no
vibrator service it is inert until P2-08.
