# Update DualSub Study from its own releases

## Status

Implemented 2026-11-05 (end-user report in chat: automatic update still uses the old repository).

## Context / problem

DualSub Study is published by hand in `fastmandotcom-creator/duosub-study` as tag `study-<version>` with one asset, `DualSub-Study.apk`. The published APK (study-1.0.4.7, checked 2026-11-05) is a debug build with application id `com.kienhoang.dualsubreplay.study`, versionName `1.0.4-study.7`, versionCode 41.

`updateChannelFor` only knows the official app (`com.kienhoang.dualsubreplay`, not debuggable) and PR previews (`.preview`). The Study app matches neither, so its channel is `None`: it cannot update itself, and the update dialog opens the releases page of `fastmandotcom-creator/duosub-study`.

## Goals

- A Study build (`com.kienhoang.dualsubreplay.study`) checks, downloads, verifies and installs updates from `fastmandotcom-creator/duosub-study` releases, through the same dialog, reminder and automatic-update settings as the official app.
- The dialog's "What's new" and "open releases" links go to the Study repository.

## Non-goals

- Changing how Study releases are built or published (still by hand; no workflow change here).
- Changing the official or preview channels, or the `update-info.json` / security-fix logic (the Study repository has no such file).
- Moving the project link, the bug-report address and the remote switches file away from `UPDATE_REPOSITORY`. Follow-up if wanted.

## User-visible behavior

Settings → App updates and the drawer's Check for updates work in Study. A release `study-1.0.4.8` is newer than the installed `1.0.4-study.7`; the same or an older release is up to date.

## Technical constraints / invariants

- Only the latest non-draft, non-prerelease release is read, through `releases/latest`.
- The APK is the asset named `DualSub-Study.apk`; it must match GitHub's SHA-256 digest, be this package and have a higher versionCode (existing `AppUpdater` checks). Android still refuses a different signing key.
- Downloads stay limited to GitHub hosts.
- The installed `X.Y.Z-study.N` and the tag `study-X.Y.Z.N` are compared as the numbers `X.Y.Z.N`. A tag `study-1.0.4` equals `1.0.4`.
- Each Study release must raise versionCode and use versionName `X.Y.Z-study.N`, as 1.0.4-study.7 does; the updater cannot work otherwise.

## Proposed approach / plan

- `UpdateChannel.Study`, chosen by application id (debuggable allowed, because Study is published as a debug build).
- `parseStudyRelease` in `data/AppUpdates.kt`, next to `parseOfficialRelease`.
- `AppUpdater.checkNow` reads the Study latest release for that channel.
- `AppUpdateUi` opens the Study releases page for that channel.

## Acceptance criteria

- [x] `updateChannelFor` returns `Study` for `com.kienhoang.dualsubreplay.study`, debuggable or not.
- [x] `parseStudyRelease` offers `study-1.0.4.8` to `1.0.4-study.7`, and nothing for `study-1.0.4.7`, an older tag, a draft or prerelease, or a release without `DualSub-Study.apk`.
- [x] The offered download is the asset's URL with its SHA-256 digest.
- [ ] On a device: Check for updates in an older Study build offers the newer release and installs it (needs a signed older build; not run).

## Validation

- Unit tests in `AppUpdatesTest`.
- Not run: instrumented UI, a real update install on a device.

## Release intent

Patch. The change is in `app/src/main`. No exact version.
