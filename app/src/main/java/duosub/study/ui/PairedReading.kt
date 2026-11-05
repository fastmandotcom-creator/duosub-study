package duosub.study.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import duosub.study.R
import duosub.study.data.LearningWordSelection
import duosub.study.data.SubtitleSegment
import kotlinx.coroutines.delay

/** One side of a reading pair: the original line, then its translation. */
internal enum class ReadSide { PRIMARY, SECONDARY }

/**
 * One pass is primary then secondary. Two times, the default, is hello, xin chào, hello, xin chào.
 * A blank side is still listed; the speaker skips it.
 */
internal fun pairedReadSteps(times: Int): List<ReadSide> =
    List(times.coerceAtLeast(1)) { listOf(ReadSide.PRIMARY, ReadSide.SECONDARY) }.flatten()

/** Which pass is being read, so the panel can show "Primary 1/2". */
internal data class PairReadProgress(
    val pass: Int,
    val times: Int,
    val side: ReadSide,
)

/** Keeps the sentence being spoken selected even if the page's playback clock has moved ahead. */
internal fun pairedReadingLineIndex(
    currentIndex: Int,
    segments: List<SubtitleSegment>,
    readingSegmentId: Long?,
): Int {
    val readingIndex = readingSegmentId?.let { id -> segments.indexOfFirst { it.id == id } } ?: -1
    return readingIndex.takeIf { it >= 0 } ?: currentIndex
}

/** A pause between the two sides, so the pair does not blur into one sound. */
internal const val PAIR_READ_GAP_MS = 280L

internal data class SpokenPair(
    val primary: String,
    val primaryLanguage: String,
    val secondary: String,
    val secondaryLanguage: String,
)

/**
 * Speaks [times] pairs. [speak] returns false when that side failed; a blank side should return true.
 * [gapMs] is zero in tests.
 */
internal suspend fun playPairedReading(
    times: Int,
    speak: suspend (ReadSide) -> Boolean,
    onProgress: (PairReadProgress) -> Unit = {},
    gapMs: Long = PAIR_READ_GAP_MS,
): Boolean {
    val steps = pairedReadSteps(times)
    steps.forEachIndexed { index, side ->
        onProgress(PairReadProgress(index / 2 + 1, steps.size / 2, side))
        if (!speak(side)) return false
        if (index < steps.lastIndex && gapMs > 0) delay(gapMs)
    }
    return true
}

/** The line on screen, as a pair. A word that is the whole line uses that line's translation. */
internal fun currentSpokenPair(state: DuoSubUiState): SpokenPair? {
    val source = state.resolvedSourceLanguage ?: "en"
    if (state.liveFallback) {
        val primary = state.liveOriginal?.trim().orEmpty()
        if (primary.isEmpty()) return null
        return SpokenPair(primary, source, state.liveTranslated?.trim().orEmpty(), state.targetLanguage)
    }
    return state.segments.getOrNull(state.currentIndex)?.let { spokenPairOf(state, it) }
}

/** [segment] as the pair shown for it (line 1 then line 2), or null when its first line is blank. */
internal fun spokenPairOf(
    state: DuoSubUiState,
    segment: SubtitleSegment,
): SpokenPair? {
    val line = state.studyLine(segment)
    val primary = line.originalText.trim()
    if (primary.isEmpty()) return null
    val vietnameseEnglish = state.usesVietnameseEnglishPair()
    return SpokenPair(
        primary,
        if (vietnameseEnglish) DEFAULT_SOURCE_LANGUAGE else state.resolvedSourceLanguage ?: DEFAULT_SOURCE_LANGUAGE,
        line.translatedText?.trim().orEmpty(),
        if (vietnameseEnglish) DEFAULT_TARGET_LANGUAGE else state.targetLanguage,
    )
}

/**
 * The secondary text for a tapped word: a saved meaning, or the line's translation when the word
 * is the whole line (hello / xin chào). A word inside a longer line has no pair until it is saved.
 */
internal fun wordPairSecondary(
    selection: LearningWordSelection,
    savedMeaning: String?,
): String {
    val saved = savedMeaning?.trim().orEmpty()
    if (saved.isNotEmpty()) return saved
    val line = selection.segment ?: return ""
    val spoken = selection.parts.joinToString("") { it.text }.trim()
    if (spoken != line.originalText.trim()) return ""
    return line.translatedText?.trim().orEmpty()
}

internal val LocalPairedReading = staticCompositionLocalOf<PairedLineReaderHost?> { null }

/** Reads the current line, or a word and its meaning, and can start the video again afterwards. */
internal class PairedLineReaderHost(
    private val pronouncer: WordPronouncer,
    private val controller: YouTubeWebController,
) {
    var state: DuoSubUiState? = null
    var times: Int = DEFAULT_PAIR_READ_COUNT
    var progress by mutableStateOf<PairReadProgress?>(null)
        private set
    var readingSegmentId by mutableStateOf<Long?>(null)
        private set
    private var generation = 0

    /**
     * The line [segmentId] has played to its end and the video is paused: read its pair, then play on
     * into the next line, which is read the same way when it ends.
     */
    fun readEndedLine(segmentId: Long) {
        val current = state ?: return
        val segment = current.segments.firstOrNull { it.id == segmentId } ?: return
        val pair = spokenPairOf(current, segment)
        if (pair == null) {
            controller.play()
            return
        }
        read(pair, resumeAfter = true, segmentId = segmentId)
    }

    fun readCurrent(resumeAfter: Boolean) {
        val current = state ?: return
        val pair = currentSpokenPair(current) ?: return
        val segmentId = current.segments.getOrNull(current.currentIndex)?.id
        read(pair, resumeAfter, segmentId)
    }

    fun read(
        pair: SpokenPair,
        resumeAfter: Boolean,
        segmentId: Long? = null,
    ) {
        if (pair.primary.isBlank() && pair.secondary.isBlank()) return
        val ticket = ++generation
        controller.pause()
        readingSegmentId = segmentId
        progress = null
        pronouncer.readPair(
            pair = pair,
            times = times,
            onProgress = { if (ticket == generation) progress = it },
            onFinished = {
                if (ticket != generation) return@readPair
                progress = null
                readingSegmentId = null
                if (resumeAfter) controller.play()
            },
        )
    }
}

@Composable
internal fun ReadPairButton() {
    val reader = LocalPairedReading.current ?: return
    val progress = reader.progress
    val description =
        if (progress == null) {
            stringResource(R.string.study_play_mode_read_pair)
        } else {
            stringResource(
                if (progress.side == ReadSide.PRIMARY) {
                    R.string.study_read_pair_primary_progress
                } else {
                    R.string.study_read_pair_secondary_progress
                },
                progress.pass,
                progress.times,
            )
        }
    IconButton(onClick = { reader.readCurrent(resumeAfter = false) }, modifier = Modifier.testTag("read_pair")) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = description)
    }
}
