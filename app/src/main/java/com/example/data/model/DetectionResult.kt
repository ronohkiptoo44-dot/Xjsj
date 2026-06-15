package com.example.data.model

import android.graphics.RectF

data class DetectionResult(
    val title: String,
    val confidence: Float,
    val boundingBox: RectF // Relative coordinates [0, 1]
)
