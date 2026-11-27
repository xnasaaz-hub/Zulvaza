package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ZulvazaOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun computeCropFrameSize(vp: IntSize, targetAspect: Float): Pair<Float, Float> {
    if (vp.width <= 0 || vp.height <= 0) return 100f to (100f / targetAspect)
    val maxW = vp.width * 0.90f
    val maxH = vp.height * 0.78f
    var boxW = maxW
    var boxH = boxW / targetAspect
    if (boxH > maxH) {
        boxH = maxH
        boxW = boxH * targetAspect
    }
    return boxW to boxH
}

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
            decodeOrientedBitmap(context, imageUri, maxDim = 2400)
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
        val (cropW, cropH) = computeCropFrameSize(vp, targetAspect)
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
            color = Color(0xFF090E1A),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Studio Header Bar
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (!isProcessing) onCancel() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close cropper", tint = Color.White)
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset framing", tint = Color.White)
                        }
                    }
                }

                // Direct Canvas Viewport (1:1 pixel math match with exported crop)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(sourceBitmap, viewportSize) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val bmp = sourceBitmap ?: return@detectTransformGestures
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 12f)
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
                        }
                        .pointerInput(sourceBitmap, viewportSize) {
                            detectTapGestures(
                                onDoubleTap = {
                                    val bmp = sourceBitmap ?: return@detectTapGestures
                                    if (zoom > 1.25f) {
                                        zoom = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        val nextZoom = 2.4f
                                        val (clampedX, clampedY) = clampOffset(
                                            offsetX,
                                            offsetY,
                                            nextZoom,
                                            bmp,
                                            viewportSize
                                        )
                                        zoom = nextZoom
                                        offsetX = clampedX
                                        offsetY = clampedY
                                    }
                                }
                            )
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
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val vp = IntSize(size.width.roundToInt(), size.height.roundToInt())
                                val (boxW, boxH) = computeCropFrameSize(vp, targetAspect)
                                val baseFitScale = max(boxW / bmp.width.toFloat(), boxH / bmp.height.toFloat())
                                val effectiveScale = baseFitScale * zoom

                                val drawW = (bmp.width * effectiveScale).roundToInt().coerceAtLeast(1)
                                val drawH = (bmp.height * effectiveScale).roundToInt().coerceAtLeast(1)
                                val dstLeft = ((size.width - drawW) / 2f + offsetX).roundToInt()
                                val dstTop = ((size.height - drawH) / 2f + offsetY).roundToInt()

                                drawImage(
                                    image = imageBitmap,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(bmp.width, bmp.height),
                                    dstOffset = IntOffset(dstLeft, dstTop),
                                    dstSize = IntSize(drawW, drawH),
                                    filterQuality = FilterQuality.High
                                )

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
                                    drawRect(Color.Black.copy(alpha = 0.72f))
                                }

                                // Subtle Rule-of-Thirds Grid inside cutout
                                clipPath(cutPath, clipOp = ClipOp.Intersect) {
                                    val gridColor = Color.White.copy(alpha = 0.25f)
                                    val stroke1 = 1.dp.toPx()
                                    drawLine(gridColor, Offset(left + boxW / 3f, top), Offset(left + boxW / 3f, top + boxH), stroke1)
                                    drawLine(gridColor, Offset(left + 2f * boxW / 3f, top), Offset(left + 2f * boxW / 3f, top + boxH), stroke1)
                                    drawLine(gridColor, Offset(left, top + boxH / 3f), Offset(left + boxW, top + boxH / 3f), stroke1)
                                    drawLine(gridColor, Offset(left, top + 2f * boxH / 3f), Offset(left + boxW, top + 2f * boxH / 3f), stroke1)
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

                // Bottom Controls Panel
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(0.38f)
                                    .defaultMinSize(minHeight = 50.dp)
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
                                                outWidth = if (isCircularLogo) 800 else 1600,
                                                outHeight = if (isCircularLogo) 800 else 900
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
                                    .weight(0.62f)
                                    .defaultMinSize(minHeight = 50.dp)
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
    outWidth: Int,
    outHeight: Int
): ByteArray {
    val (cropW, cropH) = computeCropFrameSize(viewportSize, targetAspect)
    val baseFitScale = max(cropW / source.width.toFloat(), cropH / source.height.toFloat())
    val effectiveScale = (baseFitScale * zoom).coerceAtLeast(0.01f)

    val srcCropW = (cropW / effectiveScale).coerceIn(1f, source.width.toFloat())
    val srcCropH = (cropH / effectiveScale).coerceIn(1f, source.height.toFloat())

    val centerSrcX = source.width / 2f - (offsetX / effectiveScale)
    val centerSrcY = source.height / 2f - (offsetY / effectiveScale)

    val srcLeft = (centerSrcX - srcCropW / 2f).coerceIn(0f, (source.width - srcCropW).coerceAtLeast(0f))
    val srcTop = (centerSrcY - srcCropH / 2f).coerceIn(0f, (source.height - srcCropH).coerceAtLeast(0f))

    val leftInt = srcLeft.roundToInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val topInt = srcTop.roundToInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val cropIntW = srcCropW.roundToInt().coerceIn(1, source.width - leftInt)
    val cropIntH = srcCropH.roundToInt().coerceIn(1, source.height - topInt)

    val cropped = Bitmap.createBitmap(source, leftInt, topInt, cropIntW, cropIntH)
    val targetW = min(outWidth, max(outWidth, cropped.width)).coerceAtMost(outWidth)
    val targetH = outHeight
    val finalBmp = if (cropped.width != targetW || cropped.height != targetH) {
        Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    } else {
        cropped
    }

    val baos = ByteArrayOutputStream()
    finalBmp.compress(Bitmap.CompressFormat.JPEG, 92, baos)
    return baos.toByteArray()
}

private fun decodeOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme == "http" || scheme == "https") {
            val conn = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }
            val bytes = try {
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxSide / sampleSize > maxDim * 2) sampleSize *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            return applyExifOrientation(raw, orientation)
        }

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

        applyExifOrientation(raw, orientation)
    } catch (_: Exception) {
        null
    }
}

private fun applyExifOrientation(raw: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
