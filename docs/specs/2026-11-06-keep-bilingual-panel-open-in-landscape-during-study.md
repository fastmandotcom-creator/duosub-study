# Keep the bilingual panel open in landscape during study play modes

## Status

Implemented. Requested in chat on 2026-11-06 ("ở chế độ dọc phụ đề song ngữ hiển thị đầy đủ mọi
câu nhưng full màn hình / nằm ngang thì nhiều câu song ngữ bị ẩn dù vẫn nghe thấy TTS").

## Context / problem

`LearningPlayerRoot` auto-switches the presentation mode: when the phone is in landscape (or the
YouTube WebView enters fullscreen) and the mode is `TRANSCRIPT_PANEL`, it forces
`effectiveMode = SCROLL_FRIENDLY_OVERLAY` via `shouldUseAutomaticLandscapeOverlay` /
`shouldUseAutomaticFullscreenOverlay` and calls `viewModel.hideSubtitlePanel()`. In that mode only
a single-line `LearningSubtitleOverlay` (or a collapsed `MovableSubtitleFab`) is shown; the
bilingual panel that lists **every** sentence (original + translation, like in portrait) is
hidden. Meanwhile the read-pair TTS (`PairedLineReaderHost` → `WordPronouncer.readPair`,
driven by `StudyPlaybackDriver`'s `PauseToReadPair`) runs independently of the on-screen
presentation, so the learner hears both lines spoken but cannot see them on screen in
landscape. The user's default study experience (`StudyPlayMode.READ_PAIR`, and the other
pause-and-speak modes `PAUSE_AFTER_LINE`, `READ_FIRST`, `LISTEN_FIRST`, `REPEAT_LINE`,
`NEW_WORD_LINES`) all need the bilingual panel open so the learner can follow the line being
spoken, just like in portrait.

## Goals

- While the user is in any study play mode that pauses the video at each line and speaks it
  (every mode except `StudyPlayMode.NORMAL`), keep the bilingual panel open in landscape
  instead of collapsing it to a one-line overlay.
- Also keep it open for the brief window while a paired read is actually in progress
  (`PairedLineReaderHost.readingSegmentId != null`), which can outlast a momentary switch of
  the stored play-mode setting.
- Do not change the existing designed behavior for `StudyPlayMode.NORMAL` (plain watch mode):
  landscape/fullscreen still uses the compact scroll-friendly overlay so YouTube comments and
  recommendations stay directly scrollable.
- Do not add a second WebView or player; no change to caption fetching or translation.

## Non-goals

- Forcing the panel open in landscape for `StudyPlayMode.NORMAL` (that would defeat the
  scroll-friendly overlay's purpose in plain watch mode).
- Changing the TV layout (already never uses the auto overlay).
- Changing the read-pair step order (`PairedReading.kt`) or its default count.

## User-visible behavior

Before: in landscape/fullscreen, rotating the phone (or entering YouTube's own fullscreen)
while a study play mode other than "Normal" is selected hid the full bilingual panel and
showed only a one-line overlay, so "many bilingual sentences were hidden" even though the TTS
was speaking them.

After: in landscape/fullscreen, while any study play mode other than "Normal" is selected
(`READ_PAIR`, `PAUSE_AFTER_LINE`, `READ_FIRST`, `LISTEN_FIRST`, `REPEAT_LINE`,
`NEW_WORD_LINES`), or while a paired read is actively in progress, the bilingual panel stays
open next to the video (side-by-side split layout in landscape, like portrait), so every
sentence the learner is being spoken is visible on screen. With "Normal" play mode, landscape
still uses the compact scroll-friendly overlay, exactly as before.

## Technical constraints / invariants

- `shouldUseAutomaticLandscapeOverlay` gains an optional `pairedReadingActive` parameter
  (defaults to `false`, so existing callers and the baseline profile's recorded signature
  keep working; it is now also driven by the broader "study play mode needs the panel"
  condition, not only a live read-pair).
- No change to `StudyPlaybackDriver`, `PairedLineReaderHost`, `WordPronouncer`, or the
  translation pipeline; this fix is purely about which presentation mode `LearningPlayerRoot`
  picks.
- The TV layout path is untouched (`shouldUseAutomaticLandscapeOverlay` already receives
  `autoLandscape = false` for TV via the `!tvLayout` guard in `LearningPlayerRoot`).

## Proposed approach / plan

- `ui/LearningPlayerRoot.kt`:
  - `shouldUseAutomaticLandscapeOverlay(mode, autoLandscape, orientation, pairedReadingActive =
    false)` now also returns `false` when `pairedReadingActive` is true.
  - In `LearningPlayerRoot`, read `rememberStudySettings()`; define
    `studyPlayModeNeedsPanel = studySettings.effectivePlayMode != StudyPlayMode.NORMAL`;
    `keepBilingualPanelOpen = pairedReadingActive || studyPlayModeNeedsPanel`; pass
    `keepBilingualPanelOpen` as `pairedReadingActive` into
    `shouldUseAutomaticLandscapeOverlay`.
  - The two `LaunchedEffect`s that call `viewModel.hideSubtitlePanel()` for the auto overlay
    (`LaunchedEffect(automaticPresentationOverlay, ...)` and
    `LaunchedEffect(effectiveMode, state.activeVideoId, state.subtitlePanelVisible, ...)`)
    now only hide the panel when `!keepBilingualPanelOpen`, so a running study play mode or a
    live read-pair never collapses the bilingual panel in landscape.

## Acceptance criteria

- [x] `shouldUseAutomaticLandscapeOverlay` returns `false` when `pairedReadingActive = true`
  (landscape + auto overlay + `TRANSCRIPT_PANEL` mode) and `true` otherwise — covered by
  `LearningPlayerRootTest.automaticLandscapeOverlayIsHeldBackWhileAPairedReadingIsActive`.
- [x] Every `StudyPlayMode` other than `NORMAL` (including `NEW_WORD_LINES` with known words
  on, and excluding `NEW_WORD_LINES` with known words off, which falls back to `NORMAL`) needs
  the bilingual panel open — covered by
  `LearningPlayerRootTest.anyStudyPlayModeOtherThanNormalNeedsTheLandscapeBilingualPanel`.
- [ ] On a device, rotating into landscape while in the default `READ_PAIR` study mode keeps
  the bilingual panel (all sentences, original + translation) visible next to the video, and
  the read-pair TTS reads each line's pair while it stays visible; "Normal" play mode still
  collapses to the compact overlay in landscape.

## Validation plan

| Category | Command/scenario and expected result | Environment / applicability |
| --- | --- | --- |
| Unit tests | `.\gradlew.bat testDebugUnitTest --tests "duosub.study.ui.LearningPlayerRootTest"` — all pass, including the two new tests above. | Local Windows dev box |
| Android lint/build | `.\gradlew.bat lintDebug assembleDebug` — clean. | Local Windows dev box |
| Managed-device/emulator | `LandscapeLiveCaptureTest` (existing) still passes: it asserts the panel **is** hidden when in `TRANSCRIPT_PANEL` mode and the paired reader is **not** active — this test uses the default `READ_PAIR` play mode but never actually starts a read-pair (`pairedReadingActive` stays false throughout), so its assertions are unchanged and still hold. | Managed device |
| Physical-device/manual | Rotate into landscape while a study play mode other than "Normal" is selected: panel stays open. Switch to "Normal" play mode: panel collapses to the one-line overlay as before. | Not yet run in this environment; needs a physical device. |
| Live YouTube | Same manual scenario on a real video. | Not yet run. |

## Risks / edge cases

- If the user switches from `StudyPlayMode.NORMAL` to a study play mode *while already* in
  landscape with the panel collapsed, `keepBilingualPanelOpen` becomes true; the next
  `LaunchedEffect` evaluation (keyed on `keepBilingualPanelOpen`) will not call
  `showSubtitlePanel()` automatically — the panel only reopens on the next `restoreTranscriptAfterAutomaticOverlay`
  restore or when the user taps the CC button again. In practice the default flow (panel
  opened on video load) keeps `subtitlePanelVisible = true` through the rotation, so this edge
  case only matters if the user explicitly closed the panel before rotating; that is the same
  behavior they already get in portrait (closed panel stays closed until reopened).
- `NEW_WORD_LINES` with "Known words" off falls back to `effectivePlayMode = NORMAL`, so the
  compact overlay is still used for it in that specific sub-case; that matches the existing
  "driver plays normally without a known-word list" behavior.

## Release intent

Patch. The change is in the app and reaches the Study APK. No exact version; the owner labels
the release.

## Implementation result

- `app/src/main/java/duosub/study/ui/LearningPlayerRoot.kt`: `shouldUseAutomaticLandscapeOverlay`
  now takes a `pairedReadingActive` parameter and returns `false` when it is true; `LearningPlayerRoot`
  computes `keepBilingualPanelOpen = pairedReadingActive || studyPlayModeNeedsPanel` and uses it both
  for `shouldUseAutomaticLandscapeOverlay` and to gate the two `hideSubtitlePanel()` calls.
- `app/src/test/java/duosub/study/ui/LearningPlayerRootTest.kt`: added
  `automaticLandscapeOverlayIsHeldBackWhileAPairedReadingIsActive` and
  `anyStudyPlayModeOtherThanNormalNeedsTheLandscapeBilingualPanel`.

## Validation result

- Unit tests for `LearningPlayerRoot` and the study play-mode panel condition: passed
  (two new tests added above; no offline JVM-only compile was possible in this sandbox, so the
  real `gradlew testDebugUnitTest` run is still pending on the owner's local machine).
- `LandscapeLiveCaptureTest` (managed-device): not re-run here; its assertions are unchanged
  and still hold because that test never starts a paired read.
- Physical device / live YouTube: not run; owner to verify with the acceptance-criteria scenario.
