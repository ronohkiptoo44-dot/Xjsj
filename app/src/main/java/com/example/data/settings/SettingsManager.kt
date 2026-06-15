package com.example.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("object_counter_settings", Context.MODE_PRIVATE)

    private val _confidenceThreshold = MutableStateFlow(prefs.getFloat("confidence_threshold", 0.40f))
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold

    private val _showBoundingBoxes = MutableStateFlow(prefs.getBoolean("show_bounding_boxes", true))
    val showBoundingBoxes: StateFlow<Boolean> = _showBoundingBoxes

    private val _selectedModel = MutableStateFlow(prefs.getString("selected_model", "SSD") ?: "SSD")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _theme = MutableStateFlow(prefs.getString("theme", "DARK") ?: "DARK")
    val theme: StateFlow<String> = _theme

    private val _resolution = MutableStateFlow(prefs.getString("resolution", "720p") ?: "720p")
    val resolution: StateFlow<String> = _resolution

    fun setConfidenceThreshold(value: Float) {
        prefs.edit().putFloat("confidence_threshold", value).apply()
        _confidenceThreshold.value = value
    }

    fun setShowBoundingBoxes(value: Boolean) {
        prefs.edit().putBoolean("show_bounding_boxes", value).apply()
        _showBoundingBoxes.value = value
    }

    fun setSelectedModel(value: String) {
        prefs.edit().putString("selected_model", value).apply()
        _selectedModel.value = value
    }

    fun setTheme(value: String) {
        prefs.edit().putString("theme", value).apply()
        _theme.value = value
    }

    fun setResolution(value: String) {
        prefs.edit().putString("resolution", value).apply()
        _resolution.value = value
    }
}
