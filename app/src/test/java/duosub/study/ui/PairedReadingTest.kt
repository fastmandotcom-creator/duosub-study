package duosub.study.ui

import duosub.study.data.AnalyzedToken
import duosub.study.data.LearningWordSelection
import duosub.study.data.PartOfSpeech
import duosub.study.data.SubtitleSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedReadingTest {
    @Test
    fun twoTimesIsPrimaryThenSecondaryTwice() {
        assertEquals(
            listOf(ReadSide.PRIMARY, ReadSide.SECONDARY, ReadSide.PRIMARY, ReadSide.SECONDARY),
            pairedReadSteps(2),
        )
        assertEquals(listOf(ReadSide.PRIMARY, ReadSide.SECONDARY), pairedReadSteps(1))
        assertEquals(DEFAULT_PAIR_READ_COUNT, storedPairReadCount(0))
    }

    @Test
    fun aFailedSideStopsThePair() =
        runBlocking {
            val spoken = mutableListOf<ReadSide>()
            val ok =
                playPairedReading(times = 2, speak = { side ->
                    spoken += side
                    side == ReadSide.PRIMARY
                }, gapMs = 0)
            assertTrue(!ok)
            assertEquals(listOf(ReadSide.PRIMARY, ReadSide.SECONDARY), spoken)
        }

    @Test
    fun aSavedMeaningIsTheSecondaryAndAWholeLineUsesItsTranslation() {
        val line = SubtitleSegment(1, 0, 1_000, "hello", "xin chào")
        val word = token("hello")
        val selection = LearningWordSelection(word, "en", "vi", "abcdefghijk", line, false, listOf(word))
        assertEquals("xin chào", wordPairSecondary(selection, null))
        assertEquals("chào", wordPairSecondary(selection, "chào"))
        val inside = LearningWordSelection(token("cat"), "en", "vi", "abcdefghijk", line.copy(originalText = "hello cat"), false)
        assertEquals("", wordPairSecondary(inside, null))
    }

    private fun token(text: String) = AnalyzedToken(text, 0, text.length, PartOfSpeech.OTHER)
}
