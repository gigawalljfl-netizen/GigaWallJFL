package com.gigawalljfl.geolock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EarnedMedalDao {

    @Query("SELECT * FROM earned_medals ORDER BY earnedAtEpochMillis ASC")
    fun observeAll(): Flow<List<EarnedMedal>>

    // IGNORE: se a medalha já foi registrada, mantém a data original de conquista
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(medal: EarnedMedal)
}
