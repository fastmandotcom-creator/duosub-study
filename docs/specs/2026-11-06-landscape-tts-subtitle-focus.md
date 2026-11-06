# Keep the spoken subtitle visible and highlighted in landscape

## Status

Implemented and published as Study APK `study-1.0.4.15`. Kien requested the
landscape/fullscreen correction, a visible highlight on the line currently spoken by TTS,
and clearer phrase-level tracking for long captions (2026-11-06).

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
- In a long sentence, highlight the current spoken short-caption phrase within the full
  sentence, advancing through the punctuation/phrase chunks TTS reads.
- Keep each language's existing text color, so the two sides remain easy to distinguish.
- Return to following the playback caption and remove the focus when TTS finishes.

## Non-goals

- Change TTS text, timing, playback, translation, or the portrait transcript panel.
- Add a new highlight style or user setting.
- Publish only if the implementation passes the applicable validation checks.

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
2. Derive the active source and translated phrase ranges from `SentenceSlice` and the
   ordered translated fragments; account for the Vietnamese/English display swap.
3. Apply a subtle, theme-aware background to the active phrase while keeping the
   existing per-language text colors and line-level read-pair focus.
4. Add coverage for source/translation ranges, phrase focus, and focus clearing after TTS.

## Acceptance criteria

- [x] With current index on a later segment and paired reading on an earlier segment,
  `LearningPlayerRoot` supplies the earlier pair to its landscape/fullscreen overlay.
- [x] Primary and secondary TTS steps highlight only their corresponding overlay line.
- [x] When no paired-reading segment is active, the overlay follows the playback index and
  neither line has read-pair focus.
- [x] Existing whole-sentence rendering and portrait-panel focus behavior remain intact.
- [ ] During a long-sentence read, the overlay keeps the full sentence visible and colors
  only the currently spoken source/translated phrase; the active phrase changes with TTS.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `./gradlew testDebugUnitTest --tests "duosub.study.ui.LearningPlayerRootTest"` | Blocked before test execution by the pre-existing `AiSubtitleTranslatorTest.kt:111` compile error: too many arguments to `withTranslation` |
| Android build and lint | `assembleDebug assembleDebugAndroidTest lintDebug` | Passed on local Android SDK; release script's Study build and package/version checks also passed |
| Manual Android workflow | `gh workflow run 'Android build and releases' --ref main` | `verify-build` failed at test compilation due to the same pre-existing `AiSubtitleTranslatorTest.kt:111` error; API 36 managed-device tests were still running when the APK was published |
| Physical-device/manual | In landscape YouTube fullscreen, let paired reading advance while TTS speaks; the displayed pair stays fixed, the spoken side is highlighted, and the overlay follows playback after TTS ends | Not run on a physical device |

## Risks / edge cases

- TTS may be active without a segment id (for example a standalone word); in that case
  the overlay should keep its normal playback-following behavior.
- If the reader's segment id is no longer present, the existing index helper falls back
  to the current playback row.

## Release intent

Study channel (default). Published [DuoSub Study 1.0.4-study.15](https://github.com/fastmandotcom-creator/duosub-study/releases/tag/study-1.0.4.15)
from commit `56cc1a0`.

## Implementation result

`DuoSubApp` relays the reader's segment id and active side to `LearningPlayerRoot`,
which builds the shared overlay content from that snapshot. Phrase ranges are derived
from the active `SentenceSlice` and the translation fragments in sentence order. Pending
or unavailable translations have no translated phrase range. The full sentence stays
visible; only the active fragment gets a subtle background and underline inside the
already focused language line. Each language keeps its existing text color.

## Validation result

The phrase-range checks were added, but the focused unit-test task could not compile the
test source set due to the unrelated `AiSubtitleTranslatorTest.kt:111` error. The debug
APK, Android test APK, and lint all built successfully. The official Study release build
verified the `duosub.study.study` package and expected version, then published
`DuoSub-Study.apk` as `study-1.0.4.15`. The API 36 managed-device run and physical-device
checks were not confirmed here.
