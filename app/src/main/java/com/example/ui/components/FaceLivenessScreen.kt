package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalGetImage::class)
@Composable
fun FaceLivenessScreen(
    onVerified: (ByteArray) -> Unit,
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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var faceReady by remember { mutableStateOf(false) }
    var isTooCloseOrBad by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var liveHint by remember { mutableStateOf("Center your face inside the circle") }
    var capturedStraightBytes by remember { mutableStateOf<ByteArray?>(null) }
    var verificationCompleted by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = scanProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "faceProgress"
    )

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.22f)
            .build()
        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { detector.close() }
        }
    }

    fun captureStraightPortrait() {
        val capture = imageCapture ?: return
        if (isCapturingPhoto || capturedStraightBytes != null) return
        isCapturingPhoto = true
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = faceImageProxyToRotatedBitmap(image)
                    image.close()
                    isCapturingPhoto = false
                    if (bmp != null) {
                        capturedStraightBytes = compressPortraitJpegUnder5Mb(bmp)
                        verificationCompleted = true
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturingPhoto = false
                    cameraError = exception.message ?: "Unable to capture face photo"
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
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Camera Access Required", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Allow camera access to scan and verify your face.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("Cancel", color = Color.White)
                    }
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Allow Camera")
                    }
                }
            }
            return@Box
        }

        if (verificationCompleted && capturedStraightBytes != null) {
            val previewBmp = remember(capturedStraightBytes) {
                BitmapFactory.decodeByteArray(capturedStraightBytes, 0, capturedStraightBytes!!.size)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SuccessGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Face Verified",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Verified face portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .border(4.dp, SuccessGreen, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedStraightBytes = null
                            verificationCompleted = false
                            scanProgress = 0f
                            faceReady = false
                            isTooCloseOrBad = false
                            liveHint = "Center your face inside the circle"
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(0.4f)
                            .defaultMinSize(minHeight = 52.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake", color = Color.White, maxLines = 1)
                    }
                    Button(
                        onClick = { onVerified(capturedStraightBytes!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(0.6f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("use_verified_face_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@Box
        }

        val previewView = remember { PreviewView(context) }
        val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

        DisposableEffect(lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            var lastProcessedMs = 0L
            var stableHoldCount = 0

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(analyzerExecutor) { proxy ->
                                val now = System.currentTimeMillis()
                                if (now - lastProcessedMs < 130L || verificationCompleted || isCapturingPhoto) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                lastProcessedMs = now

                                val mediaImage = proxy.image
                                if (mediaImage == null) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val rot = proxy.imageInfo.rotationDegrees
                                val imgW = if (rot == 90 || rot == 270) proxy.height else proxy.width
                                val imgH = if (rot == 90 || rot == 270) proxy.width else proxy.height
                                val meanLuma = estimateFrameLuma(proxy)
                                val input = InputImage.fromMediaImage(mediaImage, rot)

                                detector.process(input)
                                    .addOnSuccessListener(mainExecutor) { faces ->
                                        if (verificationCompleted || isCapturingPhoto) return@addOnSuccessListener
                                        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

                                        if (meanLuma < 42.0) {
                                            stableHoldCount = 0
                                            faceReady = false
                                            isTooCloseOrBad = true
                                            scanProgress = 0.05f
                                            liveHint = "Too dark — move to brighter lighting"
                                            return@addOnSuccessListener
                                        }

                                        if (face == null) {
                                            stableHoldCount = 0
                                            faceReady = false
                                            isTooCloseOrBad = false
                                            scanProgress = 0f
                                            liveHint = "Position your face inside the circle"
                                            return@addOnSuccessListener
                                        }

                                        val box = face.boundingBox
                                        val cx = box.centerX().toFloat() / imgW.coerceAtLeast(1)
                                        val cy = box.centerY().toFloat() / imgH.coerceAtLeast(1)
                                        val sizeFrac = box.width().toFloat() / imgW.coerceAtLeast(1)
                                        val yaw = abs(face.headEulerAngleY)
                                        val pitch = abs(face.headEulerAngleX)
                                        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.8f
                                        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.8f

                                        when {
                                            sizeFrac > 0.64f -> {
                                                stableHoldCount = 0
                                                faceReady = false
                                                isTooCloseOrBad = true
                                                scanProgress = 0.18f
                                                liveHint = "Too close — move back slightly"
                                            }
                                            sizeFrac < 0.22f -> {
                                                stableHoldCount = 0
                                                faceReady = false
                                                isTooCloseOrBad = false
                                                scanProgress = 0.18f
                                                liveHint = "Move slightly closer to camera"
                                            }
                                            cx !in 0.28f..0.72f || cy !in 0.24f..0.74f -> {
                                                stableHoldCount = 0
                                                faceReady = false
                                                isTooCloseOrBad = false
                                                scanProgress = 0.25f
                                                liveHint = "Center your face inside the circle"
                                            }
                                            yaw > 13f || pitch > 13f -> {
                                                stableHoldCount = 0
                                                faceReady = false
                                                isTooCloseOrBad = false
                                                scanProgress = 0.35f
                                                liveHint = "Look straight ahead at the camera"
                                            }
                                            leftEyeOpen < 0.35f || rightEyeOpen < 0.35f -> {
                                                stableHoldCount = 0
                                                faceReady = false
                                                isTooCloseOrBad = false
                                                scanProgress = 0.40f
                                                liveHint = "Keep your eyes clearly open"
                                            }
                                            else -> {
                                                stableHoldCount++
                                                faceReady = true
                                                isTooCloseOrBad = false
                                                val p = (stableHoldCount / 7f).coerceIn(0.15f, 1f)
                                                scanProgress = p
                                                liveHint = if (p >= 1f) {
                                                    "Taking photo..."
                                                } else {
                                                    "Hold steady... ${(p * 100).roundToInt()}%"
                                                }
                                                if (stableHoldCount >= 7) {
                                                    captureStraightPortrait()
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        proxy.close()
                                    }
                            }
                        }

                    imageCapture = capture
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        capture,
                        analysis
                    )
                } catch (e: Exception) {
                    cameraError = e.message ?: "Unable to start front camera"
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

        // Single Circular Aperture with Animated Sweep Progress Ring
        val ringColor = when {
            isTooCloseOrBad -> ErrorRed
            faceReady -> SuccessGreen
            else -> ZulvazaOrange
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val circleDiameter = size.width * 0.72f
            val left = (size.width - circleDiameter) / 2f
            val top = (size.height - circleDiameter) / 2f - 18.dp.toPx()
            val circleRect = Rect(left, top, left + circleDiameter, top + circleDiameter)
            val circlePath = Path().apply { addOval(circleRect) }

            clipPath(circlePath, clipOp = ClipOp.Difference) {
                drawRect(Color(0xFF060B14).copy(alpha = 0.84f))
            }

            // Track circle outline
            drawOval(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(left, top),
                size = Size(circleDiameter, circleDiameter),
                style = Stroke(width = 4.dp.toPx())
            )

            // Animated Sweep Ring
            val arcPad = 6.dp.toPx()
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(left - arcPad, top - arcPad),
                size = Size(circleDiameter + arcPad * 2f, circleDiameter + arcPad * 2f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Minimal Top Header: Close + Clean Circular Scan Title/Percentage Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("face_liveness_close")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close face scanner", tint = Color.White)
            }

            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.84f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, ringColor.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = ringColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val pct = (animatedProgress * 100).roundToInt().coerceIn(0, 100)
                    Text(
                        text = if (pct > 0) "Face Scan • $pct%" else "Face Verification",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        // Minimal Bottom Floating Status Pill (replaces 5-segment progress block)
        Surface(
            color = Color(0xFF0F172A).copy(alpha = 0.88f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, ringColor.copy(alpha = 0.55f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 34.dp)
        ) {
            Text(
                text = cameraError ?: liveHint,
                color = when {
                    cameraError != null || isTooCloseOrBad -> Color(0xFFFCA5A5)
                    faceReady -> Color(0xFF86EFAC)
                    else -> Color.White
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

private fun estimateFrameLuma(proxy: ImageProxy): Double {
    val plane = proxy.planes.firstOrNull() ?: return 120.0
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val w = proxy.width
    val h = proxy.height
    var sum = 0L
    var count = 0
    val stepX = (w / 12).coerceAtLeast(4)
    val stepY = (h / 12).coerceAtLeast(4)
    for (y in (h / 4) until (3 * h / 4) step stepY) {
        val rowOff = y * rowStride
        for (x in (w / 4) until (3 * w / 4) step stepX) {
            val idx = rowOff + x
            if (idx in 0 until buffer.limit()) {
                sum += (buffer.get(idx).toInt() and 0xFF)
                count++
            }
        }
    }
    return if (count > 0) sum.toDouble() / count else 120.0
}

private fun faceImageProxyToRotatedBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rot = image.imageInfo.rotationDegrees
    val matrix = Matrix().apply {
        if (rot != 0) postRotate(rot.toFloat())
        postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun compressPortraitJpegUnder5Mb(source: Bitmap): ByteArray {
    val maxDim = 1080
    val scale = minOf(maxDim.toFloat() / max(source.width, 1), maxDim.toFloat() / max(source.height, 1), 1f)
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        source
    }
    var quality = 88
    var bytes: ByteArray
    do {
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bytes = baos.toByteArray()
        quality -= 10
    } while (bytes.size > 4_800_000 && quality >= 40)
    return bytes
}
