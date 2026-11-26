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

@Entity(tableName = "daily_schedule_blocks")
data class ScheduleBlockEntity(
    @PrimaryKey val blockId: String,
    val dateEpochDay: Long,
    val title: String,
    val blockType: String,      // FIXED_ANCHOR | FLEX_TASK | SHOCK_ABSORBER_BUFFER_8A | TRANSITION_BREAK
    val priorityTier: String,   // ANCHOR_IMMUTABLE | P1_CORE_NON_NEGOTIABLE | P2_IMPORTANT_COMPRESSIBLE | P3_BONUS_ROLLOVER
    val energyTag: String,      // PEAK_DEEP_WORK | STEADY_WORK | LOW_DRAIN_ADMIN | PHYSICAL_TRAINING
    val startTimeMinutesFromMidnight: Int,
    val durationMinutes: Int,
    val status: String,         // PENDING | ACTIVE | COMPLETED | MISSED | SALVAGED_15M_SPRINT | ROLLED_OVER
    val linkedPWChapterId: String? = null
)

@Entity(tableName = "schedule_profile")
data class ScheduleProfileEntity(
    @PrimaryKey val id: Int = 1,
    val wakeUpMinutes: Int = 330,           // Q1: 5:30 AM
    val sleepMinutes: Int = 1380,           // Q1: 11:00 PM
    val coachingStartMinutes: Int = 480,    // Q2: 8:00 AM
    val coachingEndMinutes: Int = 810,      // Q2: 1:30 PM
    val sportsStartMinutes: Int = 1080,     // Q3: 6:00 PM
    val sportsEndMinutes: Int = 1140,       // Q3: 7:00 PM
    val lunchMinutes: Int = 825,            // Q4: 1:45 PM
    val dinnerMinutes: Int = 1260,          // Q4: 9:00 PM
    val peakFocusStartMinutes: Int = 360,   // Q5: 6:00 AM
    val afternoonDipStartMinutes: Int = 870,// Q5: 2:30 PM
    val p1TargetMinutes: Int = 120,         // Q6: P1 Deep Study
    val p2TargetMinutes: Int = 90,          // Q6: P2 PW / Practice
    val p3TargetMinutes: Int = 45,          // Q6: P3 Bonus Revision
    val dangerStartMinutes: Int = 1290,     // Q7: 9:30 PM
    val dangerEndMinutes: Int = 1380        // Q7: 11:00 PM
)

@Entity(tableName = "pw_syllabus_and_backlog")
data class PwSyllabusEntity(
    @PrimaryKey val chapterId: String,
    val subjectName: String,
    val chapterName: String,
    val totalBatchLectures: Int,
    val completedLectures: Int,
    val avgLectureDurationMinutes: Int = 90,
    val oneShotDurationMinutes: Int = 120,
    val notesAndPyqDurationMinutes: Int = 75,
    val difficultyWeightageTier: String, // TIER1_HARD_FULL_BATCH | TIER2_MEDIUM_ONESHOT | TIER3_EASY_NOTES_PYQ
    val dppCompleted: Boolean = false,
    val isIncludedInUpcomingExam: Boolean = true
)

@Entity(tableName = "pw_tests_and_notices")
data class PwNoticeTestEntity(
    @PrimaryKey val itemId: String,
    val itemType: String,               // LIVE_CLASS_ALERT | DPP_UPLOAD | TEST_SCHEDULED | BATCH_NOTICE
    val title: String,
    val rawNotificationText: String,
    val timestampEpochMillis: Long,
    val requiredChapterIdsJson: String = "",
    val isTestUnlocked: Boolean = false,
    val isTestCompleted: Boolean = false,
    val scheduledSundaySlotDate: Long? = null
)

@Entity(tableName = "pw_batch_clock_slots")
data class PwBatchSlotEntity(
    @PrimaryKey val slotId: String,
    val subjectName: String,
    val linkedChapterId: String,
    val dayOfWeekValue: Int,            // 1 = Monday .. 7 = Sunday
    val classTimeMinutes: Int,          // e.g. 1140 = 7:00 PM
    val lastProcessedEpochDay: Long? = null
)

@Entity(tableName = "pw_study_tasks")
data class PwStudyTaskEntity(
    @PrimaryKey val taskId: String,
    val dateEpochDay: Long,
    val chapterId: String,
    val title: String,
    val strategy: String,
    val estimatedMinutes: Int,
    val youtubeSearchUrl: String,
    val isCompleted: Boolean = false
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

    // Loads the user's 7-question schedule onboarding profile
    @Query("SELECT * FROM schedule_profile WHERE id = 1")
    suspend fun getScheduleProfile(): ScheduleProfileEntity?

    // Saves or updates the 7-question schedule profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleProfile(profile: ScheduleProfileEntity)

    // Lists all schedule blocks for a specific day ordered chronologically
    @Query("SELECT * FROM daily_schedule_blocks WHERE dateEpochDay = :epochDay ORDER BY startTimeMinutesFromMidnight ASC")
    suspend fun getScheduleForDay(epochDay: Long): List<ScheduleBlockEntity>

    // Replaces or saves schedule blocks in bulk
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleBlocks(blocks: List<ScheduleBlockEntity>)

    // Deletes schedule blocks for a given day before regenerating timeline
    @Query("DELETE FROM daily_schedule_blocks WHERE dateEpochDay = :epochDay")
    suspend fun deleteScheduleForDay(epochDay: Long)

    // Lists all PW syllabus chapters for backlog & exam triage
    @Query("SELECT * FROM pw_syllabus_and_backlog ORDER BY subjectName ASC")
    suspend fun getAllPwChapters(): List<PwSyllabusEntity>

    // Deletes imported chapters before switching syllabus presets
    @Query("DELETE FROM pw_syllabus_and_backlog")
    suspend fun clearPwChapters()

    // Inserts or updates PW chapters in bulk
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwChapters(chapters: List<PwSyllabusEntity>)

    // Inserts or updates a single PW chapter
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwChapter(chapter: PwSyllabusEntity)

    // Lists recent PW notices and tests newest first
    @Query("SELECT * FROM pw_tests_and_notices ORDER BY timestampEpochMillis DESC LIMIT :limit")
    suspend fun getRecentPwItems(limit: Int = 30): List<PwNoticeTestEntity>

    // Inserts or updates PW notice or test items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwItems(items: List<PwNoticeTestEntity>)

    // Inserts a single PW notification item
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwItem(item: PwNoticeTestEntity)

    // Lists offline batch clock weekly class slots
    @Query("SELECT * FROM pw_batch_clock_slots")
    suspend fun getPwBatchSlots(): List<PwBatchSlotEntity>

    // Saves offline batch clock slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwBatchSlots(slots: List<PwBatchSlotEntity>)

    // Lists study checklist tasks by date, including completion state
    @Query("SELECT * FROM pw_study_tasks WHERE dateEpochDay >= :fromEpochDay ORDER BY dateEpochDay ASC, title ASC")
    suspend fun getPwStudyTasks(fromEpochDay: Long): List<PwStudyTaskEntity>

    // Inserts generated plan tasks without resetting completed checklist rows
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPwStudyTasks(tasks: List<PwStudyTaskEntity>)

    // Finds one study task by its stable ID
    @Query("SELECT * FROM pw_study_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getPwStudyTask(taskId: String): PwStudyTaskEntity?

    // Saves a completed study task
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPwStudyTask(task: PwStudyTaskEntity)
}

@Database(
    entities = [
        HabitEntity::class,
        RpEventEntity::class,
        StreakEntity::class,
        DailyAnalyticsEntity::class,
        ScheduleBlockEntity::class,
        ScheduleProfileEntity::class,
        PwSyllabusEntity::class,
        PwNoticeTestEntity::class,
        PwBatchSlotEntity::class,
        PwStudyTaskEntity::class
    ],
    version = 5,
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

        // Upgrades Room database schema from Phase 2 (v2) to Phase 4 (v3)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_schedule_blocks (
                        blockId TEXT NOT NULL PRIMARY KEY,
                        dateEpochDay INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        blockType TEXT NOT NULL,
                        priorityTier TEXT NOT NULL,
                        energyTag TEXT NOT NULL,
                        startTimeMinutesFromMidnight INTEGER NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        linkedPWChapterId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedule_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        wakeUpMinutes INTEGER NOT NULL,
                        sleepMinutes INTEGER NOT NULL,
                        coachingStartMinutes INTEGER NOT NULL,
                        coachingEndMinutes INTEGER NOT NULL,
                        sportsStartMinutes INTEGER NOT NULL,
                        sportsEndMinutes INTEGER NOT NULL,
                        lunchMinutes INTEGER NOT NULL,
                        dinnerMinutes INTEGER NOT NULL,
                        peakFocusStartMinutes INTEGER NOT NULL,
                        afternoonDipStartMinutes INTEGER NOT NULL,
                        p1TargetMinutes INTEGER NOT NULL,
                        p2TargetMinutes INTEGER NOT NULL,
                        p3TargetMinutes INTEGER NOT NULL,
                        dangerStartMinutes INTEGER NOT NULL,
                        dangerEndMinutes INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Upgrades Room database schema from Phase 4 (v3) to Phase 7 (v4)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pw_syllabus_and_backlog (
                        chapterId TEXT NOT NULL PRIMARY KEY,
                        subjectName TEXT NOT NULL,
                        chapterName TEXT NOT NULL,
                        totalBatchLectures INTEGER NOT NULL,
                        completedLectures INTEGER NOT NULL,
                        avgLectureDurationMinutes INTEGER NOT NULL,
                        oneShotDurationMinutes INTEGER NOT NULL,
                        notesAndPyqDurationMinutes INTEGER NOT NULL,
                        difficultyWeightageTier TEXT NOT NULL,
                        dppCompleted INTEGER NOT NULL,
                        isIncludedInUpcomingExam INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pw_tests_and_notices (
                        itemId TEXT NOT NULL PRIMARY KEY,
                        itemType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        rawNotificationText TEXT NOT NULL,
                        timestampEpochMillis INTEGER NOT NULL,
                        requiredChapterIdsJson TEXT NOT NULL,
                        isTestUnlocked INTEGER NOT NULL,
                        isTestCompleted INTEGER NOT NULL,
                        scheduledSundaySlotDate INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pw_batch_clock_slots (
                        slotId TEXT NOT NULL PRIMARY KEY,
                        subjectName TEXT NOT NULL,
                        linkedChapterId TEXT NOT NULL,
                        dayOfWeekValue INTEGER NOT NULL,
                        classTimeMinutes INTEGER NOT NULL,
                        lastProcessedEpochDay INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        // Adds the persistent PW day-by-day checklist
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS pw_study_tasks (taskId TEXT NOT NULL PRIMARY KEY, dateEpochDay INTEGER NOT NULL, chapterId TEXT NOT NULL, title TEXT NOT NULL, strategy TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL, youtubeSearchUrl TEXT NOT NULL, isCompleted INTEGER NOT NULL)""")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
