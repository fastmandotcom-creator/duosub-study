package duosub.study.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import duosub.study.R
import duosub.study.data.SubtitleSegment
import duosub.study.data.SubtitleWord

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
    val active =
        activeRaw?.let { row ->
            val slice = row.sentence
            val whole =
                if (slice == null || slice.cuts.isEmpty()) row
                else row.copy(
                    originalText = slice.text,
                    translatedText = sentenceTranslation(state.segments, row),
                    words = sentenceWords(state.segments, row),
                )
            state.studyLine(whole)
        }
    if (active != null) {
        val fragment = activeRaw
        val wordOffset = fragment?.let { sentenceWordOffset(state.segments, it) } ?: 0
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
internal fun sentenceWords(rows: List<SubtitleSegment>, row: SubtitleSegment): List<SubtitleWord> {
    val slice = row.sentence ?: return row.words
    val sameSentence = rows.filter { it.sentence?.text == slice.text }
    val ordered = sameSentence.sortedBy { it.sentence?.index ?: 0 }
    return ordered.flatMap { it.words }
}

/** The full translation of the sentence [row] belongs to: the translations of that sentence's own
 * display rows, in row order, joined with one space. Rows still pending are skipped, so the
 * sentence reads on from the rows that have answered; null when none is translated yet. */
internal fun sentenceTranslation(rows: List<SubtitleSegment>, row: SubtitleSegment): String? {
    val slice = row.sentence ?: return row.translatedText
    val ordered =
        rows
            .filter { it.sentence?.text == slice.text && it.translatedText != null }
            .sortedBy { it.sentence?.index ?: 0 }
    val parts = ordered.map { it.translatedText }.filter { it.isNotBlank() }
    return parts.joinToString(" ").takeIf { it.isNotEmpty() }
}

/** How many whole-sentence words sit before [row]'s own words, so a fragment word index maps onto the joined list. */
internal fun sentenceWordOffset(rows: List<SubtitleSegment>, row: SubtitleSegment): Int {
    val slice = row.sentence ?: return 0
    return rows
        .filter { it.sentence?.text == slice.text && (it.sentence?.index ?: 0) < slice.index }
        .sumOf { it.words.size }
}

internal fun LearningOverlayContent.isEmpty(): Boolean = originalText == null && translatedText == null && statusText == null
