# Join auto-caption pieces into whole sentences (ASR)

## Status

Approved for implementation

## Context / problem

The user (a language learner) sees one spoken sentence torn across several
short caption lines when the source track is YouTube's auto-generated (ASR,
punctuation-free) captions. `SubtitleMerger.merge` already splits those cues
into readable lines, but `sentenceCaptionUnits` then re-joins consecutive
lines back into one "sentence" **only when the pause between them is at most
`MAX_JOIN_PAUSE_MS` (1.2 s)**. Real speech frequently contains 2–3 s pauses
inside one sentence, so an unpunctuated ASR sentence is left as several small
units, and each unit is translated **in isolation** — losing the sentence
context that makes the translation natural and useful for learning.

The CJK unfinished-piece case was already relaxed to 3 s
(`MAX_UNFINISHED_PAUSE_MS`, owner report 2026-10-29); unpunctuated **Latin**
ASR tracks had no equivalent, so the tearing was most visible on auto English
subtitles.

## Goals

- One unpunctuated auto-caption sentence stays **one translation unit** across
  natural mid-speech pauses, so it is translated with full sentence context.
- Display rows can still wrap short (≤ 48 chars); they keep their `SentenceSlice`
  and the translation is cut back into them by `translationSlices`.
- Do not merge two *separate* spoken sentences into one unit: the join must
  still stop at a clearly long pause or at a readable size.
- No regression for punctuated (creator) tracks or CJK tracks.

## Non-goals

- No change to how creator (punctuated) tracks are merged or translated.
- No change to the on-device engine or the Google web endpoints.
- No new network traffic, settings, or UI.

## User-visible behavior

- Before: on an auto (ASR) English video, a sentence broken by a ~2 s pause
  shows as two caption lines, each translated alone ("…the model can" /
  "That is actually…"), so the translation reads in pieces.
- After: the same two pieces are joined into one unit before translation, so
  the second line's translation uses the whole sentence as context; the two
  lines still wrap short for readability.

## Technical constraints / invariants

- `SubtitleMerger` and `TranslationCoordinator` keep their current public
  behavior for `natural = false` and for `generated = false` (default), so
  existing tests are unchanged.
- `generated` (the ASR flag) is plumbed from `CaptionTrackResult.isGenerated`
  down to `prepareCaptionDisplayStore` → `sentenceCaptionUnits`, keeping the
  decision tied to the actually loaded track.
- CJK unfinished-join (3 s) and the readable-size caps still apply; the new
  ASR thresholds only affect unpunctuated, non-CJK, generated tracks.

## Proposed approach / plan

1. `TranslationCoordinator`: add ASR thresholds
   (`MAX_AS_JOIN_PAUSE_MS` 2.4 s, `MAX_AS_UNIT_CHARACTERS` 200,
   `MAX_AS_UNIT_DURATION_MS` 12 s) and a `generated` flag on
   `sentenceCaptionUnits`; choose the join pause and size cap by track type.
2. `CaptionFormat`: add `generated` to `captionDisplaySegments` and
   `prepareCaptionDisplayStore`, forwarding it.
3. `AppViewModel`: pass `source.second` (`isGenerated`) into
   `prepareCaptionDisplayStore` inside `runStoredTranslation`.
4. `TranslationEvaluation` (benchmark-only): emit the ASR-joined inputs.
5. Unit tests in `TranslationCoordinatorTest` for the ASR join.

## Acceptance criteria

- [ ] Unpunctuated ASR Latin pieces 2–3 s apart, within the ASR caps, join
      into one unit when `generated = true` (and stay separate when
      `generated = false`).
- [ ] ASR pieces separated by a pause > `MAX_AS_JOIN_PAUSE_MS` do **not** join.
- [ ] ASR pieces whose combined text/duration exceeds the ASR caps do **not**
      join.
- [ ] Creator (punctuated) and CJK tracks keep the previous thresholds
      (regression tests unchanged).
- [ ] `testDebugUnitTest`, `formatCheck`, `lintDebug` pass.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `./gradlew testDebugUnitTest --tests "*TranslationCoordinatorTest*"` — ASR join/separate/caps assertions pass | offline JVM |
| Android lint/build | `./gradlew formatCheck complexityCheck lintDebug` | offline |
| Managed-device/emulator | `./gradlew pixel2Api36DebugAndroidTest` (full offline suites unaffected) | offline |
| Physical-device/manual | On an auto-caption (ASR) English video, one sentence with a 2–3 s pause shows a single translated sentence (lines wrap short) | needs phone + live YouTube |
| Live YouTube | same as above, real ASR track | owner verifies preview APK |
| Documentation/process | spec + PR template filled | n/a |

## Risks / edge cases

- A 2.4 s pause joining two *different* sentences: mitigated by the readable
  caps (≤ 200 chars / 12 s) and by the "no sentence-end mark" guard; two
  unrelated sentences that far apart in time usually also cross a cap.
- CJK auto tracks still use the 3 s unfinished rule (unchanged).

## Release intent

`release:patch` — the change ships in the APK (caption joining + translation
context); no feature-level break.

## Implementation result

Implemented in 5 files:
- `ui/TranslationCoordinator.kt`: added ASR thresholds (`MAX_AS_JOIN_PAUSE_MS` 2.4 s,
  `MAX_AS_UNIT_CHARACTERS` 200, `MAX_AS_UNIT_DURATION_MS` 12 s) and a `generated` flag on
  `sentenceCaptionUnits`; the join pause and size cap now depend on track type (CJK unfinished,
  unpunctuated Latin ASR, or the default readable limits).
- `ui/CaptionFormat.kt`: added `generated` to `captionDisplaySegments` and
  `prepareCaptionDisplayStore`, forwarding it.
- `ui/AppViewModel.kt`: pass `source.second` (`isGenerated`) into `prepareCaptionDisplayStore`.
- `test/ui/TranslationCoordinatorTest.kt`: 3 new ASR-join tests.
- No change to `SubtitleMerger`, the Google/ML Kit engines, or existing CJK/punctuated behavior.

## Validation result

- JVM compile of the caption core (`SubtitleModels`, `WordTiming`, `SubtitleMerger`,
  `TranslationCoordinator`, `TranslationSlicing`) + adapted unit tests: **PASS** — all
  15 tests in `TranslationCoordinatorTest` (12 existing + 3 new ASR) pass. The Gradle Android
  build could not start in this sandbox (system TMPDIR and Gradle native lock are write-blocked),
  so `formatCheck`/`lintDebug`/managed-device are not run here; CI will run them.
- Not run here: `formatCheck`, `complexityCheck`, `lintDebug`, `assembleDebug`,
  `pixel2Api36DebugAndroidTest`, physical-device / live-YouTube ASR check — CI/owner verify.
- Remaining criteria: live ASR "one sentence un-torn" on a phone.
