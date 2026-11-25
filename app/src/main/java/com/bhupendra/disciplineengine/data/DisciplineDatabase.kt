package com.bhupendra.disciplineengine.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "habits_table")
data class HabitEntity(
    @PrimaryKey val habitId: String,
    val title: String,
    val tier: String,                // AUTO_VERIFIED_GOLD | MANUAL_HONOR
    val verificationSource: String,  // CAMERA_PUSHUP | FOCUS_TIMER | ZERO_REELS_BLOCKER | PW_LECTURE_SYNC | MANUAL_CHECK
    val rpReward: Int,
    val isKeystone: Boolean,
    @ColumnInfo(defaultValue = "1") val targetValue: Int = 1,
    @ColumnInfo(defaultValue = "0") val currentValue: Int = 0,
    @ColumnInfo(defaultValue = "0") val isCompletedToday: Boolean = false,
    @ColumnInfo(defaultValue = "1.10") val progressiveOverloadRate: Float = 1.10f,
    @ColumnInfo(defaultValue = "NULL") val lastCompletedEpochDay: Long? = null
)

@Entity(tableName = "rp_events")
data class RpEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val timestamp: Long,
    val source: String,      // PUSHUP_ALARM | P1_BLOCK | PW_BLOCK | ZERO_TEMPTATION | PENALTY | BOSS_PENALTY
    val rpDelta: Int         // +25, +40, +30, +15, -35, -50 per blueprint
)

@Entity(tableName = "streak_state")
data class StreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val isCracked: Boolean = false,          // S2 Cracked Emblem (24h grace)
    val crackedExpireAt: Long = 0L,
    val totalRp: Long = 0L,
    val lastMissDate: Long? = null,
    @ColumnInfo(defaultValue = "'ACTIVE_FLAME'") val streakStatus: String = "ACTIVE_FLAME",
    @ColumnInfo(defaultValue = "40") val penaltyRepsRequired: Int = 40,
    @ColumnInfo(defaultValue = "0") val earnedEmergencyShields: Int = 0,
    @ColumnInfo(defaultValue = "2") val monthlyDarkRoomBypassTokensLeft: Int = 2,
    @ColumnInfo(defaultValue = "0") val lastTokenResetYearMonth: Int = 0,
    @ColumnInfo(defaultValue = "0") val reelsTimeBankMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val dopamineDebtPushups: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val illnessModeActiveEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "'Rookie Aspirant'") val equippedTitle: String = "Rookie Aspirant",
    @ColumnInfo(defaultValue = "20.0") val statSTR: Float = 20f,
    @ColumnInfo(defaultValue = "20.0") val statINT: Float = 20f,
    @ColumnInfo(defaultValue = "20.0") val statWIL: Float = 20f,
    @ColumnInfo(defaultValue = "20.0") val statSPD: Float = 20f,
    @ColumnInfo(defaultValue = "20.0") val statCON: Float = 20f,
    @ColumnInfo(defaultValue = "NULL") val lastEvaluatedEpochDay: Long? = null
)

@Entity(tableName = "daily_analytics_log")
data class DailyAnalyticsEntity(
    @PrimaryKey val dateEpochDay: Long,
    val scheduleCompletionPercent: Float = 0f,
    val habitsCompletedPercent: Float = 0f,
    val wakeUpAlarmCompletedOnTime: Boolean = false,
    val wakeUpSpeedSeconds: Int = 0,
    val totalPushupReps: Int = 0,
    val totalSquatReps: Int = 0,
    val blockedReelsAttemptsByHourJson: String = "[]",
    val deepStudyMinutesByHourJson: String = "[]"
)

@Dao
interface DisciplineDao {
    // Returns all logged RP events from newest to oldest
    @Query("SELECT * FROM rp_events ORDER BY timestamp DESC")
    suspend fun getAllRpEvents(): List<RpEventEntity>

    // Returns the latest RP events limited by count
    @Query("SELECT * FROM rp_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRpEvents(limit: Int): List<RpEventEntity>

    // Inserts a new RP reward or penalty event
    @Insert
    suspend fun addRpEvent(event: RpEventEntity)

    // Sums all RP deltas to calculate current total RP
    @Query("SELECT COALESCE(SUM(rpDelta), 0) FROM rp_events")
    suspend fun totalRp(): Long

    // Loads the single-row streak and rank state
    @Query("SELECT * FROM streak_state WHERE id = 1")
    suspend fun getStreak(): StreakEntity?

    // Saves or updates the single-row streak state
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: StreakEntity)

    // Adds or updates a habit definition
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHabit(habit: HabitEntity)

    // Inserts multiple habits without overwriting existing progress
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitsIfMissing(habits: List<HabitEntity>)

    // Lists all configured habits
    @Query("SELECT * FROM habits_table")
    suspend fun getAllHabits(): List<HabitEntity>

    // Saves or updates daily analytics summary for a given day
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyAnalytics(log: DailyAnalyticsEntity)

    // Loads daily analytics for a specific epoch day (used for Ghost Rival)
    @Query("SELECT * FROM daily_analytics_log WHERE dateEpochDay = :epochDay")
    suspend fun getAnalyticsForDay(epochDay: Long): DailyAnalyticsEntity?

    // Loads recent daily analytics logs ordered newest first
    @Query("SELECT * FROM daily_analytics_log ORDER BY dateEpochDay DESC LIMIT :limit")
    suspend fun getRecentAnalytics(limit: Int): List<DailyAnalyticsEntity>
}

@Database(
    entities = [
        HabitEntity::class,
        RpEventEntity::class,
        StreakEntity::class,
        DailyAnalyticsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class DisciplineDatabase : RoomDatabase() {
    abstract fun dao(): DisciplineDao

    companion object {
        @Volatile
        private var INSTANCE: DisciplineDatabase? = null

        // Upgrades Room database schema from Phase 1 (v1) to Phase 2 (v2)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits_table ADD COLUMN targetValue INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE habits_table ADD COLUMN currentValue INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits_table ADD COLUMN isCompletedToday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits_table ADD COLUMN progressiveOverloadRate REAL NOT NULL DEFAULT 1.10")
                db.execSQL("ALTER TABLE habits_table ADD COLUMN lastCompletedEpochDay INTEGER DEFAULT NULL")

                db.execSQL("ALTER TABLE streak_state ADD COLUMN streakStatus TEXT NOT NULL DEFAULT 'ACTIVE_FLAME'")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN penaltyRepsRequired INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN earnedEmergencyShields INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN monthlyDarkRoomBypassTokensLeft INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN lastTokenResetYearMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN reelsTimeBankMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN dopamineDebtPushups INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN illnessModeActiveEpochDay INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN equippedTitle TEXT NOT NULL DEFAULT 'Rookie Aspirant'")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN statSTR REAL NOT NULL DEFAULT 20.0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN statINT REAL NOT NULL DEFAULT 20.0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN statWIL REAL NOT NULL DEFAULT 20.0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN statSPD REAL NOT NULL DEFAULT 20.0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN statCON REAL NOT NULL DEFAULT 20.0")
                db.execSQL("ALTER TABLE streak_state ADD COLUMN lastEvaluatedEpochDay INTEGER DEFAULT NULL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_analytics_log (
                        dateEpochDay INTEGER NOT NULL PRIMARY KEY,
                        scheduleCompletionPercent REAL NOT NULL,
                        habitsCompletedPercent REAL NOT NULL,
                        wakeUpAlarmCompletedOnTime INTEGER NOT NULL,
                        wakeUpSpeedSeconds INTEGER NOT NULL,
                        totalPushupReps INTEGER NOT NULL,
                        totalSquatReps INTEGER NOT NULL,
                        blockedReelsAttemptsByHourJson TEXT NOT NULL,
                        deepStudyMinutesByHourJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Returns the singleton Room database instance with registered migrations
        fun get(context: Context): DisciplineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DisciplineDatabase::class.java,
                    "discipline_engine.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
