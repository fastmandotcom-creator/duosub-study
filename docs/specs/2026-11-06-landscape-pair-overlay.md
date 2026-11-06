# Landscape: the single subtitle overlay follows the pair being read

Status: implemented (unverified on device)

## Problem

In landscape (automatic single-overlay presentation, option B: the multi-line
transcript panel stays hidden), the overlay box showed the **wrong line** while
a read-pair was being spoken: TTS read the line that had just ended, but the
video's playback clock had already advanced, so the overlay — which followed
`state.currentIndex` — showed the next line ("the tail of the torn sentence").
The user saw fragments while the audio kept the correct pair. Portrait looked
fine because the panel stayed available, but landscape had only the one box.

## Decision

Keep option B (single overlay, panel hidden in landscape) and lock that overlay
to the pair actually being read, using the existing
`learningOverlayContent(readingSegmentId, readingSide)` parameters — the same
mechanism the panel already uses (`pairedReadingLineIndex` in
`PairedReading.kt`). When no pair is being read, the overlay follows
`state.currentIndex` as before.

## Implementation

`LearningPlayerRoot.kt`: the one `overlayContent` value (used by both the
fullscreen and the normal landscape overlay paths) is now

```kotlin
val overlayContent =
    learningOverlayContent(
        state,
        readingSegmentId = pairedReader?.readingSegmentId,
        readingSide = pairedReader?.progress?.side,
    )
```

`readingSegmentId` is set by `PairedLineReaderHost.read` for the whole read
(primary once, secondary ×n) and cleared on finish, so the box stays on the
read pair the entire time the speaker is talking. `readingSide` only feeds
which side shows the reading focus line. No layout, panel-visibility, or
TTS/audio changes.

## Validation

- Unit: `LearningPlayerRootTest.overlayStaysOnThePairBeingReadWhenVideoHighlightMovesAhead`
  already proves `learningOverlayContent` returns the read segment when the
  video index moved ahead; the wiring above is the same call the panel used.
- To verify on device: study Read-pair mode in landscape/fullscreen, let a line
  finish and the pair start — the box must keep the pair's original +
  translation until the read finishes, regardless of where the highlight moves.
- NOT run in this change: Gradle build and instrumented tests (sandbox has no
  Android toolchain); owner to run `.\gradlew.bat assembleDebug` /
  `pixel2Api36DebugAndroidTest` before publishing.
