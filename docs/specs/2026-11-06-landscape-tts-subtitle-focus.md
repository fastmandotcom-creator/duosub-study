# Keep the spoken subtitle visible and highlight bilingual pairs

## Status

The original landscape/fullscreen correction was published as Study APK
`study-1.0.4.15`. The follow-up pair-highlighting change was committed and pushed as
`7387619`, with its UI test correction in `ca148f4`; the Study APK has not been
republished. Kien requested the original
correction, a visible highlight on the line currently spoken by TTS, and clearer
phrase-level tracking for long captions (2026-11-06). Follow-up request (2026-11-06):
keep both sides of the active pair highlighted throughout primary and secondary speech,
and give each consecutive pair a distinct color.

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
| Full unit suite | `./gradlew testDebugUnitTest` | 1,137 tests ran; two unrelated existing `SubtitleTextSanitizerTest` assertions fail (`maybe...` and `cool<3`) |
| Android build and lint | `formatNewCode`, focused `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` | Passed locally after the paired-highlight changes |
| Manual Android workflow | `gh workflow run 406759641 --ref main` (run `42180222366`, commit `ca148f4`) | `verify-build` failed at `formatCheck` on untouched existing files. The API 36 run passed the new pair-highlight UI test but reported six unrelated existing UI test failures |
| Compose UI test | `pixel2Api36DebugAndroidTest` | `TranslationPositionAndThemeUiTest.pairedReadingKeepsBothLanguagePhrasesHighlightedInOnePairColor` passed on API 36 in run `42180222366`; the full device suite failed on six other existing tests |
| Physical-device/manual | In portrait and landscape YouTube fullscreen, let paired reading advance while TTS speaks; both phrase highlights retain one pair color through both sides, adjacent pairs differ, and the overlay follows playback after TTS ends | Not run on a physical device |

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
visible; only the active fragment in each language gets the same subtle background and
underline for the entire pair, including while TTS switches sides. Portrait cards apply
the same paired-color mark to both complete segment lines. The pair index selects from a
stable five-color cycle; adjacent pairs always differ. Existing text colors and the
side-specific focus border remain unchanged.

## Validation result

The pair-highlight unit tests pass with the command listed above. The combined local run
of `formatNewCode`, focused `testDebugUnitTest`, `assembleDebug`,
`assembleDebugAndroidTest`, and `lintDebug` passed, and the corrected instrumented test
source compiles locally. On API 36, `pairedReadingKeepsBothLanguagePhrasesHighlightedInOnePairColor`
passed; the overall managed-device job failed on six unrelated existing UI tests.
`verify-build` failed at `formatCheck`, which reported violations only in untouched files
(`SubtitleTextSanitizer.kt`, `PlaybackTranslation.kt`, `PerRowTranslationTest.kt`, and
`TranslationCoordinatorTest.kt`). Earlier, the AI and TTS focused tests passed; AI tests
use `FakeModel` and do not contact an AI service or require a key. The stale test expectations
were updated to the app's row-wise translation contract in commit `25e36df`. The full unit
suite runs 1,137 tests but remains blocked by two unrelated `SubtitleTextSanitizerTest`
assertion failures. The original implementation was published as `study-1.0.4.15`; the
follow-up pair-highlighting change has not been published.
