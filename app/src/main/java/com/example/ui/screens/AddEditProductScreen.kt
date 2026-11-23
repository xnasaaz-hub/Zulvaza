package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.ui.components.ProductImageCarousel
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.StatusPill
import com.example.ui.components.VideoUploadThumbnailCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddEditProductScreen(
    editingProduct: ProductItem?,
    resumingDraft: ProductDraft?,
    sellerId: String,
    isBusy: Boolean,
    resolveImageUrl: (String) -> String?,
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

    val scope = rememberCoroutineScope()

    var showBuyerPreview by remember { mutableStateOf(false) }

    var currentDraftId by remember(editingProduct, resumingDraft) {
        mutableStateOf(resumingDraft?.id ?: "")
    }
    var name by remember(editingProduct, resumingDraft) {
        mutableStateOf(editingProduct?.name ?: resumingDraft?.name ?: "")
    }
    // Strictly digits-only PKR integer pricing
    var priceText by remember(editingProduct, resumingDraft) {
        mutableStateOf(
            (editingProduct?.price ?: resumingDraft?.price)
                ?.toInt()
                ?.toString()
                ?.filter { it.isDigit() }
                ?: ""
        )
    }
    var stockText by remember(editingProduct, resumingDraft) {
        mutableStateOf(
            (editingProduct?.stock ?: resumingDraft?.stock)
                ?.toString()
                ?.filter { it.isDigit() }
                ?: ""
        )
    }
    var description by remember(editingProduct, resumingDraft) {
        mutableStateOf(editingProduct?.description ?: resumingDraft?.description ?: "")
    }
    var imagePaths by remember(editingProduct, resumingDraft) {
        mutableStateOf(editingProduct?.imagePaths ?: resumingDraft?.imagePaths ?: emptyList())
    }
    var videoPath by remember(editingProduct, resumingDraft) {
        mutableStateOf(editingProduct?.videoPath ?: resumingDraft?.videoPath)
    }
    var attributesMap by remember(editingProduct, resumingDraft) {
        mutableStateOf(editingProduct?.attributes ?: resumingDraft?.attributes ?: emptyMap())
    }
    var specKey by remember { mutableStateOf("") }
    var specValue by remember { mutableStateOf("") }

    val nameRequester = remember { BringIntoViewRequester() }
    val priceRequester = remember { BringIntoViewRequester() }
    val stockRequester = remember { BringIntoViewRequester() }
    val descRequester = remember { BringIntoViewRequester() }
    val specKeyRequester = remember { BringIntoViewRequester() }
    val specValueRequester = remember { BringIntoViewRequester() }

    var isUploadingImage by remember { mutableStateOf(false) }
    var imageUploadProgress by remember { mutableFloatStateOf(0f) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isUploadingVideo by remember { mutableStateOf(false) }
    var videoUploadProgress by remember { mutableFloatStateOf(0f) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var autosaveLabel by remember { mutableStateOf<String?>(null) }

    // Autosave draft every 2.5s of inactivity when adding a new product
    LaunchedEffect(name, priceText, stockText, description, imagePaths, videoPath, attributesMap) {
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
            category = "General",
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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingPhotoUris = uris
        scope.launch {
            isUploadingImage = true
            try {
                uris.forEachIndexed { idx, uri ->
                    imageUploadProgress = idx / uris.size.toFloat()
                    val path = onUploadImage(uri) { p ->
                        imageUploadProgress = (idx + p) / uris.size.toFloat()
                    }
                    imagePaths = (imagePaths + path).distinct()
                    pendingPhotoUris = pendingPhotoUris.filterNot { it == uri }
                }
                onToast("${uris.size} product photo(s) uploaded", false)
            } catch (e: Exception) {
                onToast(e.message ?: "Image upload failed", true)
            } finally {
                pendingPhotoUris = emptyList()
                isUploadingImage = false
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedVideoUri = uri
        scope.launch {
            isUploadingVideo = true
            videoUploadProgress = 0f
            try {
                val path = onUploadVideo(uri) { p -> videoUploadProgress = p }
                videoPath = path
                onToast("Product video uploaded", false)
            } catch (e: Exception) {
                selectedVideoUri = null
                onToast(e.message ?: "Video upload failed", true)
            } finally {
                isUploadingVideo = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Redesigned Studio Header Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("product_studio_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = ZulvazaOrange.copy(alpha = 0.14f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = ZulvazaOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = if (editingProduct != null) "Edit Product" else "Add Product",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                editingProduct != null -> "Updates go live after quick review"
                                autosaveLabel != null -> autosaveLabel!!
                                else -> "Photos, video & pricing details"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (autosaveLabel != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showBuyerPreview = !showBuyerPreview },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (showBuyerPreview) ZulvazaOrange else MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(
                        imageVector = if (showBuyerPreview) Icons.Default.Edit else Icons.Default.Preview,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showBuyerPreview) "Edit" else "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showBuyerPreview) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProductImageCarousel(imageUrls = imagePaths.map { resolveImageUrl(it) })
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stockInt = stockText.toIntOrNull() ?: 0
                        StatusPill(
                            text = if (stockInt > 0) "In Stock ($stockInt)" else "Out of Stock",
                            bg = SuccessGreenSoft,
                            fg = SuccessGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = name.ifBlank { "Your Product Title" },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = Formatters.formatPkr(priceText.toDoubleOrNull() ?: 0.0),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.headlineSmall,
                        color = ZulvazaOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = description.ifBlank { "Your detailed buyer-facing description will appear here." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (attributesMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attributesMap.forEach { (k, v) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "$k: $v",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (onBack == null) 96.dp else 20.dp))
            return@Column
        }

        // Section 1: Media Gallery (Photos + Video)
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
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Product Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${imagePaths.size}/8 photos (Min 2) • 1 video",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (imagePaths.size >= 2) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isUploadingImage,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("add_product_pick_photos")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isUploadingImage) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { imageUploadProgress },
                        color = ZulvazaOrange,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                    )
                }

                if (imagePaths.isNotEmpty() || pendingPhotoUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        imagePaths.forEachIndexed { idx, path ->
                            Box(
                                modifier = Modifier
                                    .size(94.dp)
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
                            Box(modifier = Modifier.size(94.dp)) {
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
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isUploadingVideo) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .testTag("add_product_pick_video")
                ) {
                    VideoUploadThumbnailCard(
                        localUri = selectedVideoUri,
                        uploadedPath = videoPath,
                        progress = videoUploadProgress,
                        isUploading = isUploadingVideo
                    )
                }
            }
        }

        // Section 2: Title, Digits-Only Price & Stock, and Description
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
                    label = { Text("Product Title") },
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

        // Action Bar: Save Draft + Publish
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (editingProduct == null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val saved = onSaveDraft(
                                ProductDraft(
                                    id = currentDraftId,
                                    sellerId = sellerId,
                                    name = name,
                                    description = description,
                                    price = priceText.toDoubleOrNull(),
                                    stock = stockText.toIntOrNull(),
                                    category = "General",
                                    imagePaths = imagePaths,
                                    videoPath = videoPath,
                                    attributes = attributesMap
                                ),
                                false
                            )
                            if (saved != null) currentDraftId = saved.id
                        }
                    },
                    enabled = !isBusy && !isUploadingImage && !isUploadingVideo,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(0.42f)
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("save_draft_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Draft", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    val stock = stockText.toIntOrNull()
                    when {
                        name.isBlank() -> onToast("Enter a product name", true)
                        imagePaths.size < 2 -> onToast("Upload at least 2 product photos", true)
                        videoPath.isNullOrBlank() -> onToast("Upload 1 required product video", true)
                        price == null || price <= 0.0 -> onToast("Enter a valid PKR price", true)
                        stock == null || stock < 0 -> onToast("Enter valid stock quantity", true)
                        description.isBlank() -> onToast("Enter a product description", true)
                        else -> {
                            onSubmitProduct(
                                editingProduct,
                                name.trim(),
                                editingProduct?.category?.ifBlank { "General" } ?: "General",
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
                },
                enabled = !isBusy && !isUploadingImage && !isUploadingVideo,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(if (editingProduct == null) 0.58f else 1f)
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag("submit_product_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Publish",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
