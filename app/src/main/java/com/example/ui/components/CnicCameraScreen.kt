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
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CnicCaptureResult
import com.example.data.model.CnicDualScanResult
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
import kotlin.math.max
import kotlin.math.roundToInt

private enum class DualScanPhase {
    SCAN_FRONT,
    FLIP_ANIMATION,
    SCAN_BACK,
    REVIEW
}

@Composable
fun CnicDualScannerScreen(
    initialFront: CnicCaptureResult? = null,
    initialBack: CnicCaptureResult? = null,
    startOnSide: String? = null, // null = normal flow, "Front" or "Back" = single rescan
    onCompleted: (CnicDualScanResult) -> Unit,
    onClose: () -> Unit
) {
    var frontResult by remember { mutableStateOf(initialFront) }
    var backResult by remember { mutableStateOf(initialBack) }
    var phase by remember {
        mutableStateOf(
            when {
                startOnSide.equals("Back", ignoreCase = true) -> DualScanPhase.SCAN_BACK
                startOnSide.equals("Front", ignoreCase = true) -> DualScanPhase.SCAN_FRONT
                initialFront != null && initialBack != null -> DualScanPhase.REVIEW
                else -> DualScanPhase.SCAN_FRONT
            }
        )
    }

    BackHandler {
        when (phase) {
            DualScanPhase.REVIEW -> onClose()
            else -> {
                if (frontResult != null && backResult != null) {
                    phase = DualScanPhase.REVIEW
                } else {
                    onClose()
                }
            }
        }
    }

    when (phase) {
        DualScanPhase.SCAN_FRONT -> {
            key("scan_front") {
                CnicLiveCameraOverlayScreen(
                    sideLabel = "Front",
                    stepBadge = "Step 1 of 2 • Front Side",
                    onCaptured = { captured ->
                        frontResult = captured
                        phase = if (backResult != null && startOnSide != null) {
                            DualScanPhase.REVIEW
                        } else {
                            DualScanPhase.FLIP_ANIMATION
                        }
                    },
                    onClose = {
                        if (frontResult != null && backResult != null) {
                            phase = DualScanPhase.REVIEW
                        } else {
                            onClose()
                        }
                    }
                )
            }
        }

        DualScanPhase.FLIP_ANIMATION -> {
            CnicFlipTransitionScreen(
                frontResult = frontResult,
                onFinished = { phase = DualScanPhase.SCAN_BACK },
                onClose = onClose
            )
        }

        DualScanPhase.SCAN_BACK -> {
            key("scan_back") {
                CnicLiveCameraOverlayScreen(
                    sideLabel = "Back",
                    stepBadge = "Step 2 of 2 • Back Side",
                    onCaptured = { captured ->
                        backResult = captured
                        phase = DualScanPhase.REVIEW
                    },
                    onClose = {
                        if (frontResult != null && backResult != null) {
                            phase = DualScanPhase.REVIEW
                        } else {
                            onClose()
                        }
                    }
                )
            }
        }

        DualScanPhase.REVIEW -> {
            val front = frontResult
            val back = backResult
            if (front != null && back != null) {
                CnicDualReviewScreen(
                    front = front,
                    back = back,
                    onRescanFront = { phase = DualScanPhase.SCAN_FRONT },
                    onRescanBack = { phase = DualScanPhase.SCAN_BACK },
                    onUseNow = { onCompleted(CnicDualScanResult(front = front, back = back)) },
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun CnicFlipTransitionScreen(
    frontResult: CnicCaptureResult?,
    onFinished: () -> Unit,
    onClose: () -> Unit
) {
    var flipStarted by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    val rotationYDeg by animateFloatAsState(
        targetValue = if (flipStarted) 180f else 0f,
        animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        label = "cnicCardFlipRotation"
    )

    val frontBitmap = remember(frontResult) {
        frontResult?.jpegBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    LaunchedEffect(Unit) {
        delay(260)
        flipStarted = true
        delay(1450)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060B14))
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = SuccessGreen.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Front Scanned • Flip Card to Back",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 3D Animated Flipping Card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(85.6f / 54f)
                    .graphicsLayer {
                        rotationY = rotationYDeg
                        cameraDistance = 12f * density
                    }
            ) {
                if (rotationYDeg <= 90f) {
                    // Front Face
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(2.5.dp, SuccessGreen),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (frontBitmap != null) {
                                Image(
                                    bitmap = frontBitmap.asImageBitmap(),
                                    contentDescription = "Scanned CNIC front",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = "CNIC Front Captured",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Back Face (counter-rotated 180° so content isn't mirrored)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(2.5.dp, ZulvazaOrange),
                        color = Color(0xFF131D33),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CNIC BACK SIDE",
                                    color = ZulvazaOrange,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = null,
                                    tint = ZulvazaOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "QR / Address & Signature Side",
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                    modifier = Modifier.size(34.dp)
                                ) {}
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Turn your CNIC to the Back side",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Preparing camera to auto-scan and crop the back side...",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CnicDualReviewScreen(
    front: CnicCaptureResult,
    back: CnicCaptureResult,
    onRescanFront: () -> Unit,
    onRescanBack: () -> Unit,
    onUseNow: () -> Unit,
    onClose: () -> Unit
) {
    val frontBitmap = remember(front.jpegBytes) {
        BitmapFactory.decodeByteArray(front.jpegBytes, 0, front.jpegBytes.size)
    }
    val backBitmap = remember(back.jpegBytes) {
        BitmapFactory.decodeByteArray(back.jpegBytes, 0, back.jpegBytes.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060B14))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close review", tint = Color.White)
            }

            Surface(
                color = SuccessGreen.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Both Sides Cropped Cleanly",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Review Scanned CNIC",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Only the CNIC card area inside the frame was captured. Tap Use Now to continue with auto-filled details.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodySmall
            )

            CnicReviewCardTile(
                title = "CNIC Front Side",
                bitmap = frontBitmap,
                onRescan = onRescanFront,
                rescanTag = "rescan_cnic_front_button"
            )

            CnicReviewCardTile(
                title = "CNIC Back Side",
                bitmap = backBitmap,
                onRescan = onRescanBack,
                rescanTag = "rescan_cnic_back_button"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onUseNow,
            colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
                .testTag("cnic_use_now_button")
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Use Now",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CnicReviewCardTile(
    title: String,
    bitmap: Bitmap?,
    onRescan: () -> Unit,
    rescanTag: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onRescan,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .testTag(rescanTag)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rescan", color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(85.6f / 54f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, SuccessGreen.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun CnicLiveCameraOverlayScreen(
    sideLabel: String, // "Front" or "Back"
    stepBadge: String = "CNIC $sideLabel • Auto-Scan",
    onCaptured: (CnicCaptureResult) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val topOffsetPx = with(LocalDensity.current) { 18.dp.toPx() }

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
    var ocrReadyState by remember { mutableStateOf(false) }
    var steadyState by remember { mutableStateOf(false) }
    var liveHint by remember { mutableStateOf("Place your CNIC $sideLabel card inside the frame") }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

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
        delay(620)
        onCaptured(result)
    }

    fun triggerAutoCapture() {
        val capture = imageCapture ?: return
        if (isCapturing || showSuccessTick) return
        isCapturing = true
        val vpW = previewSize.width
        val vpH = previewSize.height
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
                    // Crop strictly to the exact pixels inside the green CNIC guide box
                    val cardBitmap = cropBitmapToCnicFrame(
                        rotated = rotated,
                        previewWidthPx = vpW,
                        previewHeightPx = vpH,
                        topOffsetPx = topOffsetPx
                    )
                    val ocrFinalText = try {
                        val task = textRecognizer.process(InputImage.fromBitmap(cardBitmap, 0))
                        Tasks.await(task, 2500, TimeUnit.MILLISECONDS).text
                    } catch (_: Exception) {
                        ""
                    }
                    val baos = ByteArrayOutputStream()
                    cardBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
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
            .onSizeChanged { previewSize = it }
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
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(lifecycleOwner, sideLabel) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val mainExecutor = ContextCompat.getMainExecutor(context)
                var prevSignature: IntArray? = null
                var confirmedCardHits = 0
                var lastAnalyzedMs = 0L

                val ratioSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setResolutionSelector(ratioSelector)
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        val capture = ImageCapture.Builder()
                            .setResolutionSelector(ratioSelector)
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(ratioSelector)
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
                                            val analysisBitmap = yuvImageProxyToCroppedCardBitmap(
                                                proxy = proxy,
                                                previewWidthPx = previewSize.width,
                                                previewHeightPx = previewSize.height,
                                                topOffsetPx = topOffsetPx
                                            )
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
                                                ocrReadyState = false
                                                steadyState = false
                                                detectionProgress = 0.05f
                                                liveHint = "Too dark — turn on flash or move to brighter light"
                                            }
                                            metrics.edgeScore < 7.5 || metrics.borderContrast < 7.0 -> {
                                                confirmedCardHits = 0
                                                cardDetectedState = false
                                                ocrReadyState = false
                                                steadyState = false
                                                detectionProgress = 0.15f
                                                liveHint = "No card detected — align CNIC $sideLabel inside the box"
                                            }
                                            !ocrPlausible -> {
                                                confirmedCardHits = (confirmedCardHits - 1).coerceAtLeast(0)
                                                cardDetectedState = true
                                                ocrReadyState = false
                                                steadyState = metrics.isSteady
                                                detectionProgress = 0.35f
                                                liveHint = if (detectedTextLines > 0) {
                                                    "Reading CNIC $sideLabel text — move slightly closer & steady"
                                                } else {
                                                    "Card edges seen — make sure CNIC $sideLabel text is clear"
                                                }
                                            }
                                            !metrics.isSteady -> {
                                                cardDetectedState = true
                                                ocrReadyState = true
                                                steadyState = false
                                                detectionProgress = 0.68f
                                                liveHint = "CNIC $sideLabel detected! Hold steady..."
                                            }
                                            else -> {
                                                confirmedCardHits++
                                                cardDetectedState = true
                                                ocrReadyState = true
                                                steadyState = true
                                                detectionProgress = (0.55f + confirmedCardHits * 0.225f).coerceIn(0f, 1f)
                                                liveHint = "CNIC $sideLabel verified — cropping card..."
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

            // Darkened mask + 85.6:54 ID Card frame + alignment guide + scanning beam
            val frameColor = when {
                showSuccessTick || (cardDetectedState && ocrReadyState) -> SuccessGreen
                else -> ZulvazaOrange
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cardRatio = 85.6f / 54f
                val frameWidth = size.width * 0.88f
                val frameHeight = frameWidth / cardRatio
                val left = (size.width - frameWidth) / 2f
                val top = (size.height - frameHeight) / 2f - topOffsetPx
                val rect = Rect(left, top, left + frameWidth, top + frameHeight)
                val roundRect = RoundRect(rect, CornerRadius(18.dp.toPx(), 18.dp.toPx()))
                val cutPath = Path().apply { addRoundRect(roundRect) }

                clipPath(cutPath, clipOp = ClipOp.Difference) {
                    drawRect(Color(0xFF060B14).copy(alpha = 0.82f))
                }

                // Border outline matching exact crop bounds
                drawRoundRect(
                    color = frameColor.copy(alpha = 0.60f),
                    topLeft = Offset(left, top),
                    size = Size(frameWidth, frameHeight),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Subtle ID layout alignment guides inside cutout
                if (!showSuccessTick) {
                    val guideStroke = 1.2.dp.toPx()
                    val guideCol = Color.White.copy(alpha = 0.20f)
                    if (sideLabel.equals("Front", ignoreCase = true)) {
                        // Portrait box on left side + chip & detail guide lines
                        val photoW = frameWidth * 0.22f
                        val photoH = frameHeight * 0.44f
                        drawRoundRect(
                            color = guideCol,
                            topLeft = Offset(left + frameWidth * 0.06f, top + frameHeight * 0.26f),
                            size = Size(photoW, photoH),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = guideStroke)
                        )
                    } else {
                        // QR box watermark on right for Back side
                        val qrSide = frameHeight * 0.34f
                        drawRoundRect(
                            color = guideCol,
                            topLeft = Offset(left + frameWidth - qrSide - frameWidth * 0.07f, top + frameHeight * 0.50f),
                            size = Size(qrSide, qrSide),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = guideStroke)
                        )
                    }
                }

                // Bold corner brackets
                val bracketLen = 30.dp.toPx()
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

                if (!showSuccessTick) {
                    val beamY = top + frameHeight * scanLineFraction
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                frameColor.copy(alpha = 0.92f),
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
                        color = Color.Black.copy(alpha = 0.85f),
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

            // Top Glassmorphic Controls + Segmented Front/Back Stepper
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        color = Color(0xFF0F172A).copy(alpha = 0.84f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                                text = stepBadge,
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

                Spacer(modifier = Modifier.height(10.dp))

                // 2-Side Progress Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBack = sideLabel.equals("Back", ignoreCase = true)
                    CnicSideDotPill("1. Front", active = !isBack, done = isBack)
                    CnicSideDotPill("2. Back", active = isBack, done = false)
                }
            }

            // Bottom Smart Detection Status Card (3-Pill Readiness + Live Hint)
            if (!showSuccessTick) {
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CnicReadinessChip("Card", cardDetectedState, Modifier.weight(1f))
                            CnicReadinessChip("OCR Text", ocrReadyState, Modifier.weight(1f))
                            CnicReadinessChip("Steady", steadyState, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = if (cardDetectedState && ocrReadyState) SuccessGreen else ZulvazaOrange,
                                modifier = Modifier.size(18.dp)
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
                            color = if (cardDetectedState && ocrReadyState) SuccessGreen else ZulvazaOrange,
                            trackColor = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Background is automatically cropped out — keep CNIC edges inside the frame.",
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CnicSideDotPill(label: String, active: Boolean, done: Boolean) {
    Surface(
        color = when {
            done -> SuccessGreen.copy(alpha = 0.24f)
            active -> ZulvazaOrange.copy(alpha = 0.26f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.dp,
            when {
                done -> SuccessGreen.copy(alpha = 0.7f)
                active -> ZulvazaOrange
                else -> Color.White.copy(alpha = 0.2f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active || done) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CnicReadinessChip(label: String, ready: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (ready) SuccessGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (ready) SuccessGreen.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = if (ready) SuccessGreen else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (ready) Color.White else Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (ready) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
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

private fun yuvImageProxyToCroppedCardBitmap(
    proxy: ImageProxy,
    previewWidthPx: Int,
    previewHeightPx: Int,
    topOffsetPx: Float
): Bitmap? {
    return try {
        val raw = proxy.toBitmap()
        val rotation = proxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } else {
            raw
        }
        cropBitmapToCnicFrame(rotated, previewWidthPx, previewHeightPx, topOffsetPx)
    } catch (_: Exception) {
        null
    }
}

/**
 * Computes exact PreviewView FILL_CENTER inverse projection of the 85.6:54 CNIC frame
 * so ONLY the card pixels inside the green border are extracted, with zero background.
 */
private fun cropBitmapToCnicFrame(
    rotated: Bitmap,
    previewWidthPx: Int,
    previewHeightPx: Int,
    topOffsetPx: Float
): Bitmap {
    val bmpW = rotated.width
    val bmpH = rotated.height
    val cardRatio = 85.6f / 54f

    if (previewWidthPx <= 0 || previewHeightPx <= 0) {
        val cropW = (bmpW * 0.88f).roundToInt().coerceIn(32, bmpW)
        val cropH = (cropW / cardRatio).roundToInt().coerceIn(32, bmpH)
        val left = ((bmpW - cropW) / 2f).roundToInt().coerceIn(0, (bmpW - cropW).coerceAtLeast(0))
        val top = ((bmpH - cropH) / 2f).roundToInt().coerceIn(0, (bmpH - cropH).coerceAtLeast(0))
        return Bitmap.createBitmap(rotated, left, top, cropW, cropH)
    }

    val viewW = previewWidthPx.toFloat()
    val viewH = previewHeightPx.toFloat()
    val frameWidth = viewW * 0.88f
    val frameHeight = frameWidth / cardRatio
    val rectLeft = (viewW - frameWidth) / 2f
    val rectTop = (viewH - frameHeight) / 2f - topOffsetPx
    val rectRight = rectLeft + frameWidth
    val rectBottom = rectTop + frameHeight

    // FILL_CENTER scale of rotated bitmap into view bounds
    val scale = max(viewW / bmpW.toFloat(), viewH / bmpH.toFloat())
    val scaledW = bmpW * scale
    val scaledH = bmpH * scale
    val offsetX = (viewW - scaledW) / 2f
    val offsetY = (viewH - scaledH) / 2f

    val bmpLeft = ((rectLeft - offsetX) / scale).roundToInt().coerceIn(0, (bmpW - 32).coerceAtLeast(0))
    val bmpTop = ((rectTop - offsetY) / scale).roundToInt().coerceIn(0, (bmpH - 32).coerceAtLeast(0))
    val bmpRight = ((rectRight - offsetX) / scale).roundToInt().coerceIn(bmpLeft + 32, bmpW)
    val bmpBottom = ((rectBottom - offsetY) / scale).roundToInt().coerceIn(bmpTop + 32, bmpH)

    val cropW = (bmpRight - bmpLeft).coerceIn(1, bmpW - bmpLeft)
    val cropH = (bmpBottom - bmpTop).coerceIn(1, bmpH - bmpTop)
    return Bitmap.createBitmap(rotated, bmpLeft, bmpTop, cropW, cropH)
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
