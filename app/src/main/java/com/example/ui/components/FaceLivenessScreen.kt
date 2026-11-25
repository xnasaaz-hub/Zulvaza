package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.speech.tts.TextToSpeech
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class FaceLivenessStep(
    val title: String,
    val instruction: String,
    val speechCue: String
) {
    STRAIGHT("Straight", "Look straight into the oval frame", "Look straight at the camera"),
    LEFT("Left", "Slowly turn your head Left", "Slowly turn your head left"),
    RIGHT("Right", "Slowly turn your head Right", "Now turn your head right"),
    UP("Up", "Tilt your head slightly Up", "Look slightly up"),
    DOWN("Down", "Tilt your head slightly Down", "Look slightly down")
}

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

    var audioEnabled by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsReady = true
            }
        }
        ttsInstance = engine
        onDispose {
            runCatching { engine?.stop(); engine?.shutdown() }
        }
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    var faceInFrame by remember { mutableStateOf(false) }
    var liveHint by remember { mutableStateOf("Position your face inside the oval guide") }
    var capturedStraightBytes by remember { mutableStateOf<ByteArray?>(null) }
    var livenessCompleted by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var leftTurnYawSign by remember { mutableFloatStateOf(0f) }

    val currentStep = FaceLivenessStep.entries.getOrNull(currentStepIndex)

    LaunchedEffect(currentStepIndex, ttsReady, audioEnabled, livenessCompleted, hasCameraPermission) {
        if (hasCameraPermission && ttsReady && audioEnabled) {
            val phrase = if (livenessCompleted) {
                "Face verified successfully"
            } else {
                currentStep?.speechCue.orEmpty()
            }
            if (phrase.isNotBlank()) {
                runCatching {
                    ttsInstance?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "liveness_cue_$currentStepIndex")
                }
            }
        }
    }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.24f)
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
                Text("Front Camera Required", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Allow camera access to complete guided live face verification.",
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

        if (livenessCompleted && capturedStraightBytes != null) {
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
                            "Face Verified Successfully",
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

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "All 5 liveness checks passed (Straight, Left, Right, Up, Down).",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedStraightBytes = null
                            livenessCompleted = false
                            currentStepIndex = 0
                            leftTurnYawSign = 0f
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
                        Text("Use Face Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            var stableHits = 0
            var stepCooldownUntil = 0L

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
                                if (now - lastProcessedMs < 140L || livenessCompleted) {
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
                                val input = InputImage.fromMediaImage(mediaImage, rot)

                                detector.process(input)
                                    .addOnSuccessListener(mainExecutor) { faces ->
                                        if (livenessCompleted) return@addOnSuccessListener
                                        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                        if (face == null) {
                                            stableHits = 0
                                            faceInFrame = false
                                            liveHint = "Position your face clearly inside the oval"
                                            return@addOnSuccessListener
                                        }

                                        val box = face.boundingBox
                                        val cx = box.centerX().toFloat() / imgW.coerceAtLeast(1)
                                        val cy = box.centerY().toFloat() / imgH.coerceAtLeast(1)
                                        val sizeFrac = box.width().toFloat() / imgW.coerceAtLeast(1)

                                        val centered = cx in 0.24f..0.76f && cy in 0.20f..0.78f && sizeFrac >= 0.22f
                                        faceInFrame = centered
                                        if (!centered) {
                                            stableHits = 0
                                            liveHint = if (sizeFrac < 0.22f) {
                                                "Move slightly closer to the camera"
                                            } else {
                                                "Center your face inside the oval guide"
                                            }
                                            return@addOnSuccessListener
                                        }

                                        if (now < stepCooldownUntil) return@addOnSuccessListener

                                        val yaw = face.headEulerAngleY // left/right
                                        val pitch = face.headEulerAngleX // up/down
                                        val step = FaceLivenessStep.entries.getOrNull(currentStepIndex)

                                        val matched = when (step) {
                                            FaceLivenessStep.STRAIGHT -> {
                                                abs(yaw) <= 10f && abs(pitch) <= 10f
                                            }
                                            FaceLivenessStep.LEFT -> {
                                                abs(yaw) >= 13.5f
                                            }
                                            FaceLivenessStep.RIGHT -> {
                                                if (leftTurnYawSign > 0f) yaw <= -13.5f
                                                else if (leftTurnYawSign < 0f) yaw >= 13.5f
                                                else abs(yaw) >= 13.5f
                                            }
                                            FaceLivenessStep.UP -> {
                                                pitch >= 10f
                                            }
                                            FaceLivenessStep.DOWN -> {
                                                pitch <= -10f
                                            }
                                            null -> false
                                        }

                                        if (matched) {
                                            stableHits++
                                            liveHint = "Great! Hold steady..."
                                            val requiredHits = if (step == FaceLivenessStep.STRAIGHT) 3 else 2
                                            if (stableHits >= requiredHits) {
                                                stableHits = 0
                                                stepCooldownUntil = now + 750L
                                                if (step == FaceLivenessStep.STRAIGHT) {
                                                    captureStraightPortrait()
                                                }
                                                if (step == FaceLivenessStep.LEFT) {
                                                    leftTurnYawSign = if (yaw >= 0f) 1f else -1f
                                                }
                                                if (currentStepIndex < FaceLivenessStep.entries.lastIndex) {
                                                    currentStepIndex++
                                                    liveHint = FaceLivenessStep.entries[currentStepIndex].instruction
                                                } else {
                                                    livenessCompleted = true
                                                }
                                            }
                                        } else {
                                            stableHits = 0
                                            liveHint = when {
                                                step == FaceLivenessStep.RIGHT &&
                                                    ((leftTurnYawSign > 0f && yaw > 10f) || (leftTurnYawSign < 0f && yaw < -10f)) ->
                                                    "Wrong way — follow the → arrow"
                                                step == FaceLivenessStep.UP && pitch < -8f ->
                                                    "Wrong way — tilt UP toward the ↑ arrow"
                                                step == FaceLivenessStep.DOWN && pitch > 8f ->
                                                    "Wrong way — tilt DOWN toward the ↓ arrow"
                                                else -> step?.instruction ?: "Hold steady"
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

        // Animated directional pulse & glow for arrow HUD
        val arrowInfinite = rememberInfiniteTransition(label = "livenessArrowPulse")
        val arrowShiftFraction by arrowInfinite.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowShift"
        )
        val arrowAlpha by arrowInfinite.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowAlpha"
        )

        // Translucent Mask + Center Oval Guide + Directional Target Arc
        val guideColor = if (faceInFrame) SuccessGreen else ZulvazaOrange
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ovalW = size.width * 0.70f
            val ovalH = ovalW * 1.32f
            val left = (size.width - ovalW) / 2f
            val top = (size.height - ovalH) / 2f - 18.dp.toPx()
            val ovalRect = Rect(left, top, left + ovalW, top + ovalH)
            val ovalPath = Path().apply { addOval(ovalRect) }

            clipPath(ovalPath, clipOp = ClipOp.Difference) {
                drawRect(Color(0xFF060B14).copy(alpha = 0.80f))
            }

            // Base oval guide
            drawOval(
                color = guideColor.copy(alpha = 0.45f),
                topLeft = Offset(left, top),
                size = Size(ovalW, ovalH),
                style = Stroke(width = 3.dp.toPx())
            )

            // Directional glowing arc cue on the active movement side
            val arcPad = 8.dp.toPx()
            val arcTopLeft = Offset(left - arcPad, top - arcPad)
            val arcSize = Size(ovalW + arcPad * 2f, ovalH + arcPad * 2f)
            val activeAccent = if (faceInFrame) ZulvazaOrange else Color.White.copy(alpha = 0.75f)
            when (currentStep) {
                FaceLivenessStep.STRAIGHT -> {
                    drawOval(
                        color = if (faceInFrame) SuccessGreen else Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = Size(ovalW, ovalH),
                        style = Stroke(width = 4.5.dp.toPx())
                    )
                }
                FaceLivenessStep.LEFT -> {
                    drawArc(
                        color = activeAccent.copy(alpha = arrowAlpha),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                FaceLivenessStep.RIGHT -> {
                    drawArc(
                        color = activeAccent.copy(alpha = arrowAlpha),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                FaceLivenessStep.UP -> {
                    drawArc(
                        color = activeAccent.copy(alpha = arrowAlpha),
                        startAngle = 230f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                FaceLivenessStep.DOWN -> {
                    drawArc(
                        color = activeAccent.copy(alpha = arrowAlpha),
                        startAngle = 50f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                null -> Unit
            }
        }

        // Clean Top Bar: Close + Step Counter Pill + Voice Cue Toggle (No 5-word pill strip)
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
                Icon(Icons.Default.Close, contentDescription = "Close face verification", tint = Color.White)
            }

            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.82f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Step ${currentStepIndex + 1} of 5",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(
                onClick = { audioEnabled = !audioEnabled },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = if (audioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Toggle voice guide",
                    tint = if (audioEnabled) ZulvazaOrange else Color.White
                )
            }
        }

        // Animated Directional Arrow Cue System
        if (currentStep != null) {
            val shiftPx = (arrowShiftFraction * 18f).roundToInt()
            when (currentStep) {
                FaceLivenessStep.STRAIGHT -> {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.78f),
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, if (faceInFrame) SuccessGreen else ZulvazaOrange),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 82.dp)
                            .alpha(arrowAlpha)
                            .testTag("liveness_arrow_straight")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Look straight ahead",
                                tint = if (faceInFrame) SuccessGreen else ZulvazaOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LOOK STRAIGHT",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                FaceLivenessStep.LEFT -> {
                    LivenessDirectionalArrowBadge(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "TURN LEFT",
                        alpha = arrowAlpha,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .offset { IntOffset(x = -shiftPx, y = -28) }
                            .testTag("liveness_arrow_left")
                    )
                }
                FaceLivenessStep.RIGHT -> {
                    LivenessDirectionalArrowBadge(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        label = "TURN RIGHT",
                        alpha = arrowAlpha,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .offset { IntOffset(x = shiftPx, y = -28) }
                            .testTag("liveness_arrow_right")
                    )
                }
                FaceLivenessStep.UP -> {
                    LivenessDirectionalArrowBadge(
                        icon = Icons.Default.ArrowUpward,
                        label = "LOOK UP",
                        alpha = arrowAlpha,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 82.dp)
                            .offset { IntOffset(x = 0, y = -shiftPx) }
                            .testTag("liveness_arrow_up")
                    )
                }
                FaceLivenessStep.DOWN -> {
                    LivenessDirectionalArrowBadge(
                        icon = Icons.Default.ArrowDownward,
                        label = "LOOK DOWN",
                        alpha = arrowAlpha,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 166.dp)
                            .offset { IntOffset(x = 0, y = shiftPx) }
                            .testTag("liveness_arrow_down")
                    )
                }
            }
        }

        // Bottom Live Prompt Card + Step Dots
        Surface(
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (cameraError != null) {
                    Text(cameraError!!, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = currentStep?.instruction ?: "Verifying...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = liveHint,
                    color = if (faceInFrame) SuccessGreen else Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(FaceLivenessStep.entries.size) { idx ->
                        val activeColor = when {
                            idx < currentStepIndex -> SuccessGreen
                            idx == currentStepIndex -> ZulvazaOrange
                            else -> Color.White.copy(alpha = 0.20f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivenessDirectionalArrowBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(alpha)
    ) {
        Surface(
            shape = CircleShape,
            color = ZulvazaOrange,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 8.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            color = Color(0xFF0F172A).copy(alpha = 0.9f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.7f))
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun faceImageProxyToRotatedBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rot = image.imageInfo.rotationDegrees
    val matrix = Matrix().apply {
        if (rot != 0) postRotate(rot.toFloat())
        // Mirror horizontally to match natural front-facing selfie perspective
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
