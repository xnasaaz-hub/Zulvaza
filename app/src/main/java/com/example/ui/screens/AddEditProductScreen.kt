package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.ui.components.ProductVideoPlayer
import com.example.ui.components.SignedRemoteImage
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SELLER_PRODUCT_CATEGORIES = listOf(
    "Electronics",
    "Fashion",
    "Home & Living",
    "Beauty",
    "Sports",
    "Groceries",
    "Books",
    "Toys",
    "Automotive",
    "Health",
    "Other"
)

private fun normalizeSellerCategory(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed in SELLER_PRODUCT_CATEGORIES) return trimmed
    return when (trimmed) {
        "Fashion & Apparel", "Accessories", "Footwear" -> "Fashion"
        "Beauty & Personal Care" -> "Beauty"
        "Sports & Outdoors" -> "Sports"
        "Kids & Toys" -> "Toys"
        "Health & Wellness" -> "Health"
        "General" -> "Other"
        else -> SELLER_PRODUCT_CATEGORIES.first()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    editingProduct: ProductItem?,
    resumingDraft: ProductDraft?,
    sellerId: String,
    isBusy: Boolean,
    formResetKey: Int = 0,
    resolveImageUrl: (String) -> String?,
    resolveVideoUrl: (String) -> String? = { null },
    onUploadImage: suspend (Uri, (Float) -> Unit) -> String,
    onUploadVideo: suspend (Uri, (Float) -> Unit) -> String,
    onSaveDraft: suspend (ProductDraft, Boolean) -> ProductDraft?,
    onSubmitProduct: (
        editingProduct: ProductItem?,
        name: String,
        category: String,
        price: Double,
        stock: Int,
        description: String,
        imagePaths: List<String>,
        videoPath: String?,
        attributes: Map<String, String>,
        draftIdToDelete: String?
    ) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: (() -> Unit)?
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentDraftId by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(resumingDraft?.id ?: "")
    }
    var name by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(editingProduct?.name ?: resumingDraft?.name ?: "")
    }
    var category by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(
            normalizeSellerCategory(
                editingProduct?.category?.takeIf { it.isNotBlank() }
                    ?: resumingDraft?.category?.takeIf { it.isNotBlank() }
            )
        )
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    // Strictly digits-only PKR integer pricing & stock
    var priceText by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(
            (editingProduct?.price ?: resumingDraft?.price)
                ?.toInt()
                ?.toString()
                ?.filter { it.isDigit() }
                ?: ""
        )
    }
    var stockText by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(
            (editingProduct?.stock ?: resumingDraft?.stock)
                ?.toString()
                ?.filter { it.isDigit() }
                ?: ""
        )
    }
    var description by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(editingProduct?.description ?: resumingDraft?.description ?: "")
    }
    var imagePaths by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(editingProduct?.imagePaths ?: resumingDraft?.imagePaths ?: emptyList())
    }
    var videoPath by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(editingProduct?.videoPath ?: resumingDraft?.videoPath)
    }
    var attributesMap by remember(editingProduct, resumingDraft, formResetKey) {
        mutableStateOf(editingProduct?.attributes ?: resumingDraft?.attributes ?: emptyMap())
    }
    var specKey by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf("") }
    var specValue by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf("") }

    val nameRequester = remember { BringIntoViewRequester() }
    val priceRequester = remember { BringIntoViewRequester() }
    val stockRequester = remember { BringIntoViewRequester() }
    val descRequester = remember { BringIntoViewRequester() }
    val specKeyRequester = remember { BringIntoViewRequester() }
    val specValueRequester = remember { BringIntoViewRequester() }

    var isUploadingMedia by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf(false) }
    var mediaUploadProgress by remember(editingProduct, resumingDraft, formResetKey) { mutableFloatStateOf(0f) }
    var mediaUploadLabel by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf("") }
    var pendingPhotoUris by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedVideoUri by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf<Uri?>(null) }
    var showInlineVideoPlayer by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf(false) }
    var autosaveLabel by remember(editingProduct, resumingDraft, formResetKey) { mutableStateOf<String?>(null) }

    // Autosave draft every 2.5s of inactivity when adding a new product
    LaunchedEffect(name, category, priceText, stockText, description, imagePaths, videoPath, attributesMap) {
        if (editingProduct != null) return@LaunchedEffect
        if (name.isBlank() && description.isBlank() && imagePaths.isEmpty() && videoPath == null) return@LaunchedEffect
        delay(2500)
        val draft = ProductDraft(
            id = currentDraftId,
            sellerId = sellerId,
            name = name,
            description = description,
            price = priceText.toDoubleOrNull(),
            stock = stockText.toIntOrNull(),
            category = category,
            imagePaths = imagePaths,
            videoPath = videoPath,
            attributes = attributesMap
        )
        val saved = onSaveDraft(draft, true)
        if (saved != null) {
            currentDraftId = saved.id
            autosaveLabel = "Draft saved"
        }
    }

    // Unified single media picker accepting both Photos & Video together
    val unifiedMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val videoUris = uris.filter { uri ->
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val last = uri.lastPathSegment.orEmpty().lowercase()
            mime.startsWith("video/") ||
                last.endsWith(".mp4") ||
                last.endsWith(".mov") ||
                last.endsWith(".webm") ||
                last.endsWith(".mkv")
        }
        val remainingPhotoSlots = (8 - imagePaths.size).coerceAtLeast(0)
        val pickedPhotos = uris.filterNot { it in videoUris }.take(remainingPhotoSlots)
        val pickedVideo = videoUris.firstOrNull()

        if (uris.count { it !in videoUris } > remainingPhotoSlots && remainingPhotoSlots == 0 && pickedVideo == null) {
            onToast("Maximum 8 product photos allowed", true)
            return@rememberLauncherForActivityResult
        }

        val totalTasks = pickedPhotos.size + (if (pickedVideo != null) 1 else 0)
        if (totalTasks == 0) return@rememberLauncherForActivityResult

        pendingPhotoUris = pickedPhotos
        if (pickedVideo != null) {
            selectedVideoUri = pickedVideo
        }

        scope.launch {
            isUploadingMedia = true
            mediaUploadProgress = 0f
            var completedTasks = 0
            try {
                pickedPhotos.forEachIndexed { idx, uri ->
                    mediaUploadLabel = "Uploading photo ${idx + 1} of ${pickedPhotos.size}..."
                    val path = onUploadImage(uri) { p ->
                        mediaUploadProgress = (completedTasks + p) / totalTasks.toFloat()
                    }
                    imagePaths = (imagePaths + path).distinct()
                    pendingPhotoUris = pendingPhotoUris.filterNot { it == uri }
                    completedTasks++
                    mediaUploadProgress = completedTasks / totalTasks.toFloat()
                }

                if (pickedVideo != null) {
                    mediaUploadLabel = "Uploading product video..."
                    try {
                        val path = onUploadVideo(pickedVideo) { p ->
                            mediaUploadProgress = (completedTasks + p) / totalTasks.toFloat()
                        }
                        videoPath = path
                        completedTasks++
                    } catch (e: Exception) {
                        selectedVideoUri = null
                        throw e
                    }
                }

                val summaryParts = buildList {
                    if (pickedPhotos.isNotEmpty()) add("${pickedPhotos.size} photo(s)")
                    if (pickedVideo != null) add("1 video")
                }
                if (summaryParts.isNotEmpty()) {
                    onToast("Uploaded ${summaryParts.joinToString(" & ")}", false)
                }
            } catch (e: Exception) {
                onToast(e.message ?: "Media upload failed", true)
            } finally {
                pendingPhotoUris = emptyList()
                mediaUploadLabel = ""
                isUploadingMedia = false
            }
        }
    }

    fun triggerPublish() {
        val price = priceText.toDoubleOrNull()
        val stock = stockText.toIntOrNull()
        when {
            name.isBlank() -> onToast("Enter a product name", true)
            category.isBlank() -> onToast("Select a product category", true)
            imagePaths.size < 2 -> onToast("Upload at least 2 product photos", true)
            videoPath.isNullOrBlank() -> onToast("Upload 1 required product video", true)
            price == null || price <= 0.0 -> onToast("Enter a valid PKR price", true)
            stock == null || stock < 0 -> onToast("Enter valid stock quantity", true)
            description.isBlank() -> onToast("Enter a product description", true)
            else -> {
                onSubmitProduct(
                    editingProduct,
                    name.trim(),
                    category.trim(),
                    price,
                    stock,
                    description.trim(),
                    imagePaths,
                    videoPath,
                    attributesMap,
                    currentDraftId.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    fun triggerSaveDraft() {
        scope.launch {
            val saved = onSaveDraft(
                ProductDraft(
                    id = currentDraftId,
                    sellerId = sellerId,
                    name = name,
                    description = description,
                    price = priceText.toDoubleOrNull(),
                    stock = stockText.toIntOrNull(),
                    category = category,
                    imagePaths = imagePaths,
                    videoPath = videoPath,
                    attributes = attributesMap
                ),
                false
            )
            if (saved != null) {
                currentDraftId = saved.id
                autosaveLabel = "Draft saved"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Pinned Studio Top Bar with Draft & Publish actions
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("product_studio_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Column(
                            modifier = Modifier.padding(start = if (onBack == null) 6.dp else 0.dp)
                        ) {
                            Text(
                                text = if (editingProduct != null) "Edit Product" else "Add Product",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    editingProduct != null -> "Updates go live after review"
                                    autosaveLabel != null -> autosaveLabel!!
                                    else -> "Create & publish listing"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autosaveLabel != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingProduct == null) {
                            OutlinedButton(
                                onClick = ::triggerSaveDraft,
                                enabled = !isBusy && !isUploadingMedia,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 42.dp)
                                    .testTag("save_draft_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Draft", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Button(
                            onClick = ::triggerPublish,
                            enabled = !isBusy && !isUploadingMedia,
                            colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("submit_product_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Publish", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Unified Media Upload (Photos + Playable Video)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = ZulvazaOrange.copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = ZulvazaOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Product Media",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${imagePaths.size}/8 photos (min 2) • ${if (videoPath != null) "1/1 video ready" else "1 video required"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (imagePaths.size >= 2 && videoPath != null) {
                                        SuccessGreen
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Single Unified Media Upload Button for Images & Video
                    OutlinedButton(
                        onClick = {
                            unifiedMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = !isUploadingMedia,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.25.dp, ZulvazaOrange.copy(alpha = 0.55f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("add_product_pick_media")
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = ZulvazaOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUploadingMedia) mediaUploadLabel.ifBlank { "Uploading media..." } else "Add Photos & Videos",
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isUploadingMedia) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { mediaUploadProgress.coerceIn(0.05f, 1f) },
                            color = ZulvazaOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }

                    val activeVideoPlayUrl = selectedVideoUri?.toString()
                        ?: videoPath?.let { resolveVideoUrl(it) }
                    val hasAnyMedia = imagePaths.isNotEmpty() ||
                        pendingPhotoUris.isNotEmpty() ||
                        videoPath != null ||
                        selectedVideoUri != null

                    // Unified Horizontal Media Strip (Photos + Playable Video Tile)
                    if (hasAnyMedia) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            imagePaths.forEachIndexed { idx, path ->
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (idx == 0) 2.dp else 1.dp,
                                            color = if (idx == 0) ZulvazaOrange else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    SignedRemoteImage(
                                        url = resolveImageUrl(path),
                                        contentDescription = "Uploaded product photo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (idx == 0) {
                                        Surface(
                                            color = ZulvazaOrange,
                                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "Cover",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { imagePaths = imagePaths.filterNot { it == path } },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            pendingPhotoUris.forEach { uri ->
                                Box(modifier = Modifier.size(96.dp)) {
                                    SignedRemoteImage(
                                        url = uri.toString(),
                                        contentDescription = "Uploading product photo",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            if (videoPath != null || selectedVideoUri != null) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ZulvazaNavy)
                                        .border(
                                            width = if (showInlineVideoPlayer) 2.dp else 1.dp,
                                            color = if (showInlineVideoPlayer) ZulvazaOrange else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable(enabled = !activeVideoPlayUrl.isNullOrBlank()) {
                                            showInlineVideoPlayer = !showInlineVideoPlayer
                                        }
                                        .testTag("add_product_video_tile")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (videoPath == null && isUploadingMedia) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(26.dp),
                                                strokeWidth = 2.5.dp,
                                                color = Color.White
                                            )
                                        } else {
                                            Surface(
                                                shape = CircleShape,
                                                color = ZulvazaOrange,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = if (showInlineVideoPlayer) "Hide video player" else "Play product video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        color = if (videoPath != null) SuccessGreen else ZulvazaOrange,
                                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "Video",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            videoPath = null
                                            selectedVideoUri = null
                                            showInlineVideoPlayer = false
                                        },
                                        enabled = !isUploadingMedia,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove video",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = showInlineVideoPlayer && !activeVideoPlayUrl.isNullOrBlank()) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (!activeVideoPlayUrl.isNullOrBlank()) {
                                    ProductVideoPlayer(videoUrl = activeVideoPlayUrl)
                                }
                            }
                        }
                    } else if (!isUploadingMedia) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select 2+ photos & 1 short product video using the button above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 2: Product Name, Category, Digits-Only Price & Stock, Description
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Listing Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g. Multani Hand-Embroidered Khaddar Suit") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(nameRequester)
                            .onFocusEvent { if (it.isFocused) scope.launch { delay(180); nameRequester.bringIntoView() } }
                            .testTag("product_name_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = ZulvazaOrange) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("product_category_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            SELLER_PRODUCT_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cat,
                                            fontWeight = if (cat == category) FontWeight.Bold else FontWeight.Normal,
                                            color = if (cat == category) ZulvazaOrange else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        category = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { raw -> priceText = raw.filter { c -> c.isDigit() } },
                            label = { Text("Price (PKR)") },
                            prefix = { Text("Rs. ", fontWeight = FontWeight.SemiBold, color = ZulvazaNavy) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(priceRequester)
                                .onFocusEvent { if (it.isFocused) scope.launch { delay(180); priceRequester.bringIntoView() } }
                                .testTag("product_price_input")
                        )
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { raw -> stockText = raw.filter { c -> c.isDigit() } },
                            label = { Text("Stock Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(stockRequester)
                                .onFocusEvent { if (it.isFocused) scope.launch { delay(180); stockRequester.bringIntoView() } }
                                .testTag("product_stock_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Product Description") },
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(descRequester)
                            .onFocusEvent { if (it.isFocused) scope.launch { delay(180); descRequester.bringIntoView() } }
                            .testTag("product_description_input")
                    )
                }
            }

            // Section 3: Specifications & Variants
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = ZulvazaNavy, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Specifications & Variants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Optional: Brand, Size, Color, Material or Warranty",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = specKey,
                            onValueChange = { specKey = it },
                            label = { Text("Spec") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(specKeyRequester)
                                .onFocusEvent { if (it.isFocused) scope.launch { delay(180); specKeyRequester.bringIntoView() } }
                        )
                        OutlinedTextField(
                            value = specValue,
                            onValueChange = { specValue = it },
                            label = { Text("Value") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(specValueRequester)
                                .onFocusEvent { if (it.isFocused) scope.launch { delay(180); specValueRequester.bringIntoView() } }
                        )
                        Surface(
                            color = ZulvazaOrange,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (specKey.isNotBlank() && specValue.isNotBlank()) {
                                        attributesMap = attributesMap + (specKey.trim() to specValue.trim())
                                        specKey = ""
                                        specValue = ""
                                    } else {
                                        onToast("Enter both specification name and value", true)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Add specification", tint = Color.White)
                            }
                        }
                    }

                    if (attributesMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attributesMap.forEach { (k, v) ->
                                InputChip(
                                    selected = true,
                                    onClick = { attributesMap = attributesMap - k },
                                    label = { Text("$k: $v") },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove $k", modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
