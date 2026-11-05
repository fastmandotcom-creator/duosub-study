# Move the Android package to `duosub.study`

## Status

Validated. The user explicitly chose the exact package `duosub.study` and
excluded the website. Use only the project's official GitHub repository when a
repository link is needed.

## Context / problem

The Android source and installed application still use the old
`com.kienhoang.dualsubreplay` identity. A pending local migration changed it to
`com.duosub.study`, but that is not the requested identity and its update-channel
implementation no longer matches its callers and tests.

## Goals

- Use `duosub.study` exactly as the Android namespace and application ID.
- Use `duosub.study` as the root Kotlin package and align source/test paths and
  package references with it.
- Keep official, preview, and Study update channels routed to the correct
  releases for their respective application IDs.
- Keep all download and repository references on the official
  `fastmandotcom-creator/duosub-study` GitHub repository.

## Non-goals

- Website or landing-page changes.
- Changing release versions, signing keys, app branding, or release automation
  policy.
- Automatically transferring private data from an installation using the old
  application ID.

## User-visible behavior

The new APK installs as `duosub.study`. Android treats it as a different app
from `com.kienhoang.dualsubreplay`; it does not upgrade the old installation or
inherit its private data. The old app can remain installed separately.

## Technical constraints / invariants

- Android application identity is the `applicationId`; changing it breaks
  in-place upgrades regardless of matching signing certificates.
- `namespace`, Kotlin package declarations, generated `R`/`BuildConfig`
  references, manifests, tests, benchmark code, and build workflows must agree
  with the new package.
- Official updates, PR previews, and Study updates must remain separate
  channels. The expected IDs are `duosub.study`, `duosub.study.preview`, and
  `duosub.study.study`.
- Existing updater host allowlisting, APK identity/checksum/version validation,
  and GitHub release behavior remain intact.

## Proposed approach / plan

### Files and responsibilities

- `app/src/{main,test,androidTest,benchmark}/java/` and
  `benchmark/src/main/java/`: Kotlin packages and matching source paths moved
  from `com/kienhoang/dualsubreplay` to `duosub/study`.
- `app/build.gradle.kts`, `benchmark/build.gradle.kts`, and
  `app/src/benchmark/AndroidManifest.xml`: app/benchmark namespace and package
  configuration.
- `app/src/main/java/duosub/study/data/AppUpdates.kt`,
  `app/src/main/java/duosub/study/update/AppUpdater.kt`,
  `app/src/main/java/duosub/study/ui/AppUpdateUi.kt`, and
  `app/src/test/java/duosub/study/data/AppUpdatesTest.kt`: package-specific
  update channel selection and release parsing.
- `.github/workflows/android.yml`, `.github/workflows/release-on-main.yml`,
  `tools/report_apk.py`, `tools/capture_performance.py`, and
  `tools/tests/test_report_failed_tests.py`: CI selectors, release package
  verification, APK inspection, and test diagnostics.
- `AGENTS.md`, `docs/project/tech-stack.md`,
  `docs/quality-and-performance.md`, and
  `docs/specs/2026-11-05-study-app-updates.md`: active package documentation.
  Historical QA evidence is not rewritten.

### Tasks

1. **Test channel identity first.** In `AppUpdatesTest.kt`, use
   `duosub.study` for the official ID, `duosub.study.preview` for PR previews,
   and `duosub.study.study` for Study. Keep assertions that debug official
   builds, smoke builds, and preview IDs without a PR/build suffix select
   `None`. Run the focused test and confirm it fails against the incomplete
   pending `AppUpdates.kt`.
2. **Restore updater routing without changing release behavior.** In
   `AppUpdates.kt`, restore the explicit Official/Preview/Study/None channels,
   official and Study asset names/URLs, and the preview version parser. Set the
   official application ID to exactly `duosub.study`; derive preview and Study
   IDs from it. Keep the existing parsers, checksums, package/signature checks,
   and GitHub host allowlist. Run `AppUpdatesTest` and keep
   `AppUpdater`/`AppUpdateUi` exhaustive over every channel.
3. **Move Kotlin packages and paths.** Move app main, unit-test, device-test,
   and benchmark sources from `com/duosub/study` to `duosub/study`; move
   benchmark-module sources from `com/duosub/study` to `duosub/study`. Update
   every affected package declaration, import, and generated class reference.
4. **Align build and tooling metadata.** Set app `namespace` and
   `applicationId` to `duosub.study`; set benchmark namespace/application
   references consistently; update release APK identity checks, managed-test
   selectors, APK-report package detection, startup activity names, and the
   package assertions in tool tests.
5. **Update active documentation and verify scope.** Replace the wrong package
   in active project/spec/quality docs. Do not edit `site/` or historical QA
   evidence. Run focused unit tests, format, lint, APK build, and the Python
   tooling tests; search active code/config/docs for `com.kienhoang` and
   `com.duosub.study`, and check that all project URLs still use only the
   official GitHub repository.

## Acceptance criteria

- [x] Gradle `namespace` and `applicationId` are exactly `duosub.study`.
- [x] App/test/benchmark Kotlin declarations and imports build under
  `duosub.study`.
- [x] Update-channel tests prove `duosub.study` is Official,
  `duosub.study.preview` is Preview, and `duosub.study.study` is Study.
- [x] Existing update integrity checks and per-channel release parsing tests
  pass.
- [x] No `com.kienhoang` or `com.duosub.study` package references remain in
  active source/build configuration.
- [x] No website files are changed; repository links point only to the official
  project repository.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `testDebugUnitTest` passed, including `AppUpdatesTest` and the full app unit suite | Local JDK 17 and Android SDK |
| Android lint/build | `formatCheck complexityCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest :app:compileBenchmarkKotlin :benchmark:assemble` passed using `--no-daemon --rerun-tasks`; `formatCheck` used a temporary `python` → `python3` PATH alias because this host only provides `python3` | Local Android SDK |
| APK identity | `aapt dump badging app/build/outputs/apk/debug/app-debug.apk` reported `package: name='duosub.study'` | Local Android SDK build-tools 36.0.0 |
| Python tooling | `python3 -m unittest discover -s tools/tests -p 'test_*.py' -v`; 42 tests passed | Local Python 3 |
| Managed-device/emulator | Not run: no connected device and API 36 AOSP x86_64 system image is not installed | SDK has API 36.1 Google Play ARM64 and API 34 ARM64 images |
| Physical-device/manual | Not run; no physical device attached | Not available |
| Live YouTube | Not applicable; package migration does not change playback | — |
| Performance (baseline/target) | Not applicable | — |
| Documentation/process | Search for stale package IDs; verify website paths are untouched | Local repository |

## Risks / edge cases

- Existing users cannot update the old package in place and app-private data
  does not migrate automatically.
- A wrongly routed update could offer an APK with a different package or
  signing key; existing package and signature checks must remain enforced.

## Release intent

`release:patch`. The Android application identity changes; no manual version
constant changes are part of this migration.

## Implementation result

Moved the app, app test, app benchmark, and benchmark-module Kotlin package
trees to `duosub.study`; aligned Android IDs, CI/package assertions, APK tools,
and active package documentation. Restored Official, Preview, Study, and None
update routing and the associated release parsers/integrity helpers. Website
files and historical QA evidence were left untouched.

## Validation result

All listed local unit, formatting-ratchet, complexity, lint, assembly, and
Python tooling checks passed. The built APK reports package ID `duosub.study`.
Managed-device and physical-device tests were not run because their required
device environments are unavailable. The work remains uncommitted in the
existing dirty `main` worktree; some package moves are staged to keep the
pre-existing index consistent. No commit, push, or release was performed.
