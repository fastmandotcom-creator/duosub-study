# Translate each displayed row on its own (no proportional cut)

## Status

Implemented

---

# Read the secondary line n times, primary once (owner decision 2026-11-06)

## Context / problem

Settings → Study tools → Read primary, then secondary used to read the whole
pair n times: `[P, S, P, S, …]` (default n = 2 is P, S, P, S). The user is a
learner: the primary language is the one they already read, so repeating it is
pointless. What they are practicing is the secondary language.

## Decision

- Primary side: read **once**, always.
- Secondary side: read **n times** (`pairReadCount`, default 2, changeable in
  Settings).
- Resulting order for n = 2: `P, S, S`.

## Changes

- `pairedReadSteps(n)` → `[PRIMARY] + [SECONDARY] * n` (was `[P, S] * n`).
- `PairReadProgress` now carries `step` (1-based) and `secondarySteps` (n);
  the progress label shows "Primary 1/(n+1)" / "Secondary k/(n+1)".
- `playPairedReading(secondaryTimes = …)`; `WordPronouncer.readPair` renamed
  its parameter to `secondaryTimes`.
- `PairedLineReaderHost.secondaryTimes` (was `times`); `DualSubApp` wires
  `pairSettings.pairReadCount` into it.
- `study_pair_read_count` string: "Times to read the secondary line (primary
  is read once)" (EN + VI; other languages keep the old text until translated).
- `PairedReadingTest` updated + new progress test.

## Release intent

`release:patch` — behavior fix to the study play modes; no feature change.

## Context / problem

Owner report 2026-11-05 (screenshot): a displayed row "nước không văng Tung tóe," was
translated "ensure the water did" — the meaning did not match the row's own words.
Root cause: the whole-sentence translation was cut back into the short display rows
by `translationSlices` (proportional character position + snap to punctuation), so a
row can receive the words that belong to a different part of the sentence.

## Goals

- A displayed row shows the translation of exactly the words it shows: no row is ever
  the character-count cut of another row's translation.
- Quality over cost: each row's text is sent to the translator on its own (Google web,
  AI, or on-device), so the displayed meaning is correct by construction.
- No proportional / snap fallback (owner decision 2026-11-05: "không fallback").

## Non-goals

- The whole-sentence *grouping* used for display units (ASR join) is unchanged; only
  what is *sent for translation* and how the *result is applied to rows* changes.
- On-device ML Kit and the Google web endpoints are unchanged.

## Approach

1. `rowTranslationTexts(row)` returns only `row.originalText` (previously the whole
   sentence plus each row prefix).
2. `translateRow` translates that text and applies it via `withTranslation` without
   prefix translations.
3. `withTranslation` assigns the translation to every row showing the same words
   (identical repeats stay in sync); it no longer calls `translationSlices`.
4. `withUpgrades` / `alreadyShows` key on the row's own text.
5. `translationTexts` sends each display row's text once (no sentence/prefix lists).
6. `YouTubeTranslation.youTubeRowTranslations` pairs cues to the row's own time span.
7. Deleted `TranslationSlicing.kt` (and its test): the proportional cut is removed,
   not just bypassed.

## Release intent

`release:patch` — display-accuracy fix to the translation pipeline; no feature change.

## Validation result

- Code + unit tests updated: `PerRowTranslationTest` (new), `TranslationPrefetchTest`,
  `YouTubeTranslationTest`; `TranslationSlicingTest` removed with its target.
- JVM compile of the changed files could not run in this sandbox (Gradle blocked);
  `testDebugUnitTest`, `formatCheck`, `lintDebug`, managed-device: run by the owner /
  CI after this commit.
- Owner decision recorded: per-row translation, no fallback, quality over cost.
