# Update DualSub Study from its own releases

## Status

Implemented 2026-11-05. End-user APK release will run from the next matching push to `main`.

## Context / problem

DualSub Study is published in `fastmandotcom-creator/duosub-study` as tag `study-<version>` with one asset, `DualSub-Study.apk`. The published APK (study-1.0.4.8, checked 2026-11-05) is a debug build with application id `duosub.study.study`, versionName `1.0.4-study.8`, versionCode 42.

`updateChannelFor` only knows the official app (`duosub.study`, not debuggable) and PR previews (`.preview`). The Study app matches neither, so its channel is `None`: it cannot update itself, and the update dialog opens the releases page of `fastmandotcom-creator/duosub-study`.

## Goals

- A Study build (`duosub.study.study`) checks, downloads, verifies and installs updates from `fastmandotcom-creator/duosub-study` releases, through the same dialog, reminder and automatic-update settings as the official app.
- The dialog's "What's new" and "open releases" links go to the Study repository.
- A push to `main` that changes shipped app code automatically builds and publishes the next Study APK release directly, without a PR, unit-test suite, or managed-device test gate.

## Non-goals

- Changing the official or preview channels, or the `update-info.json` / security-fix logic (the Study repository has no such file).
- Moving the project link, the bug-report address and the remote switches file away from `UPDATE_REPOSITORY`. Follow-up if wanted.
- Publishing website changes as part of an APK release.

## User-visible behavior

Settings → App updates and the drawer's Check for updates work in Study. A release `study-1.0.4.8` is newer than the installed `1.0.4-study.7`; the same or an older release is up to date.

## Technical constraints / invariants

- Only the latest non-draft, non-prerelease release is read, through `releases/latest`.
- The APK is the asset named `DualSub-Study.apk`; it must match GitHub's SHA-256 digest, be this package and have a higher versionCode (existing `AppUpdater` checks). Android still refuses a different signing key.
- Downloads stay limited to GitHub hosts.
- The installed `X.Y.Z-study.N` and the tag `study-X.Y.Z.N` are compared as the numbers `X.Y.Z.N`. A tag `study-1.0.4` equals `1.0.4`.
- Each Study release must raise versionCode and use versionName `X.Y.Z-study.N`, as 1.0.4-study.7 does; the updater cannot work otherwise.
- Study APK releases use the persistent CI debug-signing key and must keep the same certificate as the previous public Study APK so Android can install updates over it.
- Release work is limited to shipped-app/build changes; documentation, test-only, and website-only changes do not start an APK build.

## Proposed approach / plan

- `UpdateChannel.Study`, chosen by application id (debuggable allowed, because Study is published as a debug build).
- `parseStudyRelease` in `data/AppUpdates.kt`, next to `parseOfficialRelease`.
- `AppUpdater.checkNow` reads the Study latest release for that channel.
- `AppUpdateUi` opens the Study releases page for that channel.
- A successful push-triggered workflow publishes exactly one verified `DualSub-Study.apk`; unrelated pushes do not start APK builds.
- Android unit, lint, and managed-device suites remain available only by manual workflow dispatch and do not delay APK release.

## Acceptance criteria

- [x] `updateChannelFor` returns `Study` for `duosub.study.study`, debuggable or not.
- [x] `parseStudyRelease` offers `study-1.0.4.8` to `1.0.4-study.7`, and nothing for `study-1.0.4.7`, an older tag, a draft or prerelease, or a release without `DualSub-Study.apk`.
- [x] The offered download is the asset's URL with its SHA-256 digest.
- [ ] On a device: Check for updates in an older Study build offers the newer release and installs it (needs a signed older build; not run).
- [x] App-code pushes to `main` publish the Study APK without starting the general Android test workflow.
- [x] The publishing workflow checks package/version/signing continuity, reuses a draft for the same source commit, and publishes `DualSub-Study.apk`.

## Validation

- Unit tests in `AppUpdatesTest`.
- Not run: instrumented UI, a real update install on a device.
- Owner request: no test suite or separate verification run for this release-workflow simplification. The first matching push will exercise the APK build and signing checks.

## Release workflow implementation

- Direct pushes to `main` that change app or build inputs run one cached Gradle `assembleDebug` build for `duosub.study.study`; tests, lint, and managed-device tests remain manual-only.
- Release numbering follows the existing `study-X.Y.Z.N` channel and uses a monotonically increasing Android version code.
- The workflow requires the existing cached signing key and compares its APK certificate with the current public Study APK before publishing.
- Website publishing is not coupled to APK releases.

## Release intent

Patch. The change is in `app/src/main`. No exact version.
