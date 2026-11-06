package duosub.study.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import duosub.study.R
import duosub.study.data.SubtitleSegment
import duosub.study.data.SubtitleWord

internal data class SubtitleTextRange(
    val start: Int,
    val endExclusive: Int,
)

internal data class LearningOverlayContent(
    val originalText: String?,
    val translatedText: String?,
    val statusText: String?,
    val activeWordIndex: Int = -1,
    val segment: SubtitleSegment? = null,
    val words: List<SubtitleWord> = segment?.words.orEmpty(),
    /** Settings → Languages → Third line, under the translation; null while off or not translated yet. */
    val thirdText: String? = null,
    /** The side currently spoken by paired reading, if this overlay is showing that sentence. */
    val readingSide: ReadSide? = null,
    /** Display-segment index used to keep the pair's highlight color stable across both TTS sides. */
    val spokenPairIndex: Int? = null,
    /** The active short-caption phrase's range in each full sentence shown during paired reading. */
    val spokenOriginalRange: SubtitleTextRange? = null,
    val spokenTranslatedRange: SubtitleTextRange? = null,
)

private data class OverlaySentencePresentation(
    val fragment: SubtitleSegment,
    val rawSentence: SubtitleSegment,
    val shownSentence: SubtitleSegment,
    val translation: SentenceTranslationContent,
)

/** Looks up the app's own texts, so plain JVM tests can pass a stub instead of Android resources. */
internal interface UiStrings {
    fun get(
        @StringRes id: Int,
        vararg formatArgs: Any,
    ): String
}

/** [UiStrings] read from these resources, in the interface language. */
internal fun Resources.uiStrings(): UiStrings =
    object : UiStrings {
        override fun get(
            id: Int,
            vararg formatArgs: Any,
        ): String = if (formatArgs.isEmpty()) getString(id) else getString(id, *formatArgs)
    }

/** [learningOverlayContent] with the app's own texts in the interface language. */
@Composable
internal fun learningOverlayContent(state: DuoSubUiState): LearningOverlayContent? {
    val reader = LocalPairedReading.current
    val readingSegmentId = reader?.readingSegmentId
    return learningOverlayContent(
        state,
        LocalResources.current.uiStrings(),
        readingSegmentId = readingSegmentId,
        readingSide = reader?.progress?.side.takeIf { readingSegmentId != null },
    )
}

internal fun learningOverlayContent(
    state: DuoSubUiState,
    strings: UiStrings,
    readingSegmentId: Long? = null,
    readingSide: ReadSide? = null,
): LearningOverlayContent? {
    val content = unfilteredLearningOverlayContent(state, strings, readingSegmentId, readingSide) ?: return null
    return content.copy(
        originalText = content.originalText.takeIf { state.showOriginal() },
        translatedText = content.translatedText.takeIf { state.showTranslation() },
        thirdText = content.thirdText.takeIf { state.showTranslation() },
        statusText = content.statusText.takeIf { state.showOriginal() || state.showTranslation() },
    )
}

private fun unfilteredLearningOverlayContent(
    state: DuoSubUiState,
    strings: UiStrings,
    readingSegmentId: Long?,
    readingSide: ReadSide?,
): LearningOverlayContent? {
    if (state.activeVideoId == null) return null
    if (state.liveFallback) {
        return LearningOverlayContent(
            activeWordIndex = if (state.wordHighlightEnabled) state.activeWordIndex else -1,
            words = state.liveOriginal?.let(::liveCaptionWords).orEmpty(),
            originalText = state.liveOriginal,
            translatedText = state.liveTranslated ?: if (state.liveOriginal != null) strings.get(R.string.player_translating) else null,
            statusText = strings.get(R.string.player_live_subtitles_status, state.statusMessage.orEmpty()),
        )
    }
    val activeIndex = pairedReadingLineIndex(state.currentIndex, state.segments, readingSegmentId)
    val activeRaw = state.segments.getOrNull(activeIndex)
    // In the single-line overlay one screen row is the only caption surface, so a short display row
    // that is only a piece of a longer sentence ("…did not splash") is the "torn last part" the
    // learner sees in landscape. Expand such a row to its whole sentence, which the portrait panel
    // already shows as its stacked rows; the fragment's own words stay the reading anchor.
    val presentation =
        activeRaw?.let { row ->
            val translation = sentenceTranslationContent(state.segments, row)
            val slice = row.sentence
            val whole =
                if (slice == null || slice.cuts.isEmpty()) {
                    row
                } else {
                    row.copy(
                        originalText = slice.text,
                        translatedText = translation.text,
                        words = sentenceWords(state.segments, row),
                    )
                }
            OverlaySentencePresentation(
                fragment = row,
                rawSentence = whole,
                shownSentence = state.studyLine(whole),
                translation = translation,
            )
        }
    if (presentation != null) {
        val active = presentation.shownSentence
        val fragment = presentation.fragment
        val wholeRaw = presentation.rawSentence
        val shownFragment = state.studyLine(fragment)
        val spokenOriginalRange =
            if (readingSegmentId != null) {
                rangeForShownText(
                    fullText = active.originalText,
                    sourceText = wholeRaw.originalText,
                    sourceRange = sentenceFragmentRange(fragment),
                    translatedText = wholeRaw.translatedText,
                    translatedRange = presentation.translation.activeRange,
                    fragmentText = shownFragment.originalText,
                )
            } else {
                null
            }
        val spokenTranslatedRange =
            if (readingSegmentId != null) {
                rangeForShownText(
                    fullText = active.translatedText,
                    sourceText = wholeRaw.originalText,
                    sourceRange = sentenceFragmentRange(fragment),
                    translatedText = wholeRaw.translatedText,
                    translatedRange = presentation.translation.activeRange,
                    fragmentText = shownFragment.translatedText,
                )
            } else {
                null
            }
        val wordOffset = sentenceWordOffset(state.segments, fragment)
        val adjustedWord = state.activeWordIndex
        val activeWord =
            if (adjustedWord >= 0) adjustedWord + wordOffset else -1
        return LearningOverlayContent(
            originalText = active.originalText,
            translatedText = active.translatedText ?: strings.get(pendingTranslationTextRes(false, state.translationError != null)),
            statusText = null,
            activeWordIndex = if (readingSide == null && state.wordHighlightEnabled) activeWord else -1,
            segment = active,
            words = active.words,
            thirdText = state.thirdLineFor(active),
            readingSide = readingSide.takeIf { readingSegmentId != null },
            spokenPairIndex = activeIndex.takeIf { readingSegmentId != null },
            spokenOriginalRange = spokenOriginalRange,
            spokenTranslatedRange = spokenTranslatedRange,
        )
    }
    val status =
        state.errorMessage ?: state.statusMessage
            ?: if (state.segments.isNotEmpty()) strings.get(R.string.player_waiting_next_caption) else null
    return status?.let {
        LearningOverlayContent(originalText = null, translatedText = null, statusText = it)
    }
}

/** The timed words of the whole sentence [row] belongs to, so karaoke follows the full sentence. */
internal fun sentenceWords(
    rows: List<SubtitleSegment>,
    row: SubtitleSegment,
): List<SubtitleWord> {
    val slice = row.sentence ?: return row.words
    val sameSentence = rows.filter { it.sentence?.text == slice.text }
    val ordered = sameSentence.sortedBy { it.sentence?.index ?: 0 }
    return ordered.flatMap { it.words }
}

/** The full translation of the sentence [row] belongs to: the translations of that sentence's own
 * display rows, in row order, joined with one space. Rows still pending are skipped, so the
 * sentence reads on from the rows that have answered; null when none is translated yet. */
internal data class SentenceTranslationContent(
    val text: String?,
    val activeRange: SubtitleTextRange?,
)

internal fun sentenceTranslation(
    rows: List<SubtitleSegment>,
    row: SubtitleSegment,
): String? = sentenceTranslationContent(rows, row).text

internal fun sentenceTranslationContent(
    rows: List<SubtitleSegment>,
    row: SubtitleSegment,
): SentenceTranslationContent {
    val slice = row.sentence ?: return SentenceTranslationContent(row.translatedText, fullTextRange(row.translatedText))
    val ordered =
        rows
            .filter { it.sentence?.text == slice.text && !it.translatedText.isNullOrBlank() }
            .sortedBy { it.sentence?.index ?: 0 }
    val parts = mutableListOf<String>()
    var activeRange: SubtitleTextRange? = null
    var length = 0
    ordered.forEach { sibling ->
        val part = sibling.translatedText?.takeIf(String::isNotBlank) ?: return@forEach
        if (parts.isNotEmpty()) length++
        val start = length
        length += part.length
        if (sibling.sentence?.index == slice.index) activeRange = SubtitleTextRange(start, length)
        parts += part
    }
    return SentenceTranslationContent(parts.joinToString(" ").takeIf(String::isNotEmpty), activeRange)
}

internal fun sentenceFragmentRange(row: SubtitleSegment): SubtitleTextRange? {
    val slice = row.sentence ?: return fullTextRange(row.originalText)
    if (slice.cuts.isEmpty()) return fullTextRange(row.originalText)
    if (slice.index !in 0..slice.cuts.size) return null
    val startBound = slice.cuts.getOrNull(slice.index - 1) ?: 0
    val endBound = slice.cuts.getOrNull(slice.index) ?: slice.text.length
    if (startBound !in 0..slice.text.length || endBound !in startBound..slice.text.length) return null
    val text = row.originalText.trim()
    if (text.isEmpty()) return null
    val start = slice.text.indexOf(text, startBound)
    return start
        .takeIf { it >= startBound && it + text.length <= endBound }
        ?.let { SubtitleTextRange(it, it + text.length) }
}

private fun fullTextRange(text: String?): SubtitleTextRange? = text?.takeIf(String::isNotBlank)?.let { SubtitleTextRange(0, it.length) }

private fun rangeForShownText(
    fullText: String?,
    sourceText: String,
    sourceRange: SubtitleTextRange?,
    translatedText: String?,
    translatedRange: SubtitleTextRange?,
    fragmentText: String?,
): SubtitleTextRange? {
    if (fullText.isNullOrBlank() || fragmentText.isNullOrBlank()) return null
    val exactRange =
        when (fullText) {
            sourceText -> sourceRange
            translatedText -> translatedRange
            else -> null
        }
    if (exactRange != null && exactRange.endExclusive <= fullText.length) return exactRange
    val start = fullText.indexOf(fragmentText)
    return start.takeIf { it >= 0 }?.let { SubtitleTextRange(it, it + fragmentText.length) }
}

/** How many whole-sentence words sit before [row]'s own words, so a fragment word index maps onto the joined list. */
internal fun sentenceWordOffset(
    rows: List<SubtitleSegment>,
    row: SubtitleSegment,
): Int {
    val slice = row.sentence ?: return 0
    return rows
        .filter { it.sentence?.text == slice.text && it.sentence.index < slice.index }
        .sumOf { it.words.size }
}

internal fun LearningOverlayContent.isEmpty(): Boolean = originalText == null && translatedText == null && statusText == null
