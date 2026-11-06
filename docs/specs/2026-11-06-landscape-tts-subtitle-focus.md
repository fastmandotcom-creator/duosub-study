# Keep the spoken subtitle visible and highlighted in landscape

## Status

Implemented. Kien requested the landscape/fullscreen correction and a visible highlight
on the line currently spoken by TTS (2026-11-06). This follows the earlier overlay
investigation; the previous attempt was reverted, and this request authorizes
implementing the behavior. It does not authorize publishing a release.

## Context / problem

In landscape and YouTube fullscreen, the transcript panel is hidden and the single
subtitle overlay follows the playback clock's `currentIndex`. Paired-reading TTS keeps
reading the segment that just ended while that clock can advance, so the overlay can
show a different caption or no relevant caption. The portrait transcript panel already
uses the reader's `readingSegmentId` and `readingSide` to stay on the spoken pair and
highlight the active language.

## Goals

- Keep the landscape/fullscreen overlay on the same caption pair that paired-reading TTS
  is speaking, even after playback advances.
- Highlight only the currently spoken language line in that overlay, using the existing
  read-pair focus styling.
- Return to following the playback caption and remove the focus when TTS finishes.

## Non-goals

- Change TTS text, timing, playback, translation, or the portrait transcript panel.
- Add a new highlight style or user setting.
- Publish a release as part of this implementation.

## User-visible behavior

While paired-reading TTS speaks, the landscape/fullscreen bilingual overlay stays on the
spoken caption instead of advancing with the video and gives the spoken language line a
themed background and border. When TTS finishes, the overlay follows the current video
caption again. The portrait panel retains its existing behavior.

## Technical constraints / invariants

- Keep one YouTube WebView/player and preserve the existing playback/TTS flow.
- Reuse `pairedReadingLineIndex`, `learningOverlayContent`'s existing reader parameters,
  and `PairedReadingFocusLine`; do not add a competing playback state.
- Preserve the full-sentence overlay behavior for rows grouped by `SentenceSlice`.

## Proposed approach / plan

1. Relay `readingSegmentId` and the active `readingSide` from `DuoSubApp` across the
   `CompositionLocal` boundary to the overlay content built in `LearningPlayerRoot`.
2. Keep the existing overlay focus rendering driven by `LearningOverlayContent.readingSide`.
3. Add unit coverage for the overlay remaining on the read segment while `currentIndex`
   advances, and instrumented coverage that the overlay highlights only the active side.

## Acceptance criteria

- [x] With current index on a later segment and paired reading on an earlier segment,
  `LearningPlayerRoot` supplies the earlier pair to its landscape/fullscreen overlay.
- [x] Primary and secondary TTS steps highlight only their corresponding overlay line.
- [x] When no paired-reading segment is active, the overlay follows the playback index and
  neither line has read-pair focus.
- [x] Existing whole-sentence rendering and portrait-panel focus behavior remain intact.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `./gradlew testDebugUnitTest --tests "duosub.study.ui.LearningPlayerRootTest"` passes | Local Android SDK; test-source compilation may be blocked by an existing unrelated error in `AiSubtitleTranslatorTest.kt` |
| Android build | `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew assembleDebug` passes | Local Android SDK |
| Instrumented | `./gradlew assembleDebugAndroidTest` passes and the paired-reading focus test shows only the spoken line highlighted | Android test toolchain |
| Physical-device/manual | In landscape YouTube fullscreen, let paired reading advance while TTS speaks; the displayed pair stays fixed, the spoken side is highlighted, and the overlay follows playback after TTS ends | Owner's phone; not established by offline tests |

## Risks / edge cases

- TTS may be active without a segment id (for example a standalone word); in that case
  the overlay should keep its normal playback-following behavior.
- If the reader's segment id is no longer present, the existing index helper falls back
  to the current playback row.

## Release intent

Study channel (default). No APK publication requested; publish only when the owner
explicitly asks.

## Implementation result

`DuoSubApp` now relays the reader's segment id and active side to
`LearningPlayerRoot`, which builds the shared overlay content from that snapshot.
This crosses the existing `CompositionLocal` boundary while keeping the portrait panel
and fullscreen/landscape overlays on the same TTS state. The overlay's existing
`PairedReadingFocusLine` styling provides the active-line background and border. The
instrumented focus test also checks that the highlight disappears when reading ends.

## Validation result

- Passed: `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew assembleDebug assembleDebugAndroidTest` — app and instrumented test code compile.
- Passed: `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew lintDebug`.
- Not passed: `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew testDebugUnitTest --tests 'duosub.study.ui.LearningPlayerRootTest'` — existing unrelated compile error at `AiSubtitleTranslatorTest.kt:111` (too many arguments to `withTranslation`).
- Not run to completion: `pixel2Api36DebugAndroidTest` for `TranslationPositionAndThemeUiTest#pairedReadingVisiblyEmphasizesOnlyTheSideBeingSpoken` — managed emulator could not start after five attempts; instrumented APK compilation passed.
- Not passed: `formatCheck` — this host has `python3` but no `python` command. Running `python3 tools/check_format_ratchet.py` also reports existing formatting-budget violations in unrelated files.
- Not passed: `complexityCheck` — reports `SubtitlePanel` at 115 lines against the 100-line limit; the function is outside this change.
- Not run: physical-device landscape/fullscreen and live YouTube behavior; requires the owner's phone.
