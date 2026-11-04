package com.kienhoang.dualsubreplay.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kienhoang.dualsubreplay.R
import com.kienhoang.dualsubreplay.data.LearningWordSelection
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

/** A pause between the two sides, so the pair does not blur into one sound. */
internal const val PAIR_READ_GAP_MS = 280L

/** How long a line waits for its translation before the pair is read with the original only. */
internal const val PAIR_READ_TRANSLATION_WAIT_MS = 2_000L

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
internal fun currentSpokenPair(state: DualSubUiState): SpokenPair? {
    val source = state.resolvedSourceLanguage ?: "en"
    if (state.liveFallback) {
        val primary = state.liveOriginal?.trim().orEmpty()
        if (primary.isEmpty()) return null
        return SpokenPair(primary, source, state.liveTranslated?.trim().orEmpty(), state.targetLanguage)
    }
    val line = state.segments.getOrNull(state.currentIndex)?.asStudyPair(state.resolvedSourceLanguage, state.targetLanguage) ?: return null
    val primary = line.originalText.trim()
    if (primary.isEmpty()) return null
    return SpokenPair(primary, DEFAULT_SOURCE_LANGUAGE, line.translatedText?.trim().orEmpty(), DEFAULT_TARGET_LANGUAGE)
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
    var state: DualSubUiState? = null
    var times: Int = DEFAULT_PAIR_READ_COUNT
    var progress by mutableStateOf<PairReadProgress?>(null)
        private set
    val clipPlaying: Boolean get() = controller.clipPlaying
    private var generation = 0

    fun readCurrent(resumeAfter: Boolean) {
        val pair = state?.let(::currentSpokenPair) ?: return
        read(pair, resumeAfter)
    }

    fun read(
        pair: SpokenPair,
        resumeAfter: Boolean,
    ) {
        if (pair.primary.isBlank() && pair.secondary.isBlank()) return
        val ticket = ++generation
        controller.pause()
        progress = null
        pronouncer.readPair(
            pair = pair,
            times = times,
            onProgress = { if (ticket == generation) progress = it },
            onFinished = {
                if (ticket != generation) return@readPair
                progress = null
                if (resumeAfter) controller.play()
            },
        )
    }
}

/** The play mode: each new line is read as the pair, then the video continues. */
@Composable
internal fun PairedLineAutoReader(
    host: PairedLineReaderHost,
    state: DualSubUiState,
    mode: StudyPlayMode,
) {
    val line = state.segments.getOrNull(state.currentIndex)
    val translated = line?.translatedText != null
    LaunchedEffect(state.activeVideoId, line?.id, mode, translated, state.translationError) {
        if (mode != StudyPlayMode.READ_PAIR || state.liveFallback || line == null || line.originalText.isBlank()) return@LaunchedEffect
        if (host.clipPlaying) return@LaunchedEffect
        if (!translated && state.translationError == null && state.translatedVisibility != CaptionVisibility.NEVER) {
            delay(PAIR_READ_TRANSLATION_WAIT_MS)
        }
        host.read(
            SpokenPair(
                line.originalText.trim(),
                state.resolvedSourceLanguage ?: "en",
                line.translatedText?.trim().orEmpty(),
                state.targetLanguage,
            ),
            resumeAfter = true,
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
