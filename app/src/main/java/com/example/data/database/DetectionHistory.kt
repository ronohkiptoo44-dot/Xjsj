package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_history")
data class DetectionHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionName: String,
    val date: Long = System.currentTimeMillis(),
    val sourceType: String, // "camera", "image", "video"
    val totalObjectsCount: Int,
    val labeledCounts: String, // e.g., "person: 2, cup: 3"
    val processingTimeMs: Long,
    val imagePath: String? = null
)
