package com.omb9.glucosehero.`data`.local.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.omb9.glucosehero.`data`.local.entity.SupplyEntity
import com.omb9.glucosehero.domain.model.SupplyType
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
public class SupplyDao_Impl(
  __db: RoomDatabase,
) : SupplyDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSupplyEntity: EntityInsertAdapter<SupplyEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSupplyEntity = object : EntityInsertAdapter<SupplyEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `supplies` (`id`,`type`,`started_at`,`expected_lifespan_days`,`replaced_at`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SupplyEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, __SupplyType_enumToString(entity.type))
        statement.bindLong(3, entity.startedAt)
        statement.bindLong(4, entity.expectedLifespanDays.toLong())
        val _tmpReplacedAt: Long? = entity.replacedAt
        if (_tmpReplacedAt == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpReplacedAt)
        }
      }
    }
  }

  public override suspend fun insert(entity: SupplyEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSupplyEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun deactivateAndInsert(
    type: String,
    replacedAt: Long,
    entity: SupplyEntity,
  ): Long = performInTransactionSuspending(__db) {
    super@SupplyDao_Impl.deactivateAndInsert(type, replacedAt, entity)
  }

  public override fun observeActive(): Flow<List<SupplyEntity>> {
    val _sql: String = """
        |
        |        SELECT * FROM supplies
        |        WHERE replaced_at IS NULL
        |        ORDER BY started_at ASC
        |        
        """.trimMargin()
    return createFlow(__db, false, arrayOf("supplies")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfStartedAt: Int = getColumnIndexOrThrow(_stmt, "started_at")
        val _columnIndexOfExpectedLifespanDays: Int = getColumnIndexOrThrow(_stmt, "expected_lifespan_days")
        val _columnIndexOfReplacedAt: Int = getColumnIndexOrThrow(_stmt, "replaced_at")
        val _result: MutableList<SupplyEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SupplyEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpType: SupplyType
          _tmpType = __SupplyType_stringToEnum(_stmt.getText(_columnIndexOfType))
          val _tmpStartedAt: Long
          _tmpStartedAt = _stmt.getLong(_columnIndexOfStartedAt)
          val _tmpExpectedLifespanDays: Int
          _tmpExpectedLifespanDays = _stmt.getLong(_columnIndexOfExpectedLifespanDays).toInt()
          val _tmpReplacedAt: Long?
          if (_stmt.isNull(_columnIndexOfReplacedAt)) {
            _tmpReplacedAt = null
          } else {
            _tmpReplacedAt = _stmt.getLong(_columnIndexOfReplacedAt)
          }
          _item = SupplyEntity(_tmpId,_tmpType,_tmpStartedAt,_tmpExpectedLifespanDays,_tmpReplacedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deactivateSuppliesOfType(type: String, replacedAt: Long) {
    val _sql: String = """
        |
        |        UPDATE supplies
        |        SET replaced_at = ?
        |        WHERE type = ? AND replaced_at IS NULL
        |        
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, replacedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, type)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __SupplyType_enumToString(_value: SupplyType): String = when (_value) {
    SupplyType.SENSOR -> "SENSOR"
    SupplyType.INSULIN_VIAL -> "INSULIN_VIAL"
    SupplyType.PUMP_SITE -> "PUMP_SITE"
  }

  private fun __SupplyType_stringToEnum(_value: String): SupplyType = when (_value) {
    "SENSOR" -> SupplyType.SENSOR
    "INSULIN_VIAL" -> SupplyType.INSULIN_VIAL
    "PUMP_SITE" -> SupplyType.PUMP_SITE
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
