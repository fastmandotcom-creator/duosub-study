# Contributing to DualSub Replay

Thank you for helping make language learning on Android more accessible.

This project does **not** use feature branches or pull requests. All development
happens directly on `main`: commit the focused change, push it, and let the
[Android build](.github/workflows/android.yml) verify it. Read on before pushing.

## Before you start

- Search existing issues and [Discussions](https://github.com/fastmandotcom-creator/duosub-study/discussions) to avoid duplicate work.
- For a bug, include the Android version, app version, video URL when it is safe to share, caption languages, and reproducible steps.
- Read [AGENTS.md](AGENTS.md), the [project mission](docs/project/mission.md), and [technical context](docs/project/tech-stack.md). For significant changes, follow the [spec workflow](docs/specs/README.md): define acceptance criteria, a plan, validation, and release intent **before coding**. Discuss unresolved larger scope in an issue first; an already-authorized change does not require another approval round.
- Never include credentials, signing files, personal YouTube data, or copyrighted video/caption dumps.
- Keep each commit to one focused change; explain the user-facing impact and link the spec (or state why a spec is not required). Record actual implementation and validation results against acceptance criteria in the same commit's spec.
- If an AI agent wrote or changed the code, say so in the commit message and follow [AGENTS.md](AGENTS.md#ai-agents-coding-directly-on-main).

## Local setup

Requirements: JDK 17, Android SDK 36, and an Android Studio version compatible with Android Gradle Plugin 9.3. The Gradle 9.5 wrapper is included.

Run the CI-parity checks from PowerShell:

```powershell
.\gradlew.bat formatCheck complexityCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Managed-device tests additionally require the API 36 AOSP x86_64 system image:

```powershell
.\gradlew.bat pixel2Api36DebugAndroidTest
```

On Linux or macOS, use `bash ./gradlew` instead of `.\gradlew.bat`.

## Committing directly on `main`

- Commit on `main`. No forks, no feature branches, no pull requests. Use a clear commit subject line and link the spec in the body; record release intent (see [Release intent](docs/specs/README.md#release-intent-is-a-decision-not-automation)).
- Add or update tests for changed behavior. A code commit that reaches the APK (`app/src/main/**`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` or `gradle/**`) expects a patch release by default; override with `release:minor`, `release:major`, or an exact `Release-Version: X.Y.Z` in the commit body when the change warrants it.
- Preserve the single-WebView playback architecture and the origin checks around JavaScript playback bridges.
- Include screenshots or a short recording for visible UI changes, and say whether you tried it on a real phone (and which one).
- Distinguish *not run* from *passed* in the spec's validation section; documentation-only edits need appropriate link/consistency checks, not claims of runtime testing.

## Publishing a release

The Study APK (`duosub.study.study`, tag `study-*`) is published with one local command **after** the source commit is pushed to `main`:

```powershell
git push origin main
python3 tools/publish_study_apk.py
```

The command builds one verified `DualSub-Study.apk` and publishes it to [GitHub Releases](https://github.com/fastmandotcom-creator/duosub-study/releases). It numbers the release on the `study-X.Y.Z.N` channel and uses the local Android debug signer; the next release increments `N`. See [Update DualSub Study from its own releases](docs/specs/2026-11-05-study-app-updates.md) for the full flow, versioning and recovery.

The Android test suites (unit, lint, build, managed-device) remain available through the [manual `Android build` workflow](https://github.com/fastmandotcom-creator/duosub-study/actions/workflows/android.yml) (`workflow_dispatch`); they do not gate Study release publication. Run them when you want evidence before shipping. Do not skip, disable or loosen a test to get a green run.

Security problems go through a [private report](SECURITY.md), not a public issue or a push to `main`.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
