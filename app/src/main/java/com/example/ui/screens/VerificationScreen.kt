package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SellerIdentityDetails
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.VerificationStatus
import com.example.ui.components.CnicDualScannerScreen
import com.example.ui.components.FaceLivenessScreen
import com.example.ui.components.ImageCropDialog
import com.example.ui.components.LocationGpsSection
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.StoreUsernameField
import com.example.ui.components.StoreUsernameUiState
import com.example.ui.state.CnicOcrBackgroundState
import com.example.ui.state.CnicOcrStatus
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.CnicMatchValidationResult
import com.example.util.CnicMatchValidator
import com.example.util.CnicVisualTransformation
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VerificationScreen(
    verification: SellerVerification,
    store: SellerStore?,
    cnicOcrState: CnicOcrBackgroundState,
    canNavigateBack: Boolean,
    isBusy: Boolean,
    usernameConflictNonce: Int = 0,
    resolveDocUrl: (String) -> String?,
    resolveFaceUrl: (String) -> String?,
    resolveStoreMedia: (String?) -> String?,
    onUploadCnic: suspend (ByteArray, String) -> String,
    onProcessCnicOcrBackground: (ByteArray, ByteArray, Boolean) -> Unit,
    onVerifyCnicMatchWithBackend: suspend (String, String, String, String, String, String) -> CnicMatchValidationResult,
    onUploadFacePhoto: suspend (ByteArray) -> String,
    onUploadStoreMediaBytes: suspend (ByteArray, String, String, String?, (Float) -> Unit) -> String,
    onCheckStoreUsername: suspend (String) -> String,
    onSubmit: (SellerIdentityDetails, SellerStore?) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }

    // Sub-screen overlays inside wizard
    var showDualCnicScanner by remember { mutableStateOf(false) }
    var cnicRescanStartSide by remember { mutableStateOf<String?>(null) }
    var showFaceScanner by remember { mutableStateOf(false) }
    var isUploadingCnic by remember { mutableStateOf(false) }
    var isUploadingFace by remember { mutableStateOf(false) }

    // Store crop target for Step 5
    var cropStoreTarget by remember { mutableStateOf<Pair<Boolean, Uri>?>(null) }
    var uploadingStoreLogo by remember { mutableStateOf(false) }
    var uploadingStoreBanner by remember { mutableStateOf(false) }

    // OCR state & local preview bytes
    var frontScanOcrText by remember(verification) {
        mutableStateOf(verification.details.ocrFrontText.takeIf { it.isNotBlank() })
    }
    var backScanOcrText by remember(verification) {
        mutableStateOf(verification.details.ocrBackText.takeIf { it.isNotBlank() })
    }
    var ocrAppliedForScan by remember { mutableStateOf(false) }
    var localFrontBytes by remember { mutableStateOf<ByteArray?>(null) }
    var localBackBytes by remember { mutableStateOf<ByteArray?>(null) }
    var localFaceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var scanMismatches by remember { mutableStateOf<List<String>>(emptyList()) }

    // Date picker target ("dob", "issue", or "expiry")
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    val initial = verification.details
    var fullName by remember(verification) { mutableStateOf(initial.fullName) }
    var cnicDigits by remember(verification) { mutableStateOf(Formatters.sanitizeCnicDigits(initial.cnic)) }
    var gender by remember(verification) { mutableStateOf(initial.gender.ifBlank { "male" }) }
    var dateOfBirth by remember(verification) { mutableStateOf(initial.dateOfBirth) }
    var issueDate by remember(verification) { mutableStateOf(initial.issueDate) }
    var expiryDate by remember(verification) { mutableStateOf(initial.expiryDate) }
    var cnicFrontPath by remember(verification) { mutableStateOf(initial.cnicFront) }
    var cnicBackPath by remember(verification) { mutableStateOf(initial.cnicBack) }
    var facePhotoPath by remember(verification) { mutableStateOf(initial.facePhoto) }

    // Apply background OCR extraction results automatically as soon as ready
    LaunchedEffect(cnicOcrState.status, cnicOcrState.extracted) {
        if (cnicOcrState.status == CnicOcrStatus.COMPLETED) {
            if (cnicOcrState.frontText.isNotBlank()) frontScanOcrText = cnicOcrState.frontText
            if (cnicOcrState.backText.isNotBlank()) backScanOcrText = cnicOcrState.backText
            if (!ocrAppliedForScan) {
                val ex = cnicOcrState.extracted
                if (ex.fullName.isNotBlank() && fullName.isBlank()) fullName = ex.fullName
                if (ex.cnicDigits.length == 13 && cnicDigits.length != 13) cnicDigits = ex.cnicDigits
                if (ex.dateOfBirth.isNotBlank() && dateOfBirth.isBlank()) dateOfBirth = ex.dateOfBirth
                if (ex.issueDate.isNotBlank() && issueDate.isBlank()) issueDate = ex.issueDate
                if (ex.expiryDate.isNotBlank() && expiryDate.isBlank()) expiryDate = ex.expiryDate
                if (ex.gender.isNotBlank()) gender = ex.gender
                ocrAppliedForScan = true
            }
        }
    }

    // Contact & Location (Step 4)
    var phoneDigits by remember(verification) { mutableStateOf(Formatters.formatLocalPakistanPhone(initial.phone)) }
    var whatsappDigits10 by remember(verification) { mutableStateOf(Formatters.stripPakistanPrefix(initial.whatsapp)) }
    var city by remember(verification) { mutableStateOf(initial.city) }
    var street by remember(verification) { mutableStateOf(initial.street) }
    var state by remember(verification) { mutableStateOf(initial.state) }
    var address by remember(verification) { mutableStateOf(initial.address) }
    var address2 by remember(verification) { mutableStateOf(initial.address2) }
    var zipCode by remember(verification) { mutableStateOf(initial.zipCode) }
    var latitude by remember(verification) { mutableStateOf<Double?>(initial.latitude) }
    var longitude by remember(verification) { mutableStateOf<Double?>(initial.longitude) }
    var genderExpanded by remember { mutableStateOf(false) }

    // Store setup (Step 5)
    var shopName by remember(store) { mutableStateOf(store?.shopName.orEmpty()) }
    var shopSlug by remember(store) { mutableStateOf(store?.slug.orEmpty()) }
    var usernameManuallyEdited by remember(store) { mutableStateOf(!store?.slug.isNullOrBlank()) }
    var usernameUiState by remember { mutableStateOf(StoreUsernameUiState()) }
    var shopBio by remember(store) { mutableStateOf(store?.bio.orEmpty()) }
    var shopIntro by remember(store) { mutableStateOf(store?.introduction.orEmpty()) }
    var shopLogoPath by remember(store) { mutableStateOf(store?.logoPath) }
    var shopBannerPath by remember(store) { mutableStateOf(store?.bannerPath) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropStoreTarget = true to uri
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropStoreTarget = false to uri
    }

    BackHandler {
        when {
            showDualCnicScanner -> showDualCnicScanner = false
            showFaceScanner -> showFaceScanner = false
            cropStoreTarget != null -> cropStoreTarget = null
            step > 1 -> step -= 1
            canNavigateBack -> onBack()
        }
    }

    if (showDualCnicScanner) {
        CnicDualScannerScreen(
            startOnSide = cnicRescanStartSide,
            onCompleted = { dual ->
                showDualCnicScanner = false
                cnicRescanStartSide = null
                localFrontBytes = dual.front.jpegBytes
                localBackBytes = dual.back.jpegBytes
                frontScanOcrText = null
                backScanOcrText = null
                ocrAppliedForScan = false
                scanMismatches = emptyList()

                isUploadingCnic = true
                scope.launch {
                    try {
                        val frontPath = onUploadCnic(dual.front.jpegBytes, "front")
                        val backPath = onUploadCnic(dual.back.jpegBytes, "back")
                        cnicFrontPath = frontPath
                        cnicBackPath = backPath
                        // Run OCR & verification extraction in background right after upload completes
                        onProcessCnicOcrBackground(
                            dual.front.jpegBytes,
                            dual.back.jpegBytes,
                            dual.front.hasDetectedFace
                        )
                        onToast("CNIC images uploaded — OCR reading in background", false)
                    } catch (e: Exception) {
                        onToast(e.message ?: "Failed to upload CNIC images", true)
                    } finally {
                        isUploadingCnic = false
                    }
                }
            },
            onClose = {
                showDualCnicScanner = false
                cnicRescanStartSide = null
            }
        )
        return
    }

    if (showFaceScanner) {
        FaceLivenessScreen(
            onVerified = { faceBytes ->
                showFaceScanner = false
                localFaceBytes = faceBytes
                isUploadingFace = true
                scope.launch {
                    try {
                        val uploadedFacePath = onUploadFacePhoto(faceBytes)
                        facePhotoPath = uploadedFacePath
                        onToast("Face verified and uploaded", false)
                    } catch (e: Exception) {
                        onToast(e.message ?: "Failed to upload face photo", true)
                    } finally {
                        isUploadingFace = false
                    }
                }
            },
            onClose = { showFaceScanner = false }
        )
        return
    }

    if (cropStoreTarget != null) {
        val (isLogo, pickedUri) = cropStoreTarget!!
        ImageCropDialog(
            imageUri = pickedUri,
            isCircularLogo = isLogo,
            title = if (isLogo) "Crop & Adjust Store Logo" else "Crop & Adjust Cover Banner",
            onCropped = { croppedBytes ->
                cropStoreTarget = null
                scope.launch {
                    if (isLogo) uploadingStoreLogo = true else uploadingStoreBanner = true
                    try {
                        val uploaded = onUploadStoreMediaBytes(croppedBytes, "jpg", "image/jpeg", null) {}
                        if (isLogo) {
                            shopLogoPath = uploaded
                            onToast("Store logo cropped & uploaded", false)
                        } else {
                            shopBannerPath = uploaded
                            onToast("Cover banner cropped & uploaded", false)
                        }
                    } catch (e: Exception) {
                        onToast(e.message ?: "Upload failed", true)
                    } finally {
                        uploadingStoreLogo = false
                        uploadingStoreBanner = false
                    }
                }
            },
            onCancel = { cropStoreTarget = null }
        )
    }

    if (datePickerTarget != null) {
        val currentValue = when (datePickerTarget) {
            "dob" -> dateOfBirth
            "issue" -> issueDate
            else -> expiryDate
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseIsoDateToMillis(currentValue)
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val formatted = Formatters.formatMillisToIsoDate(millis)
                            when (datePickerTarget) {
                                "dob" -> dateOfBirth = formatted
                                "issue" -> issueDate = formatted
                                else -> expiryDate = formatted
                            }
                            scanMismatches = emptyList()
                        }
                        datePickerTarget = null
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    val stepTitle = when (step) {
        1 -> "CNIC Card Scan"
        2 -> "Face Liveness Check"
        3 -> "Identity Details"
        4 -> "Contact & Location"
        else -> "Store Profile Setup"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 580.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (step > 1 || canNavigateBack) {
                        IconButton(onClick = { if (step > 1) step -= 1 else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Text("Seller Verification", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Step $step of 5 • $stepTitle",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (!canNavigateBack) {
                    TextButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log out")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5-Step Progress Dots + Bar
            LinearProgressIndicator(
                progress = { step / 5f },
                color = ZulvazaOrange,
                trackColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labels = listOf("1.CNIC", "2.Face", "3.Details", "4.Contact", "5.Store")
                labels.forEachIndexed { idx, lbl ->
                    val sNum = idx + 1
                    val color = when {
                        sNum < step -> SuccessGreen
                        sNum == step -> ZulvazaOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = if (sNum == step) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            if (verification.status == VerificationStatus.REJECTED && !verification.reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ErrorRedSoft,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Previous verification rejected",
                                style = MaterialTheme.typography.titleSmall,
                                color = ErrorRed
                            )
                            Text(
                                verification.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (step) {
                // ================= STEP 1: CNIC CARD SCAN =================
                1 -> {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ZulvazaOrange.copy(alpha = 0.14f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DocumentScanner,
                                            contentDescription = null,
                                            tint = ZulvazaOrange,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Scan Your Physical CNIC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Camera scans Front side first, flips automatically to scan Back side, and crops strictly to the card border.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    cnicRescanStartSide = null
                                    showDualCnicScanner = true
                                },
                                enabled = !isUploadingCnic,
                                colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("scan_cnic_card_button")
                            ) {
                                if (isUploadingCnic) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading Scanned CNIC...", maxLines = 1)
                                } else {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (cnicFrontPath.isNotBlank() && cnicBackPath.isNotBlank()) "Rescan Both Sides" else "Scan CNIC Card",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (cnicFrontPath.isNotBlank() || cnicBackPath.isNotBlank() || localFrontBytes != null || localBackBytes != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CnicStepPreviewTile(
                                        title = "CNIC Front",
                                        localBytes = localFrontBytes,
                                        signedUrl = resolveDocUrl(cnicFrontPath),
                                        hasPath = cnicFrontPath.isNotBlank(),
                                        isUploading = isUploadingCnic,
                                        onRescan = {
                                            cnicRescanStartSide = "Front"
                                            showDualCnicScanner = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("capture_cnic_front")
                                    )
                                    CnicStepPreviewTile(
                                        title = "CNIC Back",
                                        localBytes = localBackBytes,
                                        signedUrl = resolveDocUrl(cnicBackPath),
                                        hasPath = cnicBackPath.isNotBlank(),
                                        isUploading = isUploadingCnic,
                                        onRescan = {
                                            cnicRescanStartSide = "Back"
                                            showDualCnicScanner = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("capture_cnic_back")
                                    )
                                }
                            }
                            if (cnicFrontPath.isNotBlank() && cnicBackPath.isNotBlank() && !isUploadingCnic) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = if (cnicOcrState.status == CnicOcrStatus.COMPLETED) {
                                        SuccessGreen.copy(alpha = 0.10f)
                                    } else {
                                        ZulvazaOrange.copy(alpha = 0.10f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (cnicOcrState.status == CnicOcrStatus.PROCESSING) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = ZulvazaOrange
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Running CNIC text OCR in the background — you can continue.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ZulvazaOrange
                                            )
                                        } else if (cnicOcrState.status == CnicOcrStatus.COMPLETED) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Background CNIC OCR & card check completed.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Both CNIC sides uploaded cleanly.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SuccessGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val cnicReadyToContinue = cnicFrontPath.isNotBlank() &&
                        cnicBackPath.isNotBlank() &&
                        !isUploadingCnic

                    Spacer(modifier = Modifier.height(18.dp))
                    if (cnicReadyToContinue) {
                        Button(
                            onClick = { step = 2 },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("step1_next_button")
                        ) {
                            Text(
                                "Continue to Face Verification",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isUploadingCnic) {
                                    "Uploading cropped CNIC images to server..."
                                } else {
                                    "Scan both Front & Back sides of your CNIC card to unlock Next."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }

                // ================= STEP 2: FACE LIVENESS VERIFICATION =================
                2 -> {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (facePhotoPath.isNotBlank() || localFaceBytes != null) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.14f),
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
                                            "Face Verified Successfully",
                                            color = SuccessGreen,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val localFaceBmp = remember(localFaceBytes) {
                                    localFaceBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                }
                                val remoteFaceUrl = resolveFaceUrl(facePhotoPath)
                                Box(
                                    modifier = Modifier
                                        .size(148.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, SuccessGreen, CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        localFaceBmp != null -> {
                                            Image(
                                                bitmap = localFaceBmp.asImageBitmap(),
                                                contentDescription = "Verified face photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        !remoteFaceUrl.isNullOrBlank() -> {
                                            SignedRemoteImage(
                                                url = remoteFaceUrl,
                                                contentDescription = "Verified face photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = ZulvazaOrange.copy(alpha = 0.14f),
                                    modifier = Modifier.size(84.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = ZulvazaOrange,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Live 5-Step Face Verification",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Look straight, then follow voice & screen prompts to turn Left, Right, Up, and Down inside the green oval.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = { showFaceScanner = true },
                                enabled = !isUploadingFace,
                                colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("start_face_verification_button")
                            ) {
                                if (isUploadingFace) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading Face Photo...", maxLines = 1)
                                } else {
                                    Icon(
                                        imageVector = if (facePhotoPath.isNotBlank()) Icons.Default.Refresh else Icons.Default.Face,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (facePhotoPath.isNotBlank()) "Retake Face Verification" else "Start Face Verification",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    val faceReadyToContinue = facePhotoPath.isNotBlank() && !isUploadingFace

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { step = 1 },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(if (faceReadyToContinue) 0.35f else 1f)
                                .defaultMinSize(minHeight = 52.dp)
                        ) { Text("Back", maxLines = 1) }

                        if (faceReadyToContinue) {
                            Button(
                                onClick = { step = 3 },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(0.65f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("step2_next_button")
                            ) {
                                Text("Continue to Details", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (!faceReadyToContinue) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isUploadingFace) {
                                "Uploading verified face photo..."
                            } else {
                                "Complete live face verification above to continue."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ================= STEP 3: IDENTITY DETAILS (AUTO-FILLED FROM OCR) =================
                3 -> {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            val nameBringer = remember { BringIntoViewRequester() }
                            val cnicBringer = remember { BringIntoViewRequester() }

                            Text("Identity Details (Verify CNIC Match)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Confirm your details match your uploaded CNIC card.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (cnicOcrState.status == CnicOcrStatus.PROCESSING || cnicOcrState.isVerifyingMatch) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = ZulvazaOrange.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ZulvazaOrange
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (cnicOcrState.isVerifyingMatch) {
                                                "Verifying details against uploaded CNIC..."
                                            } else {
                                                "Completing background OCR from uploaded CNIC..."
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZulvazaOrange
                                        )
                                    }
                                }
                            } else if (cnicOcrState.status == CnicOcrStatus.COMPLETED) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Background CNIC OCR ready for match verification",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    scanMismatches = emptyList()
                                },
                                label = { Text("Full Legal Name (as on CNIC)") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(nameBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); nameBringer.bringIntoView() } }
                                    .testTag("kyc_full_name")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = cnicDigits,
                                onValueChange = { raw ->
                                    cnicDigits = Formatters.sanitizeCnicDigits(raw)
                                    scanMismatches = emptyList()
                                },
                                label = { Text("CNIC Number") },
                                placeholder = { Text("12345-1234567-1") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                visualTransformation = remember { CnicVisualTransformation() },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("${cnicDigits.length}/13 digits (format: 12345-1234567-1)") },
                                isError = cnicDigits.isNotEmpty() && cnicDigits.length != 13,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(cnicBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); cnicBringer.bringIntoView() } }
                                    .testTag("kyc_cnic")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            DateSelectorField(
                                label = "Date of Birth",
                                value = dateOfBirth,
                                onClick = { datePickerTarget = "dob" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("kyc_dob")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                DateSelectorField(
                                    label = "Issue Date",
                                    value = issueDate,
                                    onClick = { datePickerTarget = "issue" },
                                    modifier = Modifier.weight(1f)
                                )
                                DateSelectorField(
                                    label = "Expiry Date",
                                    value = expiryDate,
                                    onClick = { datePickerTarget = "expiry" },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val genderOptions = listOf(
                                Triple("male", "Male", R.drawable.ic_gender_male),
                                Triple("female", "Female", R.drawable.ic_gender_female),
                                Triple("other", "Other", R.drawable.ic_gender_other)
                            )
                            val selectedGender = genderOptions.firstOrNull { it.first == gender } ?: genderOptions.first()

                            ExposedDropdownMenuBox(
                                expanded = genderExpanded,
                                onExpandedChange = { genderExpanded = !genderExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedGender.second,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gender") },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = selectedGender.third),
                                            contentDescription = selectedGender.second,
                                            tint = ZulvazaOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .testTag("kyc_gender")
                                )
                                ExposedDropdownMenu(
                                    expanded = genderExpanded,
                                    onDismissRequest = { genderExpanded = false }
                                ) {
                                    genderOptions.forEach { (key, label, iconRes) ->
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(id = iconRes),
                                                    contentDescription = label,
                                                    tint = if (gender == key) ZulvazaOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            text = { Text(label) },
                                            onClick = {
                                                gender = key
                                                genderExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (scanMismatches.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = ErrorRedSoft,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cnic_mismatch_error_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = "CNIC mismatch", tint = ErrorRed, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "CNIC Details Do Not Match Scanned Card",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = ErrorRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            scanMismatches.forEach { reason ->
                                                Text("• $reason", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { step = 2 },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .defaultMinSize(minHeight = 52.dp)
                        ) { Text("Back", maxLines = 1) }

                        Button(
                            onClick = {
                                when {
                                    fullName.isBlank() -> onToast("Enter your full legal name", true)
                                    cnicDigits.length != 13 -> onToast("Enter a valid 13-digit CNIC", true)
                                    dateOfBirth.isBlank() -> onToast("Select your date of birth", true)
                                    issueDate.isBlank() || expiryDate.isBlank() -> onToast("Select CNIC issue and expiry dates", true)
                                    expiryDate <= issueDate -> onToast("CNIC expiry date must be after issue date", true)
                                    else -> {
                                        scope.launch {
                                            val matchResult = onVerifyCnicMatchWithBackend(
                                                fullName,
                                                cnicDigits,
                                                issueDate,
                                                expiryDate,
                                                cnicFrontPath,
                                                cnicBackPath
                                            )
                                            if (!matchResult.isMatch) {
                                                scanMismatches = matchResult.mismatches
                                                onToast("Entered details do not match your uploaded CNIC card.", true)
                                            } else {
                                                scanMismatches = emptyList()
                                                step = 4
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !cnicOcrState.isVerifyingMatch,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.65f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("kyc_next_step_button")
                        ) {
                            if (cnicOcrState.isVerifyingMatch) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking CNIC...", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("Continue to Contact", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // ================= STEP 4: PHONE, WHATSAPP (+92) & LOCATION =================
                4 -> {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            val phoneBringer = remember { BringIntoViewRequester() }
                            val whatsappBringer = remember { BringIntoViewRequester() }
                            val cityBringer = remember { BringIntoViewRequester() }
                            val stateBringer = remember { BringIntoViewRequester() }
                            val streetBringer = remember { BringIntoViewRequester() }
                            val addrBringer = remember { BringIntoViewRequester() }
                            val addr2Bringer = remember { BringIntoViewRequester() }
                            val zipBringer = remember { BringIntoViewRequester() }

                            Text("Contact Numbers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = phoneDigits,
                                onValueChange = { raw -> phoneDigits = Formatters.sanitizeLocalPakistanPhone(raw) },
                                label = { Text("Mobile Phone (11 digits)") },
                                placeholder = { Text("03001234567") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                supportingText = {
                                    Text(
                                        if (phoneDigits.isNotEmpty() && !phoneDigits.startsWith("0")) {
                                            "Must start with 0 (e.g. 03001234567)"
                                        } else {
                                            "${phoneDigits.length}/11 digits"
                                        }
                                    )
                                },
                                isError = phoneDigits.isNotEmpty() && !Formatters.isValidLocalPakistanPhone(phoneDigits),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(phoneBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); phoneBringer.bringIntoView() } }
                                    .testTag("kyc_phone")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = whatsappDigits10,
                                onValueChange = { raw ->
                                    whatsappDigits10 = raw.filter { it.isDigit() }.take(10)
                                },
                                label = { Text("WhatsApp Number (10 digits)") },
                                placeholder = { Text("3001234567") },
                                prefix = {
                                    Text(
                                        text = "+92 ",
                                        fontWeight = FontWeight.Bold,
                                        color = ZulvazaOrange
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                supportingText = {
                                    Text(
                                        if (whatsappDigits10.isNotEmpty() && !whatsappDigits10.startsWith("3")) {
                                            "Enter 10 digits without leading 0 (e.g. 3001234567)"
                                        } else {
                                            "${whatsappDigits10.length}/10 digits after +92"
                                        }
                                    )
                                },
                                isError = whatsappDigits10.isNotEmpty() && !Regex("^3\\d{9}$").matches(whatsappDigits10),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(whatsappBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); whatsappBringer.bringIntoView() } }
                                    .testTag("kyc_whatsapp")
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pickup & Business Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))

                            LocationGpsSection(
                                latitude = latitude,
                                longitude = longitude,
                                onCoordinatesChanged = { lat, lng ->
                                    latitude = lat
                                    longitude = lng
                                },
                                onAddressGeocoded = { geo ->
                                    if (geo.city.isNotBlank()) city = geo.city
                                    if (geo.street.isNotBlank()) street = geo.street
                                    if (geo.state.isNotBlank()) state = geo.state
                                    if (geo.address.isNotBlank()) address = geo.address
                                    if (geo.zipCode.isNotBlank()) zipCode = geo.zipCode
                                    onToast("Location updated — verify your address details below", false)
                                },
                                onError = { msg -> onToast(msg, true) }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = { Text("City") },
                                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .bringIntoViewRequester(cityBringer)
                                        .onFocusEvent { if (it.isFocused) scope.launch { delay(160); cityBringer.bringIntoView() } }
                                        .testTag("kyc_city")
                                )
                                OutlinedTextField(
                                    value = state,
                                    onValueChange = { state = it },
                                    label = { Text("Province / State") },
                                    placeholder = { Text("Punjab") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .bringIntoViewRequester(stateBringer)
                                        .onFocusEvent { if (it.isFocused) scope.launch { delay(160); stateBringer.bringIntoView() } }
                                        .testTag("kyc_state")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = street,
                                onValueChange = { street = it },
                                label = { Text("Street / Sector / Block") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(streetBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); streetBringer.bringIntoView() } }
                                    .testTag("kyc_street")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Full Address Line 1") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(addrBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); addrBringer.bringIntoView() } }
                                    .testTag("kyc_address")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = address2,
                                onValueChange = { address2 = it },
                                label = { Text("Address Line 2 (Optional)") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(addr2Bringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); addr2Bringer.bringIntoView() } }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = zipCode,
                                onValueChange = { raw -> zipCode = raw.filter { it.isDigit() }.take(5) },
                                label = { Text("5-Digit Postal / Zip Code") },
                                placeholder = { Text("54000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = zipCode.isNotEmpty() && zipCode.length != 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(zipBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); zipBringer.bringIntoView() } }
                                    .testTag("kyc_zip")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { step = 3 },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .defaultMinSize(minHeight = 52.dp)
                        ) { Text("Back", maxLines = 1) }

                        Button(
                            onClick = {
                                when {
                                    !Formatters.isValidLocalPakistanPhone(phoneDigits) ->
                                        onToast("Enter valid 11-digit mobile starting with 0 (e.g. 03001234567)", true)
                                    !Regex("^3\\d{9}$").matches(whatsappDigits10) ->
                                        onToast("Enter valid 10-digit WhatsApp after +92 (e.g. 3001234567)", true)
                                    city.isBlank() || street.isBlank() || state.isBlank() || address.isBlank() ->
                                        onToast("Please fill city, state, street, and address", true)
                                    zipCode.length != 5 ->
                                        onToast("Enter a 5-digit Pakistan postal code", true)
                                    else -> step = 5
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.65f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("step4_next_button")
                        ) {
                            Text("Continue to Store Setup", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // ================= STEP 5: STORE PROFILE SETUP & FINAL SUBMIT =================
                else -> {
                    val bannerUrl = resolveStoreMedia(shopBannerPath)
                    val logoUrl = resolveStoreMedia(shopLogoPath)
                    val storeNameBringer = remember { BringIntoViewRequester() }
                    val storeSlugBringer = remember { BringIntoViewRequester() }
                    val storeBioBringer = remember { BringIntoViewRequester() }
                    val storeIntroBringer = remember { BringIntoViewRequester() }

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Set Up Your Storefront", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Upload your store logo, cover banner, and brand details before submitting for review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Banner Picker (16:9)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(138.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ZulvazaNavy)
                                    .clickable(enabled = !uploadingStoreBanner) {
                                        bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    .testTag("onboarding_store_banner")
                            ) {
                                if (!bannerUrl.isNullOrBlank()) {
                                    SignedRemoteImage(url = bannerUrl, contentDescription = "Store banner", modifier = Modifier.fillMaxSize())
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
                                        if (uploadingStoreBanner) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                        } else {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                uploadingStoreBanner -> "Uploading banner..."
                                                !bannerUrl.isNullOrBlank() -> "Change cover banner"
                                                else -> "Upload cover banner (16:9)"
                                            },
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Logo Picker (1:1)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(2.dp, ZulvazaOrange, CircleShape)
                                        .clickable(enabled = !uploadingStoreLogo) {
                                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                        .testTag("onboarding_store_logo"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!logoUrl.isNullOrBlank()) {
                                        SignedRemoteImage(url = logoUrl, contentDescription = "Store logo", modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.Storefront, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(32.dp))
                                    }
                                    if (uploadingStoreLogo) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Store Logo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (!logoUrl.isNullOrBlank()) "Logo selected • Tap to change & crop" else "Tap circle to pick & crop logo (1:1)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = shopName,
                                onValueChange = {
                                    shopName = it
                                    if (!usernameManuallyEdited) {
                                        shopSlug = Formatters.generateStoreUsernameFromName(it)
                                    }
                                },
                                label = { Text("Store Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(storeNameBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); storeNameBringer.bringIntoView() } }
                                    .testTag("onboarding_store_name")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            StoreUsernameField(
                                username = shopSlug,
                                storeName = shopName,
                                recheckTrigger = usernameConflictNonce,
                                onUsernameChange = { shopSlug = it },
                                onUserEdited = { usernameManuallyEdited = true },
                                onCheckUsername = onCheckStoreUsername,
                                onStateChange = { usernameUiState = it },
                                fieldModifier = Modifier
                                    .bringIntoViewRequester(storeSlugBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); storeSlugBringer.bringIntoView() } },
                                testTag = "onboarding_store_slug"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = shopBio,
                                onValueChange = { shopBio = it },
                                label = { Text("Short Store Bio") },
                                minLines = 2,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(storeBioBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); storeBioBringer.bringIntoView() } }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = shopIntro,
                                onValueChange = { shopIntro = it },
                                label = { Text("Store Description (Optional)") },
                                minLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(storeIntroBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { delay(160); storeIntroBringer.bringIntoView() } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val phoneE164 = Formatters.normalizePakistanPhoneToE164(phoneDigits)
                    val whatsappE164 = "+92$whatsappDigits10"

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { step = 4 },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier
                                .weight(0.35f)
                                .defaultMinSize(minHeight = 52.dp)
                        ) {
                            Text("Back", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = {
                                when {
                                    shopName.isBlank() -> onToast("Enter your store name", true)
                                    !usernameUiState.canProceed -> onToast("Choose an available store username", true)
                                    else -> {
                                        val ocrFront = cnicOcrState.frontText.ifBlank { frontScanOcrText.orEmpty() }
                                        val ocrBack = cnicOcrState.backText.ifBlank { backScanOcrText.orEmpty() }
                                        val finalValidation = CnicMatchValidator.validateAgainstEnteredDetails(
                                            fullName = fullName.trim(),
                                            cnicDigits = cnicDigits,
                                            issueDateIso = issueDate,
                                            expiryDateIso = expiryDate,
                                            frontText = ocrFront,
                                            backText = ocrBack
                                        )
                                        val finalDetails = SellerIdentityDetails(
                                            fullName = fullName.trim(),
                                            cnic = cnicDigits,
                                            gender = gender,
                                            dateOfBirth = dateOfBirth,
                                            issueDate = issueDate,
                                            expiryDate = expiryDate,
                                            cnicFront = cnicFrontPath,
                                            cnicBack = cnicBackPath,
                                            facePhoto = facePhotoPath,
                                            phone = phoneE164,
                                            whatsapp = whatsappE164,
                                            city = city.trim(),
                                            street = street.trim(),
                                            state = state.trim(),
                                            address = address.trim(),
                                            address2 = address2.trim(),
                                            zipCode = zipCode,
                                            latitude = latitude,
                                            longitude = longitude,
                                            ocrFrontText = ocrFront,
                                            ocrBackText = ocrBack,
                                            ocrMatchPassed = finalValidation.isMatch,
                                            ocrMatchSummary = CnicMatchValidator.buildMatchSummary(
                                                extracted = cnicOcrState.extracted,
                                                validation = finalValidation,
                                                faceDetectedOnFront = cnicOcrState.frontFaceDetected
                                            ),
                                            cnicFaceDetected = cnicOcrState.frontFaceDetected
                                        )
                                        val finalStore = SellerStore(
                                            sellerId = store?.sellerId.orEmpty(),
                                            shopName = shopName.trim(),
                                            slug = shopSlug.trim(),
                                            bio = shopBio.trim(),
                                            introduction = shopIntro.trim(),
                                            logoPath = shopLogoPath,
                                            bannerPath = shopBannerPath,
                                            socialProfiles = store?.socialProfiles ?: emptyList()
                                        )
                                        onSubmit(finalDetails, finalStore)
                                    }
                                }
                            },
                            enabled = shopName.isNotBlank() &&
                                usernameUiState.canProceed &&
                                !usernameUiState.isChecking &&
                                !isBusy &&
                                !uploadingStoreLogo &&
                                !uploadingStoreBanner,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            modifier = Modifier
                                .weight(0.65f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("kyc_submit_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Submit Verification", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun parseIsoDateToMillis(iso: String): Long? {
    if (iso.isBlank()) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(iso)?.time
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun DateSelectorField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = ZulvazaOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value.ifBlank { "YYYY-MM-DD" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (value.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CnicStepPreviewTile(
    title: String,
    localBytes: ByteArray?,
    signedUrl: String?,
    hasPath: Boolean,
    isUploading: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localBmp = remember(localBytes) {
        localBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(85.6f / 54f)
                .border(
                    width = 1.5.dp,
                    color = if (hasPath || localBmp != null) SuccessGreen else ZulvazaOrange.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isUploading, onClick = onRescan)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    isUploading && localBmp == null -> {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    localBmp != null -> {
                        Image(
                            bitmap = localBmp.asImageBitmap(),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    hasPath && !signedUrl.isNullOrBlank() -> {
                        SignedRemoteImage(
                            url = signedUrl,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = ZulvazaOrange)
                    }
                }
                if (hasPath || localBmp != null) {
                    Surface(
                        color = SuccessGreen,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Captured",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(2.dp)
                                .size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("Tap to rescan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
