package duosub.study.ui

import duosub.study.data.SubtitleSegment
import duosub.study.data.SubtitleStore
import duosub.study.translation.GOOGLE_RECHECK_INTERVAL_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class CaptionPlaybackRequest(
    val timeMs: Long = 0,
    val paused: Boolean = true,
    val enabled: Boolean = false,
    val seekGeneration: Long = 0,
    /** Raised by "Retry translation". After a failure, translation waits for this to change. */
    val translationAttempt: Long = 0,
)

/**
 * A short reason for the translation bar; translator errors can be empty or very technical, so
 * those show [fallback] (the localized "Translation is unavailable right now…" text) instead.
 */
internal fun translationFailureMessage(
    error: Exception,
    fallback: String,
): String =
    error.message?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TRANSLATION_ERROR_LENGTH }
        ?: fallback

private const val MAX_TRANSLATION_ERROR_LENGTH = 160

/** The stored captions are showing and only their translation stopped, so a retry need not reload them. */
internal fun onlyTranslationFailed(state: DuoSubUiState): Boolean =
    state.translationError != null && state.errorMessage == null && !state.liveFallback && state.segments.isNotEmpty()

/**
 * Better translations that arrive after rows already show one (Translate with AI: the AI's lines
 * replace Google's, owner request 2026-10-28). [version] changes whenever some arrive; [lookup]
 * returns the one for a text, or null.
 */
internal class TranslationUpgrades(
    val version: StateFlow<Long>,
    val lookup: (String) -> String?,
)

/**
 * [rows] with every row that [lookup] has a translation for showing it. A row already showing that
 * translation keeps it as it is.
 */
internal fun withUpgrades(
    rows: List<SubtitleSegment>,
    lookup: (String) -> String?,
): List<SubtitleSegment> {
    val seen = HashSet<String>()
    return rows.indices.fold(rows) { current, index ->
        val row = current[index]
        val text = row.originalText
        if (!seen.add(text)) return@fold current
        val translated = lookup(text)?.trim()?.takeIf { it.isNotEmpty() } ?: return@fold current
        if (alreadyShows(current, row, translated)) return@fold current
        withTranslation(current, index, translated)
    }
}

/** Whether the rows in [rows] that show the same words as [row] already display [translated]. */
private fun alreadyShows(
    rows: List<SubtitleSegment>,
    row: SubtitleSegment,
    translated: String,
): Boolean {
    val parts = rows.filter { it.originalText == row.originalText }
    if (parts.any { it.translatedText == null }) return false
    val shown = parts.first().translatedText.orEmpty().filterNot(Char::isWhitespace)
    return shown.isNotEmpty() && shown == translated.filterNot(Char::isWhitespace)
}

/**
 * Event driven: sleeps when the window is ready or the app is backgrounded. A paused video still
 * gets its window translated (the current row, then the next minute), so lines show before play.
 *
 * Original rows keep following playback when translation fails: [onTranslationFailure] reports the
 * error once, and no further row is translated until [CaptionPlaybackRequest.translationAttempt]
 * changes, so a failing model is not retried in a loop.
 *
 * [prefetch], when given, receives the texts of the next row and of the next minute's sentences first,
 * so an online engine can translate them in one request and [translate] then finds them cached.
 * [upgrades], when given, replace shown translations as they arrive, also while paused.
 */
internal suspend fun translatePlaybackWindow(
    store: SubtitleStore,
    requests: StateFlow<CaptionPlaybackRequest>,
    translate: suspend (String) -> String,
    onTranslationFailure: (Exception) -> Unit = {},
    prefetch: (suspend (List<String>) -> Unit)? = null,
    upgrades: TranslationUpgrades? = null,
    onWindow: (List<SubtitleSegment>, Boolean) -> Unit,
) {
    var indices = IntRange.EMPTY
    var rows = emptyList<SubtitleSegment>()
    var failedAttempt: Long? = null
    var upgradedAt: Long? = null
    while (true) {
        currentCoroutineContext().ensureActive()
        val request = requests.value
        if (!request.enabled) {
            requests.first { it != request }
            continue
        }
        val nextIndices = store.windowIndices(request.timeMs)
        if (nextIndices != indices) {
            val previous = rows.associateBy { it.id }
            rows =
                withContext(Dispatchers.IO) { store.read(nextIndices) }.map { row ->
                    row.copy(translatedText = previous[row.id]?.translatedText)
                }
            indices = nextIndices
            upgradedAt = null
        }
        // Reading disk can suspend across a seek. Never show the old position afterwards.
        if (requests.value != request) continue
        if (upgrades != null && upgrades.version.value != upgradedAt) {
            upgradedAt = upgrades.version.value
            rows = withUpgrades(rows, upgrades.lookup)
        }
        val next = nextWindowTranslation(rows, request).takeIf { failedAttempt != request.translationAttempt }
        onWindow(rows, next != null)
        if (next == null) {
            if (upgrades == null) {
                requests.first { it != request }
            } else {
                combine(requests, upgrades.version) { now, version -> now != request || version != upgradedAt }.first { it }
            }
            continue
        }
        rows =
            try {
                prefetch?.invoke(upcomingTranslationTexts(rows, next))
                translateRow(rows, next, translate)
            } catch (error: Exception) {
                // Cancelling this loop still ends it; a translator error only stops translating.
                currentCoroutineContext().ensureActive()
                failedAttempt = request.translationAttempt
                onTranslationFailure(error)
                continue
            }
        // An in-flight task may finish after a seek/pause. Cache it, but never publish at the wrong position.
        if (requests.value.enabled && requests.value.seekGeneration == request.seekGeneration) {
            onWindow(rows, nextWindowTranslation(rows, requests.value) != null)
        }
    }
}

/**
 * Translates row [index] with its own text. The row shows the translation of exactly the words it
 * shows: no whole-sentence cut, no proportional placement.
 */
private suspend fun translateRow(
    rows: List<SubtitleSegment>,
    index: Int,
    translate: suspend (String) -> String,
): List<SubtitleSegment> {
    val row = rows[index]
    val translated = translate(rowTranslationTexts(row).first()).trim()
    currentCoroutineContext().ensureActive()
    return withTranslation(rows, index, translated)
}

/**
 * What translating [row] sends: the row's own text. Each display row is translated on its own, so
 * the words shown under a row are always the translation of that row's own words (owner decision
 * 2026-11-05, per-row contract) — a row is never the cut of another row's translation.
 */
internal fun rowTranslationTexts(row: SubtitleSegment): List<String> = listOf(row.originalText)

/** How far past the row being translated an online engine translates in the same request. */
internal const val PREFETCH_AHEAD_MS = 60_000L

/**
 * The texts of row [index] and of every later untranslated sentence that starts within [aheadMs] of
 * it (in practice the next minute of the window), each sentence once.
 */
internal fun upcomingTranslationTexts(
    rows: List<SubtitleSegment>,
    index: Int,
    aheadMs: Long = PREFETCH_AHEAD_MS,
): List<String> {
    val horizon = rows[index].startMs + aheadMs
    val upcoming = (index..rows.lastIndex).map(rows::get).takeWhile { it.startMs <= horizon }
    return translationTexts(upcoming.filterIndexed { position, row -> position == 0 || row.translatedText == null })
}

/** Everything translating [rows] sends: each display row's own text once. */
internal fun translationTexts(rows: List<SubtitleSegment>): List<String> =
    rows.map { it.originalText }.distinct()

/** How far ahead the online engine translates in the background, past the [PREFETCH_AHEAD_MS] the window asks for. */
internal const val BACKGROUND_PREFETCH_AHEAD_MS = 5 * 60_000L

/** The wait before each background request, so filling minutes 2 to 5 never sends a burst. */
internal const val BACKGROUND_PREFETCH_PAUSE_MS = 3_000L

/** Once everything ahead is translated, the background waits for this much playback (or a seek) before looking again. */
internal const val BACKGROUND_PREFETCH_STEP_MS = 30_000L

/** While Google keeps refusing, the background pause doubles up to this (owner request 2026-10-30). */
internal const val MAX_BACKGROUND_PREFETCH_PAUSE_MS = 60_000L

/** A background step that keeps failing rests twice as long each time, up to this. */
internal const val MAX_BACKGROUND_REST_MS = 16 * 60_000L

/**
 * The pause before a background request: [baseMs] (3 s, or a remote switch's slower pace), doubled
 * for each request in a row Google refused somewhere, so a busy shared address is not pressed harder.
 */
internal fun backgroundPrefetchPauseMs(
    baseMs: Long,
    refusalStreak: Int,
): Long = (baseMs shl refusalStreak.coerceIn(0, 10)).coerceAtMost(maxOf(MAX_BACKGROUND_PREFETCH_PAUSE_MS, baseMs))

/** The rest after the [failures]th failed background step in a row: [restMs], then 2, 4 and 8 times it. */
internal fun backgroundRestMs(
    restMs: Long,
    failures: Int,
): Long = (restMs shl (failures - 1).coerceIn(0, 10)).coerceAtMost(maxOf(MAX_BACKGROUND_REST_MS, restMs))

/** Five minutes of speech is far fewer rows; this only bounds one read. */
private const val MAX_BACKGROUND_PREFETCH_ROWS = 400

/**
 * While the video plays, hands the sentences starting 1 to 5 minutes ahead to [fetch], one step at a
 * time with [pauseMs] before each. [fetch] sends one request for the texts not translated yet and
 * returns the texts it sent (none when all were translated); a text is handed over at most once, so a
 * line Google leaves blank is not asked again here. A failed step waits [restMs], doubling for each
 * failure in a row (see [backgroundRestMs]): the playing minute, not this prefetch, decides when
 * Google has stopped working. [pauseMs] is asked before each step, so it follows Google's refusals.
 */
internal suspend fun prefetchInBackground(
    store: SubtitleStore,
    requests: StateFlow<CaptionPlaybackRequest>,
    pauseMs: () -> Long = { BACKGROUND_PREFETCH_PAUSE_MS },
    restMs: Long = GOOGLE_RECHECK_INTERVAL_MS,
    fetch: suspend (List<String>) -> List<String>,
) {
    val sent = HashSet<String>()
    var failures = 0
    while (true) {
        delay(pauseMs())
        val request = requests.value
        if (!request.enabled || request.paused) {
            requests.first { it.enabled && !it.paused }
            continue
        }
        val fetched =
            try {
                val indices =
                    store.indicesBetween(
                        request.timeMs + PREFETCH_AHEAD_MS,
                        request.timeMs + BACKGROUND_PREFETCH_AHEAD_MS,
                        MAX_BACKGROUND_PREFETCH_ROWS,
                    )
                val rows = withContext(Dispatchers.IO) { store.read(indices) }
                fetch(translationTexts(rows).filterNot(sent::contains))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                delay(backgroundRestMs(restMs, ++failures))
                continue
            }
        failures = 0
        sent += fetched
        if (fetched.isEmpty()) {
            requests.first {
                it.seekGeneration != request.seekGeneration ||
                    it.timeMs !in request.timeMs until request.timeMs + BACKGROUND_PREFETCH_STEP_MS
            }
        }
    }
}

/**
 * Gives row [index] its own translation. Rows that show the same words (identical repeats of one
 * display row) share the same translation, so repeated lines stay in sync; a row's translation is
 * never a cut of another row's.
 */
internal fun withTranslation(
    rows: List<SubtitleSegment>,
    index: Int,
    translated: String,
): List<SubtitleSegment> {
    val row = rows[index]
    val text = row.originalText
    return rows.map { other ->
        if (other.originalText == text) other.copy(translatedText = translated) else other
    }
}

/**
 * The next row of the window to translate, also while paused (owner request 2026-10-27): the first
 * request carries the next minute, so a paused video gets that minute at once. Only the background
 * look-ahead of minutes 2 to 5 waits for playback.
 */
internal fun nextWindowTranslation(
    rows: List<SubtitleSegment>,
    request: CaptionPlaybackRequest,
): Int? {
    if (!request.enabled || rows.isEmpty()) return null
    val current = nearestSegmentIndex(rows, request.timeMs)
    val ahead = request.timeMs + duosub.study.data.SUBTITLE_LOOK_AHEAD_MS
    val behind = request.timeMs - duosub.study.data.SUBTITLE_LOOK_BEHIND_MS
    // Current cue first, then upcoming speech, then rewind history. Gaps do not pull in distant cues.
    return (current..rows.lastIndex).firstOrNull {
        rows[it].translatedText == null && rows[it].startMs <= ahead && rows[it].endMs > request.timeMs
    } ?: (current downTo 0).firstOrNull {
        rows[it].translatedText == null && rows[it].endMs > behind && rows[it].startMs <= request.timeMs
    }
}

/**
 * Rows [rows] dropped from the start of [previous] when the playback window slid forward, 0 for the
 * same window, or null when [rows] does not start inside [previous] (a seek, a reload, the first window).
 * Row ids are store indices, so equal ids are the same row in both windows.
 */
internal fun windowShift(
    previous: List<SubtitleSegment>,
    rows: List<SubtitleSegment>,
): Int? {
    val firstId = rows.firstOrNull()?.id ?: return null
    return previous.indexOfFirst { it.id == firstId }.takeIf { it >= 0 }
}
