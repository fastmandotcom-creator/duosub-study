package duosub.study.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ASR / auto-caption stray marks the owner sees (`>>`, arrows, zero-width, control, bracketed
 * sound notes) must be removed, while real words in every subtitle script pass through unchanged.
 */
class SubtitleTextSanitizerTest {
    @Test
    fun arrowAndRunMarkersAreRemoved() {
        assertEquals("hello world", SubtitleTextSanitizer.sanitize("hello >> world"))
        assertEquals("the end", SubtitleTextSanitizer.sanitize("the << end"))
        assertEquals("next", SubtitleTextSanitizer.sanitize("» next «"))
        assertEquals("", SubtitleTextSanitizer.sanitize(">>"))
    }

    @Test
    fun zeroWidthAndControlCharactersAreRemoved() {
        // No-break space, thin space and zero-width joiner collapse to one space; control char gone.
        assertEquals("a b c", SubtitleTextSanitizer.sanitize("a\u00A0b\u2009c"))
        assertEquals("x", SubtitleTextSanitizer.sanitize("x\u200B"))
        assertEquals("ok", SubtitleTextSanitizer.sanitize("o\u0000k"))
    }

    @Test
    fun bracketedSoundNotesAndMusicSymbolsAreRemoved() {
        assertEquals("the song", SubtitleTextSanitizer.sanitize("the song [music]"))
        assertEquals("end", SubtitleTextSanitizer.sanitize("end (applause)"))
        assertEquals("good", SubtitleTextSanitizer.sanitize("good \u266A"))
    }

    @Test
    fun gluedAngleMarksKeepTheWord() {
        assertEquals("cool", SubtitleTextSanitizer.sanitize("cool<3"))
        assertEquals("yes", SubtitleTextSanitizer.sanitize("yes>"))
    }

    @Test
    fun realWordsPassThroughUnchanged() {
        assertEquals("Xin chào, mọi người!", SubtitleTextSanitizer.sanitize("Xin chào, mọi người!"))
        assertEquals("今日、楽しい場所です。", SubtitleTextSanitizer.sanitize("今日、楽しい場所です。"))
        assertEquals("这是我的朋友。", SubtitleTextSanitizer.sanitize("这是我的朋友。"))
        assertEquals("좋은 아침입니다", SubtitleTextSanitizer.sanitize("좋은 아침입니다"))
        assertEquals("สวัสดีครับ", SubtitleTextSanitizer.sanitize("สวัสดีครับ"))
        assertEquals("The price is 25% today.", SubtitleTextSanitizer.sanitize("The price is 25% today."))
    }

    @Test
    fun aLoneStraySymbolIsDroppedButAValidPunctuationIsKept() {
        assertEquals("hi", SubtitleTextSanitizer.sanitize("hi \u263A"))
        assertEquals("wait?", SubtitleTextSanitizer.sanitize("wait?"))
        assertEquals("so, ok", SubtitleTextSanitizer.sanitize("so, ok"))
    }

    @Test
    fun aRunOfEllipsesIsCollapsedToOne() {
        // A whole token that is only dots/ellipsis is dropped; a word's trailing ellipsis survives.
        assertEquals("thinking", SubtitleTextSanitizer.sanitize("thinking ……"))
        assertEquals("maybe", SubtitleTextSanitizer.sanitize("maybe..."))
    }

    @Test
    fun emptyAndBlankInput() {
        assertEquals("", SubtitleTextSanitizer.sanitize(""))
        assertEquals("x", SubtitleTextSanitizer.sanitize("   x   "))
    }
}
