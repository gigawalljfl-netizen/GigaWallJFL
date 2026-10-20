package com.gigawalljfl.geolock.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeCapsuleDao {

    @Query("SELECT * FROM time_capsules ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<TimeCapsule>>

    @Query("SELECT * FROM time_capsules WHERE id = :id")
    suspend fun getById(id: String): TimeCapsule?

    @Query("SELECT * FROM time_capsules WHERE isUnlocked = 1")
    fun observeUnlocked(): Flow<List<TimeCapsule>>

    @Query("SELECT COUNT(*) FROM time_capsules WHERE isUnlocked = 1")
    fun observeUnlockedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capsule: TimeCapsule)

    @Update
    suspend fun update(capsule: TimeCapsule)

    @Delete
    suspend fun delete(capsule: TimeCapsule)

    @Query("UPDATE time_capsules SET isUnlocked = 1, unlockedAtEpochMillis = :at WHERE id = :id")
    suspend fun markUnlocked(id: String, at: Long)
}
