package com.omb9.glucosehero.`data`.local.db

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.omb9.glucosehero.`data`.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucoseStats
import com.omb9.glucosehero.domain.model.MealContext
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class EntryDao_Impl(
  __db: RoomDatabase,
) : EntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfEntryEntity: EntityInsertAdapter<EntryEntity>

  private val __deleteAdapterOfEntryEntity: EntityDeleteOrUpdateAdapter<EntryEntity>

  private val __updateAdapterOfEntryEntity: EntityDeleteOrUpdateAdapter<EntryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfEntryEntity = object : EntityInsertAdapter<EntryEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `entries` (`id`,`timestamp`,`glucose_mgdl`,`meal_context`,`insulin_basal_units`,`insulin_bolus_units`,`carbs_grams`,`meal_description`,`exercise_minutes`,`exercise_intensity`,`note`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: EntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.timestamp)
        val _tmpGlucoseMgdl: Double? = entity.glucoseMgdl
        if (_tmpGlucoseMgdl == null) {
          statement.bindNull(3)
        } else {
          statement.bindDouble(3, _tmpGlucoseMgdl)
        }
        val _tmpMealContext: MealContext? = entity.mealContext
        if (_tmpMealContext == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, __MealContext_enumToString(_tmpMealContext))
        }
        val _tmpInsulinBasalUnits: Double? = entity.insulinBasalUnits
        if (_tmpInsulinBasalUnits == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpInsulinBasalUnits)
        }
        val _tmpInsulinBolusUnits: Double? = entity.insulinBolusUnits
        if (_tmpInsulinBolusUnits == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpInsulinBolusUnits)
        }
        val _tmpCarbsGrams: Int? = entity.carbsGrams
        if (_tmpCarbsGrams == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpCarbsGrams.toLong())
        }
        val _tmpMealDescription: String? = entity.mealDescription
        if (_tmpMealDescription == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpMealDescription)
        }
        val _tmpExerciseMinutes: Int? = entity.exerciseMinutes
        if (_tmpExerciseMinutes == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpExerciseMinutes.toLong())
        }
        val _tmpExerciseIntensity: ActivityIntensity? = entity.exerciseIntensity
        if (_tmpExerciseIntensity == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, __ActivityIntensity_enumToString(_tmpExerciseIntensity))
        }
        val _tmpNote: String? = entity.note
        if (_tmpNote == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpNote)
        }
      }
    }
    this.__deleteAdapterOfEntryEntity = object : EntityDeleteOrUpdateAdapter<EntryEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: EntryEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfEntryEntity = object : EntityDeleteOrUpdateAdapter<EntryEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `entries` SET `id` = ?,`timestamp` = ?,`glucose_mgdl` = ?,`meal_context` = ?,`insulin_basal_units` = ?,`insulin_bolus_units` = ?,`carbs_grams` = ?,`meal_description` = ?,`exercise_minutes` = ?,`exercise_intensity` = ?,`note` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: EntryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.timestamp)
        val _tmpGlucoseMgdl: Double? = entity.glucoseMgdl
        if (_tmpGlucoseMgdl == null) {
          statement.bindNull(3)
        } else {
          statement.bindDouble(3, _tmpGlucoseMgdl)
        }
        val _tmpMealContext: MealContext? = entity.mealContext
        if (_tmpMealContext == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, __MealContext_enumToString(_tmpMealContext))
        }
        val _tmpInsulinBasalUnits: Double? = entity.insulinBasalUnits
        if (_tmpInsulinBasalUnits == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpInsulinBasalUnits)
        }
        val _tmpInsulinBolusUnits: Double? = entity.insulinBolusUnits
        if (_tmpInsulinBolusUnits == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpInsulinBolusUnits)
        }
        val _tmpCarbsGrams: Int? = entity.carbsGrams
        if (_tmpCarbsGrams == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpCarbsGrams.toLong())
        }
        val _tmpMealDescription: String? = entity.mealDescription
        if (_tmpMealDescription == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpMealDescription)
        }
        val _tmpExerciseMinutes: Int? = entity.exerciseMinutes
        if (_tmpExerciseMinutes == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpExerciseMinutes.toLong())
        }
        val _tmpExerciseIntensity: ActivityIntensity? = entity.exerciseIntensity
        if (_tmpExerciseIntensity == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, __ActivityIntensity_enumToString(_tmpExerciseIntensity))
        }
        val _tmpNote: String? = entity.note
        if (_tmpNote == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpNote)
        }
        statement.bindLong(12, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: EntryEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfEntryEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun delete(entity: EntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfEntryEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: EntryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfEntryEntity.handle(_connection, entity)
  }

  public override fun observeEventsSince(since: Long): Flow<List<EntryEntity>> {
    val _sql: String = "SELECT * FROM entries WHERE timestamp >= ? ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfGlucoseMgdl: Int = getColumnIndexOrThrow(_stmt, "glucose_mgdl")
        val _columnIndexOfMealContext: Int = getColumnIndexOrThrow(_stmt, "meal_context")
        val _columnIndexOfInsulinBasalUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_basal_units")
        val _columnIndexOfInsulinBolusUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_bolus_units")
        val _columnIndexOfCarbsGrams: Int = getColumnIndexOrThrow(_stmt, "carbs_grams")
        val _columnIndexOfMealDescription: Int = getColumnIndexOrThrow(_stmt, "meal_description")
        val _columnIndexOfExerciseMinutes: Int = getColumnIndexOrThrow(_stmt, "exercise_minutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exercise_intensity")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _result: MutableList<EntryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: EntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpGlucoseMgdl: Double?
          if (_stmt.isNull(_columnIndexOfGlucoseMgdl)) {
            _tmpGlucoseMgdl = null
          } else {
            _tmpGlucoseMgdl = _stmt.getDouble(_columnIndexOfGlucoseMgdl)
          }
          val _tmpMealContext: MealContext?
          if (_stmt.isNull(_columnIndexOfMealContext)) {
            _tmpMealContext = null
          } else {
            _tmpMealContext = __MealContext_stringToEnum(_stmt.getText(_columnIndexOfMealContext))
          }
          val _tmpInsulinBasalUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBasalUnits)) {
            _tmpInsulinBasalUnits = null
          } else {
            _tmpInsulinBasalUnits = _stmt.getDouble(_columnIndexOfInsulinBasalUnits)
          }
          val _tmpInsulinBolusUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBolusUnits)) {
            _tmpInsulinBolusUnits = null
          } else {
            _tmpInsulinBolusUnits = _stmt.getDouble(_columnIndexOfInsulinBolusUnits)
          }
          val _tmpCarbsGrams: Int?
          if (_stmt.isNull(_columnIndexOfCarbsGrams)) {
            _tmpCarbsGrams = null
          } else {
            _tmpCarbsGrams = _stmt.getLong(_columnIndexOfCarbsGrams).toInt()
          }
          val _tmpMealDescription: String?
          if (_stmt.isNull(_columnIndexOfMealDescription)) {
            _tmpMealDescription = null
          } else {
            _tmpMealDescription = _stmt.getText(_columnIndexOfMealDescription)
          }
          val _tmpExerciseMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseMinutes)) {
            _tmpExerciseMinutes = null
          } else {
            _tmpExerciseMinutes = _stmt.getLong(_columnIndexOfExerciseMinutes).toInt()
          }
          val _tmpExerciseIntensity: ActivityIntensity?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = __ActivityIntensity_stringToEnum(_stmt.getText(_columnIndexOfExerciseIntensity))
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          _item = EntryEntity(_tmpId,_tmpTimestamp,_tmpGlucoseMgdl,_tmpMealContext,_tmpInsulinBasalUnits,_tmpInsulinBolusUnits,_tmpCarbsGrams,_tmpMealDescription,_tmpExerciseMinutes,_tmpExerciseIntensity,_tmpNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeGlucoseEventsSince(since: Long): Flow<List<EntryEntity>> {
    val _sql: String = """
        |
        |        SELECT * FROM entries
        |        WHERE glucose_mgdl IS NOT NULL AND timestamp >= ?
        |        ORDER BY timestamp ASC
        |        
        """.trimMargin()
    return createFlow(__db, false, arrayOf("entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfGlucoseMgdl: Int = getColumnIndexOrThrow(_stmt, "glucose_mgdl")
        val _columnIndexOfMealContext: Int = getColumnIndexOrThrow(_stmt, "meal_context")
        val _columnIndexOfInsulinBasalUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_basal_units")
        val _columnIndexOfInsulinBolusUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_bolus_units")
        val _columnIndexOfCarbsGrams: Int = getColumnIndexOrThrow(_stmt, "carbs_grams")
        val _columnIndexOfMealDescription: Int = getColumnIndexOrThrow(_stmt, "meal_description")
        val _columnIndexOfExerciseMinutes: Int = getColumnIndexOrThrow(_stmt, "exercise_minutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exercise_intensity")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _result: MutableList<EntryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: EntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpGlucoseMgdl: Double?
          if (_stmt.isNull(_columnIndexOfGlucoseMgdl)) {
            _tmpGlucoseMgdl = null
          } else {
            _tmpGlucoseMgdl = _stmt.getDouble(_columnIndexOfGlucoseMgdl)
          }
          val _tmpMealContext: MealContext?
          if (_stmt.isNull(_columnIndexOfMealContext)) {
            _tmpMealContext = null
          } else {
            _tmpMealContext = __MealContext_stringToEnum(_stmt.getText(_columnIndexOfMealContext))
          }
          val _tmpInsulinBasalUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBasalUnits)) {
            _tmpInsulinBasalUnits = null
          } else {
            _tmpInsulinBasalUnits = _stmt.getDouble(_columnIndexOfInsulinBasalUnits)
          }
          val _tmpInsulinBolusUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBolusUnits)) {
            _tmpInsulinBolusUnits = null
          } else {
            _tmpInsulinBolusUnits = _stmt.getDouble(_columnIndexOfInsulinBolusUnits)
          }
          val _tmpCarbsGrams: Int?
          if (_stmt.isNull(_columnIndexOfCarbsGrams)) {
            _tmpCarbsGrams = null
          } else {
            _tmpCarbsGrams = _stmt.getLong(_columnIndexOfCarbsGrams).toInt()
          }
          val _tmpMealDescription: String?
          if (_stmt.isNull(_columnIndexOfMealDescription)) {
            _tmpMealDescription = null
          } else {
            _tmpMealDescription = _stmt.getText(_columnIndexOfMealDescription)
          }
          val _tmpExerciseMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseMinutes)) {
            _tmpExerciseMinutes = null
          } else {
            _tmpExerciseMinutes = _stmt.getLong(_columnIndexOfExerciseMinutes).toInt()
          }
          val _tmpExerciseIntensity: ActivityIntensity?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = __ActivityIntensity_stringToEnum(_stmt.getText(_columnIndexOfExerciseIntensity))
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          _item = EntryEntity(_tmpId,_tmpTimestamp,_tmpGlucoseMgdl,_tmpMealContext,_tmpInsulinBasalUnits,_tmpInsulinBolusUnits,_tmpCarbsGrams,_tmpMealDescription,_tmpExerciseMinutes,_tmpExerciseIntensity,_tmpNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeById(id: Long): Flow<EntryEntity?> {
    val _sql: String = "SELECT * FROM entries WHERE id = ?"
    return createFlow(__db, false, arrayOf("entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfGlucoseMgdl: Int = getColumnIndexOrThrow(_stmt, "glucose_mgdl")
        val _columnIndexOfMealContext: Int = getColumnIndexOrThrow(_stmt, "meal_context")
        val _columnIndexOfInsulinBasalUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_basal_units")
        val _columnIndexOfInsulinBolusUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_bolus_units")
        val _columnIndexOfCarbsGrams: Int = getColumnIndexOrThrow(_stmt, "carbs_grams")
        val _columnIndexOfMealDescription: Int = getColumnIndexOrThrow(_stmt, "meal_description")
        val _columnIndexOfExerciseMinutes: Int = getColumnIndexOrThrow(_stmt, "exercise_minutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exercise_intensity")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _result: EntryEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpGlucoseMgdl: Double?
          if (_stmt.isNull(_columnIndexOfGlucoseMgdl)) {
            _tmpGlucoseMgdl = null
          } else {
            _tmpGlucoseMgdl = _stmt.getDouble(_columnIndexOfGlucoseMgdl)
          }
          val _tmpMealContext: MealContext?
          if (_stmt.isNull(_columnIndexOfMealContext)) {
            _tmpMealContext = null
          } else {
            _tmpMealContext = __MealContext_stringToEnum(_stmt.getText(_columnIndexOfMealContext))
          }
          val _tmpInsulinBasalUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBasalUnits)) {
            _tmpInsulinBasalUnits = null
          } else {
            _tmpInsulinBasalUnits = _stmt.getDouble(_columnIndexOfInsulinBasalUnits)
          }
          val _tmpInsulinBolusUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBolusUnits)) {
            _tmpInsulinBolusUnits = null
          } else {
            _tmpInsulinBolusUnits = _stmt.getDouble(_columnIndexOfInsulinBolusUnits)
          }
          val _tmpCarbsGrams: Int?
          if (_stmt.isNull(_columnIndexOfCarbsGrams)) {
            _tmpCarbsGrams = null
          } else {
            _tmpCarbsGrams = _stmt.getLong(_columnIndexOfCarbsGrams).toInt()
          }
          val _tmpMealDescription: String?
          if (_stmt.isNull(_columnIndexOfMealDescription)) {
            _tmpMealDescription = null
          } else {
            _tmpMealDescription = _stmt.getText(_columnIndexOfMealDescription)
          }
          val _tmpExerciseMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseMinutes)) {
            _tmpExerciseMinutes = null
          } else {
            _tmpExerciseMinutes = _stmt.getLong(_columnIndexOfExerciseMinutes).toInt()
          }
          val _tmpExerciseIntensity: ActivityIntensity?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = __ActivityIntensity_stringToEnum(_stmt.getText(_columnIndexOfExerciseIntensity))
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          _result = EntryEntity(_tmpId,_tmpTimestamp,_tmpGlucoseMgdl,_tmpMealContext,_tmpInsulinBasalUnits,_tmpInsulinBolusUnits,_tmpCarbsGrams,_tmpMealDescription,_tmpExerciseMinutes,_tmpExerciseIntensity,_tmpNote)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun averageGlucoseSince(since: Long): Double? {
    val _sql: String = """
        |
        |        SELECT AVG(glucose_mgdl) FROM entries
        |        WHERE glucose_mgdl IS NOT NULL AND timestamp >= ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _result: Double?
        if (_stmt.step()) {
          val _tmp: Double?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getDouble(0)
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeGlucoseStatsSince(since: Long): Flow<GlucoseStats> {
    val _sql: String = """
        |
        |        SELECT AVG(glucose_mgdl) AS avgMgdl,
        |               COUNT(*) AS readingCount,
        |               COUNT(DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime'))
        |                   AS loggedDays
        |        FROM entries
        |        WHERE glucose_mgdl IS NOT NULL AND timestamp >= ?
        |        
        """.trimMargin()
    return createFlow(__db, false, arrayOf("entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _columnIndexOfAvgMgdl: Int = 0
        val _columnIndexOfReadingCount: Int = 1
        val _columnIndexOfLoggedDays: Int = 2
        val _result: GlucoseStats
        if (_stmt.step()) {
          val _tmpAvgMgdl: Double?
          if (_stmt.isNull(_columnIndexOfAvgMgdl)) {
            _tmpAvgMgdl = null
          } else {
            _tmpAvgMgdl = _stmt.getDouble(_columnIndexOfAvgMgdl)
          }
          val _tmpReadingCount: Int
          _tmpReadingCount = _stmt.getLong(_columnIndexOfReadingCount).toInt()
          val _tmpLoggedDays: Int
          _tmpLoggedDays = _stmt.getLong(_columnIndexOfLoggedDays).toInt()
          _result = GlucoseStats(_tmpAvgMgdl,_tmpReadingCount,_tmpLoggedDays)
        } else {
          error("The query result was empty, but expected a single row to return a NON-NULL object of type 'com.omb9.glucosehero.domain.model.GlucoseStats'.")
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun timeInRangeSince(
    since: Long,
    low: Double,
    high: Double,
  ): Double? {
    val _sql: String = """
        |
        |        SELECT CAST(SUM(CASE WHEN glucose_mgdl BETWEEN ? AND ? THEN 1 ELSE 0 END) AS REAL)
        |               / COUNT(*)
        |        FROM entries
        |        WHERE glucose_mgdl IS NOT NULL AND timestamp >= ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, low)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, high)
        _argIndex = 3
        _stmt.bindLong(_argIndex, since)
        val _result: Double?
        if (_stmt.step()) {
          val _tmp: Double?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getDouble(0)
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun dailySummaries(since: Long, limit: Int): List<DailyGlucoseSummary> {
    val _sql: String = """
        |
        |        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
        |               AVG(glucose_mgdl) AS avgMgdl,
        |               MIN(glucose_mgdl) AS minMgdl,
        |               MAX(glucose_mgdl) AS maxMgdl,
        |               COUNT(*) AS readings
        |        FROM entries
        |        WHERE glucose_mgdl IS NOT NULL AND timestamp >= ?
        |        GROUP BY day
        |        ORDER BY day DESC
        |        LIMIT ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfDay: Int = 0
        val _columnIndexOfAvgMgdl: Int = 1
        val _columnIndexOfMinMgdl: Int = 2
        val _columnIndexOfMaxMgdl: Int = 3
        val _columnIndexOfReadings: Int = 4
        val _result: MutableList<DailyGlucoseSummary> = mutableListOf()
        while (_stmt.step()) {
          val _item: DailyGlucoseSummary
          val _tmpDay: String
          _tmpDay = _stmt.getText(_columnIndexOfDay)
          val _tmpAvgMgdl: Double
          _tmpAvgMgdl = _stmt.getDouble(_columnIndexOfAvgMgdl)
          val _tmpMinMgdl: Double
          _tmpMinMgdl = _stmt.getDouble(_columnIndexOfMinMgdl)
          val _tmpMaxMgdl: Double
          _tmpMaxMgdl = _stmt.getDouble(_columnIndexOfMaxMgdl)
          val _tmpReadings: Int
          _tmpReadings = _stmt.getLong(_columnIndexOfReadings).toInt()
          _item = DailyGlucoseSummary(_tmpDay,_tmpAvgMgdl,_tmpMinMgdl,_tmpMaxMgdl,_tmpReadings)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun recentEntries(limit: Int): List<EntryEntity> {
    val _sql: String = "SELECT * FROM entries ORDER BY timestamp DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfGlucoseMgdl: Int = getColumnIndexOrThrow(_stmt, "glucose_mgdl")
        val _columnIndexOfMealContext: Int = getColumnIndexOrThrow(_stmt, "meal_context")
        val _columnIndexOfInsulinBasalUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_basal_units")
        val _columnIndexOfInsulinBolusUnits: Int = getColumnIndexOrThrow(_stmt, "insulin_bolus_units")
        val _columnIndexOfCarbsGrams: Int = getColumnIndexOrThrow(_stmt, "carbs_grams")
        val _columnIndexOfMealDescription: Int = getColumnIndexOrThrow(_stmt, "meal_description")
        val _columnIndexOfExerciseMinutes: Int = getColumnIndexOrThrow(_stmt, "exercise_minutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exercise_intensity")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _result: MutableList<EntryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: EntryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpGlucoseMgdl: Double?
          if (_stmt.isNull(_columnIndexOfGlucoseMgdl)) {
            _tmpGlucoseMgdl = null
          } else {
            _tmpGlucoseMgdl = _stmt.getDouble(_columnIndexOfGlucoseMgdl)
          }
          val _tmpMealContext: MealContext?
          if (_stmt.isNull(_columnIndexOfMealContext)) {
            _tmpMealContext = null
          } else {
            _tmpMealContext = __MealContext_stringToEnum(_stmt.getText(_columnIndexOfMealContext))
          }
          val _tmpInsulinBasalUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBasalUnits)) {
            _tmpInsulinBasalUnits = null
          } else {
            _tmpInsulinBasalUnits = _stmt.getDouble(_columnIndexOfInsulinBasalUnits)
          }
          val _tmpInsulinBolusUnits: Double?
          if (_stmt.isNull(_columnIndexOfInsulinBolusUnits)) {
            _tmpInsulinBolusUnits = null
          } else {
            _tmpInsulinBolusUnits = _stmt.getDouble(_columnIndexOfInsulinBolusUnits)
          }
          val _tmpCarbsGrams: Int?
          if (_stmt.isNull(_columnIndexOfCarbsGrams)) {
            _tmpCarbsGrams = null
          } else {
            _tmpCarbsGrams = _stmt.getLong(_columnIndexOfCarbsGrams).toInt()
          }
          val _tmpMealDescription: String?
          if (_stmt.isNull(_columnIndexOfMealDescription)) {
            _tmpMealDescription = null
          } else {
            _tmpMealDescription = _stmt.getText(_columnIndexOfMealDescription)
          }
          val _tmpExerciseMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseMinutes)) {
            _tmpExerciseMinutes = null
          } else {
            _tmpExerciseMinutes = _stmt.getLong(_columnIndexOfExerciseMinutes).toInt()
          }
          val _tmpExerciseIntensity: ActivityIntensity?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = __ActivityIntensity_stringToEnum(_stmt.getText(_columnIndexOfExerciseIntensity))
          }
          val _tmpNote: String?
          if (_stmt.isNull(_columnIndexOfNote)) {
            _tmpNote = null
          } else {
            _tmpNote = _stmt.getText(_columnIndexOfNote)
          }
          _item = EntryEntity(_tmpId,_tmpTimestamp,_tmpGlucoseMgdl,_tmpMealContext,_tmpInsulinBasalUnits,_tmpInsulinBolusUnits,_tmpCarbsGrams,_tmpMealDescription,_tmpExerciseMinutes,_tmpExerciseIntensity,_tmpNote)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM entries WHERE id = ?"
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

  private fun __MealContext_enumToString(_value: MealContext): String = when (_value) {
    MealContext.NONE -> "NONE"
    MealContext.FASTING -> "FASTING"
    MealContext.BEFORE_MEAL -> "BEFORE_MEAL"
    MealContext.AFTER_MEAL -> "AFTER_MEAL"
    MealContext.BEDTIME -> "BEDTIME"
  }

  private fun __ActivityIntensity_enumToString(_value: ActivityIntensity): String = when (_value) {
    ActivityIntensity.LIGHT -> "LIGHT"
    ActivityIntensity.MODERATE -> "MODERATE"
    ActivityIntensity.INTENSE -> "INTENSE"
  }

  private fun __MealContext_stringToEnum(_value: String): MealContext = when (_value) {
    "NONE" -> MealContext.NONE
    "FASTING" -> MealContext.FASTING
    "BEFORE_MEAL" -> MealContext.BEFORE_MEAL
    "AFTER_MEAL" -> MealContext.AFTER_MEAL
    "BEDTIME" -> MealContext.BEDTIME
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  private fun __ActivityIntensity_stringToEnum(_value: String): ActivityIntensity = when (_value) {
    "LIGHT" -> ActivityIntensity.LIGHT
    "MODERATE" -> ActivityIntensity.MODERATE
    "INTENSE" -> ActivityIntensity.INTENSE
    else -> throw IllegalArgumentException("Can't convert value to enum, unknown value: " + _value)
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
