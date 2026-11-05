package duosub.study.ui

import duosub.study.data.SentenceSlice
import duosub.study.data.SubtitleSegment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A displayed row must show the translation of exactly the words it shows. A row is never a
 * character-count cut of another row's whole-sentence translation (owner decision 2026-11-05,
 * per-row contract; quality over cost, no proportional fallback).
 */
class PerRowTranslationTest {
    private val sentence = "According to the young man, he carefully closed the lid of the toilet before flushing to ensure the water did not splash."
    private val cut1 = sentence.indexOf("he carefully")
    private val cut2 = sentence.indexOf("before flushing")
    private val rows =
        listOf(
            SubtitleSegment(0, 0, 2_000, sentence.substring(0, cut1), sentence = SentenceSlice(sentence, listOf(cut1, cut2), 0)),
            SubtitleSegment(1, 2_000, 4_000, sentence.substring(cut1, cut2), sentence = SentenceSlice(sentence, listOf(cut1, cut2), 1)),
            SubtitleSegment(2, 4_000, 6_000, sentence.substring(cut2), sentence = SentenceSlice(sentence, listOf(cut1, cut2), 2)),
        )

    @Test
    fun eachRowIsSentAsItsOwnText() {
        assertEquals(rows.map { it.originalText }, rows.map { rowTranslationTexts(it).single() })
    }

    @Test
    fun aRowGetsOnlyItsOwnTranslation() {
        val shown = withTranslation(rows, 1, "the lid was carefully closed")
        assertEquals(null, shown[0].translatedText)
        assertEquals("the lid was carefully closed", shown[1].translatedText)
        assertEquals(null, shown[2].translatedText)
    }

    @Test
    fun repeatedRowsShareTheSameTranslation() {
        val row = rows[0]
        val repeated = listOf(row, row, row.copy(id = 99))
        val shown = withTranslation(repeated, 1, "Theo chàng trai chia sẻ")
        assertEquals(listOf("Theo chàng trai chia sẻ", "Theo chàng trai chia sẻ", "Theo chàng trai chia sẻ"), shown.map { it.translatedText })
    }

    @Test
    fun translationTextsSendsEachRowOnceWithoutTheWholeSentence() {
        assertEquals(rows.map { it.originalText }.distinct(), translationTexts(rows))
    }
}
