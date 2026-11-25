package com.bhupendra.disciplineengine.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "habits_table")
data class HabitEntity(
    @PrimaryKey val habitId: String,
    val title: String,
    val tier: String,                // AUTO_VERIFIED_GOLD | MANUAL_HONOR
    val verificationSource: String,  // CAMERA_PUSHUP | FOCUS_TIMER | ZERO_REEL_SESSION | MANUAL
    val rpReward: Int,
    val isKeystone: Boolean
)

@Entity(tableName = "rp_events")
data class RpEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val timestamp: Long,
    val source: String,      // PUSHUP_ALARM | P1_BLOCK | PW_BLOCK | ZERO_TEMPTATION | PENALTY
    val rpDelta: Int         // +25, +40, +30, +15, -35 per blueprint
)

@Entity(tableName = "streak_state")
data class StreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int,
    val bestStreak: Int,
    val isCracked: Boolean,          // S2 Cracked Emblem (24h grace)
    val crackedExpireAt: Long,
    val totalRp: Long,
    val lastMissDate: Long?
)

@Dao
interface DisciplineDao {
    // Returns all logged RP events from newest to oldest
    @Query("SELECT * FROM rp_events ORDER BY timestamp DESC")
    suspend fun getAllRpEvents(): List<RpEventEntity>

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

    // Adds a new habit definition
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHabit(habit: HabitEntity)

    // Lists all configured habits
    @Query("SELECT * FROM habits_table")
    suspend fun getAllHabits(): List<HabitEntity>
}

@Database(
    entities = [HabitEntity::class, RpEventEntity::class, StreakEntity::class],
    version = 1,
    exportSchema = true
)
abstract class DisciplineDatabase : RoomDatabase() {
    abstract fun dao(): DisciplineDao

    companion object {
        @Volatile
        private var INSTANCE: DisciplineDatabase? = null

        // Returns the singleton Room database instance
        fun get(context: Context): DisciplineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DisciplineDatabase::class.java,
                    "discipline_engine.db"
                ).build().also { INSTANCE = it }
            }
    }
}
