package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SellerIdentityDetails
import com.example.data.model.SellerVerification
import com.example.data.model.VerificationStatus
import com.example.ui.components.CnicLiveCameraOverlayScreen
import com.example.ui.components.LocationGpsSection
import com.example.ui.components.SignedRemoteImage
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.example.util.CnicMatchValidator
import com.example.util.CnicVisualTransformation
import com.example.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VerificationScreen(
    verification: SellerVerification,
    canNavigateBack: Boolean,
    isBusy: Boolean,
    resolveDocUrl: (String) -> String?,
    onUploadCnic: suspend (ByteArray, String) -> String,
    onSubmit: (SellerIdentityDetails) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }

    // Camera capture mode ("front" or "back")
    var activeCameraSide by remember { mutableStateOf<String?>(null) }
    var uploadingSide by remember { mutableStateOf<String?>(null) }
    var frontScanOcrText by remember(verification) { mutableStateOf<String?>(null) }
    var backScanOcrText by remember(verification) { mutableStateOf<String?>(null) }
    var scanMismatches by remember { mutableStateOf<List<String>>(emptyList()) }

    // Date picker target ("issue" or "expiry")
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    val initial = verification.details
    var fullName by remember(verification) { mutableStateOf(initial.fullName) }
    var cnicDigits by remember(verification) { mutableStateOf(Formatters.sanitizeCnicDigits(initial.cnic)) }
    var gender by remember(verification) { mutableStateOf(initial.gender.ifBlank { "male" }) }
    var issueDate by remember(verification) { mutableStateOf(initial.issueDate) }
    var expiryDate by remember(verification) { mutableStateOf(initial.expiryDate) }
    var cnicFrontPath by remember(verification) { mutableStateOf(initial.cnicFront) }
    var cnicBackPath by remember(verification) { mutableStateOf(initial.cnicBack) }
    var phoneDigits by remember(verification) { mutableStateOf(Formatters.formatLocalPakistanPhone(initial.phone)) }
    var whatsappDigits by remember(verification) { mutableStateOf(Formatters.formatLocalPakistanPhone(initial.whatsapp)) }

    var city by remember(verification) { mutableStateOf(initial.city) }
    var street by remember(verification) { mutableStateOf(initial.street) }
    var state by remember(verification) { mutableStateOf(initial.state) }
    var address by remember(verification) { mutableStateOf(initial.address) }
    var address2 by remember(verification) { mutableStateOf(initial.address2) }
    var zipCode by remember(verification) { mutableStateOf(initial.zipCode) }
    var latitude by remember(verification) { mutableStateOf<Double?>(initial.latitude) }
    var longitude by remember(verification) { mutableStateOf<Double?>(initial.longitude) }
    var genderExpanded by remember { mutableStateOf(false) }

    BackHandler {
        if (activeCameraSide != null) {
            activeCameraSide = null
        } else if (step == 2) {
            step = 1
        } else if (canNavigateBack) {
            onBack()
        }
    }

    if (activeCameraSide != null) {
        val side = activeCameraSide!!
        CnicLiveCameraOverlayScreen(
            sideLabel = if (side == "front") "Front" else "Back",
            onCaptured = { capture ->
                activeCameraSide = null
                uploadingSide = side
                if (side == "front") {
                    frontScanOcrText = capture.extractedText
                } else {
                    backScanOcrText = capture.extractedText
                }
                scanMismatches = emptyList()
                scope.launch {
                    try {
                        val path = onUploadCnic(capture.jpegBytes, side)
                        if (side == "front") cnicFrontPath = path else cnicBackPath = path
                        onToast("CNIC ${side.replaceFirstChar { it.uppercase() }} scanned & uploaded", false)
                    } catch (e: Exception) {
                        onToast(e.message ?: "Failed to upload CNIC photo", true)
                    } finally {
                        uploadingSide = null
                    }
                }
            },
            onClose = { activeCameraSide = null }
        )
        return
    }

    if (datePickerTarget != null) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val formatted = Formatters.formatMillisToIsoDate(millis)
                            if (datePickerTarget == "issue") issueDate = formatted else expiryDate = formatted
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

    val phoneE164 = Formatters.normalizePakistanPhoneToE164(phoneDigits)
    val whatsappE164 = Formatters.normalizePakistanPhoneToE164(whatsappDigits)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step == 2 || canNavigateBack) {
                        IconButton(onClick = { if (step == 2) step = 1 else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Text("Seller Verification", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Step $step of 2 • ${if (step == 1) "Identity & CNIC" else "Pickup Address & GPS"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZulvazaOrange
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
            LinearProgressIndicator(
                progress = { if (step == 1) 0.5f else 1f },
                color = ZulvazaOrange,
                trackColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
            )

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

            if (step == 1) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        val nameBringer = remember { BringIntoViewRequester() }
                        val cnicBringer = remember { BringIntoViewRequester() }
                        val phoneBringer = remember { BringIntoViewRequester() }
                        val whatsappBringer = remember { BringIntoViewRequester() }

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
                                .onFocusEvent { if (it.isFocused) scope.launch { nameBringer.bringIntoView() } }
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
                                .onFocusEvent { if (it.isFocused) scope.launch { cnicBringer.bringIntoView() } }
                                .testTag("kyc_cnic")
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        val genderOptions = listOf("male" to "Male", "female" to "Female", "other" to "Other")
                        val selectedGenderLabel = genderOptions.firstOrNull { it.first == gender }?.second ?: "Male"
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = !genderExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedGenderLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Gender") },
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
                                genderOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            gender = key
                                            genderExpanded = false
                                        }
                                    )
                                }
                            }
                        }

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

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Live CNIC Scanner (Front & Back)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Hold your physical CNIC inside the scanner frame — it detects the card, verifies details against your input, and uploads for review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CnicCaptureTile(
                                title = "CNIC Front",
                                path = cnicFrontPath,
                                signedUrl = resolveDocUrl(cnicFrontPath),
                                isUploading = uploadingSide == "front",
                                onClick = { activeCameraSide = "front" },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("capture_cnic_front")
                            )
                            CnicCaptureTile(
                                title = "CNIC Back",
                                path = cnicBackPath,
                                signedUrl = resolveDocUrl(cnicBackPath),
                                isUploading = uploadingSide == "back",
                                onClick = { activeCameraSide = "back" },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("capture_cnic_back")
                            )
                        }

                        if (scanMismatches.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
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
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "CNIC mismatch",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(20.dp)
                                    )
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
                                            Text(
                                                text = "• $reason",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ErrorRed
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                                .onFocusEvent { if (it.isFocused) scope.launch { phoneBringer.bringIntoView() } }
                                .testTag("kyc_phone")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = whatsappDigits,
                            onValueChange = { raw -> whatsappDigits = Formatters.sanitizeLocalPakistanPhone(raw) },
                            label = { Text("WhatsApp Number (11 digits)") },
                            placeholder = { Text("03001234567") },
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
                                    if (whatsappDigits.isNotEmpty() && !whatsappDigits.startsWith("0")) {
                                        "Must start with 0 (e.g. 03001234567)"
                                    } else {
                                        "${whatsappDigits.length}/11 digits"
                                    }
                                )
                            },
                            isError = whatsappDigits.isNotEmpty() && !Formatters.isValidLocalPakistanPhone(whatsappDigits),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(whatsappBringer)
                                .onFocusEvent { if (it.isFocused) scope.launch { whatsappBringer.bringIntoView() } }
                                .testTag("kyc_whatsapp")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        when {
                            fullName.isBlank() -> onToast("Enter your full legal name", true)
                            cnicDigits.length != 13 -> onToast("Enter a valid 13-digit CNIC", true)
                            issueDate.isBlank() || expiryDate.isBlank() -> onToast("Select CNIC issue and expiry dates", true)
                            expiryDate <= issueDate -> onToast("CNIC expiry date must be after issue date", true)
                            cnicFrontPath.isBlank() || cnicBackPath.isBlank() -> onToast("Scan both CNIC front and back via camera", true)
                            !Formatters.isValidLocalPakistanPhone(phoneDigits) -> onToast("Enter valid 11-digit mobile starting with 0 (e.g. 03001234567)", true)
                            !Formatters.isValidLocalPakistanPhone(whatsappDigits) -> onToast("Enter valid 11-digit WhatsApp starting with 0 (e.g. 03001234567)", true)
                            else -> {
                                val matchResult = CnicMatchValidator.validateAgainstEnteredDetails(
                                    fullName = fullName,
                                    cnicDigits = cnicDigits,
                                    issueDateIso = issueDate,
                                    expiryDateIso = expiryDate,
                                    frontText = frontScanOcrText,
                                    backText = backScanOcrText
                                )
                                if (!matchResult.isMatch) {
                                    scanMismatches = matchResult.mismatches
                                    onToast("Entered details do not match your scanned CNIC card. Please fix highlighted fields.", true)
                                } else {
                                    scanMismatches = emptyList()
                                    step = 2
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("kyc_next_step_button")
                ) {
                    Text(
                        text = "Continue to Address",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Step 2: Address + Native GPS
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
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

                        val cityBringer = remember { BringIntoViewRequester() }
                        val stateBringer = remember { BringIntoViewRequester() }
                        val streetBringer = remember { BringIntoViewRequester() }
                        val addrBringer = remember { BringIntoViewRequester() }
                        val addr2Bringer = remember { BringIntoViewRequester() }
                        val zipBringer = remember { BringIntoViewRequester() }

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
                                    .onFocusEvent { if (it.isFocused) scope.launch { cityBringer.bringIntoView() } }
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
                                    .onFocusEvent { if (it.isFocused) scope.launch { stateBringer.bringIntoView() } }
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
                                .onFocusEvent { if (it.isFocused) scope.launch { streetBringer.bringIntoView() } }
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
                                .onFocusEvent { if (it.isFocused) scope.launch { addrBringer.bringIntoView() } }
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
                                .onFocusEvent { if (it.isFocused) scope.launch { addr2Bringer.bringIntoView() } }
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
                                .onFocusEvent { if (it.isFocused) scope.launch { zipBringer.bringIntoView() } }
                                .testTag("kyc_zip")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { step = 1 },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(0.36f)
                            .defaultMinSize(minHeight = 52.dp)
                    ) {
                        Text("Back", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = {
                            when {
                                city.isBlank() || street.isBlank() || state.isBlank() || address.isBlank() -> {
                                    onToast("Please fill city, state, street, and address", true)
                                }
                                zipCode.length != 5 -> {
                                    onToast("Enter a 5-digit Pakistan postal code", true)
                                }
                                else -> {
                                    onSubmit(
                                        SellerIdentityDetails(
                                            fullName = fullName.trim(),
                                            cnic = cnicDigits,
                                            gender = gender,
                                            issueDate = issueDate,
                                            expiryDate = expiryDate,
                                            cnicFront = cnicFrontPath,
                                            cnicBack = cnicBackPath,
                                            phone = phoneE164,
                                            whatsapp = whatsappE164,
                                            city = city.trim(),
                                            street = street.trim(),
                                            state = state.trim(),
                                            address = address.trim(),
                                            address2 = address2.trim(),
                                            zipCode = zipCode,
                                            latitude = latitude,
                                            longitude = longitude
                                        )
                                    )
                                }
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(0.64f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("kyc_submit_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Submit Verification", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
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
private fun CnicCaptureTile(
    title: String,
    path: String,
    signedUrl: String?,
    isUploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .aspectRatio(85.6f / 54f)
            .border(
                width = 1.5.dp,
                color = if (path.isNotBlank()) SuccessGreen else ZulvazaOrange.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isUploading, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isUploading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Uploading...", style = MaterialTheme.typography.labelSmall)
                }
            } else if (path.isNotBlank() && !signedUrl.isNullOrBlank()) {
                SignedRemoteImage(
                    url = signedUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
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
                            .size(16.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (path.isNotBlank()) "Captured ✓" else "Auto-camera",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (path.isNotBlank()) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
