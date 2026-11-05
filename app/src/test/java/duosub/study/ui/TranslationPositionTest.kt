package duosub.study.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationPositionTest {
    @Test
    fun theTranslationGoesBelowUnlessSetAbove() {
        assertEquals(listOf("original", "translation"), subtitleLineOrder("original", "translation", TranslationPosition.BELOW))
        assertEquals(listOf("translation", "original"), subtitleLineOrder("original", "translation", TranslationPosition.ABOVE))
    }

    @Test
    fun aHiddenLineIsLeftOut() {
        assertEquals(listOf("original"), subtitleLineOrder("original", null, TranslationPosition.ABOVE))
        assertEquals(listOf("translation"), subtitleLineOrder(null, "translation", TranslationPosition.BELOW))
        assertEquals(emptyList<String>(), subtitleLineOrder<String>(null, null, TranslationPosition.ABOVE))
    }

    @Test
    fun anUnknownStoredValueKeepsTheTranslationBelow() {
        assertEquals(TranslationPosition.ABOVE, storedTranslationPosition("above"))
        assertEquals(TranslationPosition.BELOW, storedTranslationPosition(null))
        assertEquals(TranslationPosition.BELOW, storedTranslationPosition("sideways"))
        assert(TRANSLATION_POSITION_PREFERENCE in RESETTABLE_SETTING_KEYS)
    }

    @Test
    fun pairedReadingKeepsTheDefaultVietnameseEnglishPairInPrimaryThenSecondaryOrder() {
        val state =
            DuoSubUiState(
                studyPlayMode = StudyPlayMode.READ_PAIR,
                translationPosition = TranslationPosition.ABOVE,
            )

        assertEquals(TranslationPosition.BELOW, effectiveStudyTranslationPosition(state, readingPairActive = false))
        assertEquals(
            TranslationPosition.ABOVE,
            effectiveStudyTranslationPosition(state.copy(studyPlayMode = StudyPlayMode.NORMAL), readingPairActive = false),
        )
        assertEquals(
            TranslationPosition.BELOW,
            effectiveStudyTranslationPosition(state.copy(studyPlayMode = StudyPlayMode.NORMAL), readingPairActive = true),
        )
    }
}
