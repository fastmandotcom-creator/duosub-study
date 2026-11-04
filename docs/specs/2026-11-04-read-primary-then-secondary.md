# Read primary, then secondary

## Status

Implemented. Requested in chat on 2026-11-04: read the pair (primary, then secondary) X times, default 2. Example: hello, xin chào, hello, xin chào.

## Context / problem

Hearing a line and its meaning as one pair, then hearing that pair again, makes the meaning stick longer than reading both at once or repeating only the original.

## Goals

- Speak the original subtitle (primary), then its translation (secondary).
- Repeat that pair a chosen number of times. Default 2. Choices 1, 2, 3, 5.
- Offer it as a play mode that does this for each line, and as a button on the subtitle panel.

## Non-goals

- A second video player or a change to caption download.
- Speaking a tapped word's dictionary meaning when that meaning is not the line's translation and was not saved.

## User-visible behavior

Settings → More settings → Study tools → Play mode → Read primary, then secondary. The video pauses, the app reads primary then secondary, and repeats that pair (2 times by default). The video then continues, and the next line is read the same way. The speaker icon on the subtitle panel reads the current line the same way without changing the play mode. A saved word, or a line that is only that word, is read as the same pair from the word card.

## Technical constraints / invariants

Play modes still only pause, play, or seek the one YouTube page video. Speech uses the existing pronunciation path (Google voice while Google translates, otherwise the phone). New UI strings are in every interface language.

## Proposed approach / plan

- `pairedReadSteps` builds primary, secondary, repeated `times` times.
- `WordPronouncer.readPair` speaks that sequence and replays a recorded side instead of fetching it again.
- The play mode starts the sequence when the current line changes, then plays the page video again.

## Acceptance criteria

- [x] Two times is primary, secondary, primary, secondary.
- [x] The stored count defaults to 2 and rejects a count outside 1, 2, 3, 5.
- [x] The play mode does not also pause or repeat the line in `StudyPlaybackDriver`.

## Validation

- Passed: `PairedReadingTest`, `StudyPlaybackTest`, `AiAssistantActionsTest`, `AiActionsTest`, `PlaybackArchitectureTest`. Format ratchet and complexity check passed.
- Not run: instrumented UI, physical device, live YouTube.

## Release intent

Patch. The change is in the app and reaches the APK. No exact version; the owner labels the PR.
