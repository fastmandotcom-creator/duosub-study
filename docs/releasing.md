# Continuous integration and releases

This page describes the CI and release paths for `main`. There are two channels:

- **Study** (`duosub.study.study`, tag `study-X.Y.Z.N`) — the current way to ship the
  app to end users. It is published from a commit on `main` with one local command,
  `python3 tools/publish_study_apk.py`; it needs no PR, no label, and no test-suite
  gate.
- **Official** (`duosub.study`, tag `vX.Y.Z`) — the legacy pipeline driven by owner
  PR merges and labels. Its release logic lives in
  [tools/automatic_release.py](../tools/automatic_release.py) and runs on merged PRs;
  it is the fallback when the Study channel is not the right target (security-fix
  releases with `update-info.json` gating, or changes that only touch the
  `duosub.study` package).

## Study publishing flow

1. Commit the focused change directly on `main` (see [AGENTS.md](../AGENTS.md)).
2. Push: `git push origin main`.
3. Publish the Study APK: `python3 tools/publish_study_apk.py`.

The command:

- Reads `appVersionName` from [app/build.gradle.kts](../app/build.gradle.kts); each
  release increments the Study build number `N` on the current major.minor.patch base.
- Runs one cached `assembleDebug` with `-PpreviewApplicationIdSuffix=.study`, a
  timestamp-derived `versionCode` that grows monotonically so the in-app updater sees
  a newer version.
- Verifies the package id (`duosub.study.study`), version name, size, and
  permissions.
- Creates (or resumes) a draft GitHub Release tagged `study-X.Y.Z.N`, uploads
  `DualSub-Study.apk`, and publishes it as the latest Study release.

The command does not run the test suite, does not download the previous APK, and does
not compare signing certificates (the Study channel is a debug build signed locally;
a user whose older Study install was signed differently can uninstall and install the
new one fresh). Android unit, lint, build, and managed-device suites remain available
through the [manual `Android build` workflow](../.github/workflows/android.yml)
(`workflow_dispatch`) for extra evidence; they do not delay Study publication.

Versioning rules:

- Each Study release must use `versionName = X.Y.Z-study.N` and tag `study-X.Y.Z.N`;
  the in-app updater compares the installed and offered `X.Y.Z.N` as four numbers.
- `releases/latest` is the newest non-draft, non-prerelease Study release; the
  updater offers it only when it is newer than the installed one.
- A matching draft can be resumed by running the command again; it will not publish
  a lower `N` over a newer one.

## Official (legacy) pipeline

When **fastmandotcom-creator merges a PR into `main`**,
[tools/automatic_release.py](../tools/automatic_release.py) checks that the
latest Android CI run for the PR's final commit succeeded (both jobs:
`verify-build` and `managed-device-tests`) and then builds a production-signed
official APK. Direct pushes and merges by other accounts do not trigger it.

| PR instruction | Result from 1.0.4 |
| --- | --- |
| None, or label `release:patch` | 1.0.5 |
| Label `release:minor` | 1.1.0 |
| Label `release:major` | 2.0.0 |
| A standalone `Release-Version: 1.2.0` line in the PR description | Exactly 1.2.0 |
| Label `release:skip` | No release |

**Security fixes** (owner decision 2026-10-30, [spec](../specs/2026-10-30-scale-readiness.md)):
add the separate label `security:critical` (it combines with any release label) to a
PR that fixes a security problem. Its reservation records `"security": true` and its
release notes start with a note that it fixes a security problem (the app's Security
update dialog links to that page under What was fixed; add a short summary in general
terms, without steps to exploit it, and publish full details in a GitHub security
advisory once most people have updated). Every release from then on publishes
`update-info.json` with that release's `versionCode` as `minimum_safe_version_code`.
Installed apps read that file once a day (Settings → App updates → Tell me about new
versions, on by default): an app older than the latest security release shows a
Security update dialog at once (then daily for 3 days, then weekly, until it updates),
even after later ordinary releases; other new versions show a small banner at most
once a week.

The workflow creates a **draft reservation** before building. The final tag points to
a release-only commit whose parent is the exact merged commit; only
`appVersionName` and `appVersionCode` differ. This keeps tagged source reproducible
without pushing version commits to `main` or bypassing branch protection. The
constants on `main` remain development defaults; release tags and APKs contain the
actual release version.

The draft becomes public only after the production signature, package ID, version
name/code, APK, and checksum have been verified and the APK, its checksum and
`update-info.json` have been uploaded. A draft reserved before `update-info.json`
existed builds from source whose release tool cannot write it, so such a retry fails;
reserve it again with a new merge. Release notes are generated from GitHub's
merged-PR history. Releases queue one at a time (`queue: max`, up to GitHub's
100-pending-run limit) so simultaneous merges cannot reserve the same version. If
runs arrive out of merge order, an older merge is prevented from publishing older
source as a newer version.

**Recovery:** rerun the failed workflow, or choose **Actions → Release merged PR →
Run workflow**, select `main`, and enter the merged PR number. A retry resumes the
same draft, version, source commit, and `versionCode`; changing labels or the PR
description after reservation does not change that release. Already-published
merges are a no-op. Retrying an older reserved release never replaces a newer release
as Latest. Keep the draft's hidden automation marker intact, and do not delete failed
reservations: they prevent version reuse. If checks were not green at merge time,
finish/rerun CI and then retry the release workflow.

The landing page in `site/` (English and Vietnamese) deploys to GitHub Pages when
`site/` or `docs/images/` changes on `main`. The demo video, README GIF, poster and
social card are rendered from code in [tools/promo-video](../tools/promo-video/README.md).

## Local test-suite parity

Android unit, lint, build, and managed-device suites run through the
[manual `Android build` workflow](../.github/workflows/android.yml). They are also
triggered on every push to `main` (via the `setup-gradle` cache step), so Gradle
caches stay current; those runs never count as PR CI.

Release-automation regression tests:

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py' -v
```
