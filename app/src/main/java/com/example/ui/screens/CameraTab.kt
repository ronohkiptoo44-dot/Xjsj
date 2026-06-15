package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.MainViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CameraTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (hasCameraPermission) {
            CameraContent(viewModel)
        } else {
            // Friendly layout requesting Camera Permissions
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera Permission Required",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Permission Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Object Counter AI scans objects in real time using your device's camera. Please grant camera permission to begin scanner.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(30.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(50.dp)
                        .testTag("grant_permission_button")
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun CameraContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activeModel by viewModel.activeModel.collectAsState()
    val isSsdLoaded by viewModel.isSsdModelDownloaded.collectAsState()
    val isYoloLoaded by viewModel.isYoloModelDownloaded.collectAsState()

    // Zoom and camera properties
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    val cameraDetections by viewModel.cameraDetections.collectAsState()
    val activeShowBoxes by viewModel.activeShowBoxes.collectAsState()
    val inferenceTime by viewModel.inferenceTime.collectAsState()
    val currentFps by viewModel.fps.collectAsState()

    // Download state monitoring if models not downloaded
    val modelDownloaded = if (activeModel == "YOLO") isYoloLoaded else isSsdLoaded

    // Auto-trigger background sync
    LaunchedEffect(activeModel, modelDownloaded) {
        if (!modelDownloaded) {
            if (activeModel == "YOLO") {
                viewModel.startDownloadingYolo()
            } else {
                viewModel.startDownloadingSsd()
            }
        }
    }

    // FPS Counter utility
    var lastFrameTime = remember { System.currentTimeMillis() }
    var frameCount = remember { 0 }

    if (!modelDownloaded) {
        // Render Model Download Screen
        val isYolo = activeModel == "YOLO"
        val downloadFlow = if (isYolo) viewModel.yoloDownloadState else viewModel.ssdDownloadState
        val downloadState by downloadFlow.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Syncing AI Model",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(90.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Syncing Neural Network",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Object Counter AI is automatically downloading and configuring the lightweight $activeModel neural core (~${if (isYolo) "6" else "4"}MB) to run fully offline on your device's neural pipeline.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(30.dp))

            when (val state = downloadState) {
                is com.example.data.image.DownloadState.Idle -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Preparing secure connection...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is com.example.data.image.DownloadState.Downloading -> {
                    val progressPercent = (state.progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(0.8f).height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Optimizing AI Core: $progressPercent%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is com.example.data.image.DownloadState.Success -> {
                    Text("Ready! Activating neural pipeline...", color = MaterialTheme.colorScheme.primary)
                }
                is com.example.data.image.DownloadState.Error -> {
                    Text(
                        text = "Connection Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isYolo) viewModel.startDownloadingYolo() else viewModel.startDownloadingSsd()
                        }
                    ) {
                        Text("Retry Connection", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    } else {
        // Capture total breakdowns to overlay
        val countsByType = remember(cameraDetections) {
            cameraDetections.groupBy { it.title }.mapValues { it.value.size }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(lensFacing) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            val zoomState = camera?.cameraInfo?.zoomState?.value ?: return@detectTransformGestures
                            val currentZoomRatio = zoomState.zoomRatio
                            val targetZoomRatio = (currentZoomRatio * zoom).coerceIn(
                                zoomState.minZoomRatio,
                                zoomState.maxZoomRatio
                            )
                            camera?.cameraControl?.setZoomRatio(targetZoomRatio)
                        }
                    }
                }
        ) {
            // Android View hosting native PreviewView
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    // Configure custom ImageAnalysis for continuous real-time model runs
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    val analysisExecutor = Executors.newSingleThreadExecutor()
                    
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (viewModel.isProcessingCameraFrame.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val now = System.currentTimeMillis()
                        frameCount += 1
                        if (now - lastFrameTime >= 1000) {
                            viewModel.updateFps(frameCount)
                            frameCount = 0
                            lastFrameTime = now
                        }

                        val bitmap = try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            Log.e("CameraTab", "Failed to convert imageProxy to bitmap", e)
                            null
                        }

                        if (bitmap != null) {
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            // Handle rotation matrices natively
                            val rotatedBitmap = if (rotation != 0) {
                                try {
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                } catch (e: Exception) {
                                    Log.e("CameraTab", "Failed to rotate bitmap", e)
                                    bitmap
                                }
                            } else {
                                bitmap
                            }
                            viewModel.processCameraFrame(rotatedBitmap)
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraTab", "Use case binding failed: ${e.message}")
                    }
                }
            )

            // Draw bounding box canvas overlay
            if (activeShowBoxes) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (det in cameraDetections) {
                        val rect = det.boundingBox
                        // Map relative coords [0, 1] securely to absolute pixels canvas bounds
                        val absoluteLeft = rect.left * size.width
                        val absoluteTop = rect.top * size.height
                        val absoluteRight = rect.right * size.width
                        val absoluteBottom = rect.bottom * size.height

                        // Paint green rectangles
                        drawRect(
                            color = Color(0xFF00FF66),
                            topLeft = Offset(absoluteLeft, absoluteTop),
                            size = Size(absoluteRight - absoluteLeft, absoluteBottom - absoluteTop),
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }

            // Top overlay bar showing info speed indicators
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Info badges row
                Row(
                    modifier = Modifier
                        .background(Color(0xE60F1512), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Inference Speed",
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${inferenceTime}ms",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cyclone,
                            contentDescription = "FPS",
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${currentFps} FPS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Model Name",
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = activeModel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Camera toggle button
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .background(Color(0xE60F1512), RoundedCornerShape(12.dp))
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .testTag("toggle_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera Front Rear",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom drawer dashboard showing current counts and save option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF20F1512)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3524))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Counts (${cameraDetections.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Save session button
                        Button(
                            onClick = {
                                viewModel.saveCameraSession(countsByType, inferenceTime)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008F35)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("save_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (countsByType.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Waiting for objects in preview scope...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Display individual counts as list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(countsByType.toList()) { (label, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x1F00FF66), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label.replaceFirstChar { it.titlecase() },
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "$count",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF66),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
