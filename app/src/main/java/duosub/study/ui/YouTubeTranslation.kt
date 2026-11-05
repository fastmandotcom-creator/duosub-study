package duosub.study.ui

import duosub.study.data.RawCaptionCue
import duosub.study.data.SubtitleSegment
import duosub.study.translation.TranslationLanguages

/** Languages written without spaces between words, so joined caption pieces get none either. */
private val UNSPACED_LANGUAGES = setOf("ja", "zh", "th", "lo", "km", "my")

/**
 * Pairs every text that translating [rows] asks for (each row's own text, see
 * [rowTranslationTexts]) with YouTube's own translated captions [cues]. YouTube translates its
 * caption track cue by cue with the original timing, while the app's rows are cut differently, so
 * each cue goes to the row it overlaps most and each row's translation joins the cues of that row
 * only. Texts whose row got no cue are left out, so the caller can translate them another way. The
 * first occurrence of a repeated text wins.
 */
internal fun youTubeRowTranslations(
    rows: List<SubtitleSegment>,
    cues: List<RawCaptionCue>,
    targetLanguage: String,
): Map<String, String> {
    val separator = if (TranslationLanguages.normalize(targetLanguage).substringBefore('-') in UNSPACED_LANGUAGES) "" else " "
    val pieces = assignCuesToRows(rows, cues)
    val translations = linkedMapOf<String, String>()
    rows.forEachIndexed { index, row ->
        val text = pieces[index].joinToString(separator).trim()
        if (text.isNotEmpty()) translations.putIfAbsent(row.originalText, text)
    }
    return translations
}

/** Each cue's text, given to the row it overlaps most (cues and rows both in time order). */
private fun assignCuesToRows(
    rows: List<SubtitleSegment>,
    cues: List<RawCaptionCue>,
): List<List<String>> {
    val pieces = List(rows.size) { mutableListOf<String>() }
    var first = 0
    for (cue in cues.sortedBy(RawCaptionCue::startMs)) {
        val text = cue.text.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) continue
        while (first < rows.size && rows[first].endMs <= cue.startMs) first++
        var best = -1
        var bestOverlap = 0L
        var row = first
        while (row < rows.size && rows[row].startMs < cue.endMs) {
            val overlap = minOf(rows[row].endMs, cue.endMs) - maxOf(rows[row].startMs, cue.startMs)
            if (overlap > bestOverlap) {
                best = row
                bestOverlap = overlap
            }
            row++
        }
        if (best >= 0) pieces[best] += text
    }
    return pieces
}
