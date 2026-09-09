package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.ExportWhitelist
import com.omb9.glucosehero.util.AppJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the backup envelope codec and validation. The Room-backed
 * round trip (seed → export → wipe → import) lives in the androidTest source
 * set where a real SQLite database is available.
 */
class BackupRoundTripTest {

    @Test
    fun streamingRoundTrip_preservesEveryTableIncludingNullsAndEnums() = runBlocking {
        val foods = listOf(
            BackupFood(
                id = 1L,
                uuid = "food-uuid-1",
                name = "Oats",
                brand = null,
                barcode = "500011",
                carbsGrams = 27.0,
                proteinGrams = 5.0,
                fatGrams = null,
                kcal = null,
                servingGrams = 40.0,
                servingLabel = "cup",
                source = FoodSource.OPEN_FOOD_FACTS,
                offFetchedAt = null,
                userCorrected = false,
                useCount = 3,
                lastUsedAt = 1_000L,
                isFavorite = true,
                createdAt = 900L,
            )
        )
        val entries = listOf(
            BackupEntry(
                id = 10L,
                timestamp = 1_000L,
                glucoseMgdl = 120.0,
                mealContext = MealContext.AFTER_MEAL,
                insulinBasalUnits = null,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch, \"big\"",
                exerciseMinutes = null,
                exerciseIntensity = null,
                note = "walk #fitness",
                source = EntrySource.HERO_AI,
                hcRecordId = null,
                foodId = 1L,
                uuid = "entry-uuid-1",
                moodScore = 4,
                moodLabel = "steady",
            ),
            BackupEntry(
                id = 11L,
                timestamp = 2_000L,
                glucoseMgdl = null,
                mealContext = null,
                insulinBasalUnits = 0.8,
                insulinBolusUnits = null,
                carbsGrams = null,
                proteinGrams = null,
                fatGrams = null,
                mealDescription = null,
                exerciseMinutes = 30,
                exerciseIntensity = ActivityIntensity.MODERATE,
                note = null,
                source = EntrySource.MANUAL,
                hcRecordId = null,
                foodId = null,
                uuid = "entry-uuid-2",
            ),
        )
        val supplies = listOf(
            BackupSupply(
                id = 20L,
                type = SupplyType.SENSOR,
                startedAt = 1_000L,
                expectedLifespanDays = 10,
                replacedAt = null,
                uuid = "supply-uuid-1",
            )
        )
        val glucoseSamples = listOf(
            BackupGlucoseSample(
                id = 30L,
                timestamp = 1_000L,
                glucoseMgdl = 96.0,
                hcRecordId = "hc-1",
                sourcePackage = null,
                recordingMethod = 1,
                importedAt = 2_000L,
            )
        )
        val chat = listOf(
            BackupChatMessage(
                id = 40L,
                role = ChatRole.ASSISTANT,
                content = "Hello",
                timestamp = 1_000L,
            )
        )
        val pending = listOf(
            BackupPendingQuery(
                id = 50L,
                userMessageId = 40L,
                prompt = "What was my average?",
                createdAt = 2_000L,
            )
        )
        val insights = listOf(
            BackupInsight(
                id = 60L,
                title = "Late-night lows",
                description = "Lows cluster around 3 AM.",
                severityLevel = 2,
                createdAt = 3_000L,
            )
        )

        val counts = BackupCounts(
            entries = entries.size,
            glucoseSamples = glucoseSamples.size,
            foods = foods.size,
            supplies = supplies.size,
            insights = insights.size,
            chat = chat.size,
            pendingAiQueries = pending.size,
        )
        val profile = BackupProfile(
            profileTarget = ProfileTarget.SELF,
            name = "Ada",
            age = 41,
            diabetesType = "Type 1",
            heightCm = 165f,
            weightKg = 62f,
        )
        val settings = BackupSettings(
            themeMode = ThemeMode.AMOLED,
            accent = AccentColor.OCEAN,
            unit = GlucoseUnit.MMOL,
            use24HourTime = true,
            targetLowMgdl = 72f,
            targetHighMgdl = 160f,
            isHeroAiEnabled = false,
            showAdvancedMacros = true,
            postMealRemindersEnabled = false,
            aiProvider = AiProvider.OPENAI,
            aiBaseUrl = "https://example.invalid/v1/",
            aiModel = "model-x",
            diaHours = 5f,
            cirRatio = 8f,
            isfMgdl = 40f,
            targetGlucoseMgdl = 110f,
            barcodeLookupEnabled = false,
            healthConnectSyncEnabled = true,
            glucoseImportEnabled = true,
            nutritionImportEnabled = true,
            exerciseImportEnabled = false,
            healthConnectInitialImportRange = InitialImportRange.DAYS_90,
        )

        val output = ByteArrayOutputStream()
        streamBackupEnvelope(
            output = output,
            profile = profile,
            settings = settings,
            counts = counts,
            appVersion = "1.0.0",
            exportedAt = 1_234_567L,
            pageSize = 1,
            foods = { offset, limit -> page(foods, offset, limit) },
            entries = { offset, limit -> page(entries, offset, limit) },
            supplies = { offset, limit -> page(supplies, offset, limit) },
            glucoseSamples = { offset, limit -> page(glucoseSamples, offset, limit) },
            chat = { offset, limit -> page(chat, offset, limit) },
            pendingAiQueries = { offset, limit -> page(pending, offset, limit) },
            insights = { offset, limit -> page(insights, offset, limit) },
        )

        val decoded = decodeEnvelope(ByteArrayInputStream(output.toByteArray()))
        verifyCounts(decoded)

        val json = output.toString(Charsets.UTF_8)
        assertTrue(json.indexOf("\"counts\"") < json.indexOf("\"entries\""))
        assertTrue(json.indexOf("\"counts\"") < json.indexOf("\"glucoseSamples\""))
        assertEquals(DATABASE_VERSION, decoded.databaseVersion)
        assertEquals(BACKUP_FORMAT_VERSION, decoded.formatVersion)

        assertEquals(counts, decoded.counts)
        assertEquals(profile, decoded.profile)
        assertEquals(settings, decoded.settings)
        assertEquals(foods, decoded.foods)
        assertEquals(entries, decoded.entries)
        assertEquals(supplies, decoded.supplies)
        assertEquals(glucoseSamples, decoded.glucoseSamples)
        assertEquals(chat, decoded.chat)
        assertEquals(pending, decoded.pendingAiQueries)
        assertEquals(insights, decoded.insights)
    }

    @Test
    fun versionRefusal_rejectsNewerFormat() {
        assertThrows(BackupFormatException::class.java) {
            requireSupportedBackupFormat(BACKUP_FORMAT_VERSION + 1)
        }
    }

    @Test
    fun versionRefusal_rejectsUnknownFormat() {
        assertThrows(BackupFormatException::class.java) {
            requireSupportedBackup("not.glucosehero", BACKUP_FORMAT_VERSION)
        }
    }

    @Test
    fun formatVersion_isIndependentOfDatabaseVersion() {
        assertEquals(2, BACKUP_FORMAT_VERSION)
        assertEquals(13, DATABASE_VERSION)
        assertTrue(BACKUP_FORMAT_VERSION != DATABASE_VERSION)
    }

    @Test
    fun versionRefusal_doesNotThrowForOwnOrOlderFormat() {
        requireSupportedBackupFormat(BACKUP_FORMAT_VERSION)
        requireSupportedBackupFormat(BACKUP_FORMAT_VERSION - 1)
        requireSupportedBackup(BACKUP_FORMAT, BACKUP_FORMAT_VERSION)
        requireSupportedBackup(BACKUP_FORMAT, 1)
    }

    @Test
    fun truncation_failsDecodeAndLeavesNoPartialEnvelope() {
        val envelope = BackupEnvelope(
            counts = BackupCounts(entries = 2),
            entries = listOf(
                BackupEntry(id = 1L, timestamp = 1L, glucoseMgdl = 100.0, uuid = "a"),
                BackupEntry(id = 2L, timestamp = 2L, glucoseMgdl = 110.0, uuid = "b"),
            ),
        )
        val bytes = ByteArrayOutputStream().also { encodeEnvelope(envelope, it) }.toByteArray()
        val cut = bytes.copyOf(bytes.size / 2)

        assertThrows(Exception::class.java) {
            decodeEnvelope(ByteArrayInputStream(cut))
        }
    }

    @Test
    fun countMismatch_failsVerification() {
        val envelope = BackupEnvelope(
            counts = BackupCounts(entries = 5),
            entries = listOf(
                BackupEntry(id = 1L, timestamp = 1L, glucoseMgdl = 100.0, uuid = "a"),
            ),
        )

        assertThrows(BackupFormatException::class.java) {
            verifyCounts(envelope)
        }
    }

    @Test
    fun countMismatch_betweenStreamedAndHeader_failsVerification() {
        val expected = BackupCounts(entries = 3, glucoseSamples = 50_000)
        val actual = BackupCounts(entries = 3, glucoseSamples = 49_999)

        assertThrows(BackupFormatException::class.java) {
            verifyCounts(expected, actual)
        }

        // Equal counts are not an error.
        verifyCounts(expected, expected)
    }

    @Test
    fun exportContainsNoKeyMaterial() {
        val settings = BackupSettings()
        val envelope = BackupEnvelope(settings = settings, entries = emptyList())
        val json = ByteArrayOutputStream().also { encodeEnvelope(envelope, it) }
            .toString(Charsets.UTF_8)

        assertFalse(json.contains("ai_api_key_enc"))
        assertFalse(json.contains("glucosehero_api_key"))
        // A representative base64-shaped blob should never appear in the model.
        assertFalse(json.contains("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="))
        assertFalse(encodeSettingsForBackup(settings).contains("ai_api_key_enc"))
        assertFalse(encodeSettingsForBackup(settings).contains("apiKey"))
        for (key in listOf(
            "oauthToken",
            "accessToken",
            "driveAccessTokenEnc",
            "webdavPasswordEnc",
            "keystoreAlias",
            "androidId",
            "webhookUrl",
        )) {
            assertFalse("exported JSON contained $key", json.contains("\"$key\""))
        }
        assertFalse(ExportWhitelist.containsForbiddenKey(json))
    }

    @Test
    fun restore_rejectsForbiddenTokenKeys() {
        val json = """
            {
              "format": "glucosehero.backup",
              "formatVersion": 2,
              "appVersion": "1.0.0",
              "databaseVersion": 13,
              "exportedAt": 1,
              "counts": {},
              "profile": {},
              "settings": {"themeMode": "LIGHT", "apiKey": "secret-token"},
              "entries": [],
              "glucoseSamples": [],
              "foods": [],
              "supplies": [],
              "insights": [],
              "chat": [],
              "pendingAiQueries": []
            }
        """.trimIndent()
        assertThrows(BackupFormatException::class.java) {
            decodeEnvelope(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun restore_rejectsUnknownEntryFieldToPreventRoomInjection() {
        val json = """
            {
              "format": "glucosehero.backup",
              "formatVersion": 2,
              "appVersion": "1.0.0",
              "databaseVersion": 13,
              "exportedAt": 1,
              "counts": {"entries": 1},
              "profile": {},
              "settings": {},
              "entries": [{
                "id": 1,
                "timestamp": 1,
                "uuid": "a",
                "injectedSql": "DROP TABLE entries"
              }],
              "glucoseSamples": [],
              "foods": [],
              "supplies": [],
              "insights": [],
              "chat": [],
              "pendingAiQueries": []
            }
        """.trimIndent()
        assertThrows(BackupFormatException::class.java) {
            decodeEnvelope(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun restore_rejectsUnknownTopLevelField() {
        val json = """
            {
              "format": "glucosehero.backup",
              "formatVersion": 2,
              "appVersion": "1.0.0",
              "databaseVersion": 13,
              "exportedAt": 1,
              "counts": {},
              "profile": {},
              "settings": {},
              "oauthToken": "nope",
              "entries": [],
              "glucoseSamples": [],
              "foods": [],
              "supplies": [],
              "insights": [],
              "chat": [],
              "pendingAiQueries": []
            }
        """.trimIndent()
        assertThrows(BackupFormatException::class.java) {
            decodeEnvelope(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun encodeSettingsForBackup_dropsInjectedApiKeyFields() {
        val raw = """
            {
              "themeMode": "LIGHT",
              "ai_api_key_enc": "SECRET_BLOB",
              "apiKey": "also-secret",
              "encryptedApiKey": "nope"
            }
        """.trimIndent()
        val stripped = stripNonExportableSettings(AppJson.parseToJsonElement(raw).jsonObject)
        val json = AppJson.encodeToString(JsonObject.serializer(), stripped)

        assertFalse(json.contains("SECRET_BLOB"))
        assertFalse(json.contains("also-secret"))
        assertFalse(json.contains("nope"))
        assertFalse(json.contains("ai_api_key_enc"))
        assertFalse(json.contains("apiKey"))
        assertFalse(json.contains("encryptedApiKey"))
        assertTrue(json.contains("themeMode"))
    }

    @Test
    fun decodedEnvelope_hasAllExpectedArrayKeys() {
        val envelope = BackupEnvelope(counts = BackupCounts(entries = 1), entries = listOf(
            BackupEntry(id = 1L, timestamp = 1L, glucoseMgdl = 100.0, uuid = "a"),
        ))
        val json = ByteArrayOutputStream().also { encodeEnvelope(envelope, it) }
            .toString(Charsets.UTF_8)

        assertTrue(json.contains("\"entries\""))
        assertTrue(json.contains("\"glucoseSamples\""))
        assertTrue(json.contains("\"foods\""))
        assertTrue(json.contains("\"supplies\""))
        assertTrue(json.contains("\"insights\""))
        assertTrue(json.contains("\"chat\""))
        assertTrue(json.contains("\"pendingAiQueries\""))
    }

    @Test
    fun headerFields_areWrittenBeforeArrays() = runBlocking {
        val output = ByteArrayOutputStream()
        streamBackupEnvelope(
            output = output,
            profile = BackupProfile(),
            settings = BackupSettings(),
            counts = BackupCounts(entries = 1),
            appVersion = "1.0.0",
            exportedAt = 1_234_567L,
            earliestEntry = 1_000L,
            latestEntry = 2_000L,
            pageSize = 1,
            foods = { _, _ -> emptyList() },
            entries = { offset, limit ->
                page(
                    listOf(BackupEntry(id = 1L, timestamp = 1_000L, glucoseMgdl = 100.0, uuid = "a")),
                    offset,
                    limit,
                )
            },
            supplies = { _, _ -> emptyList() },
            glucoseSamples = { _, _ -> emptyList() },
            chat = { _, _ -> emptyList() },
            pendingAiQueries = { _, _ -> emptyList() },
            insights = { _, _ -> emptyList() },
        )
        val json = output.toString(Charsets.UTF_8)
        assertTrue(json.indexOf("\"counts\"") < json.indexOf("\"foods\":["))
        assertTrue(json.indexOf("\"earliestEntry\"") < json.indexOf("\"foods\":["))
        assertTrue(json.indexOf("\"latestEntry\"") < json.indexOf("\"entries\":["))
        assertTrue(json.indexOf("\"earliestEntry\"") < json.indexOf("\"glucoseSamples\":["))
    }

    @Test
    fun suggestedBackupFileName_usesIsoDateAndJsonSuffix() {
        val name = suggestedBackupFileName(java.time.LocalDate.of(2026, 9, 8))
        assertEquals("glucosehero-backup-2026-09-08.json", name)
    }

    @Test
    fun suggestedEncryptedBackupFileName_usesIsoDateAndGhzkSuffix() {
        val name = suggestedEncryptedBackupFileName(java.time.LocalDate.of(2026, 9, 8))
        assertEquals("glucosehero-backup-2026-09-08.ghzk", name)
    }

    @Test
    fun prunePreImportSnapshots_keepsOnlyTwoNewest() {
        val dir = kotlin.io.path.createTempDirectory("gh-snapshots").toFile()
        try {
            val older = File(dir, "pre-import-1.json").apply {
                writeText("{}")
                setLastModified(1_000L)
            }
            val middle = File(dir, "pre-import-2.json").apply {
                writeText("{}")
                setLastModified(2_000L)
            }
            val newest = File(dir, "pre-import-3.json").apply {
                writeText("{}")
                setLastModified(3_000L)
            }
            File(dir, "notes.md").writeText("leave me")

            prunePreImportSnapshots(dir, keep = 2)

            assertFalse(older.exists())
            assertTrue(middle.exists())
            assertTrue(newest.exists())
            assertTrue(File(dir, "notes.md").exists())
            assertEquals(2, dir.listFiles { _, name -> name.startsWith("pre-import-") }!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun <T> page(list: List<T>, offset: Int, limit: Int): List<T> =
        if (offset >= list.size) emptyList() else list.subList(offset, minOf(offset + limit, list.size))
}
