package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerStore
import com.example.data.model.SocialPlatform
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.SocialBrandBadge
import com.example.ui.components.copyToClipboard
import com.example.ui.components.openExternalUrl
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoreProfileScreen(
    store: SellerStore?,
    products: List<ProductItem>,
    resolveStoreMedia: (String?) -> String?,
    onEditStore: () -> Unit,
    onToast: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val activeSocials = store?.socialProfiles?.filter { it.active && it.url.isNotBlank() }.orEmpty()
    val approvedCount = products.count { it.status == ProductStatus.APPROVED }
    val totalSales = products.sumOf { it.sales }
    val slugHandle = store?.slug?.takeIf { it.isNotBlank() }
    val storefrontUrl = if (slugHandle != null) {
        "https://zulvaza.com/store/$slugHandle"
    } else {
        "https://zulvaza.com"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Cover Banner + Overlapping Logo
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.linearGradient(listOf(ZulvazaNavy, ZulvazaOrange))
                    )
            ) {
                val bannerUrl = resolveStoreMedia(store?.bannerPath)
                if (!bannerUrl.isNullOrBlank()) {
                    SignedRemoteImage(
                        url = bannerUrl,
                        contentDescription = "Store cover banner",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_seller_hero),
                        contentDescription = "Default store banner",
                        contentScale = ContentScale.Crop,
                        alpha = 0.42f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Round Logo with Gradient Ring
            Box(
                modifier = Modifier
                    .padding(start = 20.dp)
                    .offset(y = 132.dp)
                    .size(94.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(listOf(ZulvazaOrange, ZulvazaNavy, ZulvazaOrange))
                    )
                    .padding(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val logoUrl = resolveStoreMedia(store?.logoPath)
                    if (!logoUrl.isNullOrBlank()) {
                        SignedRemoteImage(
                            url = logoUrl,
                            contentDescription = "Store logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = ZulvazaOrange,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = 48.dp)
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                store?.shopName?.takeIf { it.isNotBlank() } ?: "ZULVAZA Storefront"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Shop ${store?.shopName?.takeIf { it.isNotBlank() } ?: "our store"} on ZULVAZA: $storefrontUrl"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Storefront"))
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("share_store_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Storefront", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onEditStore,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("edit_store_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Store", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = store?.shopName?.takeIf { it.isNotBlank() } ?: "Set Up Your Storefront",
                style = MaterialTheme.typography.headlineMedium
            )

            if (slugHandle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = ZulvazaOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            copyToClipboard(context, "Storefront URL", storefrontUrl)
                            onToast("Copied $storefrontUrl", false)
                        }
                        .testTag("copy_store_handle_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "zulvaza.com/@$slugHandle",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy storefront URL",
                            tint = ZulvazaOrange,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Row (Studio Tabular Numbers)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StoreStatColumn(products.size.toString(), "Listings")
                    StoreStatColumn(approvedCount.toString(), "Live")
                    StoreStatColumn(totalSales.toString(), "Sales")
                }
            }

            // Store stats card above bio & socials

            if (!store?.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = store.bio,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (activeSocials.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeSocials.forEach { sp ->
                        SocialBrandBadge(
                            platform = sp.platform,
                            label = SocialPlatform.fromKey(sp.platform).displayName,
                            onClick = { openExternalUrl(context, sp.url) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About Store", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = store?.introduction?.takeIf { it.isNotBlank() }
                            ?: "Tap Edit Store to customize your cover banner, logo, and bio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StoreStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditStoreSheetContent(
    initialStore: SellerStore?,
    isBusy: Boolean,
    resolveStoreMedia: (String?) -> String?,
    onUploadStoreMediaBytes: suspend (ByteArray, String, String, String?, (Float) -> Unit) -> String,
    onDeleteStoreMedia: (Boolean) -> Unit,
    onSaveStore: (SellerStore, () -> Unit) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var shopName by remember(initialStore) { mutableStateOf(initialStore?.shopName ?: "") }
    var slug by remember(initialStore) { mutableStateOf(initialStore?.slug ?: "") }
    var bio by remember(initialStore) { mutableStateOf(initialStore?.bio ?: "") }
    var introduction by remember(initialStore) { mutableStateOf(initialStore?.introduction ?: "") }
    var logoPath by remember(initialStore) { mutableStateOf(initialStore?.logoPath) }
    var bannerPath by remember(initialStore) { mutableStateOf(initialStore?.bannerPath) }
    val preservedSocialProfiles = initialStore?.socialProfiles ?: emptyList()

    var uploadingLogo by remember { mutableStateOf(false) }
    var uploadingBanner by remember { mutableStateOf(false) }

    // Pair(isLogo, mediaUrl) for preview dialog with Delete & Change buttons
    var previewMediaTarget by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // Pair(isLogo, pickedUri) for interactive Crop & Edit modal
    var cropMediaTarget by remember { mutableStateOf<Pair<Boolean, Uri>?>(null) }

    val nameRequester = remember { BringIntoViewRequester() }
    val slugRequester = remember { BringIntoViewRequester() }
    val bioRequester = remember { BringIntoViewRequester() }
    val introRequester = remember { BringIntoViewRequester() }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropMediaTarget = true to uri
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropMediaTarget = false to uri
        }
    }

    // Interactive Crop & Edit Modal before uploading logo or banner
    if (cropMediaTarget != null) {
        val (isLogo, pickedUri) = cropMediaTarget!!
        com.example.ui.components.ImageCropDialog(
            imageUri = pickedUri,
            isCircularLogo = isLogo,
            title = if (isLogo) "Crop & Adjust Store Logo" else "Crop & Adjust Cover Banner",
            onCropped = { croppedBytes ->
                cropMediaTarget = null
                scope.launch {
                    if (isLogo) uploadingLogo = true else uploadingBanner = true
                    try {
                        val uploadedPath = onUploadStoreMediaBytes(
                            croppedBytes,
                            "jpg",
                            "image/jpeg",
                            null
                        ) {}
                        if (isLogo) {
                            logoPath = uploadedPath
                            onToast("Store logo cropped & uploaded", false)
                        } else {
                            bannerPath = uploadedPath
                            onToast("Store banner cropped & uploaded", false)
                        }
                    } catch (e: Exception) {
                        onToast(e.message ?: "Upload failed", true)
                    } finally {
                        uploadingLogo = false
                        uploadingBanner = false
                    }
                }
            },
            onCancel = { cropMediaTarget = null }
        )
    }

    // Preview Modal when tapping existing Logo or Banner (with Delete + Change buttons)
    if (previewMediaTarget != null) {
        val (isLogo, mediaUrl) = previewMediaTarget!!
        Dialog(
            onDismissRequest = { previewMediaTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLogo) "Store Logo Preview" else "Cover Banner Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { previewMediaTarget = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SignedRemoteImage(
                        url = mediaUrl,
                        contentDescription = if (isLogo) "Store Logo" else "Store Banner",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (isLogo) 1f else 16f / 9f)
                            .clip(if (isLogo) CircleShape else RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E293B))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                previewMediaTarget = null
                                if (isLogo) {
                                    logoPath = null
                                } else {
                                    bannerPath = null
                                }
                                onDeleteStoreMedia(isLogo)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFF87171)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag(if (isLogo) "delete_store_logo_button" else "delete_store_banner_button")
                        ) {
                            Text(
                                text = "Delete",
                                color = Color(0xFFF87171),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                previewMediaTarget = null
                                if (isLogo) {
                                    logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag(if (isLogo) "change_store_logo_button" else "change_store_banner_button")
                        ) {
                            Text(
                                text = "Change",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    val bannerUrl = resolveStoreMedia(bannerPath)
    val logoUrl = resolveStoreMedia(logoPath)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Edit Store Profile", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tap Banner -> Preview (with Delete/Change) if set, or Pick + Crop if empty
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ZulvazaNavy)
                .clickable(enabled = !uploadingBanner) {
                    if (!bannerUrl.isNullOrBlank()) {
                        previewMediaTarget = false to bannerUrl
                    } else {
                        bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                .testTag("edit_store_banner_box")
        ) {
            if (!bannerUrl.isNullOrBlank()) {
                SignedRemoteImage(url = bannerUrl, contentDescription = "Cover Banner", modifier = Modifier.fillMaxSize())
            }
            Surface(
                color = Color.Black.copy(alpha = 0.56f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uploadingBanner) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            uploadingBanner -> "Uploading banner..."
                            !bannerUrl.isNullOrBlank() -> "Tap banner to preview, delete or change"
                            else -> "Upload & crop cover banner"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tap Logo Circle -> Preview (with Delete/Change) if set, or Pick + Crop if empty
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, ZulvazaOrange, CircleShape)
                    .clickable(enabled = !uploadingLogo) {
                        if (!logoUrl.isNullOrBlank()) {
                            previewMediaTarget = true to logoUrl
                        } else {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    .testTag("edit_store_logo_circle"),
                contentAlignment = Alignment.Center
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    SignedRemoteImage(url = logoUrl, contentDescription = "Store Logo", modifier = Modifier.fillMaxSize())
                }
                if (uploadingLogo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                } else if (logoUrl.isNullOrBlank()) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Upload logo", tint = ZulvazaOrange)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Store Logo", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (!logoUrl.isNullOrBlank()) {
                        "Tap logo to preview, delete, or change & crop"
                    } else {
                        "Tap circle to pick & crop square store logo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = {
                        shopName = it
                        if (slug.isBlank()) slug = Formatters.slugify(it)
                    },
                    label = { Text("Store Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(nameRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { delay(180); nameRequester.bringIntoView() } }
                        .testTag("store_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                    label = { Text("Store @handle (slug)") },
                    prefix = { Text("@") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(slugRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { delay(180); slugRequester.bringIntoView() } }
                        .testTag("store_slug_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Short Bio") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bioRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { delay(180); bioRequester.bringIntoView() } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = introduction,
                    onValueChange = { introduction = it },
                    label = { Text("About / Store Description") },
                    minLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(introRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { delay(180); introRequester.bringIntoView() } }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                when {
                    shopName.isBlank() -> onToast("Enter your store name", true)
                    slug.isBlank() -> onToast("Enter a store @handle", true)
                    else -> {
                        onSaveStore(
                            SellerStore(
                                sellerId = initialStore?.sellerId ?: "",
                                shopName = shopName.trim(),
                                slug = slug.trim(),
                                bio = bio.trim(),
                                introduction = introduction.trim(),
                                logoPath = logoPath,
                                bannerPath = bannerPath,
                                socialProfiles = preservedSocialProfiles
                            ),
                            onDismiss
                        )
                    }
                }
            },
            enabled = !isBusy && !uploadingLogo && !uploadingBanner,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .testTag("save_store_button")
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(
                    "Save Store Profile",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
