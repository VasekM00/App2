package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ActionStateDao
import com.example.data.ActionStateEntity
import com.example.data.AppDatabase
import com.example.data.LedgerDao
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsDao
import com.example.data.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ConcurrentRoomWriteTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsDao: SettingsDao
    private lateinit var ledgerDao: LedgerDao
    private lateinit var actionStateDao: ActionStateDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDao = db.settingsDao()
        ledgerDao = db.ledgerDao()
        actionStateDao = db.actionStateDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    /**
     * Test: Runs heavy concurrent database writes across multiple coroutines
     * to ensure no SQLiteConstraintException, race conditions, or database corruption occur.
     */
    @Test
    fun testConcurrentDatabaseWritesNoSQLiteConstraintException() = runBlocking {
        val concurrencyLevel = 60
        val successfulWrites = AtomicInteger(0)
        val constraintExceptions = AtomicInteger(0)
        val otherExceptions = AtomicInteger(0)

        // Launch concurrent asynchronous database writes across IO dispatcher
        val deferredResults = (1..concurrencyLevel).map { index ->
            async(Dispatchers.IO) {
                try {
                    // 1. Concurrent Settings Update (Upsert id = 1)
                    settingsDao.saveSettings(
                        SettingsEntity(
                            id = 1,
                            baseYear = 2026 + (index % 5),
                            primaryName = "ConcurrentWorker_$index",
                            vSalary = 35000.0 + (index * 100)
                        )
                    )

                    // 2. Concurrent Individual Ledger Entry Insertions
                    ledgerDao.insertEntry(
                        LedgerEntryEntity(
                            yearMonth = "2026-%02d".format((index % 12) + 1),
                            incVaclav = 35000.0 + index,
                            incEleonora = 13000.0,
                            expRent = 21770.0,
                            expGroceries = 4800.0 + index,
                            notes = "Concurrent entry $index"
                        )
                    )

                    // 3. Concurrent Bulk Ledger Entry Insertions
                    val batchEntries = listOf(
                        LedgerEntryEntity(
                            yearMonth = "2027-%02d".format((index % 12) + 1),
                            incVaclav = 38000.0,
                            expRent = 22000.0,
                            notes = "Batch A worker $index"
                        ),
                        LedgerEntryEntity(
                            yearMonth = "2028-%02d".format((index % 12) + 1),
                            incVaclav = 40000.0,
                            expRent = 23000.0,
                            notes = "Batch B worker $index"
                        )
                    )
                    ledgerDao.insertEntries(batchEntries)

                    // 4. Concurrent Action State Upserts
                    actionStateDao.saveActionState(
                        ActionStateEntity(
                            actionKey = "action_key_${index % 10}",
                            year = 2026 + (index % 3),
                            actionId = "action_${index % 10}",
                            isDone = (index % 2 == 0)
                        )
                    )

                    successfulWrites.incrementAndGet()
                } catch (e: android.database.sqlite.SQLiteConstraintException) {
                    constraintExceptions.incrementAndGet()
                } catch (e: Exception) {
                    otherExceptions.incrementAndGet()
                }
            }
        }

        deferredResults.awaitAll()

        // Assert zero SQLiteConstraintExceptions and zero unhandled exceptions
        assertEquals("Expected 0 SQLiteConstraintExceptions during concurrent writes", 0, constraintExceptions.get())
        assertEquals("Expected 0 unexpected exceptions during concurrent writes", 0, otherExceptions.get())
        assertEquals("All concurrent write operations must succeed", concurrencyLevel, successfulWrites.get())

        // Verify database data integrity
        val settings = settingsDao.getSettingsDirect()
        assertNotNull("Settings entity should be present in database", settings)
        assertEquals(1, settings?.id)

        val ledgerList = ledgerDao.getAllEntries().first()
        // Each of the 60 workers inserted 1 single entry + 2 batch entries = 180 total entries
        assertEquals("Expected 180 ledger entries in total", concurrencyLevel * 3, ledgerList.size)

        val actionStates = actionStateDao.getAllActionStates().first()
        // Unique keys modulo 10 -> 10 distinct action keys
        assertEquals("Expected 10 unique action state entries", 10, actionStates.size)
    }
}
