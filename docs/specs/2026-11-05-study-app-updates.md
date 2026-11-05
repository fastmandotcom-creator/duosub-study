# Update DuoSub Study from its own releases

## Status

Implemented 2026-11-05. Publish an end-user APK with `python3 tools/publish_study_apk.py` after pushing the code to `main`.

## Context / problem

DuoSub Study is published in `fastmandotcom-creator/duosub-study` as tag `study-<version>` with one asset, `DuoSub-Study.apk`. The published APK (study-1.0.4.9, checked 2026-11-05) is a debug build with application id `duosub.study.study`, versionName `1.0.4-study.9`.

`updateChannelFor` only knows the official app (`duosub.study`, not debuggable) and PR previews (`.preview`). The Study app matches neither, so its channel is `None`: it cannot update itself, and the update dialog opens the releases page of `fastmandotcom-creator/duosub-study`.

## Goals

- A Study build (`duosub.study.study`) checks, downloads, verifies and installs updates from `fastmandotcom-creator/duosub-study` releases, through the same dialog, reminder and automatic-update settings as the official app.
- The dialog's "What's new" and "open releases" links go to the Study repository.
- One local command builds and publishes the next Study APK after its source commit is on `main`, without a PR or test-suite gate.

## Non-goals

- Changing the official or preview channels, or the `update-info.json` / security-fix logic (the Study repository has no such file).
- Moving the project link, the bug-report address and the remote switches file away from `UPDATE_REPOSITORY`. Follow-up if wanted.
- Publishing website changes as part of an APK release.

## User-visible behavior

Settings → App updates and the drawer's Check for updates work in Study. A release `study-1.0.4.8` is newer than the installed `1.0.4-study.7`; the same or an older release is up to date.

## Technical constraints / invariants

- Only the latest non-draft, non-prerelease release is read, through `releases/latest`.
- The APK is the asset named `DuoSub-Study.apk`; it must match GitHub's SHA-256 digest, be this package and have a higher versionCode (existing `AppUpdater` checks). Android requires the same signer for an in-place install; users with an older install signed differently can uninstall it and install this APK fresh.
- Downloads stay limited to GitHub hosts.
- The installed `X.Y.Z-study.N` and the tag `study-X.Y.Z.N` are compared as the numbers `X.Y.Z.N`. A tag `study-1.0.4` equals `1.0.4`.
- Each Study release must raise versionCode and use versionName `X.Y.Z-study.N`, as 1.0.4-study.7 does; the updater cannot work otherwise.
- APK signing is not compared with older releases or treated as a release blocker. The debug build uses the local Android debug signer; if that signer changes, the user can uninstall the old app and install the new one.

## Proposed approach / plan

- `UpdateChannel.Study`, chosen by application id (debuggable allowed, because Study is published as a debug build).
- `parseStudyRelease` in `data/AppUpdates.kt`, next to `parseOfficialRelease`.
- `AppUpdater.checkNow` reads the Study latest release for that channel.
- `AppUpdateUi` opens the Study releases page for that channel.
- `python3 tools/publish_study_apk.py` publishes exactly one verified `DuoSub-Study.apk`, checking only the package and release version needed by the updater; it does not download the previous APK or compare signing certificates.
- Android unit, lint, and managed-device suites remain available only by manual workflow dispatch and do not delay APK release.

## Acceptance criteria

- [x] `updateChannelFor` returns `Study` for `duosub.study.study`, debuggable or not.
- [x] `parseStudyRelease` offers `study-1.0.4.8` to `1.0.4-study.7`, and nothing for `study-1.0.4.7`, an older tag, a draft or prerelease, or a release without `DuoSub-Study.apk`.
- [x] The offered download is the asset's URL with its SHA-256 digest.
- [ ] On a device: Check for updates in an older Study build offers the newer release and installs it (needs a signed older build; not run).
- [x] Study APK publication is a single local command after a direct push to `main`.
- [x] The publishing command checks package/version/signing continuity and publishes `DuoSub-Study.apk`.

## Validation

- Unit tests in `AppUpdatesTest`.
- Not run: instrumented UI, a real update install on a device.
- Owner request: no test suite or separate verification run for this release-flow simplification. Publishing the next APK exercises the necessary build and signing checks.

## Release workflow implementation

- Run `python3 tools/publish_study_apk.py` after the app commit is pushed to `main`; it performs one cached local Gradle `assembleDebug` build for `duosub.study.study`.
- Release numbering follows the existing `study-X.Y.Z.N` channel and uses a monotonically increasing Android version code.
- It uses the local Android debug signer, builds once, then uploads and publishes the asset. Older signatures are not a gate.
- No GitHub signing secret, previous APK download, or automated website publish is required. Android tests remain available through manual workflow dispatch only.

## Release intent

Patch. The change is in `app/src/main`. No exact version.
