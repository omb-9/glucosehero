package com.omb9.glucosehero.data.local.db.sqlcipher

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.security.KeystoreManager
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaintextToSqlCipherMigrationTest {

    private lateinit var names: MutableList<String>

    @Before
    fun setUp() {
        SqlCipherNative.load()
        names = mutableListOf()
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in names) {
            context.deleteDatabase(name)
            File(context.getDatabasePath(name).parent, "$name.encrypting").deleteRecursively()
            File(context.getDatabasePath(name).parent, "$name.pre-encrypt.bak").delete()
        }
    }

    @Test
    fun copyThenEncrypt_preservesRowsAndRemovesPlaintextHeader() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = uniqueName("sqlcipher-roundtrip")
        val plaintext = Room.databaseBuilder(context, GlucoseHeroDatabase::class.java, name)
            .addMigrations(*GlucoseHeroDatabase.ALL_MIGRATIONS)
            .build()
        try {
            plaintext.entryDao().insert(EntryEntity(timestamp = 42L, glucoseMgdl = 101.0, note = "phi"))
        } finally {
            plaintext.close()
        }
        val dbFile = context.getDatabasePath(name)
        assertTrue(SqliteFileHeaders.isPlaintextSqlite(dbFile))

        val passphrase = randomKey()
        PlaintextToSqlCipherMigrator.migrateIfNeeded(context, name, passphrase)
        assertFalse(SqliteFileHeaders.isPlaintextSqlite(dbFile))
        assertFalse(File(dbFile.parent, "$name.pre-encrypt.bak").exists())

        val encrypted = Room.databaseBuilder(context, GlucoseHeroDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(SqlCipherKeys.rawKeySpec(passphrase)))
            .addMigrations(*GlucoseHeroDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val rows = encrypted.entryDao().getAll()
            assertEquals(1, rows.size)
            assertEquals(42L, rows[0].timestamp)
            assertEquals(101.0, rows[0].glucoseMgdl!!, 0.0)
            assertEquals("phi", rows[0].note)
        } finally {
            encrypted.close()
            passphrase.fill(0)
        }

        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).close()
            fail("plaintext SQLite API must not open the encrypted file")
        } catch (_: Exception) {
            // Expected: the file is no longer a readable plaintext SQLite database.
        }
    }

    @Test
    fun failClosed_leavesPlaintextWhenExportCannotComplete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = uniqueName("sqlcipher-fail-closed")
        val plaintext = Room.databaseBuilder(context, GlucoseHeroDatabase::class.java, name)
            .addMigrations(*GlucoseHeroDatabase.ALL_MIGRATIONS)
            .build()
        plaintext.close()
        val dbFile = context.getDatabasePath(name)
        assertTrue(SqliteFileHeaders.isPlaintextSqlite(dbFile))

        val blocker = File(dbFile.parent, "$name.encrypting")
        blocker.mkdirs()
        File(blocker, "not-a-database").writeText("blocked")

        val passphrase = randomKey()
        try {
            PlaintextToSqlCipherMigrator.migrateIfNeeded(context, name, passphrase)
            fail("export into a directory must fail")
        } catch (_: SqlCipherMigrationException) {
            assertTrue(SqliteFileHeaders.isPlaintextSqlite(dbFile))
        } finally {
            passphrase.fill(0)
            blocker.deleteRecursively()
        }
    }

    @Test
    fun passphraseStore_roundTripMatchesAndDoesNotCreateSecondKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SqlCipherPassphraseStore(context, KeystoreManager())
        val wrapped = store.wrappedFile()
        val existed = wrapped.exists()
        val first = store.getOrCreatePassphrase()
        val second = store.getOrCreatePassphrase()
        try {
            assertEquals(32, first.size)
            assertTrue(first.contentEquals(second))
            assertTrue(wrapped.exists())
        } finally {
            first.fill(0)
            second.fill(0)
            if (!existed) wrapped.delete()
        }
    }

    private fun uniqueName(prefix: String): String {
        val name = "$prefix-${System.nanoTime()}.db"
        names += name
        return name
    }

    private fun randomKey(): ByteArray =
        ByteArray(SqlCipherKeys.SIZE_BYTES).also { SecureRandom().nextBytes(it) }
}
