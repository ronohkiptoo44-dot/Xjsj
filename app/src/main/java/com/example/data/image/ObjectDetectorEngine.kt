package com.example.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.data.model.DetectionResult
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObjectDetectorEngine(private val context: Context) {

    private var ssdInterpreter: Interpreter? = null
    private var yoloInterpreter: Interpreter? = null

    // Constants for SSD model
    private val ssdModelFile = "mobilenet_ssd.tflite"
    private val ssdInputSize = 300

    // Constants for YOLO model
    private val yoloModelFile = "yolov8n.tflite"
    private val yoloInputSize = 640

    init {
        initializeDetectors()
    }

    @Synchronized
    fun initializeDetectors() {
        // Try to load MobileNet SSD
        try {
            val ssdBuffer = loadModelFile(ssdModelFile)
            if (ssdBuffer != null) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                try {
                    ssdInterpreter?.close()
                } catch (e: Exception) {
                    Log.w("ObjectDetectorEngine", "Old SSD interpreter close error: ${e.message}")
                }
                ssdInterpreter = Interpreter(ssdBuffer, options)
                Log.d("ObjectDetectorEngine", "SSD Interpreter initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("ObjectDetectorEngine", "Error initializing SSD interpreter: ${e.message}")
        }

        // Try to load YOLOv8n
        try {
            val yoloBuffer = loadModelFile(yoloModelFile)
            if (yoloBuffer != null) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                try {
                    yoloInterpreter?.close()
                } catch (e: Exception) {
                    Log.w("ObjectDetectorEngine", "Old YOLO interpreter close error: ${e.message}")
                }
                yoloInterpreter = Interpreter(yoloBuffer, options)
                Log.d("ObjectDetectorEngine", "YOLO Interpreter initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("ObjectDetectorEngine", "Error initializing YOLO interpreter: ${e.message}")
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer? {
        // 1. Check if model was downloaded to filesDir
        val downloadedFile = File(context.filesDir, modelName)
        if (downloadedFile.exists() && downloadedFile.length() > 0) {
            val fis = FileInputStream(downloadedFile)
            val fileChannel = fis.channel
            val startOffset = 0L
            val declaredLength = downloadedFile.length()
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        // 2. Otherwise load from Assets
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val fis = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = fis.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.w("ObjectDetectorEngine", "Could not find model $modelName in assets or filesDir: ${e.message}")
            null
        }
    }

    fun isModelLoaded(modelType: String): Boolean {
        return if (modelType == "YOLO") yoloInterpreter != null else ssdInterpreter != null
    }

    suspend fun detect(
        bitmap: Bitmap,
        modelType: String,
        confidenceThreshold: Float
    ): List<DetectionResult> = withContext(Dispatchers.Default) {
        if (modelType == "YOLO") {
            val interpreter = yoloInterpreter ?: return@withContext emptyList()
            detectYOLO(bitmap, interpreter, confidenceThreshold)
        } else {
            val interpreter = ssdInterpreter ?: return@withContext emptyList()
            detectSSD(bitmap, interpreter, confidenceThreshold)
        }
    }

    /**
     * MobileNet SSD Object Detection
     * Output format:
     * 0: locations (Bounding Boxes) shape [1, 10, 4]
     * 1: classes (Class Id) shape [1, 10]
     * 2: scores (Confidence Score) shape [1, 10]
     * 3: num_detections (Total detected count) shape [1]
     */
    private fun detectSSD(
        bitmap: Bitmap,
        interpreter: Interpreter,
        confidenceThreshold: Float
    ): List<DetectionResult> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, ssdInputSize, ssdInputSize, true)
        
        // Input ByteBuffer: size = 1 * 300 * 300 * 3 = 270,000 bytes (for quantized Uint8)
        val imgData = ByteBuffer.allocateDirect(ssdInputSize * ssdInputSize * 3)
        imgData.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(ssdInputSize * ssdInputSize)
        scaledBitmap.getPixels(intValues, 0, ssdInputSize, 0, 0, ssdInputSize, ssdInputSize)
        
        imgData.rewind()
        for (i in intValues.indices) {
            val value = intValues[i]
            imgData.put(((value shr 16) and 0xFF).toByte())
            imgData.put(((value shr 8) and 0xFF).toByte())
            imgData.put((value and 0xFF).toByte())
        }

        // Output containers
        val outLocations = Array(1) { Array(10) { FloatArray(4) } }
        val outClasses = Array(1) { FloatArray(10) }
        val outScores = Array(1) { FloatArray(10) }
        val outNumDetections = FloatArray(1)

        val outputMap = mapOf(
            0 to outLocations,
            1 to outClasses,
            2 to outScores,
            3 to outNumDetections
        )

        val inputArr = arrayOf(imgData)
        interpreter.runForMultipleInputsOutputs(inputArr, outputMap)

        val results = mutableListOf<DetectionResult>()
        val count = outNumDetections[0].toInt().coerceAtMost(10)
        
        for (i in 0 until count) {
            val confidence = outScores[0][i]
            if (confidence >= confidenceThreshold) {
                val classIdx = outClasses[0][i].toInt()
                val label = ssdClasses[classIdx] ?: "Object $classIdx"
                
                // SSD outputs coordinates as [top, left, bottom, right] relative coordinates
                val top = outLocations[0][i][0]
                val left = outLocations[0][i][1]
                val bottom = outLocations[0][i][2]
                val right = outLocations[0][i][3]
                
                val box = RectF(
                    left.coerceIn(0f, 1f),
                    top.coerceIn(0f, 1f),
                    right.coerceIn(0f, 1f),
                    bottom.coerceIn(0f, 1f)
                )
                results.add(DetectionResult(label, confidence, box))
            }
        }
        return results
    }

    /**
     * YOLOv8 Object Detection
     * Input format: shape [1, 640, 640, 3] float32 normalized image (values 0..1).
     * Output format: shape [1, 84, 8400]
     */
    private fun detectYOLO(
        bitmap: Bitmap,
        interpreter: Interpreter,
        confidenceThreshold: Float
    ): List<DetectionResult> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, yoloInputSize, yoloInputSize, true)
        
        // Input ByteBuffer: size = 1 * 640 * 640 * 3 * 4 (bytes per float)
        val imgData = ByteBuffer.allocateDirect(1 * yoloInputSize * yoloInputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(yoloInputSize * yoloInputSize)
        scaledBitmap.getPixels(intValues, 0, yoloInputSize, 0, 0, yoloInputSize, yoloInputSize)
        
        imgData.rewind()
        for (i in intValues.indices) {
            val value = intValues[i]
            // Normalize to [0..1] float
            imgData.putFloat(((value shr 16) and 0xFF) / 255.0f)
            imgData.putFloat(((value shr 8) and 0xFF) / 255.0f)
            imgData.putFloat((value and 0xFF) / 255.0f)
        }

        // Output float array size [1, 84, 8400] -> flattened is 1 * 84 * 8400 = 705,600 values
        val outFlat = Array(1) { Array(84) { FloatArray(8400) } }
        interpreter.run(imgData, outFlat)

        val results = mutableListOf<DetectionResult>()
        val output = outFlat[0] // size 84 x 8400

        // Parse YOLOv8 outputs:
        // Rows 0..3: Cx, Cy, W, H
        // Rows 4..83: Confidence scores for each of 80 classes
        for (col in 0 until 8400) {
            // Find max class confidence
            var maxScore = -1.0f
            var maxClassId = -1
            
            for (clazz in 0 until 80) {
                val score = output[clazz + 4][col]
                if (score > maxScore) {
                    maxScore = score
                    maxClassId = clazz
                }
            }

            if (maxScore >= confidenceThreshold) {
                val cx = output[0][col]
                val cy = output[1][col]
                val w = output[2][col]
                val h = output[3][col]

                // Convert center coordinates to relative RectF
                val xmin = ((cx - w / 2f) / yoloInputSize).coerceIn(0f, 1f)
                val ymin = ((cy - h / 2f) / yoloInputSize).coerceIn(0f, 1f)
                val xmax = ((cx + w / 2f) / yoloInputSize).coerceIn(0f, 1f)
                val ymax = ((cy + h / 2f) / yoloInputSize).coerceIn(0f, 1f)

                val label = yoloClasses.getOrNull(maxClassId) ?: "Object $maxClassId"
                results.add(DetectionResult(label, maxScore, RectF(xmin, ymin, xmax, ymax)))
            }
        }

        // Apply Non-Maximum Suppression (NMS) to eliminate duplicate overlapping bounding boxes
        return nms(results)
    }

    private fun nms(results: List<DetectionResult>): List<DetectionResult> {
        val sorted = results.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionResult>()
        
        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            selected.add(current)
            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val element = iterator.next()
                if (ioU(current.boundingBox, element.boundingBox) > 0.45f) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun ioU(box1: RectF, box2: RectF): Float {
        val intersectionXmin = maxOf(box1.left, box2.left)
        val intersectionYmin = maxOf(box1.top, box2.top)
        val intersectionXmax = minOf(box1.right, box2.right)
        val intersectionYmax = minOf(box1.bottom, box2.bottom)
        
        val intersectionArea = maxOf(0f, intersectionXmax - intersectionXmin) * maxOf(0f, intersectionYmax - intersectionYmin)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        
        val unionArea = box1Area + box2Area - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    // COCO 80 Class Labels for YOLO
    private val yoloClasses = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
        "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
        "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    // COCO 91 Class labels for SSD MobileNet
    private val ssdClasses = mapOf(
        0 to "background", 1 to "person", 2 to "bicycle", 3 to "car", 4 to "motorcycle", 5 to "airplane",
        6 to "bus", 7 to "train", 8 to "truck", 9 to "boat", 10 to "traffic light", 11 to "fire hydrant",
        13 to "stop sign", 14 to "parking meter", 15 to "bench", 16 to "bird", 17 to "cat", 18 to "dog",
        19 to "horse", 20 to "sheep", 21 to "cow", 22 to "elephant", 23 to "bear", 24 to "zebra",
        25 to "giraffe", 27 to "backpack", 28 to "umbrella", 31 to "handbag", 32 to "tie", 33 to "suitcase",
        34 to "frisbee", 35 to "skis", 36 to "snowboard", 37 to "sports ball", 38 to "kite", 39 to "baseball bat",
        40 to "baseball glove", 41 to "skateboard", 42 to "surfboard", 43 to "tennis racket", 44 to "bottle",
        46 to "wine glass", 47 to "cup", 48 to "fork", 49 to "knife", 50 to "spoon", 51 to "bowl",
        52 to "banana", 53 to "apple", 54 to "sandwich", 55 to "orange", 56 to "broccoli", 57 to "carrot",
        58 to "hot dog", 59 to "pizza", 60 to "donut", 61 to "cake", 62 to "chair", 63 to "couch",
        64 to "potted plant", 65 to "bed", 67 to "dining table", 70 to "toilet", 72 to "tv", 73 to "laptop",
        74 to "mouse", 75 to "remote", 76 to "keyboard", 77 to "cell phone", 78 to "microwave", 79 to "oven",
        80 to "toaster", 81 to "sink", 82 to "refrigerator", 84 to "book", 85 to "clock", 86 to "vase",
        87 to "scissors", 88 to "teddy bear", 89 to "hair drier", 90 to "toothbrush"
    )
}
