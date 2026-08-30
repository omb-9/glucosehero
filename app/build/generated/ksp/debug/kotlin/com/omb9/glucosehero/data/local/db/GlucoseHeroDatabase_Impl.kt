package com.omb9.glucosehero.`data`.local.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class GlucoseHeroDatabase_Impl : GlucoseHeroDatabase() {
  private val _entryDao: Lazy<EntryDao> = lazy {
    EntryDao_Impl(this)
  }

  private val _chatMessageDao: Lazy<ChatMessageDao> = lazy {
    ChatMessageDao_Impl(this)
  }

  private val _pendingAiQueryDao: Lazy<PendingAiQueryDao> = lazy {
    PendingAiQueryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "491fcbe1a1570aaee358bd5615ed3972", "8a702ea7e76a5d687a892b266758e6cc") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `glucose_mgdl` REAL, `meal_context` TEXT, `insulin_basal_units` REAL, `insulin_bolus_units` REAL, `carbs_grams` INTEGER, `meal_description` TEXT, `exercise_minutes` INTEGER, `exercise_intensity` TEXT, `note` TEXT)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_timestamp` ON `entries` (`timestamp`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_glucose_mgdl` ON `entries` (`glucose_mgdl`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `pending_ai_queries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_message_id` INTEGER NOT NULL, `prompt` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '491fcbe1a1570aaee358bd5615ed3972')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `entries`")
        connection.execSQL("DROP TABLE IF EXISTS `chat_messages`")
        connection.execSQL("DROP TABLE IF EXISTS `pending_ai_queries`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsEntries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("glucose_mgdl", TableInfo.Column("glucose_mgdl", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("meal_context", TableInfo.Column("meal_context", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("insulin_basal_units", TableInfo.Column("insulin_basal_units", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("insulin_bolus_units", TableInfo.Column("insulin_bolus_units", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("carbs_grams", TableInfo.Column("carbs_grams", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("meal_description", TableInfo.Column("meal_description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("exercise_minutes", TableInfo.Column("exercise_minutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("exercise_intensity", TableInfo.Column("exercise_intensity", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsEntries.put("note", TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesEntries.add(TableInfo.Index("index_entries_timestamp", false, listOf("timestamp"), listOf("ASC")))
        _indicesEntries.add(TableInfo.Index("index_entries_glucose_mgdl", false, listOf("glucose_mgdl"), listOf("ASC")))
        val _infoEntries: TableInfo = TableInfo("entries", _columnsEntries, _foreignKeysEntries, _indicesEntries)
        val _existingEntries: TableInfo = read(connection, "entries")
        if (!_infoEntries.equals(_existingEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |entries(com.omb9.glucosehero.data.local.entity.EntryEntity).
              | Expected:
              |""".trimMargin() + _infoEntries + """
              |
              | Found:
              |""".trimMargin() + _existingEntries)
        }
        val _columnsChatMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChatMessages.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("role", TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChatMessages.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChatMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChatMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesChatMessages.add(TableInfo.Index("index_chat_messages_timestamp", false, listOf("timestamp"), listOf("ASC")))
        val _infoChatMessages: TableInfo = TableInfo("chat_messages", _columnsChatMessages, _foreignKeysChatMessages, _indicesChatMessages)
        val _existingChatMessages: TableInfo = read(connection, "chat_messages")
        if (!_infoChatMessages.equals(_existingChatMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chat_messages(com.omb9.glucosehero.data.local.entity.ChatMessageEntity).
              | Expected:
              |""".trimMargin() + _infoChatMessages + """
              |
              | Found:
              |""".trimMargin() + _existingChatMessages)
        }
        val _columnsPendingAiQueries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPendingAiQueries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingAiQueries.put("user_message_id", TableInfo.Column("user_message_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingAiQueries.put("prompt", TableInfo.Column("prompt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPendingAiQueries.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPendingAiQueries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPendingAiQueries: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPendingAiQueries: TableInfo = TableInfo("pending_ai_queries", _columnsPendingAiQueries, _foreignKeysPendingAiQueries, _indicesPendingAiQueries)
        val _existingPendingAiQueries: TableInfo = read(connection, "pending_ai_queries")
        if (!_infoPendingAiQueries.equals(_existingPendingAiQueries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |pending_ai_queries(com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity).
              | Expected:
              |""".trimMargin() + _infoPendingAiQueries + """
              |
              | Found:
              |""".trimMargin() + _existingPendingAiQueries)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "entries", "chat_messages", "pending_ai_queries")
  }

  public override fun clearAllTables() {
    super.performClear(false, "entries", "chat_messages", "pending_ai_queries")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(EntryDao::class, EntryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChatMessageDao::class, ChatMessageDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PendingAiQueryDao::class, PendingAiQueryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun entryDao(): EntryDao = _entryDao.value

  public override fun chatMessageDao(): ChatMessageDao = _chatMessageDao.value

  public override fun pendingAiQueryDao(): PendingAiQueryDao = _pendingAiQueryDao.value
}
