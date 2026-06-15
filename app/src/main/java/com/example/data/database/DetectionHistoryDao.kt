package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionHistoryDao {
    @Query("SELECT * FROM detection_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<DetectionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: DetectionHistory): Long

    @Delete
    suspend fun deleteHistory(history: DetectionHistory)

    @Query("DELETE FROM detection_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM detection_history")
    suspend fun clearHistory()
}
