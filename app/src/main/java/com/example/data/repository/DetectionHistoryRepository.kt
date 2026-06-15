package com.example.data.repository

import com.example.data.database.DetectionHistory
import com.example.data.database.DetectionHistoryDao
import kotlinx.coroutines.flow.Flow

class DetectionHistoryRepository(private val dao: DetectionHistoryDao) {
    val allHistory: Flow<List<DetectionHistory>> = dao.getAllHistory()

    suspend fun insertHistory(history: DetectionHistory): Long {
        return dao.insertHistory(history)
    }

    suspend fun deleteHistory(history: DetectionHistory) {
        dao.deleteHistory(history)
    }

    suspend fun deleteHistoryById(id: Int) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }
}
