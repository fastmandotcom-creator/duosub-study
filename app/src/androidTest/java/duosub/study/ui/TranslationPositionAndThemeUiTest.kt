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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
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
    fun pairedReadingVisiblyEmphasizesOnlyTheSideBeingSpoken() {
        preferences.edit().putString(APP_COLOR_THEME_PREFERENCE, AppColorThemeOption.DEEP_TEAL.key).commit()
        var side by mutableStateOf<ReadSide?>(ReadSide.PRIMARY)
        val segment = SubtitleSegment(1, 0, 2_000, "Xin chào", "Hello")
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
                            translationPosition = TranslationPosition.BELOW,
                        )
                        LearningSubtitleOverlay(
                            content = LearningOverlayContent("Xin chào", "Hello", null, segment = segment, readingSide = side),
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
        composeRule.onNodeWithTag("read_pair_focus_secondary", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertDoesNotExist()
        saveUiEvidence("paired-reading-primary-focus", composeRule.onRoot().captureToImage())

        preferences.edit().putString(APP_COLOR_THEME_PREFERENCE, AppColorThemeOption.LIGHT.key).commit()
        composeRule.runOnIdle { side = ReadSide.SECONDARY }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("read_pair_focus_primary", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("read_pair_focus_secondary", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertExists()
        saveUiEvidence("paired-reading-secondary-focus", composeRule.onRoot().captureToImage())

        composeRule.runOnIdle { side = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("read_pair_overlay_focus_primary", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("read_pair_overlay_focus_secondary", useUnmergedTree = true).assertDoesNotExist()
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
