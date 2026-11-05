package duosub.study.data

import java.text.Normalizer

/**
 * A standard, language-neutral filter for the messy text of YouTube's automatic captions
 * (owner request 2026-11-06: auto captions show stray marks such as `>>`, arrows, isolated
 * angle brackets, control and zero-width characters).
 *
 * The filter only removes marks that are not words; real words in any of the app's subtitle
 * scripts (Latin with diacritics, Japanese kana/kanji, Chinese, Korean, Thai) pass through
 * untouched. Two stages, in this order:
 *
 * 1. **Normalization.** Combining marks fold into their letters (NFC); every whitespace variant
 *    (tab, narrow, thin, non-breaking, zero-width, BOM) becomes one plain space and is collapsed;
 *    control characters are dropped.
 *
 * 2. **ASR-mark removal.** Per token, drop things the recognizer writes but that are not words:
 *    an all-arrow run (`>`, `>>`, `<<`, `«`, `»`), an all-punctuation run (`……`, `----`, `···`),
 *    bracketed sound notes (`[applause]`, `(music)`, `♪`), a stray angle mark glued to a word
 *    (`cool<3` keeps `cool`), and any character that is not a letter of a spoken script, a digit,
 *    a space, or common punctuation (`. , : ; ! ? ' " - – / & % # @ ( )`).
 */
internal object SubtitleTextSanitizer {

    /** A token made up only of the arrow / run markers the recognizer leaves. */
    private val RUN_MARKER = Regex("^[>\\u003C\\u00AB\\u00BB\\u2039\\u203A]+$")

    /** A token that is only dashes, dots or an ellipsis (`……`, `----`, `···`). */
    private val PUNCTUATION_RUN = Regex("^[\\u2026\\u002D\\u2013\\u2014\\u002E]+$")

    /** A bracketed note or a lone music note: `[applause]`, `(music)`, `♪`. */
    private val SOUND_NOTE = Regex("^[\\[\\(][^\\]\\)]{0,24}[\\]\\)]$|\\u266A\\u266B", RegexOption.IGNORE_CASE)

    /** A stray angle mark attached to a word; the word is kept, the mark dropped. */
    private val GLUED_ANGLE = Regex("<|>")

    /** Everything a spoken caption is made of: letters/digits of the spoken scripts, spaces, and
     * the punctuation that real captions use. Anything else (emoji, private-use, stray symbols)
     * is dropped. */
    private val VALID_CHAR = Regex(
        "[\\p{L}\\p{Nd}" +
            "\\u3040-\\u30FF" + // hiragana + katakana
            "\\u4E00-\\u9FFF" + // CJK unified ideographs
            "\\uAC00-\\uD7AF" + // hangul syllables
            "\\u0E00-\\u0E7F" + // thai
            "\\s\\u00A0\\u2002\\u2009\\u2007\\u202F" + // ordinary + narrow/no-break spaces
            "\\.,:\\;!\\?\\u00B7\\'\\u2018\\u2019\\u201A\\u201C\\u201D\\u201E\\u201F" + // quote + dot
            "\\u002D\\u2013\\u2014\\u2026" + // dash, en dash, em dash, ellipsis
            "\\/\\&\\%\\#\\@\\(\\)" +
            "]",
    )

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var result = Normalizer.normalize(text, Normalizer.Form.NFC)
        result = result.replace(Regex("[\\s\\u00A0\\u2000-\\u200B\\u2007\\u202F\\u2000\\uFEFF]"), " ")
        result = result.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
        result = result.trim()
        return result
            .split(" ")
            .filter { it.isNotEmpty() }
            .map(::cleanToken)
            .filterNot(String::isEmpty)
            .joinToString(" ")
            .trim()
    }

    /** One token with its ASR marks removed; a real word is returned unchanged. */
    private fun cleanToken(token: String): String {
        when {
            SOUND_NOTE.matches(token) -> return ""
            RUN_MARKER.matches(token) -> return ""
            PUNCTUATION_RUN.matches(token) -> return ""
            token.isNotEmpty() && GLUED_ANGLE.containsMatchIn(token) -> return token.replace(GLUED_ANGLE, "")
        }
        // A lone stray symbol that is not a letter or common punctuation.
        if (token.length == 1 && !VALID_CHAR.matches(token)) return ""
        return token
    }
}
