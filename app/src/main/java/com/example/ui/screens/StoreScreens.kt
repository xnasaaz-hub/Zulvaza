package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.SocialPlatform
import com.example.data.model.VerificationStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ImageCropDialog
import com.example.ui.components.ProductStatusBadge
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.SocialBrandBadge
import com.example.ui.components.StoreUsernameField
import com.example.ui.components.StoreUsernameUiState
import com.example.ui.components.copyToClipboard
import com.example.ui.components.openExternalUrl
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    store: SellerStore?,
    verification: SellerVerification,
    products: List<ProductItem>,
    resolveStoreMedia: (String?) -> String?,
    resolveProductImage: (String) -> String?,
    onUploadStoreMediaBytes: suspend (ByteArray, String, String, String?, (Float) -> Unit) -> String,
    onSaveStoreMedia: (Boolean, String) -> Unit,
    onDeleteStoreMedia: (Boolean) -> Unit,
    onEditStore: () -> Unit,
    onOpenProductDetail: (ProductItem) -> Unit,
    onToast: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeSocials = store?.socialProfiles?.filter { it.active && it.url.isNotBlank() }.orEmpty()
    val approvedCount = products.count { it.status == ProductStatus.APPROVED }
    val totalSales = products.sumOf { it.sales }
    val slugHandle = store?.slug?.takeIf { it.isNotBlank() }
    val storefrontUrl = if (slugHandle != null) {
        "https://zulvaza.com/shop/$slugHandle"
    } else {
        "https://zulvaza.com"
    }

    val sellerLocationLabel = remember(verification.details) {
        listOf(verification.details.state.trim(), verification.details.city.trim())
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    var selectedSectionTab by remember { mutableIntStateOf(0) } // 0 = About, 1 = Products
    var uploadingLogo by remember { mutableStateOf(false) }
    var uploadingBanner by remember { mutableStateOf(false) }
    var mediaSheetTarget by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var cropMediaTarget by remember { mutableStateOf<Pair<Boolean, Uri>?>(null) }
    var pendingCameraIsLogo by remember { mutableStateOf<Boolean?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

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

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        val isLogoCapture = pendingCameraIsLogo
        pendingCameraUri = null
        pendingCameraIsLogo = null
        if (success && capturedUri != null && isLogoCapture != null) {
            cropMediaTarget = isLogoCapture to capturedUri
        }
    }

    fun launchStoreCamera(isLogo: Boolean) {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "store_${if (isLogo) "logo" else "banner"}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraIsLogo = isLogo
        pendingCameraUri = uri
        cameraCaptureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val isLogo = pendingCameraIsLogo ?: true
        if (granted) {
            launchStoreCamera(isLogo)
        } else {
            pendingCameraIsLogo = null
            onToast("Camera permission is needed to take a photo", true)
        }
    }

    if (cropMediaTarget != null) {
        val (isLogo, pickedUri) = cropMediaTarget!!
        ImageCropDialog(
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
                        onSaveStoreMedia(isLogo, uploadedPath)
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

    // JazzCash Business-Style Bottom Sheet for Logo & Banner Preview/Edit/Crop/Delete
    if (mediaSheetTarget != null) {
        val (isLogo, currentMediaUrl) = mediaSheetTarget!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { mediaSheetTarget = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLogo) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(2.dp, ZulvazaOrange.copy(alpha = 0.7f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(124.dp)
                    ) {
                        if (!currentMediaUrl.isNullOrBlank()) {
                            SignedRemoteImage(
                                url = currentMediaUrl,
                                contentDescription = "Store logo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = "Store logo",
                                    tint = ZulvazaOrange,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ZulvazaNavy,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        if (!currentMediaUrl.isNullOrBlank()) {
                            SignedRemoteImage(
                                url = currentMediaUrl,
                                contentDescription = "Store banner preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_seller_hero),
                                contentDescription = "Default banner preview",
                                contentScale = ContentScale.Crop,
                                alpha = 0.6f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isLogo) "Edit your store logo" else "Edit your store banner",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(6.dp))

                StoreMediaSheetActionRow(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Choose Photo",
                    tint = ZulvazaNavy,
                    testTag = if (isLogo) "choose_store_logo_button" else "choose_store_banner_button",
                    onClick = {
                        mediaSheetTarget = null
                        if (isLogo) {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                )

                StoreMediaSheetActionRow(
                    icon = Icons.Default.CameraAlt,
                    label = "Take Photo",
                    tint = ZulvazaNavy,
                    testTag = if (isLogo) "take_store_logo_button" else "take_store_banner_button",
                    onClick = {
                        mediaSheetTarget = null
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            launchStoreCamera(isLogo)
                        } else {
                            pendingCameraIsLogo = isLogo
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )

                if (!currentMediaUrl.isNullOrBlank()) {
                    StoreMediaSheetActionRow(
                        icon = Icons.Default.Crop,
                        label = "Crop & Edit",
                        tint = ZulvazaOrange,
                        testTag = if (isLogo) "crop_store_logo_button" else "crop_store_banner_button",
                        onClick = {
                            mediaSheetTarget = null
                            cropMediaTarget = isLogo to Uri.parse(currentMediaUrl)
                        }
                    )

                    StoreMediaSheetActionRow(
                        icon = Icons.Default.DeleteOutline,
                        label = "Delete Photo",
                        tint = ErrorRed,
                        labelColor = ErrorRed,
                        testTag = if (isLogo) "delete_store_logo_button" else "delete_store_banner_button",
                        onClick = {
                            mediaSheetTarget = null
                            onDeleteStoreMedia(isLogo)
                        }
                    )
                }
            }
        }
    }

    val bannerUrl = resolveStoreMedia(store?.bannerPath)
    val logoUrl = resolveStoreMedia(store?.logoPath)

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
                    .height(178.dp)
                    .background(Brush.linearGradient(listOf(ZulvazaNavy, ZulvazaOrange)))
                    .clickable(enabled = !uploadingBanner) {
                        mediaSheetTarget = false to bannerUrl
                    }
                    .testTag("store_profile_banner_box")
            ) {
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
                        alpha = 0.45f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Subtle bottom scrim for polished visual contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uploadingBanner) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                uploadingBanner -> "Uploading..."
                                !bannerUrl.isNullOrBlank() -> "Edit Cover"
                                else -> "Add Cover"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Circular Store Logo overlapping Banner
            Box(
                modifier = Modifier
                    .padding(start = 18.dp)
                    .offset(y = 132.dp)
                    .size(92.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(ZulvazaOrange, ZulvazaNavy, ZulvazaOrange)))
                        .padding(3.5.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uploadingLogo) {
                            mediaSheetTarget = true to logoUrl
                        }
                        .testTag("store_profile_logo_circle")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            uploadingLogo -> {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = ZulvazaOrange)
                                }
                            }
                            !logoUrl.isNullOrBlank() -> {
                                SignedRemoteImage(
                                    url = logoUrl,
                                    contentDescription = "Store logo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = "Upload store logo",
                                        tint = ZulvazaOrange,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ZulvazaOrange,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Edit logo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Separate Edit Store & Arrow Share Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = 48.dp)
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEditStore,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 42.dp)
                        .testTag("edit_store_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Store", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZulvazaNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 42.dp)
                        .testTag("share_store_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(54.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = store?.shopName?.takeIf { it.isNotBlank() } ?: "Set Up Your Storefront",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (verification.status == VerificationStatus.APPROVED) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified seller",
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                if (slugHandle != null) {
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
                                text = "zulvaza.com/shop/$slugHandle",
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

                if (sellerLocationLabel.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = ZulvazaOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sellerLocationLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Strip
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

            Spacer(modifier = Modifier.height(14.dp))

            // About / Products Segmented Switcher
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StoreSectionTabPill(
                        label = "About",
                        icon = Icons.Default.Info,
                        selected = selectedSectionTab == 0,
                        onClick = { selectedSectionTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("store_tab_about")
                    )
                    StoreSectionTabPill(
                        label = "Products (${products.size})",
                        icon = Icons.Default.Inventory2,
                        selected = selectedSectionTab == 1,
                        onClick = { selectedSectionTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("store_tab_products")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedSectionTab == 0) {
                // ================= ABOUT TAB =================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Store Bio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = store?.bio?.takeIf { it.isNotBlank() }
                                ?: "Add a crisp store bio so shoppers recognize your brand immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (store?.bio.isNullOrBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                        Text(
                            text = "About Store",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = store?.introduction?.takeIf { it.isNotBlank() }
                                ?: "Tap Edit Store to add your brand story, specialties, and delivery details.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (sellerLocationLabel.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ZulvazaOrange.copy(alpha = 0.12f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = ZulvazaOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Registered Business Location",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = sellerLocationLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("store_about_location")
                                    )
                                }
                            }
                        }

                        if (activeSocials.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            Text(
                                text = "Social Channels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
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
                    }
                }
            } else {
                // ================= PRODUCTS TAB =================
                if (products.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.Inventory2,
                        title = "No Products Yet",
                        subtitle = "Add your first product from the Add Product tab to showcase it in your store."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        products.forEach { product ->
                            StoreProductRowCard(
                                product = product,
                                imageUrl = product.imagePaths.firstOrNull()?.let(resolveProductImage),
                                onClick = { onOpenProductDetail(product) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StoreMediaSheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.1f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoreSectionTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ZulvazaOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoreProductRowCard(
    product: ProductItem,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("store_product_card_${product.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignedRemoteImage(
                url = imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductStatusBadge(status = product.status)
                    if (product.category.isNotBlank()) {
                        Text(
                            text = product.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Formatters.formatPkr(product.price),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.titleSmall,
                        color = ZulvazaOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stock: ${product.stock}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    usernameConflictNonce: Int = 0,
    onCheckStoreUsername: suspend (String) -> String,
    onSaveStore: (SellerStore, () -> Unit) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var shopName by remember(initialStore) { mutableStateOf(initialStore?.shopName ?: "") }
    var slug by remember(initialStore) { mutableStateOf(initialStore?.slug ?: "") }
    var usernameManuallyEdited by remember(initialStore) { mutableStateOf(!initialStore?.slug.isNullOrBlank()) }
    var usernameUiState by remember { mutableStateOf(StoreUsernameUiState()) }
    var bio by remember(initialStore) { mutableStateOf(initialStore?.bio ?: "") }
    var introduction by remember(initialStore) { mutableStateOf(initialStore?.introduction ?: "") }

    val preservedLogoPath = initialStore?.logoPath
    val preservedBannerPath = initialStore?.bannerPath
    val preservedSocialProfiles = initialStore?.socialProfiles ?: emptyList()

    val nameRequester = remember { BringIntoViewRequester() }
    val slugRequester = remember { BringIntoViewRequester() }
    val bioRequester = remember { BringIntoViewRequester() }
    val introRequester = remember { BringIntoViewRequester() }

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
            Text("Edit Store Details", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap your logo or cover banner on My Store to preview, take photo, crop, change, or delete.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                        if (!usernameManuallyEdited) {
                            slug = Formatters.generateStoreUsernameFromName(it)
                        }
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
                StoreUsernameField(
                    username = slug,
                    storeName = shopName,
                    initialSavedUsername = initialStore?.slug.orEmpty(),
                    recheckTrigger = usernameConflictNonce,
                    onUsernameChange = { slug = it },
                    onUserEdited = { usernameManuallyEdited = true },
                    onCheckUsername = onCheckStoreUsername,
                    onStateChange = { usernameUiState = it },
                    fieldModifier = Modifier
                        .bringIntoViewRequester(slugRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { delay(180); slugRequester.bringIntoView() } },
                    testTag = "store_slug_input"
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
                    !usernameUiState.canProceed -> onToast("Choose an available store username", true)
                    else -> {
                        onSaveStore(
                            SellerStore(
                                sellerId = initialStore?.sellerId ?: "",
                                shopName = shopName.trim(),
                                slug = slug.trim(),
                                bio = bio.trim(),
                                introduction = introduction.trim(),
                                logoPath = preservedLogoPath,
                                bannerPath = preservedBannerPath,
                                socialProfiles = preservedSocialProfiles
                            ),
                            onDismiss
                        )
                    }
                }
            },
            enabled = shopName.isNotBlank() && usernameUiState.canProceed && !usernameUiState.isChecking && !isBusy,
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
