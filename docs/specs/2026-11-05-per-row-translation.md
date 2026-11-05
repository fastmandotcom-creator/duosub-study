# Translate each displayed row on its own (no proportional cut)

## Status

Implemented

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
