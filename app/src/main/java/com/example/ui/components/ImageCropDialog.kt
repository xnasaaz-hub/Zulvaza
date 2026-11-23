package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ZulvazaOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    isCircularLogo: Boolean,
    title: String,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri) { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val targetAspect = if (isCircularLogo) 1f else 16f / 9f

    LaunchedEffect(imageUri) {
        sourceBitmap = null
        loadError = null
        val loaded = withContext(Dispatchers.IO) {
            decodeOrientedBitmap(context, imageUri, maxDim = 2048)
        }
        if (loaded == null) {
            loadError = "Unable to load selected image."
        } else {
            sourceBitmap = loaded
        }
    }

    fun clampOffset(
        candidateX: Float,
        candidateY: Float,
        scaleFactor: Float,
        bmp: Bitmap,
        vp: IntSize
    ): Pair<Float, Float> {
        if (vp.width <= 0 || vp.height <= 0) return candidateX to candidateY
        val cropW = vp.width * 0.86f
        val cropH = cropW / targetAspect
        val baseFitScale = max(cropW / bmp.width.toFloat(), cropH / bmp.height.toFloat())
        val displayedW = bmp.width * baseFitScale * scaleFactor
        val displayedH = bmp.height * baseFitScale * scaleFactor
        val maxDx = ((displayedW - cropW) / 2f).coerceAtLeast(0f)
        val maxDy = ((displayedH - cropH) / 2f).coerceAtLeast(0f)
        return candidateX.coerceIn(-maxDx, maxDx) to candidateY.coerceIn(-maxDy, maxDy)
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0B1120),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (!isProcessing) onCancel() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel crop", tint = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", color = Color.White.copy(alpha = 0.85f))
                    }
                }

                // Crop Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(sourceBitmap, viewportSize) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val bmp = sourceBitmap ?: return@detectTransformGestures
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                val (clampedX, clampedY) = clampOffset(
                                    offsetX + pan.x,
                                    offsetY + pan.y,
                                    nextZoom,
                                    bmp,
                                    viewportSize
                                )
                                zoom = nextZoom
                                offsetX = clampedX
                                offsetY = clampedY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadError != null -> {
                            Text(
                                text = loadError!!,
                                color = Color(0xFFFCA5A5),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        sourceBitmap == null -> {
                            CircularProgressIndicator(color = ZulvazaOrange)
                        }
                        else -> {
                            val bmp = sourceBitmap!!
                            val cropW = viewportSize.width * 0.86f
                            val cropH = cropW / targetAspect
                            val baseFitScale = if (cropW > 0f && cropH > 0f) {
                                max(cropW / bmp.width.toFloat(), cropH / bmp.height.toFloat())
                            } else {
                                1f
                            }
                            val effectiveScale = baseFitScale * zoom
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Crop preview",
                                contentScale = ContentScale.None,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = effectiveScale
                                        scaleY = effectiveScale
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            )

                            // Translucent Crop Mask + Outline
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val boxW = size.width * 0.86f
                                val boxH = boxW / targetAspect
                                val left = (size.width - boxW) / 2f
                                val top = (size.height - boxH) / 2f
                                val rect = Rect(left, top, left + boxW, top + boxH)

                                val cutPath = Path().apply {
                                    if (isCircularLogo) {
                                        addOval(rect)
                                    } else {
                                        addRoundRect(
                                            RoundRect(
                                                rect,
                                                CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                            )
                                        )
                                    }
                                }

                                clipPath(cutPath, clipOp = ClipOp.Difference) {
                                    drawRect(Color.Black.copy(alpha = 0.68f))
                                }

                                if (isCircularLogo) {
                                    drawOval(
                                        color = ZulvazaOrange,
                                        topLeft = Offset(left, top),
                                        size = Size(boxW, boxH),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                } else {
                                    drawRoundRect(
                                        color = ZulvazaOrange,
                                        topLeft = Offset(left, top),
                                        size = Size(boxW, boxH),
                                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Controls: Zoom Slider + Confirm
                Surface(
                    color = Color(0xFF111827),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Drag to adjust • Pinch or slide to zoom",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = String.format("%.1fx", zoom),
                                style = MaterialTheme.typography.labelMedium,
                                color = ZulvazaOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = zoom,
                            onValueChange = { nextZoom ->
                                val bmp = sourceBitmap
                                zoom = nextZoom
                                if (bmp != null) {
                                    val (cx, cy) = clampOffset(offsetX, offsetY, nextZoom, bmp, viewportSize)
                                    offsetX = cx
                                    offsetY = cy
                                }
                            },
                            valueRange = 1f..4f,
                            colors = SliderDefaults.colors(
                                thumbColor = ZulvazaOrange,
                                activeTrackColor = ZulvazaOrange
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(0.4f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Cancel", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    val bmp = sourceBitmap ?: return@Button
                                    if (isProcessing) return@Button
                                    isProcessing = true
                                    scope.launch {
                                        val croppedBytes = withContext(Dispatchers.IO) {
                                            cropBitmapToJpeg(
                                                source = bmp,
                                                viewportSize = viewportSize,
                                                targetAspect = targetAspect,
                                                zoom = zoom,
                                                offsetX = offsetX,
                                                offsetY = offsetY,
                                                maxOutWidth = if (isCircularLogo) 800 else 1600
                                            )
                                        }
                                        isProcessing = false
                                        onCropped(croppedBytes)
                                    }
                                },
                                enabled = sourceBitmap != null && !isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(0.6f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("confirm_crop_button")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Crop & Upload", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cropBitmapToJpeg(
    source: Bitmap,
    viewportSize: IntSize,
    targetAspect: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    maxOutWidth: Int
): ByteArray {
    val vpW = viewportSize.width.coerceAtLeast(100).toFloat()
    val cropW = vpW * 0.86f
    val cropH = cropW / targetAspect
    val baseFitScale = max(cropW / source.width.toFloat(), cropH / source.height.toFloat())
    val effectiveScale = (baseFitScale * zoom).coerceAtLeast(0.01f)

    val srcCropW = (cropW / effectiveScale).coerceIn(1f, source.width.toFloat())
    val srcCropH = (cropH / effectiveScale).coerceIn(1f, source.height.toFloat())

    val centerSrcX = source.width / 2f - (offsetX / effectiveScale)
    val centerSrcY = source.height / 2f - (offsetY / effectiveScale)

    val srcLeft = (centerSrcX - srcCropW / 2f).coerceIn(0f, (source.width - srcCropW).coerceAtLeast(0f))
    val srcTop = (centerSrcY - srcCropH / 2f).coerceIn(0f, (source.height - srcCropH).coerceAtLeast(0f))

    val cropIntW = srcCropW.roundToInt().coerceIn(1, source.width - srcLeft.roundToInt().coerceAtLeast(0))
    val cropIntH = srcCropH.roundToInt().coerceIn(1, source.height - srcTop.roundToInt().coerceAtLeast(0))

    val cropped = Bitmap.createBitmap(
        source,
        srcLeft.roundToInt().coerceIn(0, (source.width - 1).coerceAtLeast(0)),
        srcTop.roundToInt().coerceIn(0, (source.height - 1).coerceAtLeast(0)),
        cropIntW,
        cropIntH
    )

    val outW = min(maxOutWidth, cropped.width).coerceAtLeast(1)
    val outH = (outW / targetAspect).roundToInt().coerceAtLeast(1)
    val finalBmp = if (cropped.width != outW || cropped.height != outH) {
        Bitmap.createScaledBitmap(cropped, outW, outH, true)
    } else {
        cropped
    }

    val baos = ByteArrayOutputStream()
    finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
    return baos.toByteArray()
}

private fun decodeOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sampleSize = 1
        while (maxSide / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return raw
        }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    } catch (_: Exception) {
        null
    }
}
