package duosub.study.ui

import duosub.study.data.ANKI_FIELDS
import duosub.study.data.AnalyzedToken
import duosub.study.data.LearningWordSelection
import duosub.study.data.PartOfSpeech
import duosub.study.data.SavedWord
import duosub.study.data.SubtitleSegment
import duosub.study.data.ankiNoteFields
import duosub.study.data.decodeWord
import duosub.study.data.defaultTransferMapping
import duosub.study.data.encodeWord
import duosub.study.data.exportAnkiTsv
import duosub.study.data.exportBackup
import duosub.study.data.mergeResaved
import duosub.study.data.parseTransfer
import duosub.study.data.previewImport
import duosub.study.data.savedWordFrom
import duosub.study.data.validateTransferWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The third subtitle line kept with saved words (asked for by mrAlishah on PR #191). */
class ThirdLineOnSavedWordsTest {
    private val row =
        SubtitleSegment(id = 4, startMs = 1_000, endMs = 2_000, originalText = "Der Hund schläft.", translatedText = "The dog sleeps.")

    private fun word(
        third: String? = null,
        language: String? = third?.let { "fa" },
    ) = SavedWord(
        "1",
        "Hund",
        null,
        "de",
        "en",
        "dog",
        "Der Hund schläft.",
        "The dog sleeps.",
        "dQw4w9WgXcQ",
        1_000,
        2_000,
        false,
        true,
        thirdSentence = third,
        thirdLanguage = language,
    )

    private fun selection(videoId: String? = "dQw4w9WgXcQ") =
        LearningWordSelection(AnalyzedToken("Hund", 4, 8, PartOfSpeech.NOUN), "de", "en", videoId, row, false)

    private fun state(videoId: String = "dQw4w9WgXcQ") =
        DuoSubUiState(
            activeVideoId = videoId,
            resolvedSourceLanguage = "de",
            targetLanguage = "en",
            thirdLineEnabled = true,
            thirdLanguage = "fa",
            thirdLines = ThirdLines(videoId, "fa", mapOf(4L to "سگ می‌خوابد.")),
        )

    @Test fun savingAWordKeepsTheThirdLineOnScreen() {
        val saved = savedWordFrom(state().withThirdLineOnScreen(selection()), "dog", online = true)

        assertEquals("سگ می‌خوابد.", saved.thirdSentence)
        assertEquals("fa", saved.thirdLanguage)
    }

    @Test fun noThirdLineWhileItIsOffOrForAnotherVideo() {
        assertNull(savedWordFrom(state().copy(thirdLineEnabled = false).withThirdLineOnScreen(selection()), "dog", true).thirdSentence)
        assertNull(savedWordFrom(state("otherVideo1").withThirdLineOnScreen(selection()), "dog", true).thirdSentence)
        assertNull(savedWordFrom(state().withThirdLineOnScreen(selection(videoId = null)), "dog", true).thirdSentence)
    }

    @Test fun aThirdLineAlreadyPickedIsKept() {
        val picked = selection().copy(thirdText = "earlier", thirdLanguage = "ja")

        assertEquals(picked, state().withThirdLineOnScreen(picked))
    }

    @Test fun storageAndBackupKeepTheThirdLine() {
        val saved = word(third = "سگ می‌خوابد.")

        assertEquals(saved, decodeWord(encodeWord(saved)))
        assertEquals(saved, parseTransfer(exportBackup(listOf(saved))).backup!!.single())
    }

    @Test fun olderWordsHaveNoThirdLine() {
        val old = encodeWord(word())

        assertNull(decodeWord(old).thirdSentence)
        assertNull(decodeWord(old).thirdLanguage)
    }

    @Test fun savingAgainWithTheLineOffKeepsTheOldThirdLine() {
        val old = word(third = "old line")

        assertEquals("old line", mergeResaved(old, word()).thirdSentence)
        assertEquals("fa", mergeResaved(old, word()).thirdLanguage)
        assertEquals("new line", mergeResaved(old, word(third = "new line", language = "ja")).thirdSentence)
    }

    @Test fun exportCarriesTheThirdLine() {
        val document = parseTransfer(exportAnkiTsv(listOf(word(third = "سگ می‌خوابد."))))
        val imported = previewImport(document, defaultTransferMapping(document), "de", "en", emptyList()).words.single()

        assertEquals("سگ می‌خوابد.", imported.thirdSentence)
        assertEquals("fa", imported.thirdLanguage)
    }

    @Test fun ankiTranslationFieldShowsTheThirdLineUnderTheTranslation() {
        val translation = ANKI_FIELDS.indexOf("Translation")

        assertEquals("The dog sleeps.<br>سگ &lt;می&gt;", ankiNoteFields(word(third = "سگ <می>"))[translation])
        assertEquals("The dog sleeps.", ankiNoteFields(word())[translation])
        assertEquals("only third", ankiNoteFields(word(third = "only third").copy(translatedSentence = null))[translation])
        assertEquals(ANKI_FIELDS.size, ankiNoteFields(word(third = "x")).size)
    }

    @Test fun importRejectsALanguageWithoutALine() {
        assertThrows(IllegalArgumentException::class.java) { validateTransferWord(word(third = null, language = "fa")) }
        assertThrows(IllegalArgumentException::class.java) { validateTransferWord(word(third = " padded ")) }
    }

    @Test fun practiceHighlightsTheSavedWordInItsLine() {
        val line = withWordHighlighted("We've done Tons of testing", "tons", androidx.compose.ui.graphics.Color.Red)

        assertEquals("We've done Tons of testing", line.text)
        assertEquals(11 until 15, line.spanStyles.single().let { it.start until it.end })
        assertEquals(emptyList<Any>(), withWordHighlighted("no match here", "tons", androidx.compose.ui.graphics.Color.Red).spanStyles)
    }
}
