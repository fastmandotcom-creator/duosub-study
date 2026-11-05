package duosub.study.ui

import duosub.study.data.SubtitleSegment
import org.junit.Assert.*
import org.junit.Test

class TranslationCoordinatorTest {
    @Test fun punctuationAndLongSilencePreventJoiningUnrelatedSentences() {
        val rows =
            listOf(
                SubtitleSegment(0, 0, 1000, "Done."),
                SubtitleSegment(1, 1000, 2000, "Next fragment"),
                SubtitleSegment(2, 4000, 5000, "a separate thought"),
            )
        assertEquals(rows.map { it.originalText }, sentenceCaptionUnits(rows, rows, true).map { it.text })
    }

    @Test fun japaneseFragmentsJoinWithoutAddingSpacesAndRetainSourceTimes() {
        val rows =
            listOf(
                SubtitleSegment(0, 0, 1000, "雨が降っていたので"),
                SubtitleSegment(1, 1000, 2000, "出かけるのをやめました。"),
            )
        assertEquals("雨が降っていたので出かけるのをやめました。", sentenceCaptionUnits(rows, rows, true).single().text)
        assertEquals(listOf(0L, 1000L), rows.map { it.startMs })
    }

    @Test fun anUnfinishedJapanesePieceJoinsTheNextAfterAPause() {
        // Owner report 2026-10-29: the speaker paused after "とても" and "梅田で", so each went to Google alone.
        val rows =
            listOf(
                SubtitleSegment(0, 0, 800, "はい。"),
                SubtitleSegment(1, 900, 1500, "とても"),
                SubtitleSegment(2, 3500, 5000, "楽しい場所です。"),
                SubtitleSegment(3, 6000, 7500, "私の前のアパートから"),
                SubtitleSegment(4, 9500, 10200, "梅田で"),
                SubtitleSegment(5, 12000, 13500, "自転車で10分ぐらいです。"),
            )
        assertEquals(
            listOf("はい。", "とても楽しい場所です。", "私の前のアパートから梅田で自転車で10分ぐらいです。"),
            sentenceCaptionUnits(rows, rows, true).map { it.text },
        )
    }

    @Test fun japaneseWithoutSentenceMarksKeepsTheShortPause() {
        val rows = listOf(SubtitleSegment(0, 0, 1000, "とても"), SubtitleSegment(1, 3000, 4000, "楽しい場所です"))
        assertEquals(listOf("とても", "楽しい場所です"), sentenceCaptionUnits(rows, rows, true).map { it.text })
    }

    @Test fun aPauseLongerThanThreeSecondsStillEndsTheUnit() {
        val rows = listOf(SubtitleSegment(0, 0, 1000, "とても"), SubtitleSegment(1, 4500, 5500, "楽しい場所です。"))
        assertEquals(listOf("とても", "楽しい場所です。"), sentenceCaptionUnits(rows, rows, true).map { it.text })
    }

    private val sentence = SubtitleSegment(0, 0, 2000, "I gave up because it was raining.")
    private val display =
        listOf(
            sentence.copy(endMs = 1000, originalText = "I gave up"),
            sentence.copy(id = 1, startMs = 1000, originalText = "because it was raining."),
        )

    @Test fun naturalTranslationRetainsContextAcrossDisplaySplits() {
        val unit = sentenceCaptionUnits(display, listOf(sentence), true).single()
        assertEquals(sentence.originalText, unit.text)
        assertEquals(listOf(0, 1), unit.indices)
        assertEquals(1000, display[1].startMs)
    }

    @Test fun rawModePreservesIndependentDisplayUnits() {
        assertEquals(display.map { it.originalText }, sentenceCaptionUnits(display, listOf(sentence), false).map { it.text })
    }

    @Test fun unmatchedDisplaySegmentsAreNeverLost() {
        assertEquals(listOf(0, 1), sentenceCaptionUnits(display, emptyList(), true).flatMap { it.indices })
    }

    @Test fun interiorSentenceEndsStartANewTranslationUnit() {
        val rows =
            listOf(
                SubtitleSegment(0, 0, 4000, "The three models, but you are paying a fraction of the price. Even at, look at"),
                SubtitleSegment(1, 4000, 5000, "this."),
            )
        val display = captionDisplaySegments(rows, CaptionFormat.WHOLE_SENTENCE, natural = true)
        assertEquals(
            listOf("The three models, but you are paying a fraction of the price.", "Even at, look at this."),
            display.map { it.originalText },
        )
        assertTrue(display[1].startMs > 0)
    }

    @Test fun unpunctuatedCaptionsStopJoiningAtAReadableLength() {
        val rows =
            listOf(
                SubtitleSegment(0, 0, 3000, "Traditional LMS can take three to over 300 seconds"),
                SubtitleSegment(1, 3000, 6000, "to do the type of classification work that this model can"),
                SubtitleSegment(2, 6000, 8000, "That is actually 40 to 200x faster."),
            )
        val display = captionDisplaySegments(rows, CaptionFormat.WHOLE_SENTENCE, natural = true)
        assertTrue(display.size > 1)
        assertTrue(display.all { it.originalText.length <= MAX_UNIT_CHARACTERS })
        assertEquals("That is actually 40 to 200x faster.", display.last().originalText)
    }

    @Test fun unpunctuatedAutoCaptionsKeepTheirUnits() {
        val rows = listOf(SubtitleSegment(0, 0, 2000, "so we went to the beach"), SubtitleSegment(1, 5000, 6000, "and it rained"))
        assertEquals(
            rows.map {
                it.originalText
            },
            captionDisplaySegments(rows, CaptionFormat.WHOLE_SENTENCE, natural = true).map { it.originalText },
        )
    }

    @Test fun generatedAsrCaptionsWithAMidSentencePauseJoinIntoOneUnit() {
        // ASR (auto) track without punctuation: the speaker paused ~2 s mid-sentence, so the two pieces
        // must join into a single unit for context-aware translation. The default 1.2 s join pause would
        // keep them apart and tear the sentence apart.
        val rows =
            listOf(
                SubtitleSegment(0, 0, 2000, "the price is"),
                SubtitleSegment(1, 4000, 6000, "very low right now"),
            )
        assertEquals(listOf("the price is", "very low right now"), sentenceCaptionUnits(rows, rows, true).map { it.text })
        assertEquals("the price is very low right now", sentenceCaptionUnits(rows, rows, true, generated = true).single().text)
    }

    @Test fun generatedAsrJoiningStillStopsAtAPausePastTheAsrLimit() {
        val rows =
            listOf(
                SubtitleSegment(0, 0, 1000, "I gave up"),
                SubtitleSegment(1, 3000, 4000, "because it was raining."),
            )
        assertEquals("I gave up because it was raining.", sentenceCaptionUnits(rows, rows, true, generated = true).single().text)
        val longPause =
            listOf(
                SubtitleSegment(0, 0, 1000, "I gave up"),
                SubtitleSegment(1, 4000, 5000, "because it was raining."),
            )
        assertEquals(listOf("I gave up", "because it was raining."), sentenceCaptionUnits(longPause, longPause, true, generated = true).map { it.text })
    }

    @Test fun generatedAsrJoiningGrowsTheReadableSizeCap() {
        // Two unpunctuated ASR pieces whose combined text exceeds the readable cap: only the ASR cap
        // keeps them one unit. The boundary is placed on a word edge, matching how pieces are joined.
        val left = "a single unbroken run of words that goes on and on and on and on and on and on and on"
        val right = "and on and on and on and on, so it keeps going like this"
        val rows =
            listOf(
                SubtitleSegment(0, 0, 5000, left),
                SubtitleSegment(1, 5000, 10_000, right),
            )
        val joined = sentenceCaptionUnits(rows, rows, true, generated = true).single()
        assertEquals("$left $right", joined.text)
        // The two pieces together exceed MAX_UNIT_CHARACTERS, so only the ASR cap keeps them joined.
        assertTrue(left.length + right.length > MAX_UNIT_CHARACTERS)
    }

    @Test fun shortPhraseRowsRememberTheirWholeSentence() {
        val text = "Though the catacombs did offer a space where inconvenient bodies could disappear."
        val display = captionDisplaySegments(listOf(SubtitleSegment(0, 0, 6000, text)), CaptionFormat.SHORT_PHRASES, natural = true)
        assertTrue(display.size > 1)
        assertEquals(display.indices.toList(), display.map { it.sentence?.index })
        assertTrue(display.all { it.sentence?.text == text })
        assertEquals(display.indices.map { it.toLong() }, display.map { it.id })
        display.drop(1).forEachIndexed { index, row ->
            assertTrue(text.substring(display[index + 1].sentence!!.cuts[index]).startsWith(row.originalText))
        }
    }
}
