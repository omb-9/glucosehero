package com.omb9.glucosehero.`data`.local.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.omb9.glucosehero.`data`.local.entity.PendingAiQueryEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PendingAiQueryDao_Impl(
  __db: RoomDatabase,
) : PendingAiQueryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPendingAiQueryEntity: EntityInsertAdapter<PendingAiQueryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPendingAiQueryEntity = object : EntityInsertAdapter<PendingAiQueryEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `pending_ai_queries` (`id`,`user_message_id`,`prompt`,`created_at`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PendingAiQueryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.userMessageId)
        statement.bindText(3, entity.prompt)
        statement.bindLong(4, entity.createdAt)
      }
    }
  }

  public override suspend fun insert(query: PendingAiQueryEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfPendingAiQueryEntity.insertAndReturnId(_connection, query)
    _result
  }

  public override suspend fun getAll(): List<PendingAiQueryEntity> {
    val _sql: String = "SELECT * FROM pending_ai_queries ORDER BY created_at ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserMessageId: Int = getColumnIndexOrThrow(_stmt, "user_message_id")
        val _columnIndexOfPrompt: Int = getColumnIndexOrThrow(_stmt, "prompt")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _result: MutableList<PendingAiQueryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PendingAiQueryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUserMessageId: Long
          _tmpUserMessageId = _stmt.getLong(_columnIndexOfUserMessageId)
          val _tmpPrompt: String
          _tmpPrompt = _stmt.getText(_columnIndexOfPrompt)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = PendingAiQueryEntity(_tmpId,_tmpUserMessageId,_tmpPrompt,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM pending_ai_queries"
    return createFlow(__db, false, arrayOf("pending_ai_queries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM pending_ai_queries WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
