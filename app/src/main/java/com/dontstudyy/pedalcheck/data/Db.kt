package com.dontstudyy.pedalcheck.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 완료된 한 번의 테스트. 중단된 테스트는 기록하지 않는다. */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    /** 정상 빨강 3개(양 끝 제외)의 평균(ms) */
    val meanMs: Int,
    /** 기준선 대비 %(부호 있음: 느리면 +). 기준선이 없었으면 null */
    val percentVsBaseline: Int?,
    val falseStarts: Int,
    val wrongPresses: Int,
    val misses: Int,
    /** 시연용으로 넣은 과거 기록 */
    val demo: Boolean = false,
)

@Entity(
    tableName = "trial",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class TrialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** 시도 순서(다시 잰 것 포함) */
    val attempt: Int,
    /** 회차 번호(0~5) */
    val slot: Int,
    val kind: String,
    val waitMs: Int,
    val shownAtNanos: Long?,
    val pressedAtNanos: Long?,
    val reactionMs: Int?,
    val verdict: String,
    val excludedFromMean: Boolean,
)

/** 기준선은 하나만 둔다(id = 1). */
@Entity(tableName = "baseline")
data class BaselineEntity(
    @PrimaryKey val id: Int = 1,
    val valueMs: Int,
    val createdAtMillis: Long,
    val sessionCount: Int,
    /** WINDOW(처음 14일 중앙값) / DEMO(시연용으로 지정) */
    val method: String,
)

@Dao
interface PedalDao {
    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Insert
    suspend fun insertTrials(list: List<TrialEntity>)

    @Query("SELECT * FROM session ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY startedAtMillis ASC")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM trial WHERE sessionId = :sessionId ORDER BY attempt")
    suspend fun trialsOf(sessionId: Long): List<TrialEntity>

    @Query("SELECT * FROM baseline WHERE id = 1")
    fun observeBaseline(): Flow<BaselineEntity?>

    @Query("SELECT * FROM baseline WHERE id = 1")
    suspend fun baseline(): BaselineEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun setBaseline(b: BaselineEntity)

    @Query("DELETE FROM baseline")
    suspend fun clearBaseline()

    @Query("DELETE FROM session")
    suspend fun clearSessions()

    @Query("DELETE FROM session WHERE demo = 1")
    suspend fun clearDemoSessions()
}

@Database(entities = [SessionEntity::class, TrialEntity::class, BaselineEntity::class], version = 1, exportSchema = false)
abstract class PedalDatabase : RoomDatabase() {
    abstract fun dao(): PedalDao

    companion object {
        @Volatile private var instance: PedalDatabase? = null

        fun get(context: Context): PedalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PedalDatabase::class.java, "pedalcheck.db")
                .build().also { instance = it }
        }
    }
}
