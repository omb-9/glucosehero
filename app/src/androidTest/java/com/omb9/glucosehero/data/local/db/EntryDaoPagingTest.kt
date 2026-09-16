package com.omb9.glucosehero.data.local.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.entity.EntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Room-paged log reads stay bounded. With 20,000 rows the rolling
 * log must load only the configured page size on refresh, and an insert must
 * invalidate the source without streaming the whole table back into Kotlin.
 */
@RunWith(AndroidJUnit4::class)
class EntryDaoPagingTest {

    private lateinit var db: GlucoseHeroDatabase
    private lateinit var dao: EntryDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, GlucoseHeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.entryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pagingSource_loadsBoundedFirstPageInsteadOfWholeTable() = runBlocking {
        seed(20_000)

        val page = dao.pagingSource().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page<Int, EntryEntity>

        assertEquals(50, page.data.size)
        assertEquals(20_000L, page.data.first().timestamp)
        assertTrue(page.nextKey != null)
    }

    @Test
    fun insertInvalidatesWithoutReloadingFullTable() = runBlocking {
        seed(20_000)
        val source = dao.pagingSource()
        source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        )

        dao.insert(EntryEntity(timestamp = System.currentTimeMillis()))
        source.invalidate()

        val refreshed = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page<Int, EntryEntity>

        assertEquals(50, refreshed.data.size)
        assertEquals(20_001L, dao.countAll().toLong())
    }

    private suspend fun seed(count: Int) {
        dao.insertAll((1..count).map { EntryEntity(timestamp = it.toLong()) })
    }
}
