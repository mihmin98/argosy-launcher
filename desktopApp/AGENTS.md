# Argosy Desktop Addendum

`desktopApp/` is the fork-owned desktop build of Argosy (Windows, Linux, macOS).
It compiles the upstream Android sources in `app/` in place and supplies desktop
replacements around them. The root `AGENTS.md` still applies in full; this file
adds the laws that exist only because of the overlay. Nothing here relaxes a root
law, and nothing here licenses an edit under `app/`.

Decisions (D1-D12, S1-S18) live in `docs/DESKTOP_PORT_PLAN.md`, the step order in
`docs/DESKTOP_PORT_IMPLEMENTATION_PLAN.md`, the upstream merge procedure in
`docs/DESKTOP_UPSTREAM_SYNC.md`, and per-step status in `DESKTOP_PORT_PROGRESS.md`
at the repo root. Read the progress file before resuming any step.

## The overlay contract

- `app/` is never edited in the fork: not a source file, not a resource, not a
  build script. Desktop code lives in `desktopApp/`, `scripts/desktop/`,
  `.github/workflows/desktop*.yml` and `gradle/desktop.versions.toml`.
  Check it with `git diff --exit-code $(git merge-base HEAD origin/main) HEAD -- app/`,
  run locally until desktop CI exists (P6-00).
- Shared root files: only `settings.gradle.kts` (the `:desktopApp` include and the
  `desktopLibs` catalog registration) and `.gitignore` may change.
  `build.gradle.kts` and `gradle/libs.versions.toml` stay as upstream has them.
  Exception: if `kotlin.jvm` cannot be applied without a root declaration, one
  `alias(libs.plugins.kotlin.jvm) apply false` line may be added to the root build,
  with the user's approval, registered in `shadows.toml` as a mirror. Any second
  root edit is a violation.
- `sigil/` stays at the upstream commit. A sigil fix goes to `rommapp/argosy-sigil`
  first; its `TRADEMARKS.md` means a divergent sigil would have to be renamed.
- No Room schema change, ever: no entity edit, no migration, no version bump.
  Desktop-only persistence goes in the separate desktop store. A fork migration
  would collide with upstream's next one.
- Upstream symbols are never renamed or re-signatured. A replacement keeps the
  public API its upstream readers compile against.
- Shared versions (Kotlin, Room, Moshi, OkHttp, Retrofit, coroutines, DataStore,
  Dagger = Hilt's line) are pinned to upstream's. When upstream bumps one, the
  desktop catalog follows; desktop never leads.

## Covering upstream code

The overlay compiles `../app/src/main/kotlin` minus what `overlay.toml` excludes.
Desktop supplies whatever the remaining sources need, choosing the cheapest tier
that works:

| Tier | What | Drift behaviour |
|---|---|---|
| T1 compat stub | a class in `android.*`, `androidx.*` or `dagger.hilt.*` on the desktop classpath, with only the members upstream calls | upstream using a new member fails the compile |
| T2 dropped-feature stand-in | same FQN as a class in a dropped package, inert behaviour (empty flows, `false` capabilities) | API changes fail the compile |
| T3 shadow | a desktop copy of a kept upstream file, same FQN, different behaviour | silent; caught only by the drift check |
| T4 mirror | an excluded upstream file whose behaviour desktop re-implements elsewhere | silent; caught only by the drift check |

- A T1 member desktop cannot honour throws
  `UnsupportedOperationException("desktop: <api>")`. A silent no-op is legitimate
  only when no-op is the correct meaning (`Log`, haptics), and the stub's KDoc says
  so. A no-op chosen because throwing broke a caller is a hidden bug, not a stub.
- Every T1 and T2 member is recorded in `docs/stub-ledger.md` with the upstream
  sites that use it.
- T3 and T4 are the expensive tiers. Prefer a stub, then a stand-in; shadow only
  when behaviour must differ. Every shadow and mirror is registered in
  `shadows.toml` with the upstream file's hash and blob id; an unregistered shadow
  is a violation even if it compiles.
- Exclusions are a Spec keyed on the upstream root, never a plain glob: a shadow
  shares its relative path with the file it replaces, and a glob would exclude the
  shadow too.
- Drift: `scripts/desktop/shadow-drift.py` fails when a registered upstream file
  changed. Port the upstream diff into the shadow, then `--accept`. Accepting
  without porting is the one way to silently lose upstream behaviour, and is never
  done to get CI green.
- Generated code (`build/generated/desktop/`: R, BuildConfig, the migrations copy,
  DI and ViewModel modules) is never hand-edited. Fix the generator in
  `scripts/desktop/` and regenerate.

## Desktop laws carried from the decisions

- Emulator ids are Android's (S2). `retroarch_64` and `retroarch_32` collapse to
  `retroarch`. A new id splits cross-device save matching, because the server save
  label derives from it.
- The two emulator resolvers (`EmulatorResolver.getEmulatorPackageForGame`,
  `GameLauncher.resolveEmulator`) stay separate (S17). Desktop supplies inventory
  underneath them; their precedence stays identical to upstream.
- Save base folders change per OS; the id-to-path rules in `docs/save-id-to-path.md`
  (repo root) do not. Base folders come from `HostPaths` tokens, never hardcoded
  absolute paths.
- Every desktop emulator CLI contract, save layout and core id is verified against
  the emulator's upstream sources (root upstream mandate). Analogy with the Android
  build of the same emulator is not evidence.
- Labels: desktop-only strings go in `src/main/res/values/strings_desktop.xml` and
  reach code as `@StringRes Int` through the generated `com.nendo.argosy.desktop.R`.
  The upstream R is generated from `app/src/main/res` and never gains a key.
- File I/O goes through `DesktopFileAccessLayer`. Its `delete`, `move` and
  `listFiles` semantics match Android's tier-1 direct-`File` behaviour exactly, and
  it never opens a path Android refuses.
- A setting desktop cannot honour is removed from the desktop settings, never shown
  inert (root ghost-setting rule). A dropped feature (D9) leaves no entry point.
- Input stays controller-first: gamepad and keyboard drive the ViewModel focus
  index; mouse hover moves the same index (D5). No Compose focus for selection.

## Proof obligations

A step is done when its Verify line passes, stated in terms of the command or
evidence that covered it:

| Claim | Covered by |
|---|---|
| desktop compiles | `:desktopApp:compileKotlin` |
| desktop logic | `:desktopApp:test` |
| Android unchanged | `:app:testDebugUnitTest` plus the empty `app/` diff |
| behaviour | a desktop run on the OS in question |
| save sync | live RomM data |

- `:desktopApp:compileKotlin` verifies the compile and nothing else.
- Save sync, per desktop emulator, per OS (including Flatpak installs): live
  `GET /api/saves`; negotiate returns `no_op` twice on a clean run; Android upload,
  desktop restore and desktop upload leave the content hash, archive root shape and
  `emulator` label unchanged, and the reverse direction too; the file is on disk
  where `SaveSyncEntity.localSavePath` says. Evidence is committed under
  `docs/save-proofs/`. No emulator ships before its proofs pass.
- A guard added to one save door lists every other door and whether it is covered
  (root "a guard added to one path is unfinished").
- Unverified platforms are said to be unverified. A macOS or Windows result taken
  only from a CI build is reported as "builds on CI", not "works".

## Working rules

- The user commits. Agents may run read-only git, `git add <paths>`, and
  `git restore --staged` on files they staged; nothing else that writes. Each step
  ends with its files staged and a one-line message in the repo's format
  (`desktop: <what is now true>`, lowercase, no body, no trailers).
- Agents never install, upgrade or remove software on the machine. A missing tool
  blocks the step; the user gets the exact commands to run.
- When unsure about scope, the files in a step, or whether an operation writes,
  ask before acting.

## Routing (touching X -> read Y first)

- Any desktop step -> its entry in `docs/DESKTOP_PORT_IMPLEMENTATION_PLAN.md`, then
  `DESKTOP_PORT_PROGRESS.md`.
- Merging upstream -> `docs/DESKTOP_UPSTREAM_SYNC.md`.
- A new stub, stand-in or shadow -> `docs/stub-ledger.md`, `shadows.toml`.
- Emulators, cores, save paths -> the platform-support skill and
  `docs/save-id-to-path.md`; save sync -> the root fragile-zone rules.
- RetroAchievements (web API viewing, `retroarch.cfg` credentials) -> the
  ra-compliance skill. Hardcore and the libretro runtime are dropped on desktop (D9).
