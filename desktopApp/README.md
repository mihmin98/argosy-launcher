# Argosy Desktop

A desktop build of [Argosy](../README.md) for Windows x64, Linux x64 and macOS
(arm64 and x64), maintained as a fork of `rommapp/argosy-launcher`. It compiles the
Android app's sources from `../app` in place, so `app/` stays identical to upstream
and the Android build is unaffected.

Built-in libretro cores and the features that depend on them, social, music,
dual-screen and Android-only integrations are not part of the desktop build; see
`docs/DESKTOP_PORT_PLAN.md` (D9) for the full list. Games launch in external
emulators.

The port is being built in phases. `DESKTOP_PORT_PROGRESS.md` at the repo root says
which steps have landed; commands below that depend on a later step say so.

## Requirements

- **JDK 21.** Gradle 8.9 cannot run on newer JDKs, so point `JAVA_HOME` at 21 even
  when the system default is newer:
  ```
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
  ```
- **Submodules:** `git submodule update --init --recursive`.
- **Android SDK** with `platforms;android-35` and `build-tools;34.0.0`, referenced
  from `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`. The desktop build does
  not use it, but the Android modules share the Gradle build and fail to configure
  without it. On first configuration AGP downloads the NDK that `libretrodroid`
  needs.
- **CMake 3.20+ and a C compiler** (gcc/clang, MinGW-w64 or MSVC, Xcode command line
  tools) to build the sigil JNI library.
- **Python 3** for the generators and checks in `scripts/desktop/`.
- **libmpv** (video playback) and **yt-dlp** (YouTube backgrounds) at runtime.

## Build and run

All commands run from the repo root.

| What | Command | Available from |
|---|---|---|
| Compile | `./gradlew :desktopApp:compileKotlin` | P0-02 |
| Tests | `./gradlew :desktopApp:test` | P0-04 |
| Run | `./gradlew :desktopApp:run` | P2-01 |
| sigil JNI (Linux, macOS) | `desktopApp/native/build-sigil.sh` | P0-09 |
| sigil JNI (Windows) | `desktopApp/native/build-sigil.ps1` | P0-09 |
| Shadow drift check | `python3 scripts/desktop/shadow-drift.py` | P0-12 |
| Installers | see `docs/DESKTOP_PORT_IMPLEMENTATION_PLAN.md`, P6-04 | P6-04 |

The Android app still builds and tests as upstream documents:
`./gradlew :app:testDebugUnitTest`.

## Data locations

| OS | Data and config | Cache and logs |
|---|---|---|
| Linux | `$XDG_DATA_HOME/ArgosyDesktop`, `$XDG_CONFIG_HOME/ArgosyDesktop` | `$XDG_CACHE_HOME/ArgosyDesktop` |
| Windows | `%APPDATA%\ArgosyDesktop` | `%LOCALAPPDATA%\ArgosyDesktop` |
| macOS | `~/Library/Application Support/ArgosyDesktop` | `~/Library/Caches/ArgosyDesktop`, `~/Library/Logs/ArgosyDesktop` |

A `portable.txt` next to the launcher keeps everything in `<launcher dir>/data`.

## Contributing

Read `AGENTS.md` in this directory before changing anything here; it holds the
overlay contract, which says what desktop may and may not touch. The upstream
`AGENTS.md` and `CONTRIBUTING.md` apply as well.
