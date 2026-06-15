package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.DetectionHistory
import com.example.data.model.DetectionResult
import com.example.data.image.DownloadState
import com.example.data.image.ModelDownloader
import com.example.data.image.ObjectDetectorEngine
import com.example.data.repository.DetectionHistoryRepository
import com.example.data.settings.SettingsManager
import com.example.util.ExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val repository: DetectionHistoryRepository,
    private val detectorEngine: ObjectDetectorEngine,
    private val modelDownloader: ModelDownloader,
    val settingsManager: SettingsManager
) : ViewModel() {

    // History flows
    val historyList: StateFlow<List<DetectionHistory>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Download monitoring flows
    val ssdDownloadState: StateFlow<DownloadState> = modelDownloader.ssdDownloadState
    val yoloDownloadState: StateFlow<DownloadState> = modelDownloader.yoloDownloadState

    // Live state for Real-Time Camera
    private val _cameraDetections = MutableStateFlow<List<DetectionResult>>(emptyList())
    val cameraDetections: StateFlow<List<DetectionResult>> = _cameraDetections

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime: StateFlow<Long> = _inferenceTime

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    // Selected image/upload counting state
    private val _analyzedImageUri = MutableStateFlow<Uri?>(null)
    val analyzedImageUri: StateFlow<Uri?> = _analyzedImageUri

    private val _imageDetections = MutableStateFlow<List<DetectionResult>>(emptyList())
    val imageDetections: StateFlow<List<DetectionResult>> = _imageDetections

    private val _isAnalyzingImage = MutableStateFlow(false)
    val isAnalyzingImage: StateFlow<Boolean> = _isAnalyzingImage

    // Video analysis state
    private val _isVideoAnalyzing = MutableStateFlow(false)
    val isVideoAnalyzing: StateFlow<Boolean> = _isVideoAnalyzing

    private val _videoProgress = MutableStateFlow(0f)
    val videoProgress: StateFlow<Float> = _videoProgress

    private val _videoDetectionResults = MutableStateFlow<Map<String, Int>>(emptyMap())
    val videoDetectionResults: StateFlow<Map<String, Int>> = _videoDetectionResults

    private val _videoTotalDetections = MutableStateFlow(0)
    val videoTotalDetections: StateFlow<Int> = _videoTotalDetections

    private val _videoProcessedFrames = MutableStateFlow(0)
    val videoProcessedFrames: StateFlow<Int> = _videoProcessedFrames

    private val _videoAnalysisTime = MutableStateFlow(0L)
    val videoAnalysisTime: StateFlow<Long> = _videoAnalysisTime

    // Sync statuses
    private val _isSsdModelDownloaded = MutableStateFlow(modelDownloader.isModelDownloaded("mobilenet_ssd.tflite"))
    val isSsdModelDownloaded: StateFlow<Boolean> = _isSsdModelDownloaded

    private val _isYoloModelDownloaded = MutableStateFlow(modelDownloader.isModelDownloaded("yolov8n.tflite"))
    val isYoloModelDownloaded: StateFlow<Boolean> = _isYoloModelDownloaded

    init {
        // Automatically start background sync of missing model weights on startup
        viewModelScope.launch {
            if (!_isSsdModelDownloaded.value) {
                startDownloadingSsd()
            }
            if (!_isYoloModelDownloaded.value) {
                startDownloadingYolo()
            }
        }
    }

    // Active configuration indicators
    val activeConfidence: StateFlow<Float> = settingsManager.confidenceThreshold
    val activeShowBoxes: StateFlow<Boolean> = settingsManager.showBoundingBoxes
    val activeModel: StateFlow<String> = settingsManager.selectedModel
    val activeTheme: StateFlow<String> = settingsManager.theme
    val activeResolution: StateFlow<String> = settingsManager.resolution

    fun startDownloadingSsd() {
        viewModelScope.launch {
            val success = modelDownloader.downloadSSD()
            if (success) {
                detectorEngine.initializeDetectors()
                _isSsdModelDownloaded.value = true
            }
        }
    }

    fun startDownloadingYolo() {
        viewModelScope.launch {
            val success = modelDownloader.downloadYOLO()
            if (success) {
                detectorEngine.initializeDetectors()
                _isYoloModelDownloaded.value = true
            }
        }
    }

    val isProcessingCameraFrame = java.util.concurrent.atomic.AtomicBoolean(false)

    // Process a live frame from camera
    fun processCameraFrame(bitmap: Bitmap) {
        if (!isProcessingCameraFrame.compareAndSet(false, true)) {
            return
        }
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val thresh = settingsManager.confidenceThreshold.value
                val model = settingsManager.selectedModel.value

                val results = try {
                    detectorEngine.detect(bitmap, model, thresh)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Inference crashed on live camera frame: ${e.message}")
                    emptyList()
                }

                val endTime = System.currentTimeMillis()

                _cameraDetections.value = results
                _inferenceTime.value = (endTime - startTime)
            } finally {
                isProcessingCameraFrame.set(false)
            }
        }
    }

    fun updateFps(fpsVal: Int) {
        _fps.value = fpsVal
    }

    // Process static image upload
    fun processImageUpload(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _isAnalyzingImage.value = true
            val startTime = System.currentTimeMillis()

            val bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error reading image: ${e.message}")
                    null
                }
            }

            if (bitmap != null) {
                val thresh = settingsManager.confidenceThreshold.value
                val model = settingsManager.selectedModel.value
                val results = try {
                    detectorEngine.detect(bitmap, model, thresh)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Detector engine failed: ${e.message}")
                    emptyList()
                }
                _imageDetections.value = results

                val elapsed = System.currentTimeMillis() - startTime
                // Render image with bounding boxes drawn over it
                val drawnBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(drawnBitmap)

                if (settingsManager.showBoundingBoxes.value) {
                    val boxPaint = Paint().apply {
                        color = Color.GREEN
                        style = Paint.Style.STROKE
                        strokeWidth = 6f
                    }
                    val textPaint = Paint().apply {
                        color = Color.GREEN
                        textSize = 34f
                        isFakeBoldText = true
                    }
                    
                    for (res in results) {
                        val rect = res.boundingBox
                        val absoluteBox = RectF(
                            rect.left * drawnBitmap.width,
                            rect.top * drawnBitmap.height,
                            rect.right * drawnBitmap.width,
                            rect.bottom * drawnBitmap.height
                        )
                        canvas.drawRect(absoluteBox, boxPaint)
                        canvas.drawText(
                            "${res.title} ${(res.confidence * 100).toInt()}%",
                            absoluteBox.left + 5f,
                            absoluteBox.top - 10f,
                            textPaint
                        )
                    }
                }

                // Save drawn bitmap to a local cache file to enable sharing/display
                val savedUri = withContext(Dispatchers.IO) {
                    try {
                        val file = File(context.cacheDir, "analyzed_image_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out ->
                            drawnBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        Uri.fromFile(file)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error saving drawn bitmap: ${e.message}")
                        null
                    }
                }

                _analyzedImageUri.value = savedUri

                // Record history item
                val breakdown = results.groupBy { it.title }
                    .map { "${it.key}: ${it.value.size}" }
                    .joinToString(", ")
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val sessionName = "Image Upload Count - ${sdf.format(Date())}"
                
                repository.insertHistory(
                    DetectionHistory(
                        id = 0,
                        sessionName = sessionName,
                        sourceType = "image",
                        totalObjectsCount = results.size,
                        labeledCounts = breakdown,
                        processingTimeMs = elapsed,
                        imagePath = savedUri?.path
                    )
                )
            }

            _isAnalyzingImage.value = false
        }
    }

    // Process local video frame-by-frame
    fun processVideoFile(context: Context, videoUri: Uri) {
        viewModelScope.launch {
            _isVideoAnalyzing.value = true
            _videoProgress.value = 0f
            _videoProcessedFrames.value = 0
            _videoTotalDetections.value = 0
            _videoDetectionResults.value = emptyMap()

            val startTime = System.currentTimeMillis()
            val totalCounts = mutableMapOf<String, Int>()

            withContext(Dispatchers.Default) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, videoUri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toLong() ?: 0L

                    if (durationMs > 0) {
                        // Extract frames every 1 second (1000ms) to ensure speed and completeness
                        val stepMs = 1000L
                        val thresh = settingsManager.confidenceThreshold.value
                        val model = settingsManager.selectedModel.value
                        val totalFramesToProcess = (durationMs / stepMs).toInt().coerceAtLeast(1)

                        for (currentMs in 0 until durationMs step stepMs) {
                            val timeUs = currentMs * 1000
                            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (frame != null) {
                                val results = try {
                                    detectorEngine.detect(frame, model, thresh)
                                } catch (e: Exception) {
                                    Log.e("MainViewModel", "Video frame detection failed: ${e.message}")
                                    emptyList()
                                }
                                
                                for (res in results) {
                                    totalCounts[res.title] = (totalCounts[res.title] ?: 0) + 1
                                }

                                _videoProcessedFrames.value += 1
                                _videoTotalDetections.value = totalCounts.values.sum()
                                _videoDetectionResults.value = HashMap(totalCounts)

                                val progress = _videoProcessedFrames.value.toFloat() / totalFramesToProcess.toFloat()
                                _videoProgress.value = progress.coerceIn(0f, 1f)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error analyzing video: ${e.message}")
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) { /* ignored */ }
                }
            }

            val totalProcessTime = System.currentTimeMillis() - startTime
            _videoAnalysisTime.value = totalProcessTime

            // Save video counting session to local history
            val breakdown = totalCounts.map { "${it.key}: ${it.value}" }.joinToString(", ")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sessionName = "Video Log - ${sdf.format(Date())}"

            repository.insertHistory(
                DetectionHistory(
                    id = 0,
                    sessionName = sessionName,
                    sourceType = "video",
                    totalObjectsCount = totalCounts.values.sum(),
                    labeledCounts = breakdown,
                    processingTimeMs = totalProcessTime,
                    imagePath = null
                )
            )

            _isVideoAnalyzing.value = false
        }
    }

    fun saveCameraSession(detectedMap: Map<String, Int>, elapsedMs: Long) {
        viewModelScope.launch {
            if (detectedMap.isEmpty()) return@launch

            val breakdown = detectedMap.map { "${it.key}: ${it.value}" }.joinToString(", ")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sessionName = "Live Camera Scan - ${sdf.format(Date())}"

            repository.insertHistory(
                DetectionHistory(
                    id = 0,
                    sessionName = sessionName,
                    sourceType = "camera",
                    totalObjectsCount = detectedMap.values.sum(),
                    labeledCounts = breakdown,
                    processingTimeMs = elapsedMs,
                    imagePath = null
                )
            )
        }
    }

    fun deleteHistoryItem(item: DetectionHistory) {
        viewModelScope.launch {
            repository.deleteHistory(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Export current item report
    fun exportReportAsPDF(context: Context, history: DetectionHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfFile = ExportUtil.generatePDF(context, history)
                withContext(Dispatchers.Main) {
                    ExportUtil.shareFile(context, pdfFile, "application/pdf", "Detection Session PDF")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error exporting PDF: ${e.message}")
            }
        }
    }

    fun exportHistoryAsCSV(context: Context, fullList: List<DetectionHistory>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (fullList.isEmpty()) return@launch
                val csvFile = ExportUtil.generateCSV(context, fullList)
                withContext(Dispatchers.Main) {
                    ExportUtil.shareFile(context, csvFile, "text/csv", "Detection History CSV")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error exporting CSV: ${e.message}")
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: DetectionHistoryRepository,
    private val detectorEngine: ObjectDetectorEngine,
    private val modelDownloader: ModelDownloader,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, detectorEngine, modelDownloader, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
