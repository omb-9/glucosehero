package com.omb9.glucosehero.`data`.local.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.omb9.glucosehero.`data`.local.entity.ChatMessageEntity
import com.omb9.glucosehero.domain.model.ChatRole
import javax.`annotation`.processing.Generated
import kotlin.IllegalArgumentException
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
public class ChatMessageDao_Impl(
  __db: RoomDatabase,
) : ChatMessageDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChatMessageEntity: EntityInsertAdapter<ChatMessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChatMessageEntity = object : EntityInsertAdapter<ChatMessageEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `chat_messages` (`id`,`role`,`content`,`timestamp`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChatMessageEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, __ChatRole_enumToString(entity.role))
        statement.bindText(3, entity.content)
        statement.bindLong(4, entity.timestamp)
      }
    }
  }

  public override suspend fun insert(message: ChatMessageEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfChatMessageEntity.insertAndReturnId(_connection, message)
    _result
  }

  public override fun observeAll(): Flow<List<ChatMessageEntity>> {
    val _sql: String = "SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC"
    return createFlow(__db, false, arrayOf("chat_messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpRole: ChatRole
          _tmpRole = __ChatRole_stringToEnum(_stmt.getText(_columnIndexOfRole))
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = ChatMessageEntity(_tmpId,_tmpRole,_tmpContent,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<ChatMessageEntity> {
    val _sql: String = "SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<ChatMessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChatMessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpRole: ChatRole
          _tmpRole = __ChatRole_stringToEnum(_stmt.getText(_columnIndexOfRole))
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = ChatMessageEntity(_tmpId,_tmpRole,_tmpContent,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM chat_messages"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __ChatRole_enumToString(_value: ChatRole): String = when (_value) {
    ChatRole.USER -> "USER"
    ChatRole.ASSISTANT -> "ASSISTANT"
    ChatRole.SYSTEM -> "SYSTEM"
  }

  private fun __ChatRole_stringToEnum(_value: String): ChatRole = when (_value) {
    "USER" -> ChatRole.USER
    "ASSISTANT" -> ChatRole.ASSISTANT
    "SYSTEM" -> ChatRole.SYSTEM
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
