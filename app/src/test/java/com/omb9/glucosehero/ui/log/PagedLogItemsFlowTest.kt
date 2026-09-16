package com.omb9.glucosehero.ui.log

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the ordering of `cachedIn` relative to the formatting `combine` in
 * [pagedLogItemsFlow].
 *
 * The crash being regression-tested is:
 *
 * ```
 * java.lang.IllegalStateException: Attempt to collect twice from pageEventFlow,
 *   which is an illegal operation. Did you forget to call
 *   Flow<PagingData<*>>.cachedIn(coroutineScope)?
 * ```
 *
 * It fired at startup because the formatting flow emitted more than once (the
 * seeded default, then the persisted value). The fix caches the raw paged flow
 * *before* combining, so those re-emissions re-map the cached page events
 * instead of collecting a live `PageFetcherSnapshot` twice.
 *
 * The data must be driven through a `PagingDataPresenter` (here
 * [AsyncPagingDataDiffer], the non-Compose equivalent of the `LazyPagingItems`
 * path that crashed in production). A collector that merely stores the emitted
 * `PagingData` objects does not collect the page events and does not reproduce
 * the failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedLogItemsFlowTest {

    @Test
    fun `re-emitting formatting does not collect the page flow twice`() = runTest {
        val formatting = MutableStateFlow(
            LogFormatting(
                unit = GlucoseUnit.MGDL,
                targetLowMgdl = 70f,
                targetHighMgdl = 180f,
                use24HourTime = false,
            ),
        )

        // cachedIn launches its source collection in the scope we hand it; capture
        // any failure there instead of letting it hit the default handler.
        val errors = mutableListOf<Throwable>()
        val pagingScope = CoroutineScope(
            UnconfinedTestDispatcher(testScheduler) +
                SupervisorJob() +
                CoroutineExceptionHandler { _, throwable -> errors += throwable },
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val differ = AsyncPagingDataDiffer(
            diffCallback = LOG_ITEM_DIFF,
            updateCallback = NoopUpdateCallback,
            mainDispatcher = dispatcher,
            workerDispatcher = dispatcher,
        )
        try {
            val flow = pagedLogItemsFlow(fakePagerFlow(), formatting, pagingScope)

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                flow.collect { differ.submitData(it) }
            }
            advanceUntilIdle()
            // 2 entries + 1 inserted day header (both entries share a day).
            assertEquals(3, differ.itemCount)

            // Second, distinct formatting emission — the real-world unit switch.
            // On the broken ordering (cachedIn *after* the combine) this collects
            // the same pageEventFlow a second time.
            formatting.value = LogFormatting(
                unit = GlucoseUnit.MMOL,
                targetLowMgdl = 70f,
                targetHighMgdl = 180f,
                use24HourTime = false,
            )
            advanceUntilIdle()

            job.cancel()
        } finally {
            pagingScope.cancel()
        }

        val doubleCollect = errors.filterIsInstance<IllegalStateException>()
            .any { it.message?.contains("collect twice", ignoreCase = true) == true }
        assertFalse(
            "pageEventFlow was collected twice — cachedIn must stay before the combine: $errors",
            doubleCollect,
        )
    }

    @Test
    fun `structurally equal formatting re-derives only once`() = runTest {
        val settings = MutableStateFlow(
            UserSettings(unit = GlucoseUnit.MMOL, use24HourTime = true),
        )
        val formatting = settings.toLogFormatting()

        val seen = mutableListOf<LogFormatting>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            formatting.collect { seen += it }
        }
        advanceUntilIdle()
        assertEquals(1, seen.size)

        // Unrelated writes (theme/accent — and, in production, the forecast JSON
        // refresh and HypoSOS pending state) must not re-derive formatting.
        settings.value = settings.value.copy(
            accent = AccentColor.OCEAN,
            themeMode = ThemeMode.AMOLED,
        )
        advanceUntilIdle()
        assertEquals(1, seen.size)

        // A formatting-relevant field change re-derives exactly once more.
        settings.value = settings.value.copy(unit = GlucoseUnit.MGDL)
        advanceUntilIdle()
        assertEquals(2, seen.size)
        assertEquals(GlucoseUnit.MGDL, seen.last().unit)

        job.cancel()
    }

    private fun fakePagerFlow(): Flow<PagingData<LogEvent>> =
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { FakeLogEventPagingSource() },
        ).flow

    private class FakeLogEventPagingSource : PagingSource<Int, LogEvent>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LogEvent> =
            LoadResult.Page(
                data = LIST_OF_EVENTS,
                prevKey = null,
                nextKey = null,
            )

        override fun getRefreshKey(state: PagingState<Int, LogEvent>): Int? = null
    }

    private object NoopUpdateCallback : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) = Unit
        override fun onRemoved(position: Int, count: Int) = Unit
        override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
        override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
    }

    private companion object {
        val LOG_ITEM_DIFF = object : DiffUtil.ItemCallback<LogListItem>() {
            override fun areItemsTheSame(oldItem: LogListItem, newItem: LogListItem): Boolean =
                when {
                    oldItem is LogListItem.Header && newItem is LogListItem.Header ->
                        oldItem.date == newItem.date
                    oldItem is LogListItem.Entry && newItem is LogListItem.Entry ->
                        oldItem.id == newItem.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: LogListItem, newItem: LogListItem): Boolean =
                oldItem == newItem
        }

        val LIST_OF_EVENTS = listOf(
            LogEvent(id = 1L, timestamp = 1_700_000_000_000L, glucoseMgdl = 110.0),
            LogEvent(id = 2L, timestamp = 1_700_000_000_000L - 60_000L, glucoseMgdl = 95.0),
        )
    }
}
