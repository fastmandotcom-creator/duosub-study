package duosub.study.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import duosub.study.data.SentenceSlice
import duosub.study.data.SubtitleSegment
import duosub.study.ui.theme.DuoSubTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TranslationPositionAndThemeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("dual_sub_preferences", Context.MODE_PRIVATE)

    @After
    fun forgetTheTheme() {
        preferences.edit().remove(APP_COLOR_THEME_PREFERENCE).commit()
    }

    // Unmerged: a subtitle card is one clickable node that holds both of its lines.
    private fun top(text: String) =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun phraseHighlight(tag: String): Pair<String, Color> {
        val text =
            composeRule
                .onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        val style = text.spanStyles.single { it.item.textDecoration == TextDecoration.Underline }
        return text.text.substring(style.start, style.end) to style.item.background
    }

    @Test
    fun aboveShowsTheTranslationFirstInThePanelAndTheOverlay() {
        val segment = SubtitleSegment(1, 0, 2_000, "Original line", "Translated line")
        composeRule.setContent {
            DuoSubTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        CompactSubtitleCard(segment, true, 1f, {}, translationPosition = TranslationPosition.BELOW)
                        CompactSubtitleCard(
                            segment.copy(id = 2, originalText = "Card original", translatedText = "Card translation"),
                            true,
                            1f,
                            {},
                            translationPosition = TranslationPosition.ABOVE,
                        )
                        LearningSubtitleOverlay(
                            LearningOverlayContent("Overlay original", "Overlay translation", null),
                            1f,
                            translationPosition = TranslationPosition.ABOVE,
                            onSettings = {},
                            onClose = {},
                        )
                    }
                }
            }
        }

        assertTrue(top("Original line") < top("Translated line"))
        assertTrue(top("Card translation") < top("Card original"))
        assertTrue(top("Overlay translation") < top("Overlay original"))
        saveUiEvidence("translation-position-above", composeRule.onRoot().captureToImage())
    }

    @Test
    fun pairedReadingKeepsBothLanguagePhrasesHighlightedInOnePairColor() {
        preferences.edit().putString(APP_COLOR_THEME_PREFERENCE, AppColorThemeOption.DEEP_TEAL.key).commit()
        var side by mutableStateOf<ReadSide?>(ReadSide.PRIMARY)
        val sentence = "Xin chào, các bạn!"
        val secondStart = sentence.indexOf("các bạn")
        val cuts = listOf(secondStart)
        val segment =
            SubtitleSegment(
                id = 1,
                startMs = 0,
                endMs = 2_000,
                originalText = sentence.substring(0, secondStart).trim(),
                translatedText = "Hello,",
                sentence = SentenceSlice(sentence, cuts, 0),
            )
        val nextSegment =
            SubtitleSegment(
                id = 2,
                startMs = 2_000,
                endMs = 4_000,
                originalText = sentence.substring(secondStart).trim(),
                translatedText = "friends!",
                sentence = SentenceSlice(sentence, cuts, 1),
            )
        val state =
            DuoSubUiState(
                activeVideoId = "dQw4w9WgXcQ",
                sourcePreference = "vi",
                resolvedSourceLanguage = "vi",
                targetLanguage = "en",
                segments = listOf(segment, nextSegment),
                currentIndex = 0,
            )
        val pairColor = pairedReadingHighlightColor(0)
        composeRule.setContent {
            DuoSubTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        CompactSubtitleCard(
                            segment = segment,
                            active = true,
                            fontScale = 1f,
                            onReplay = {},
                            readingSide = side,
                            readingPairHighlightColor = pairColor.takeIf { side != null },
                            translationPosition = TranslationPosition.BELOW,
                        )
                        LearningSubtitleOverlay(
                            content = learningOverlayContent(
                                state,
                                context.resources.uiStrings(),
                                readingSegmentId = segment.id.takeIf { side != null },
                                readingSide = side,
                            )!!,
                            fontScale = 1f,
                            translationPosition = TranslationPosition.BELOW,
                            onSettings = {},
                            onClose = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("read_pair_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_focus_secondary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertExists()
        assertEquals("Xin chào," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_phrase_primary"))
        assertEquals("Hello," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_phrase_secondary"))
        assertEquals("Xin chào," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_overlay_phrase_primary"))
        assertEquals("Hello," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_overlay_phrase_secondary"))
        saveUiEvidence("paired-reading-primary-focus", composeRule.onRoot().captureToImage())

        preferences.edit().putString(APP_COLOR_THEME_PREFERENCE, AppColorThemeOption.LIGHT.key).commit()
        composeRule.runOnIdle { side = ReadSide.SECONDARY }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("read_pair_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_focus_secondary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertExists()
        assertEquals("Xin chào," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_phrase_primary"))
        assertEquals("Hello," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_phrase_secondary"))
        assertEquals("Xin chào," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_overlay_phrase_primary"))
        assertEquals("Hello," to pairColor.copy(alpha = 0.38f), phraseHighlight("read_pair_overlay_phrase_secondary"))
        saveUiEvidence("paired-reading-secondary-focus", composeRule.onRoot().captureToImage())

        composeRule.runOnIdle { side = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theSettingsPageChangesTheTranslationPositionAndTheWholeAppColors() {
        var position = TranslationPosition.BELOW
        var background = 0f
        composeRule.setContent {
            DuoSubTheme {
                background = MaterialTheme.colorScheme.background.luminance()
                SubtitleSettingsDialog(
                    sourcePreference = "auto",
                    targetLanguage = "vi",
                    availableSourceLanguages = emptyList(),
                    fontScale = 1f,
                    landscapeSplitEnabled = true,
                    translationPosition = position,
                    onTranslationPositionChange = { position = it },
                    onSourceChange = {},
                    onTargetChange = {},
                    onFontScaleChange = {},
                    onLandscapeSplitChange = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("translation_position_above").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(TranslationPosition.ABOVE, position) }
        assertTrue(background < 0.1f)

        composeRule.onNodeWithTag("settings_section_colors").performScrollTo().performClick()
        composeRule.onNodeWithTag("color_option_app_colors_light").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { background > 0.8f }
        // The theme is chosen before its new colors are drawn; let that frame finish first.
        composeRule.waitForIdle()
        assertEquals("light", preferences.getString(APP_COLOR_THEME_PREFERENCE, null))
        saveUiEvidence("settings-light-theme")

        composeRule.onNodeWithTag("color_option_app_colors_navy").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { background < 0.1f }
        composeRule.waitForIdle()
        saveUiEvidence("settings-navy-theme")
    }

    @Test
    fun theReadingCardSwitchesTheTranscriptToInTheMiddle() {
        var follow by mutableStateOf(TranscriptFollow.PAGE)
        composeRule.setContent {
            DuoSubTheme {
                SubtitleSettingsDialog(
                    sourcePreference = "auto",
                    targetLanguage = "vi",
                    availableSourceLanguages = emptyList(),
                    fontScale = 1f,
                    landscapeSplitEnabled = true,
                    transcriptFollow = follow,
                    onTranscriptFollowChange = { follow = it },
                    onSourceChange = {},
                    onTargetChange = {},
                    onFontScaleChange = {},
                    onLandscapeSplitChange = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("transcript_follow_center").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(TranscriptFollow.CENTER, follow) }
        composeRule.onNodeWithTag("translation_position_below").performScrollTo()
        saveUiEvidence("settings-transcript-follow")
    }

    @Test
    fun theLightThemeKeepsSubtitlesReadableOnItsCards() {
        preferences.edit().putString(APP_COLOR_THEME_PREFERENCE, AppColorThemeOption.LIGHT.key).commit()
        composeRule.setContent {
            DuoSubTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Light theme transcript", style = MaterialTheme.typography.titleMedium)
                        val first = SubtitleSegment(1, 0, 2_000, "猫が好きです", "I like cats")
                        CompactSubtitleCard(first, true, 1f, {}, wordLearningEnabled = true)
                        CompactSubtitleCard(SubtitleSegment(2, 2_000, 4_000, "Next line", "Dòng tiếp theo"), false, 1f, {})
                    }
                }
            }
        }

        composeRule.onNodeWithText("I like cats").fetchSemanticsNode()
        saveUiEvidence("light-theme-transcript", composeRule.onRoot().captureToImage())
    }
}
