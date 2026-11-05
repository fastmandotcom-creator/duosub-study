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
    val active = state.segments.getOrNull(activeIndex)?.let(state::studyLine)
    if (active != null) {
        return LearningOverlayContent(
            originalText = active.originalText,
            translatedText = active.translatedText ?: strings.get(pendingTranslationTextRes(false, state.translationError != null)),
            statusText = null,
            activeWordIndex = if (readingSide == null && state.wordHighlightEnabled) state.activeWordIndex else -1,
            segment = active,
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

internal fun LearningOverlayContent.isEmpty(): Boolean = originalText == null && translatedText == null && statusText == null
