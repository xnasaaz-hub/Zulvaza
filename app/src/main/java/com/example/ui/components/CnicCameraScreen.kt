package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CnicCaptureResult
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.example.util.CnicMatchValidator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CnicLiveCameraOverlayScreen(
    sideLabel: String, // "Front" or "Back"
    onCaptured: (CnicCaptureResult) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showSuccessTick by remember { mutableStateOf(false) }
    var capturedReadyResult by remember { mutableStateOf<CnicCaptureResult?>(null) }
    var detectionProgress by remember { mutableFloatStateOf(0f) }
    var cardDetectedState by remember { mutableStateOf(false) }
    var liveHint by remember { mutableStateOf("Place your CNIC $sideLabel card inside the frame") }

    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { textRecognizer.close() }
        }
    }

    val animatedTickScale by animateFloatAsState(
        targetValue = if (showSuccessTick) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cnicSuccessTickScale"
    )

    val scanInfinite = rememberInfiniteTransition(label = "cnicScanBeam")
    val scanLineFraction by scanInfinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineFraction"
    )

    LaunchedEffect(capturedReadyResult) {
        val result = capturedReadyResult ?: return@LaunchedEffect
        showSuccessTick = true
        delay(680)
        onCaptured(result)
    }

    fun triggerAutoCapture() {
        val capture = imageCapture ?: return
        if (isCapturing || showSuccessTick) return
        isCapturing = true
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotated = imageProxyToRotatedBitmap(image)
                    image.close()
                    if (rotated == null) {
                        isCapturing = false
                        return
                    }
                    // Crop strictly to the 85.6:54 CNIC frame in center of captured photo
                    val cardBitmap = cropBitmapToCnicFrame(rotated)
                    val ocrFinalText = try {
                        val task = textRecognizer.process(InputImage.fromBitmap(cardBitmap, 0))
                        Tasks.await(task, 2500, TimeUnit.MILLISECONDS).text
                    } catch (_: Exception) {
                        ""
                    }
                    val baos = ByteArrayOutputStream()
                    cardBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    capturedReadyResult = CnicCaptureResult(
                        jpegBytes = baos.toByteArray(),
                        extractedText = ocrFinalText,
                        detectedCnicDigits = CnicMatchValidator.extractCnicDigits(ocrFinalText),
                        detectedDates = CnicMatchValidator.extractIsoDates(ocrFinalText)
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    cameraError = exception.message ?: "Capture failed"
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060B14))
            .systemBarsPadding()
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZulvazaOrange.copy(alpha = 0.16f),
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = ZulvazaOrange,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (permissionDenied) {
                        "Camera permission was denied. Live CNIC card scanning is required for identity verification."
                    } else {
                        "Grant camera permission so the scanner can detect and verify your CNIC $sideLabel automatically."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onClose,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancel", color = Color.White, maxLines = 1)
                    }
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Allow Camera", maxLines = 1)
                    }
                }
            }
        } else {
            val previewView = remember { PreviewView(context) }
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(lifecycleOwner, sideLabel) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val mainExecutor = ContextCompat.getMainExecutor(context)
                var prevSignature: IntArray? = null
                var confirmedCardHits = 0
                var lastAnalyzedMs = 0L

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .apply {
                                setAnalyzer(analyzerExecutor) { proxy ->
                                    val now = System.currentTimeMillis()
                                    if (now - lastAnalyzedMs < 320L || isCapturing || showSuccessTick) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    lastAnalyzedMs = now

                                    val metrics = evaluateCnicFrame(proxy, prevSignature)
                                    prevSignature = metrics.signature

                                    var ocrPlausible = false
                                    var detectedTextLines = 0
                                    if (metrics.meanLuma in 45.0..238.0 &&
                                        metrics.edgeScore >= 7.5 &&
                                        metrics.borderContrast >= 7.0
                                    ) {
                                        try {
                                            val analysisBitmap = yuvImageProxyToCroppedCardBitmap(proxy)
                                            if (analysisBitmap != null) {
                                                val ocrTask = textRecognizer.process(
                                                    InputImage.fromBitmap(analysisBitmap, 0)
                                                )
                                                val textResult = Tasks.await(ocrTask, 900, TimeUnit.MILLISECONDS)
                                                detectedTextLines = textResult.textBlocks.sumOf { it.lines.size }
                                                ocrPlausible = CnicMatchValidator.isPlausibleCnicFrame(
                                                    sideLabel = sideLabel,
                                                    rawText = textResult.text,
                                                    lineCount = detectedTextLines
                                                )
                                            }
                                        } catch (_: Exception) {
                                            ocrPlausible = false
                                        }
                                    }
                                    proxy.close()

                                    mainExecutor.execute {
                                        if (isCapturing || showSuccessTick) return@execute
                                        when {
                                            metrics.meanLuma < 45 -> {
                                                confirmedCardHits = 0
                                                cardDetectedState = false
                                                detectionProgress = 0.05f
                                                liveHint = "Too dark — turn on flash or move to brighter light"
                                            }
                                            metrics.edgeScore < 7.5 || metrics.borderContrast < 7.0 -> {
                                                confirmedCardHits = 0
                                                cardDetectedState = false
                                                detectionProgress = 0.15f
                                                liveHint = "No card detected — align CNIC $sideLabel inside the box"
                                            }
                                            !ocrPlausible -> {
                                                confirmedCardHits = (confirmedCardHits - 1).coerceAtLeast(0)
                                                cardDetectedState = false
                                                detectionProgress = 0.35f
                                                liveHint = if (detectedTextLines > 0) {
                                                    "Reading CNIC $sideLabel text — move slightly closer & steady"
                                                } else {
                                                    "Card edges seen — make sure CNIC $sideLabel text is clear"
                                                }
                                            }
                                            !metrics.isSteady -> {
                                                cardDetectedState = true
                                                detectionProgress = 0.65f
                                                liveHint = "CNIC $sideLabel detected! Hold steady..."
                                            }
                                            else -> {
                                                confirmedCardHits++
                                                cardDetectedState = true
                                                detectionProgress = (0.5f + confirmedCardHits * 0.25f).coerceIn(0f, 1f)
                                                liveHint = "CNIC $sideLabel verified — capturing cleanly..."
                                                if (confirmedCardHits >= 2) {
                                                    triggerAutoCapture()
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        imageCapture = capture
                        cameraProvider.unbindAll()
                        boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                            analysis
                        )
                    } catch (e: Exception) {
                        cameraError = e.message ?: "Unable to start rear camera"
                    }
                }, mainExecutor)

                onDispose {
                    runCatching { cameraProviderFuture.get().unbindAll() }
                    analyzerExecutor.shutdown()
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Darkened mask + 85.6:54 ID Card frame + corner brackets + scanning beam
            val frameColor = when {
                showSuccessTick || cardDetectedState -> SuccessGreen
                else -> ZulvazaOrange
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cardRatio = 85.6f / 54f
                val frameWidth = size.width * 0.88f
                val frameHeight = frameWidth / cardRatio
                val left = (size.width - frameWidth) / 2f
                val top = (size.height - frameHeight) / 2f - 18.dp.toPx()
                val rect = Rect(left, top, left + frameWidth, top + frameHeight)
                val roundRect = RoundRect(rect, CornerRadius(18.dp.toPx(), 18.dp.toPx()))
                val cutPath = Path().apply { addRoundRect(roundRect) }

                clipPath(cutPath, clipOp = ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.72f))
                }

                // Subtle outer border
                drawRoundRect(
                    color = frameColor.copy(alpha = 0.45f),
                    topLeft = Offset(left, top),
                    size = Size(frameWidth, frameHeight),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Bold corner brackets
                val bracketLen = 28.dp.toPx()
                val bracketStroke = 4.5.dp.toPx()
                // Top-left
                drawLine(frameColor, Offset(left, top + bracketLen), Offset(left, top + 8.dp.toPx()), bracketStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left + 8.dp.toPx(), top), Offset(left + bracketLen, top), bracketStroke, StrokeCap.Round)
                // Top-right
                drawLine(frameColor, Offset(left + frameWidth - bracketLen, top), Offset(left + frameWidth - 8.dp.toPx(), top), bracketStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left + frameWidth, top + 8.dp.toPx()), Offset(left + frameWidth, top + bracketLen), bracketStroke, StrokeCap.Round)
                // Bottom-left
                drawLine(frameColor, Offset(left, top + frameHeight - bracketLen), Offset(left, top + frameHeight - 8.dp.toPx()), bracketStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left + 8.dp.toPx(), top + frameHeight), Offset(left + bracketLen, top + frameHeight), bracketStroke, StrokeCap.Round)
                // Bottom-right
                drawLine(frameColor, Offset(left + frameWidth - bracketLen, top + frameHeight), Offset(left + frameWidth - 8.dp.toPx(), top + frameHeight), bracketStroke, StrokeCap.Round)
                drawLine(frameColor, Offset(left + frameWidth, top + frameHeight - bracketLen), Offset(left + frameWidth, top + frameHeight - 8.dp.toPx()), bracketStroke, StrokeCap.Round)

                // Animated scan line inside card frame while scanning
                if (!showSuccessTick) {
                    val beamY = top + frameHeight * scanLineFraction
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                frameColor.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(left + 12.dp.toPx(), beamY),
                        end = Offset(left + frameWidth - 12.dp.toPx(), beamY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }

            // Animated Green Tick Overlay after Auto-Capture
            AnimatedVisibility(
                visible = showSuccessTick,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Surface(
                        color = SuccessGreen,
                        shape = CircleShape,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .size(92.dp)
                            .scale(animatedTickScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "CNIC captured successfully",
                                tint = Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "CNIC $sideLabel Scanned!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Clean Top Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("cnic_camera_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = ZulvazaOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CNIC $sideLabel • Auto-Scan",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        boundCamera?.cameraControl?.enableTorch(torchEnabled)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("cnic_camera_torch")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle flash",
                        tint = if (torchEnabled) ZulvazaOrange else Color.White
                    )
                }
            }

            // Bottom Smart Detection Status Card (NO Manual Shutter Button!)
            if (!showSuccessTick) {
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 26.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (cameraError != null) {
                            Text(
                                text = cameraError!!,
                                color = Color(0xFFFCA5A5),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = if (cardDetectedState) SuccessGreen else ZulvazaOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = liveHint,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { detectionProgress },
                            color = if (cardDetectedState) SuccessGreen else ZulvazaOrange,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera captures automatically once your CNIC $sideLabel card is clearly detected inside the frame.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private data class FrameMetrics(
    val meanLuma: Double,
    val edgeScore: Double,
    val borderContrast: Double,
    val isSteady: Boolean,
    val signature: IntArray
)

private fun evaluateCnicFrame(proxy: ImageProxy, prevSignature: IntArray?): FrameMetrics {
    val yPlane = proxy.planes[0]
    val buffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val width = proxy.width
    val height = proxy.height

    val startX = (width * 0.14).toInt()
    val endX = (width * 0.86).toInt().coerceAtLeast(startX + 16)
    val startY = (height * 0.24).toInt()
    val endY = (height * 0.76).toInt().coerceAtLeast(startY + 16)

    val gridCols = 8
    val gridRows = 5
    val signature = IntArray(gridCols * gridRows)
    var lumaSum = 0L
    var edgeSum = 0L
    var sampleCount = 0

    val stepX = ((endX - startX) / 24).coerceAtLeast(4)
    val stepY = ((endY - startY) / 16).coerceAtLeast(4)

    for (y in startY until (endY - stepY) step stepY) {
        val rowOffset = y * rowStride
        val nextRowOffset = (y + stepY) * rowStride
        for (x in startX until (endX - stepX) step stepX) {
            val idx = rowOffset + x
            val rightIdx = rowOffset + x + stepX
            val downIdx = nextRowOffset + x
            if (idx < buffer.limit() && rightIdx < buffer.limit() && downIdx < buffer.limit()) {
                val p = buffer.get(idx).toInt() and 0xFF
                val pr = buffer.get(rightIdx).toInt() and 0xFF
                val pd = buffer.get(downIdx).toInt() and 0xFF
                lumaSum += p
                edgeSum += abs(p - pr) + abs(p - pd)
                sampleCount++
            }
        }
    }

    // Measure contrast near top/bottom/left/right boundaries of card box
    var borderDiffSum = 0L
    var borderSamples = 0
    val margin = 12
    for (x in startX until endX step stepX) {
        val topOut = ((startY - margin).coerceAtLeast(0)) * rowStride + x
        val topIn = ((startY + margin).coerceAtMost(height - 1)) * rowStride + x
        val botIn = ((endY - margin).coerceAtLeast(0)) * rowStride + x
        val botOut = ((endY + margin).coerceAtMost(height - 1)) * rowStride + x
        if (topOut < buffer.limit() && topIn < buffer.limit() && botIn < buffer.limit() && botOut < buffer.limit()) {
            borderDiffSum += abs((buffer.get(topOut).toInt() and 0xFF) - (buffer.get(topIn).toInt() and 0xFF))
            borderDiffSum += abs((buffer.get(botIn).toInt() and 0xFF) - (buffer.get(botOut).toInt() and 0xFF))
            borderSamples += 2
        }
    }

    var sigIdx = 0
    for (r in 0 until gridRows) {
        val y = startY + ((endY - startY) * (r + 0.5) / gridRows).toInt()
        for (c in 0 until gridCols) {
            val x = startX + ((endX - startX) * (c + 0.5) / gridCols).toInt()
            val pos = y * rowStride + x
            signature[sigIdx++] = if (pos in 0 until buffer.limit()) (buffer.get(pos).toInt() and 0xFF) else 0
        }
    }

    val isSteady = if (prevSignature == null || prevSignature.size != signature.size) {
        false
    } else {
        var diffSum = 0
        for (i in signature.indices) {
            diffSum += abs(signature[i] - prevSignature[i])
        }
        (diffSum.toDouble() / signature.size) < 11.0
    }

    val count = sampleCount.coerceAtLeast(1)
    return FrameMetrics(
        meanLuma = lumaSum.toDouble() / count,
        edgeScore = edgeSum.toDouble() / count,
        borderContrast = borderDiffSum.toDouble() / borderSamples.coerceAtLeast(1),
        isSteady = isSteady,
        signature = signature
    )
}

private fun yuvImageProxyToCroppedCardBitmap(proxy: ImageProxy): Bitmap? {
    return try {
        val raw = proxy.toBitmap()
        val rotation = proxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } else {
            raw
        }
        cropBitmapToCnicFrame(rotated)
    } catch (_: Exception) {
        null
    }
}

private fun cropBitmapToCnicFrame(rotated: Bitmap): Bitmap {
    val cardRatio = 85.6f / 54f
    val cropW = (rotated.width * 0.90f).roundToInt().coerceIn(32, rotated.width)
    val cropH = ((cropW / cardRatio) * 1.06f).roundToInt().coerceIn(32, rotated.height)
    val left = ((rotated.width - cropW) / 2f).roundToInt().coerceIn(0, (rotated.width - cropW).coerceAtLeast(0))
    val top = ((rotated.height - cropH) / 2f).roundToInt().coerceIn(0, (rotated.height - cropH).coerceAtLeast(0))
    return Bitmap.createBitmap(rotated, left, top, cropW, cropH)
}

private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
