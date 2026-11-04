package com.kienhoang.dualsubreplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingPreferencesTest {

    @Test fun keepsSupportedOnboardingLanguagePairs() {
        assertEquals("vi" to "ja", normalizedOnboardingLanguages("vi", "ja"))
    }

    @Test fun normalizesRegionalTagsInOnboardingChoices() {
        assertEquals("en" to "zh", normalizedOnboardingLanguages("en-US", "zh-CN"))
        assertEquals("he" to "en", normalizedOnboardingLanguages("iw", "en"))
    }

    @Test fun rejectsUnsupportedOnboardingLanguages() {
        assertNull(normalizedOnboardingLanguages("xx", "ja"))
        assertNull(normalizedOnboardingLanguages("vi", "zz"))
        assertNull(normalizedOnboardingLanguages("", ""))
    }

    @Test fun studyDefaultsAreEnglishCaptionsAndVietnameseTranslation() {
        assertEquals("vi", DEFAULT_SOURCE_LANGUAGE)
        assertEquals("en", DEFAULT_TARGET_LANGUAGE)
        assertEquals("vi", DualSubUiState().sourcePreference)
        assertEquals("en", DualSubUiState().targetLanguage)
        assertEquals(true, usesVietnameseEnglishPair("vi", "en"))
        assertEquals(false, usesVietnameseEnglishPair("ja", "en"))
        assertEquals("en", studyTranslationTarget("vi"))
        assertEquals("vi", studyTranslationTarget("en"))
        val english = com.kienhoang.dualsubreplay.data.SubtitleSegment(1, 0, 1, "hello", "xin chào")
        assertEquals("xin chào", english.asStudyPair("en", "vi").originalText)
        assertEquals("hello", english.asStudyPair("en", "vi").translatedText)
        val japanese = com.kienhoang.dualsubreplay.data.SubtitleSegment(2, 0, 1, "こんにちは", "xin chào")
        val shown = japanese.asStudyPair("ja", "vi", "hello")
        assertEquals("xin chào", shown.originalText)
        assertEquals("hello", shown.translatedText)
    }

    @Test fun storedSourcePreferenceFallsBackToAuto() {
        assertEquals("auto", storedSourcePreference(null))
        assertEquals("auto", storedSourcePreference(""))
        assertEquals("auto", storedSourcePreference("klingon"))
        assertEquals("auto", storedSourcePreference("auto"))
    }

    @Test fun storedSourcePreferenceKeepsSupportedCaptionTracks() {
        assertEquals("ja", storedSourcePreference("ja"))
        assertEquals("pt", storedSourcePreference("pt-BR"))
        assertEquals("he", storedSourcePreference("iw"))
    }

    @Test fun learningLanguageComesFromOnboardingThenTheSavedCaptionLanguage() {
        assertEquals("ja", storedLearningLanguage("ja", "en"))
        assertEquals("ko", storedLearningLanguage(null, "ko"))
        assertEquals("ja", storedLearningLanguage("klingon", "ja"))
        assertNull(storedLearningLanguage(null, "auto"))
        assertNull(storedLearningLanguage(null, null))
    }

    @Test fun initialGuideCompletedShowsForNewUsers() {
        assertEquals(
            false,
            initialGuideCompleted(
                preferenceExists = false,
                preferenceValue = false,
                onboardingCompleted = false,
            ),
        )
    }

    @Test fun initialGuideCompletedSkipsUsersFromBeforeTheGuide() {
        assertEquals(
            true,
            initialGuideCompleted(
                preferenceExists = false,
                preferenceValue = false,
                onboardingCompleted = true,
            ),
        )
    }

    @Test fun initialGuideCompletedHonorsStoredPreference() {
        assertEquals(
            true,
            initialGuideCompleted(
                preferenceExists = true,
                preferenceValue = true,
                onboardingCompleted = false,
            ),
        )
        assertEquals(
            false,
            initialGuideCompleted(
                preferenceExists = true,
                preferenceValue = false,
                onboardingCompleted = true,
            ),
        )
    }
}
