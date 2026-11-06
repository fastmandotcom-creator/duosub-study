# Keep the spoken subtitle visible and highlight bilingual pairs

## Status

The original landscape/fullscreen correction was published as Study APK
`study-1.0.4.15`. The follow-up pair-highlighting change was committed and pushed as
`7387619`, with its UI test correction in `ca148f4`; the Study APK has not been
republished. Kien clarified the exact expected correspondence (2026-11-06): when TTS
reads the Vietnamese phrase "Xin chào", the English phrase "Hello" translated from that
same caption segment must be highlighted at the same time, both in portrait and
landscape/fullscreen. The published `study-1.0.4.15` predates the pair-highlighting
implementation, and its earlier tests used manually supplied ranges rather than proving
the app derives the pair from caption/translation data.

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
- Keep both translated and original phrase highlights visible for the full read-pair step,
  even as TTS switches from one language side to the other.
- For a sentence split into caption rows, use each row's own whole-sentence translation
  slice as its corresponding phrase; for example, "Xin chào," / "Hello," highlights that
  pair together and not the neighboring "các bạn!" / "friends!" pair.
- In a long sentence, highlight the current spoken short-caption phrase within the full
  sentence, advancing through the punctuation/phrase chunks TTS reads.
- Use one shared color for the two sides of a pair and a different color for each adjacent
  segment pair, cycling through the existing subtitle color palette.
- Keep each language's existing text color, so the two sides remain easy to distinguish.
- Return to following the playback caption and remove the focus when TTS finishes.

## Non-goals

- Change TTS text, timing, playback, or translation.
- Add a new highlight style or user setting.
- Publish only if the implementation passes the applicable validation checks.

## User-visible behavior

While paired-reading TTS speaks, portrait and landscape/fullscreen keep the current segment
pair marked in both languages for the entire primary/secondary read. Matching phrases share
a color; each adjacent segment pair uses a distinct color. In landscape, the
full sentence remains visible while only the current phrase pair is marked. When TTS
finishes, the overlay follows the current video caption again.

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
3. Assign the segment pair a stable color from the existing subtitle palette and apply it
   to both phrase ranges regardless of the current TTS side; keep the side-specific border.
4. Apply the same paired-color mark to both lines of the active portrait transcript row.
5. Add coverage for range/color stability across both speech sides, adjacent pair colors,
   both screen presentations, and focus clearing after TTS.

## Acceptance criteria

- [x] With current index on a later segment and paired reading on an earlier segment,
  `LearningPlayerRoot` supplies the earlier pair to its landscape/fullscreen overlay.
- [x] The overlay remains on the active pair while playback moves ahead.
- [x] Both language phrases of a pair stay highlighted while primary and secondary TTS play.
- [x] With paired rows "Xin chào," / "Hello," and "các bạn!" / "friends!", selecting the
  first row yields source and translation ranges for the first pair only; selecting the
  second yields ranges for the second pair only.
- [x] Adjacent segment pairs have distinct colors shared across both languages.
- [x] The portrait transcript card also highlights both sides of the active segment pair.
- [x] When no paired-reading segment is active, the overlay follows the playback index and
  neither line has read-pair focus.
- [x] Existing whole-sentence rendering and portrait-panel focus behavior remain intact.
- [x] During a long-sentence read, the overlay keeps the full sentence visible and colors
  only the currently spoken source/translated phrase; the active phrase changes with TTS.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Focused unit tests | `./gradlew testDebugUnitTest --tests 'duosub.study.translation.AiSubtitleTranslatorTest' --tests 'duosub.study.ui.CaptionTranslationCancellationTest.shortRowsAreTranslatedFromTheirOwnText' --tests 'duosub.study.ui.TranslationFailureTest.aFailedRowDoesNotInventTranslationForTheNextRow' --tests 'duosub.study.ui.TranslationUpgradesTest' --tests 'duosub.study.ui.LearningPlayerRootTest' --tests 'duosub.study.ui.SubtitleWordHighlightTest'` | Passed; AI tests use an in-memory fake model and need no API key or network |
| Pair-highlight unit tests | `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew testDebugUnitTest --tests 'duosub.study.ui.LearningPlayerRootTest' --tests 'duosub.study.ui.SubtitleHighlightTest'` | Passed |
| Caption-to-translation pair mapping | `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew testDebugUnitTest --tests 'duosub.study.ui.LearningPlayerRootTest.spokenVietnamesePhraseHighlightsItsMatchingEnglishTranslationSlice'` | Passed |
| Actual paired text in UI | `TranslationPositionAndThemeUiTest.pairedReadingKeepsBothLanguagePhrasesHighlightedInOnePairColor` derives overlay ranges from `learningOverlayContent` for "Xin chào," / "Hello,"; verify both side spans on portrait card and overlay | Passed on API 36 in CI run `42183481106` (5/5 tests in this class) |
| Full unit suite | `./gradlew testDebugUnitTest` | 1,137 tests ran; two unrelated existing `SubtitleTextSanitizerTest` assertions fail (`maybe...` and `cool<3`) |
| Android build and lint | `formatNewCode`, focused `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` | Passed locally after the paired-highlight changes |
| Manual Android workflow | `gh workflow run 406759641 --ref main` | Run `42183481106` failed: format ratchet reported the changed UI test plus existing files; API 36 had six failures in existing `LanguageSetupScreenTest` (2), `LearningPlayerUiTest` (1), `RestoredHighlightPlaybackTest` (1), and `SubtitleUiTest` (2). All six also failed in earlier run `42159290817`; the paired-highlight UI test passed |
| Compose UI test | `pixel2Api36DebugAndroidTest` | Refined caption-derived paired-highlight UI test passed on API 36 in run `42183481106`; the local emulator is unavailable |
| Physical-device/manual | In portrait and landscape YouTube fullscreen, let paired reading advance while TTS speaks; both phrase highlights retain one pair color through both sides, adjacent pairs differ, and the overlay follows playback after TTS ends | Not run on a physical device |

## Risks / edge cases

- TTS may be active without a segment id (for example a standalone word); in that case
  the overlay should keep its normal playback-following behavior.
- If the reader's segment id is no longer present, the existing index helper falls back
  to the current playback row.

## Release intent

Study channel. Publish the paired-highlight fix after testing the actual caption-to-
translation phrase alignment; `study-1.0.4.15` does not include that implementation.

## Implementation result

`DuoSubApp` relays the reader's segment id and active side to `LearningPlayerRoot`,
which builds the shared overlay content from that snapshot. Phrase ranges are derived
from the active `SentenceSlice` and the translation fragments in sentence order. Pending
or unavailable translations have no translated phrase range. The full sentence stays
visible; only the active fragment in each language gets the same subtle background and
underline for the entire pair, including while TTS switches sides. Portrait cards apply
the same paired-color mark to both complete segment lines. The pair index selects from a
stable five-color cycle; adjacent pairs always differ. Existing text colors and the
side-specific focus border remain unchanged.

## Validation result

The exact Vietnamese-to-English caption-pair unit test passes: it checks source and
translated ranges for both "Xin chào," / "Hello," and the neighboring "các bạn!" /
"friends!" pair, remaining stable across primary and secondary speech. The refined
caption-derived paired-highlight instrumented test also passed on API 36 in CI run
`42183481106`, including the portrait card and landscape overlay. Locally,
`formatNewCode`, the focused unit test, `compileDebugAndroidTestKotlin`, both debug APK
builds, and lint passed. The CI `verify-build` job failed at `formatCheck`; its one
finding in the modified UI test was fixed in commit `7bd59f5`, while other findings are
in existing files. The API 36 job reported six failures in unrelated existing tests;
the same six failed in earlier run `42159290817`. The full unit suite runs 1,137 tests
but remains blocked by two unrelated `SubtitleTextSanitizerTest` assertion failures.
Earlier AI and TTS focused tests passed; AI tests use `FakeModel` and do not contact an
AI service or require a key. The original implementation was published as
`study-1.0.4.15`; the pair-highlighting implementation remains absent from that APK
until a new Study release.
