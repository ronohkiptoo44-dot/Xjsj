package com.example.data.image

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {

    private val _ssdDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val ssdDownloadState: StateFlow<DownloadState> = _ssdDownloadState

    private val _yoloDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val yoloDownloadState: StateFlow<DownloadState> = _yoloDownloadState

    private val ssdUrl = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/coco_ssd_mobilenet_v1_1.0_quant_2018_06_29.zip"
    private val yoloUrl = "https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8n.tflite"

    fun isModelDownloaded(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        if (file.exists() && file.length() > 0) return true
        
        return try {
            context.assets.open(modelName).use { }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadSSD(): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded("mobilenet_ssd.tflite")) {
            _ssdDownloadState.value = DownloadState.Success
            return@withContext true
        }

        _ssdDownloadState.value = DownloadState.Downloading(0f)
        try {
            val urlConnection = URI(ssdUrl).toURL().openConnection() as HttpURLConnection
            urlConnection.connect()
            
            if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                _ssdDownloadState.value = DownloadState.Error("Server returned code ${urlConnection.responseCode}")
                return@withContext false
            }

            val fileLength = urlConnection.contentLength
            val tempZipFile = File(context.cacheDir, "ssd_temp.zip")
            
            urlConnection.inputStream.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    val buffer = ByteArray(4096)
                    var totalBytesSaved = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesSaved += bytesRead
                        if (fileLength > 0) {
                            val progress = (totalBytesSaved.toFloat() / fileLength.toFloat()) * 0.9f // Reserve 10% for unzipping
                            _ssdDownloadState.value = DownloadState.Downloading(progress)
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            }

            // Extract detect.tflite from ZIP file
            _ssdDownloadState.value = DownloadState.Downloading(0.92f)
            val destFile = File(context.filesDir, "mobilenet_ssd.tflite")
            var unzipped = false
            
            ZipInputStream(tempZipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "detect.tflite") {
                        FileOutputStream(destFile).use { out ->
                            val buffer = ByteArray(4096)
                            var len = zip.read(buffer)
                            while (len != -1) {
                                out.write(buffer, 0, len)
                                len = zip.read(buffer)
                            }
                        }
                        unzipped = true
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            tempZipFile.delete()

            if (unzipped) {
                _ssdDownloadState.value = DownloadState.Success
                Log.d("ModelDownloader", "MobileNet SSD downloaded and unzipped successfully")
                true
            } else {
                _ssdDownloadState.value = DownloadState.Error("Could not find detect.tflite in zip archive")
                false
            }
        } catch (e: Exception) {
            _ssdDownloadState.value = DownloadState.Error(e.message ?: "Unknown download error")
            Log.e("ModelDownloader", "Error downloading SSD model: ${e.message}")
            false
        }
    }

    suspend fun downloadYOLO(): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded("yolov8n.tflite")) {
            _yoloDownloadState.value = DownloadState.Success
            return@withContext true
        }

        _yoloDownloadState.value = DownloadState.Downloading(0f)
        try {
            val urlConnection = URI(yoloUrl).toURL().openConnection() as HttpURLConnection
            urlConnection.connect()

            if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                _yoloDownloadState.value = DownloadState.Error("Server returned code ${urlConnection.responseCode}")
                return@withContext false
            }

            val fileLength = urlConnection.contentLength
            val destFile = File(context.filesDir, "yolov8n.tflite")
            
            urlConnection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(4096)
                    var totalBytesSaved = 0L
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesSaved += bytesRead
                        if (fileLength > 0) {
                            val progress = totalBytesSaved.toFloat() / fileLength.toFloat()
                            _yoloDownloadState.value = DownloadState.Downloading(progress)
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            }

            _yoloDownloadState.value = DownloadState.Success
            Log.d("ModelDownloader", "YOLOv8n downloaded successfully to filesDir")
            true
        } catch (e: Exception) {
            _yoloDownloadState.value = DownloadState.Error(e.message ?: "Unknown download error")
            Log.e("ModelDownloader", "Error downloading YOLO model: ${e.message}")
            false
        }
    }
}
