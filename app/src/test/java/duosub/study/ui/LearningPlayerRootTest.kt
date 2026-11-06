package duosub.study.ui

import android.content.res.Configuration
import duosub.study.R
import duosub.study.data.SubtitleSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPlayerRootTest {
    @Test
    fun storedPlayerModeFallsBackToTranscriptForUnknownValues() {
        assertEquals(
            PlayerExperienceMode.TRANSCRIPT_PANEL,
            storedPlayerExperienceMode(null),
        )
        assertEquals(
            PlayerExperienceMode.TRANSCRIPT_PANEL,
            storedPlayerExperienceMode("unknown"),
        )
        assertEquals(
            PlayerExperienceMode.SCROLL_FRIENDLY_OVERLAY,
            storedPlayerExperienceMode("scroll_friendly_overlay"),
        )
    }

    @Test
    fun overlayUsesTheActiveDuoSubtitle() {
        val state =
            DuoSubUiState(
                activeVideoId = "dQw4w9WgXcQ",
                segments =
                    listOf(
                        SubtitleSegment(1, 1_000, 2_000, "First line", "Dòng đầu tiên"),
                        SubtitleSegment(2, 2_000, 3_000, "Second line", null),
                    ),
                currentIndex = 1,
            )

        assertEquals(
            LearningOverlayContent(
                originalText = "Second line",
                translatedText = TestUiStrings.get(R.string.player_translating),
                statusText = null,
                segment = SubtitleSegment(2, 2_000, 3_000, "Second line", null),
            ),
            learningOverlayContent(state, TestUiStrings),
        )
    }

    @Test
    fun overlayStaysOnThePairBeingReadWhenVideoHighlightMovesAhead() {
        val first = SubtitleSegment(1, 1_000, 2_000, "Hello", "Xin chào")
        val next = SubtitleSegment(2, 2_000, 3_000, "Goodbye", "Tạm biệt")
        val state =
            DuoSubUiState(
                activeVideoId = "dQw4w9WgXcQ",
                resolvedSourceLanguage = "en",
                segments = listOf(first, next),
                currentIndex = 1,
            )

        assertEquals(
            LearningOverlayContent(
                originalText = "Xin chào",
                translatedText = "Hello",
                statusText = null,
                segment = first.copy(originalText = "Xin chào", translatedText = "Hello", words = emptyList()),
                readingSide = ReadSide.SECONDARY,
            ),
            learningOverlayContent(
                state,
                TestUiStrings,
                readingSegmentId = first.id,
                readingSide = ReadSide.SECONDARY,
            ),
        )
    }

    @Test
    fun pairedReadingUsesItsSegmentInsteadOfTheVideoCurrentIndex() {
        val segments =
            listOf(
                SubtitleSegment(11, 1_000, 2_000, "first", "đầu"),
                SubtitleSegment(12, 2_000, 3_000, "next", "tiếp"),
            )

        assertEquals(0, pairedReadingLineIndex(1, segments, 11))
        assertEquals(1, pairedReadingLineIndex(1, segments, null))
        assertEquals(1, pairedReadingLineIndex(1, segments, 99))
    }

    @Test
    fun overlayWordHighlightFollowsTheToggle() {
        val words =
            listOf(
                duosub.study.data.SubtitleWord("Second", 2_000, 2_400),
                duosub.study.data.SubtitleWord("line", 2_400, 3_000),
            )
        val segment = SubtitleSegment(2, 2_000, 3_000, "Second line", null, words = words)
        val baseState =
            DuoSubUiState(
                activeVideoId = "dQw4w9WgXcQ",
                segments = listOf(segment),
                currentIndex = 0,
                activeWordIndex = 1,
            )

        assertEquals(
            1,
            learningOverlayContent(baseState, TestUiStrings)?.activeWordIndex,
        )
        assertEquals(
            -1,
            learningOverlayContent(baseState.copy(wordHighlightEnabled = false), TestUiStrings)?.activeWordIndex,
        )
    }

    @Test
    fun overlayShowsLoadingOrErrorStatusWhenNoCaptionIsActive() {
        assertEquals(
            LearningOverlayContent(null, null, "Loading captions…"),
            learningOverlayContent(
                DuoSubUiState(
                    activeVideoId = "dQw4w9WgXcQ",
                    statusMessage = "Loading captions…",
                ),
                TestUiStrings,
            ),
        )
        assertEquals(
            LearningOverlayContent(null, null, "Captions unavailable"),
            learningOverlayContent(
                DuoSubUiState(
                    activeVideoId = "dQw4w9WgXcQ",
                    statusMessage = "Loading captions…",
                    errorMessage = "Captions unavailable",
                ),
                TestUiStrings,
            ),
        )
        assertNull(learningOverlayContent(DuoSubUiState(), TestUiStrings))
    }

    @Test
    fun portraitOverlayDefaultsNearTheBottomAndStaysBounded() {
        assertEquals(84, portraitLearningOverlayTopPaddingDp(0))
        assertEquals(181, portraitLearningOverlayTopPaddingDp(393))
        assertEquals(320, portraitLearningOverlayTopPaddingDp(1_000))
        assertTrue(
            portraitLearningOverlayTopPaddingDp(393, 1f) >
                portraitLearningOverlayTopPaddingDp(393, 0f),
        )
    }

    @Test
    fun overlayPositionSliderMapsHigherToLargerBottomPadding() {
        assertEquals(180, overlayBottomPaddingDp(0f))
        assertEquals(42, overlayBottomPaddingDp(DEFAULT_OVERLAY_VERTICAL_POSITION))
        assertEquals(20, overlayBottomPaddingDp(1f))
        assertEquals(DEFAULT_OVERLAY_VERTICAL_POSITION, normalizeOverlayVerticalPosition(Float.NaN))
    }

    @Test
    fun automaticLandscapeOverlayOnlyOverridesTranscriptMode() {
        assertTrue(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.TRANSCRIPT_PANEL,
                autoLandscape = true,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertFalse(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.TRANSCRIPT_PANEL,
                autoLandscape = false,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertFalse(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.TRANSCRIPT_PANEL,
                autoLandscape = true,
                orientation = Configuration.ORIENTATION_PORTRAIT,
            ),
        )
        assertFalse(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.SCROLL_FRIENDLY_OVERLAY,
                autoLandscape = true,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
    }

    @Test
    fun automaticLandscapeOverlayIsHeldBackWhileAPairedReadingIsActive() {
        // Landscape + auto overlay would normally replace the transcript panel with a one-line
        // overlay, but the read-pair TTS speaks both lines, so the bilingual panel must stay
        // open while the paired reader is mid-reading instead of hiding the learner's text.
        assertTrue(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.TRANSCRIPT_PANEL,
                autoLandscape = true,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                pairedReadingActive = false,
            ),
        )
        assertFalse(
            shouldUseAutomaticLandscapeOverlay(
                PlayerExperienceMode.TRANSCRIPT_PANEL,
                autoLandscape = true,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                pairedReadingActive = true,
            ),
        )
    }

    @Test
    fun anyStudyPlayModeOtherThanNormalNeedsTheLandscapeBilingualPanel() {
        // The landscape auto-overlay is held back for every study play mode that pauses the video
        // at each line and speaks it, so the panel (all bilingual sentences, like portrait) is not
        // collapsed to a one-line overlay while the learner is following along with the TTS.
        fun panelNeeded(mode: StudyPlayMode, knownWords: Boolean): Boolean {
            val knownWordsMode = if (knownWords) KnownWordsMode.AUTO else KnownWordsMode.OFF
            val settings = StudySettings(playMode = mode, knownWordsMode = knownWordsMode)
            return settings.effectivePlayMode != StudyPlayMode.NORMAL
        }

        assertEquals(false, panelNeeded(StudyPlayMode.NORMAL, true))

        listOf(
            StudyPlayMode.PAUSE_AFTER_LINE,
            StudyPlayMode.READ_FIRST,
            StudyPlayMode.LISTEN_FIRST,
            StudyPlayMode.REPEAT_LINE,
            StudyPlayMode.READ_PAIR,
        ).forEach { mode ->
            assertTrue("play mode $mode should keep the bilingual panel open in landscape", panelNeeded(mode, true))
        }

        // One new word needs the known-word list; without it the driver plays normally, so no panel.
        assertEquals(false, panelNeeded(StudyPlayMode.NEW_WORD_LINES, false))
        assertEquals(true, panelNeeded(StudyPlayMode.NEW_WORD_LINES, true))
    }

    @Test
    fun overlayDragAndPlayerControlAvoidanceStayBounded() {
        assertEquals(1f, overlayPositionAfterDrag(0.8f, 100f, 100f))
        assertEquals(0f, overlayPositionAfterDrag(0.2f, -100f, 100f))
        assertEquals(0.5f, overlayPositionAfterDrag(0.5f, Float.NaN, 100f))
        assertEquals(PLAYER_CONTROLS_AVOIDANCE_LIFT_DP, playerControlsAvoidanceLiftDp(true, true))
        assertEquals(0, playerControlsAvoidanceLiftDp(true, false))
        assertEquals(0, playerControlsAvoidanceLiftDp(false, true))
    }

    @Test
    fun horizontalOverlayPositionNormalizesAndStaysCenteredByDefault() {
        assertEquals(DEFAULT_OVERLAY_HORIZONTAL_POSITION, normalizeOverlayHorizontalPosition(Float.NaN))
        assertEquals(0f, normalizeOverlayHorizontalPosition(-3f))
        assertEquals(1f, normalizeOverlayHorizontalPosition(7f))
        assertEquals(-1f, overlayHorizontalShiftFraction(0f))
        assertEquals(0f, overlayHorizontalShiftFraction(0.5f))
        assertEquals(1f, overlayHorizontalShiftFraction(1f))
    }

    @Test
    fun horizontalOverlayShiftKeepsTheBoxFullyOnScreen() {
        // Degenerate sizes never shift the box.
        assertEquals(0, overlayHorizontalShiftPx(1f, 0, 0))
        assertEquals(0, overlayHorizontalShiftPx(1f, 400, 400))
        val halfFreeSpace = (800 - 300) / 2
        assertEquals(halfFreeSpace, overlayHorizontalShiftPx(1f, 800, 300))
        assertEquals(-halfFreeSpace, overlayHorizontalShiftPx(-1f, 800, 300))
        assertEquals(0, overlayHorizontalShiftPx(0f, 800, 300))
        // Out-of-range fractions are clamped instead of pushing the box off screen.
        assertEquals(halfFreeSpace, overlayHorizontalShiftPx(9f, 800, 300))
    }

    @Test
    fun fullscreenLandscapeStartsNearTheTopButKeepsDraggedPositions() {
        assertEquals(
            FULLSCREEN_LANDSCAPE_DEFAULT_OVERLAY_VERTICAL_POSITION,
            fullscreenOverlayVerticalPosition(
                DEFAULT_OVERLAY_VERTICAL_POSITION,
                Configuration.ORIENTATION_LANDSCAPE,
            ),
            0f,
        )
        assertEquals(
            DEFAULT_OVERLAY_VERTICAL_POSITION,
            fullscreenOverlayVerticalPosition(
                DEFAULT_OVERLAY_VERTICAL_POSITION,
                Configuration.ORIENTATION_PORTRAIT,
            ),
            0f,
        )
        assertEquals(
            0.37f,
            fullscreenOverlayVerticalPosition(0.37f, Configuration.ORIENTATION_LANDSCAPE),
            0f,
        )
        assertTrue(
            FULLSCREEN_LANDSCAPE_DEFAULT_OVERLAY_VERTICAL_POSITION <
                DEFAULT_OVERLAY_VERTICAL_POSITION,
        )
    }

    @Test
    fun fullscreenOverlayCanTravelFromTopToBottom() {
        val screenHeight = 800
        val top = fullscreenOverlayBottomPaddingDp(0f, screenHeight)
        val bottom = fullscreenOverlayBottomPaddingDp(1f, screenHeight)
        assertTrue("position 0 should reach the top", top >= screenHeight - FULLSCREEN_OVERLAY_ESTIMATED_HEIGHT_DP - 60)
        assertEquals(0, bottom)
        assertTrue(
            fullscreenOverlayBottomPaddingDp(0.4f, screenHeight) >
                fullscreenOverlayBottomPaddingDp(0.6f, screenHeight),
        )
        // Player-control avoidance lifts the box but clamps at the top edge.
        assertTrue(fullscreenOverlayBottomPaddingDp(1f, screenHeight, PLAYER_CONTROLS_AVOIDANCE_LIFT_DP) >= 0)
        assertEquals(
            fullscreenOverlayDragTravelDp(screenHeight),
            fullscreenOverlayBottomPaddingWithControlsDp(
                position = 0f,
                screenHeightDp = screenHeight,
                controlsLiftDp = PLAYER_CONTROLS_AVOIDANCE_LIFT_DP,
            ),
        )
    }

    @Test
    fun fullscreenDragTravelMatchesVisibleVerticalTravel() {
        assertEquals(712, fullscreenOverlayDragTravelDp(800))
        assertEquals(152, fullscreenOverlayDragTravelDp(240))
    }

    @Test
    fun captionSuppressionIsOriginCheckedAndReversible() {
        val hidden = webCaptionVisibilityScript(hidden = true)
        val restored = webCaptionVisibilityScript(hidden = false)

        assertTrue(hidden.contains("window.location.protocol !== 'https:'"))
        assertTrue(hidden.contains("host.endsWith('.youtube.com')"))
        assertTrue(hidden.contains(YOUTUBE_CAPTION_STYLE_ID))
        assertTrue(hidden.contains("visibility: hidden !important"))
        assertTrue(restored.contains("existing.remove()"))
    }

    @Test
    fun portraitOverlayTopPaddingAllowsFullVerticalTravelWhenUnlocked() {
        val screenWidth = 360
        val screenHeight = 800

        val lockedTop = portraitLearningOverlayTopPaddingDp(screenWidth, position = 0f, screenHeightDp = screenHeight, lockToVideo = true)
        val lockedBottom =
            portraitLearningOverlayTopPaddingDp(screenWidth, position = 1f, screenHeightDp = screenHeight, lockToVideo = true)
        assertTrue(lockedTop >= 84)
        assertTrue(lockedBottom <= 320)

        val unlockedTop =
            portraitLearningOverlayTopPaddingDp(screenWidth, position = 0f, screenHeightDp = screenHeight, lockToVideo = false)
        val unlockedBottom =
            portraitLearningOverlayTopPaddingDp(screenWidth, position = 1f, screenHeightDp = screenHeight, lockToVideo = false)
        assertEquals(84, unlockedTop)
        assertTrue("Unlocked bottom padding should reach lower screen area, was $unlockedBottom", unlockedBottom > 600)
    }
}
