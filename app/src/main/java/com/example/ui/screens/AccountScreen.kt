package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.BuildConfig
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.MfaFactor
import com.example.data.model.MfaTotpEnrollment
import com.example.data.model.PasswordStrength
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.UserSession
import com.example.data.model.VerificationStatus
import com.example.data.remote.AppUpdateStatus
import com.example.data.remote.PasswordChangeOtpMode
import com.example.data.remote.SupabaseConfig
import com.example.ui.components.OtpCodeBoxes
import com.example.ui.components.PinSetupModal
import com.example.ui.components.QrCodeImage
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.StatusPill
import com.example.ui.components.VerifyCurrentPinModal
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AccountSubScreen {
    MENU,
    VERIFICATION,
    SECURITY_HUB,
    SECURITY_PIN,
    SECURITY_TWO_FACTOR,
    SECURITY_PASSWORD,
    SECURITY_DEVICES,
    UPDATES
}

@Composable
fun AccountScreen(
    session: UserSession?,
    verification: SellerVerification,
    store: SellerStore?,
    mfaFactors: List<MfaFactor>,
    mfaEnrollment: MfaTotpEnrollment?,
    activeDevices: List<ActiveDeviceSession>,
    appUpdateStatus: AppUpdateStatus,
    hasAppPin: Boolean,
    isAppPinEnabled: Boolean,
    isBusy: Boolean,
    initialSubScreen: AccountSubScreen = AccountSubScreen.MENU,
    resolveStoreMedia: (String?) -> String?,
    resolveDocUrl: (String) -> String?,
    resolveFaceUrl: (String) -> String?,
    onOpenVerificationEditor: () -> Unit,
    onSetupAppPin: (String, () -> Unit) -> Unit,
    onToggleAppPinWithCurrentPin: (String, Boolean, (Boolean) -> Unit) -> Unit,
    onDeleteAppPinWithCurrentPin: (String, (Boolean) -> Unit) -> Unit,
    onRequestPasswordOtp: (String, (PasswordChangeOtpMode, String) -> Unit) -> Unit,
    onConfirmPasswordChange: (String, String, PasswordChangeOtpMode, String, () -> Unit) -> Unit,
    onStartTotpEnroll: () -> Unit,
    onConfirmTotpEnroll: (String) -> Unit,
    onCancelTotpEnroll: () -> Unit,
    onDisableTotp: (String, String, () -> Unit) -> Unit,
    onRefreshDevices: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onSignOut: () -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var subScreen by remember(initialSubScreen) { mutableStateOf(initialSubScreen) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to access your ZULVAZA Seller Center.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val displayDevices = if (activeDevices.isNotEmpty()) {
        activeDevices
    } else {
        listOf(
            ActiveDeviceSession(
                deviceId = "current",
                deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android Device" },
                osVersion = "Android ${Build.VERSION.RELEASE}",
                ipAddress = "Active Session",
                cityCountry = "Pakistan",
                isOnline = true,
                isCurrentDevice = true
            )
        )
    }

    val verifiedFactor = mfaFactors.firstOrNull {
        it.factorType.equals("totp", true) && it.status.equals("verified", true)
    }

    when (subScreen) {
        AccountSubScreen.VERIFICATION -> VerificationDetailsPage(
            verification = verification,
            resolveDocUrl = resolveDocUrl,
            resolveFaceUrl = resolveFaceUrl,
            onOpenEditor = onOpenVerificationEditor,
            onBack = { subScreen = AccountSubScreen.MENU }
        )

        AccountSubScreen.SECURITY_HUB -> SecurityHubSubScreen(
            hasAppPin = hasAppPin,
            isAppPinEnabled = isAppPinEnabled,
            is2faActive = verifiedFactor != null,
            deviceCount = displayDevices.size,
            onOpenAppPin = { subScreen = AccountSubScreen.SECURITY_PIN },
            onOpenTwoFactor = { subScreen = AccountSubScreen.SECURITY_TWO_FACTOR },
            onOpenPassword = { subScreen = AccountSubScreen.SECURITY_PASSWORD },
            onOpenDevices = { subScreen = AccountSubScreen.SECURITY_DEVICES },
            onBack = { subScreen = AccountSubScreen.MENU }
        )

        AccountSubScreen.SECURITY_PIN -> AppLockPinPage(
            hasAppPin = hasAppPin,
            isAppPinEnabled = isAppPinEnabled,
            onSetupAppPin = onSetupAppPin,
            onToggleAppPinWithCurrentPin = onToggleAppPinWithCurrentPin,
            onDeleteAppPinWithCurrentPin = onDeleteAppPinWithCurrentPin,
            onBack = { subScreen = AccountSubScreen.SECURITY_HUB }
        )

        AccountSubScreen.SECURITY_TWO_FACTOR -> TwoFactorSecurityPage(
            verifiedFactor = verifiedFactor,
            mfaEnrollment = mfaEnrollment,
            isBusy = isBusy,
            onStartTotpEnroll = onStartTotpEnroll,
            onConfirmTotpEnroll = onConfirmTotpEnroll,
            onCancelTotpEnroll = onCancelTotpEnroll,
            onDisableTotp = onDisableTotp,
            onToast = onToast,
            onBack = { subScreen = AccountSubScreen.SECURITY_HUB }
        )

        AccountSubScreen.SECURITY_PASSWORD -> ChangePasswordPage(
            isBusy = isBusy,
            onRequestPasswordOtp = onRequestPasswordOtp,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onToast = onToast,
            onBack = { subScreen = AccountSubScreen.SECURITY_HUB }
        )

        AccountSubScreen.SECURITY_DEVICES -> DeviceLoginsPage(
            devices = displayDevices,
            isBusy = isBusy,
            onRefreshDevices = onRefreshDevices,
            onRemoveDevice = onRemoveDevice,
            onBack = { subScreen = AccountSubScreen.SECURITY_HUB }
        )

        AccountSubScreen.UPDATES -> UpdatesSubScreen(
            updateStatus = appUpdateStatus,
            onCheckForUpdate = onCheckForUpdate,
            onDownloadUpdate = onDownloadUpdate,
            onInstallUpdate = onInstallUpdate,
            onBack = { subScreen = AccountSubScreen.MENU }
        )

        AccountSubScreen.MENU -> {
            BackHandler { onBack() }
            val accountName = store?.shopName?.takeIf { it.isNotBlank() }
                ?: verification.details.fullName.takeIf { it.isNotBlank() }
                ?: "ZULVAZA Seller"

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    }
                }

                // Account Info (No Seller ID, No Account Role)
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_account_info_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Account Info",
                                style = MaterialTheme.typography.labelLarge,
                                color = ZulvazaOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(ZulvazaNavy),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val logo = resolveStoreMedia(store?.logoPath)
                                    if (!logo.isNullOrBlank()) {
                                        SignedRemoteImage(
                                            url = logo,
                                            contentDescription = "Store logo",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = ZulvazaOrange,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = accountName,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = session?.email ?: "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Verification Entry (displays submitted KYC details & status)
                item {
                    SettingsMenuNavigationCard(
                        icon = Icons.Default.VerifiedUser,
                        iconTint = SuccessGreen,
                        title = "Verification",
                        subtitle = "Submitted CNIC, legal name, contact & address info",
                        badgeText = verification.status.label,
                        badgePositive = verification.status == VerificationStatus.APPROVED,
                        onClick = { subScreen = AccountSubScreen.VERIFICATION },
                        testTag = "account_verification"
                    )
                }

                // Security Hub Entry
                item {
                    SettingsMenuNavigationCard(
                        icon = Icons.Default.Security,
                        iconTint = ZulvazaOrange,
                        title = "Security",
                        subtitle = "App Lock PIN, Two-Factor, Password & Device logins",
                        onClick = { subScreen = AccountSubScreen.SECURITY_HUB },
                        testTag = "account_security"
                    )
                }

                // App Updates Entry (No GitHub mentions)
                item {
                    SettingsMenuNavigationCard(
                        icon = Icons.Default.SystemUpdate,
                        iconTint = ZulvazaNavy,
                        title = "App Updates",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} • Check for new updates",
                        onClick = { subScreen = AccountSubScreen.UPDATES },
                        testTag = "account_updates"
                    )
                }

                // Log Out
                item {
                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("account_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Log Out",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuNavigationCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgePositive: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(
                            text = badgeText,
                            bg = if (badgePositive) SuccessGreenSoft else MaterialTheme.colorScheme.surfaceVariant,
                            fg = if (badgePositive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecurityHubSubScreen(
    hasAppPin: Boolean,
    isAppPinEnabled: Boolean,
    is2faActive: Boolean,
    deviceCount: Int,
    onOpenAppPin: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenDevices: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("security_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text("Security", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Manage your account protection & sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Security overview banner
        Surface(
            color = if (is2faActive || isAppPinEnabled) SuccessGreenSoft else ZulvazaOrange.copy(alpha = 0.08f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.dp,
                if (is2faActive || isAppPinEnabled) SuccessGreen.copy(alpha = 0.35f) else ZulvazaOrange.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (is2faActive || isAppPinEnabled) SuccessGreen.copy(alpha = 0.16f) else ZulvazaOrange.copy(alpha = 0.16f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (is2faActive || isAppPinEnabled) SuccessGreen else ZulvazaOrange
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            is2faActive && isAppPinEnabled -> "PIN & 2FA Protection Active"
                            is2faActive -> "2FA Protection Active"
                            isAppPinEnabled -> "App Lock PIN Active"
                            else -> "Enhance Account Protection"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (is2faActive || isAppPinEnabled) SuccessGreen else ZulvazaNavy
                    )
                    Text(
                        text = if (is2faActive || isAppPinEnabled) {
                            "Your seller center is safeguarded with local & cloud security."
                        } else {
                            "Set a 5-digit App PIN or Two-Factor Authenticator for stronger security."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4 Dedicated Security Pages
        SettingsMenuNavigationCard(
            icon = Icons.Default.Lock,
            iconTint = ZulvazaNavy,
            title = "App Lock PIN",
            subtitle = "Require a 5-digit PIN when opening Seller Center",
            badgeText = when {
                isAppPinEnabled -> "On"
                hasAppPin -> "Off"
                else -> "Not Set"
            },
            badgePositive = isAppPinEnabled,
            onClick = onOpenAppPin,
            testTag = "security_open_app_pin"
        )

        SettingsMenuNavigationCard(
            icon = Icons.Default.PhonelinkLock,
            iconTint = ZulvazaOrange,
            title = "Two-Factor Authenticator",
            subtitle = "Manage TOTP app verification, QR setup & backup",
            badgeText = if (is2faActive) "Active" else "Off",
            badgePositive = is2faActive,
            onClick = onOpenTwoFactor,
            testTag = "security_open_two_factor"
        )

        SettingsMenuNavigationCard(
            icon = Icons.Default.LockReset,
            iconTint = ZulvazaNavy,
            title = "Change Password",
            subtitle = "Update your login password securely with email OTP",
            onClick = onOpenPassword,
            testTag = "security_open_password"
        )

        SettingsMenuNavigationCard(
            icon = Icons.Default.Devices,
            iconTint = SuccessGreen,
            title = "Device Logins",
            subtitle = "Inspect signed-in devices & revoke access",
            badgeText = "$deviceCount Active",
            badgePositive = true,
            onClick = onOpenDevices,
            testTag = "security_open_devices"
        )
    }
}

@Composable
private fun AppLockPinPage(
    hasAppPin: Boolean,
    isAppPinEnabled: Boolean,
    onSetupAppPin: (String, () -> Unit) -> Unit,
    onToggleAppPinWithCurrentPin: (String, Boolean, (Boolean) -> Unit) -> Unit,
    onDeleteAppPinWithCurrentPin: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var showSetupPinModal by remember { mutableStateOf(false) }
    var pendingToggleTarget by remember { mutableStateOf<Boolean?>(null) }
    var showVerifyForChangePin by remember { mutableStateOf(false) }
    var showVerifyForDeletePin by remember { mutableStateOf(false) }

    if (showSetupPinModal) {
        PinSetupModal(
            title = if (hasAppPin) "Set New 5-Digit PIN" else "Set 5-Digit Security PIN",
            subtitle = "Enter a 5-digit PIN to lock ZULVAZA Seller Center on launch.",
            allowSkip = false,
            onCompleted = { newPin ->
                onSetupAppPin(newPin) {
                    showSetupPinModal = false
                }
            },
            onDismiss = { showSetupPinModal = false }
        )
    }

    if (pendingToggleTarget != null) {
        val turnOn = pendingToggleTarget!!
        VerifyCurrentPinModal(
            title = if (turnOn) "Verify PIN to Enable Lock" else "Verify PIN to Disable Lock",
            subtitle = "Enter your current 5-digit security PIN to confirm.",
            onVerifyPin = { entered, onResult ->
                onToggleAppPinWithCurrentPin(entered, turnOn) { ok ->
                    onResult(ok)
                    if (ok) {
                        pendingToggleTarget = null
                    }
                }
            },
            onDismiss = { pendingToggleTarget = null }
        )
    }

    if (showVerifyForChangePin) {
        VerifyCurrentPinModal(
            title = "Verify Current PIN",
            subtitle = "Enter your existing 5-digit PIN before setting a new one.",
            onVerifyPin = { entered, onResult ->
                // Verify without altering enabled state (pass current state)
                onToggleAppPinWithCurrentPin(entered, isAppPinEnabled) { ok ->
                    onResult(ok)
                    if (ok) {
                        showVerifyForChangePin = false
                        showSetupPinModal = true
                    }
                }
            },
            onDismiss = { showVerifyForChangePin = false }
        )
    }

    if (showVerifyForDeletePin) {
        VerifyCurrentPinModal(
            title = "Verify PIN to Remove Lock",
            subtitle = "Enter your 5-digit security PIN to delete App Lock.",
            onVerifyPin = { entered, onResult ->
                onDeleteAppPinWithCurrentPin(entered) { ok ->
                    onResult(ok)
                    if (ok) {
                        showVerifyForDeletePin = false
                    }
                }
            },
            onDismiss = { showVerifyForDeletePin = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("pin_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("App Lock PIN", style = MaterialTheme.typography.headlineSmall)
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isAppPinEnabled) SuccessGreen else ZulvazaNavy).copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isAppPinEnabled) SuccessGreen else ZulvazaNavy
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("5-Digit Security PIN", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    isAppPinEnabled -> "Enabled on app launch"
                                    hasAppPin -> "PIN saved (Currently Off)"
                                    else -> "Not configured"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isAppPinEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (hasAppPin) {
                        Switch(
                            checked = isAppPinEnabled,
                            onCheckedChange = { turnOn ->
                                pendingToggleTarget = turnOn
                            },
                            modifier = Modifier.testTag("app_pin_toggle_switch")
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )

                Text(
                    text = "Lock ZULVAZA Seller Center with a salted 5-digit PIN so nobody else using your phone can access orders or store settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!hasAppPin) {
                    Button(
                        onClick = { showSetupPinModal = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("setup_app_pin_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set 5-Digit Security PIN", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showVerifyForChangePin = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("change_app_pin_button")
                        ) {
                            Text("Change PIN", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { showVerifyForDeletePin = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("delete_app_pin_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete PIN", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoFactorSecurityPage(
    verifiedFactor: MfaFactor?,
    mfaEnrollment: MfaTotpEnrollment?,
    isBusy: Boolean,
    onStartTotpEnroll: () -> Unit,
    onConfirmTotpEnroll: (String) -> Unit,
    onCancelTotpEnroll: () -> Unit,
    onDisableTotp: (String, String, () -> Unit) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enrollCode by remember { mutableStateOf("") }
    var disableCode by remember { mutableStateOf("") }
    var totpVerifyModalTitle by remember { mutableStateOf<String?>(null) }
    var isSavingQr by remember { mutableStateOf(false) }

    if (totpVerifyModalTitle != null && verifiedFactor != null) {
        AlertDialog(
            onDismissRequest = {
                disableCode = ""
                totpVerifyModalTitle = null
            },
            title = { Text(totpVerifyModalTitle!!) },
            text = {
                Column {
                    Text(
                        "Enter the current 6-digit code from your authenticator app to verify before turning off or removing 2FA.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OtpCodeBoxes(
                        code = disableCode,
                        onCodeChange = { disableCode = it },
                        onComplete = { entered ->
                            if (!isBusy) {
                                onDisableTotp(verifiedFactor.id, entered) {
                                    disableCode = ""
                                    totpVerifyModalTitle = null
                                }
                            }
                        },
                        enabled = !isBusy
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDisableTotp(verifiedFactor.id, disableCode) {
                            disableCode = ""
                            totpVerifyModalTitle = null
                        }
                    },
                    enabled = disableCode.length == 6 && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Verify & Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disableCode = ""
                        totpVerifyModalTitle = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("two_factor_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Two-Factor Authenticator", style = MaterialTheme.typography.headlineSmall)
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = (if (verifiedFactor != null) SuccessGreen else ZulvazaOrange).copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhonelinkLock,
                                    contentDescription = null,
                                    tint = if (verifiedFactor != null) SuccessGreen else ZulvazaOrange
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Authenticator App (TOTP)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    verifiedFactor != null -> "Active & Verified"
                                    mfaEnrollment != null -> "Setup in Progress"
                                    else -> "Currently Off"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (verifiedFactor != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = verifiedFactor != null || mfaEnrollment != null,
                        onCheckedChange = { turnOn ->
                            if (turnOn) {
                                if (verifiedFactor == null && mfaEnrollment == null && !isBusy) {
                                    onStartTotpEnroll()
                                }
                            } else {
                                if (verifiedFactor != null) {
                                    disableCode = ""
                                    totpVerifyModalTitle = "Verify Code to Turn Off 2FA"
                                } else if (mfaEnrollment != null) {
                                    enrollCode = ""
                                    onCancelTotpEnroll()
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("totp_toggle_switch")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
                Text(
                    "Use Google Authenticator, Authy, or 1Password for a 6-digit login code. Turning OFF or removing 2FA strictly requires entering a valid 6-digit code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (verifiedFactor != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = SuccessGreenSoft,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Enabled",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    Text(
                                        text = "2FA protection is active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = {
                                    disableCode = ""
                                    totpVerifyModalTitle = "Verify Code to Remove Authenticator"
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("totp_remove_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (mfaEnrollment != null) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Scan or Save QR Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Scan this QR in Google Authenticator or save it to your gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    QrCodeImage(content = mfaEnrollment.totpUri, sizeDp = 196.dp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "TOTP Secret", mfaEnrollment.secret)
                                onToast("Secret key copied", false)
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("totp_copy_secret_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Key", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isSavingQr) {
                                    isSavingQr = true
                                    scope.launch {
                                        val saved = Formatters.saveQrToGallery(context, mfaEnrollment.totpUri)
                                        isSavingQr = false
                                        if (saved) {
                                            onToast("QR code saved to Gallery (Pictures/ZULVAZA)", false)
                                        } else {
                                            onToast("Could not save QR code to Gallery", true)
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingQr,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("totp_save_qr_gallery_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isSavingQr) "Saving..." else "Save QR",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Secret: ${mfaEnrollment.secret}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, "TOTP Secret", mfaEnrollment.secret)
                                    onToast("Secret key copied", false)
                                },
                                modifier = Modifier.testTag("totp_inline_copy_secret_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy TOTP secret",
                                    tint = ZulvazaOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter 6-digit code to activate", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    OtpCodeBoxes(
                        code = enrollCode,
                        onCodeChange = { enrollCode = it },
                        onComplete = { code -> if (!isBusy) onConfirmTotpEnroll(code) },
                        enabled = !isBusy
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                enrollCode = ""
                                onCancelTotpEnroll()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { onConfirmTotpEnroll(enrollCode) },
                            enabled = enrollCode.length == 6 && !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Verify & Enable", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangePasswordPage(
    isBusy: Boolean,
    onRequestPasswordOtp: (String, (PasswordChangeOtpMode, String) -> Unit) -> Unit,
    onConfirmPasswordChange: (String, String, PasswordChangeOtpMode, String, () -> Unit) -> Unit,
    onToast: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    var otpSent by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpMode by remember { mutableStateOf(PasswordChangeOtpMode.REAUTHENTICATE_NONCE) }
    var otpEmail by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(otpSent, resendSeconds) {
        if (otpSent && resendSeconds > 0) {
            delay(1000)
            resendSeconds--
        }
    }

    val currentPwRequester = remember { BringIntoViewRequester() }
    val newPwRequester = remember { BringIntoViewRequester() }
    val confirmPwRequester = remember { BringIntoViewRequester() }
    val strength = Formatters.evaluatePasswordStrength(newPassword)
    val lengthOk = newPassword.length >= 8
    val hasLetterAndNumber = newPassword.any { it.isDigit() } && newPassword.any { it.isLetter() }
    val hasSymbol = newPassword.any { !it.isLetterOrDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("password_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Change Password", style = MaterialTheme.typography.headlineSmall)
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = (if (otpSent) ZulvazaOrange else ZulvazaNavy).copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (otpSent) Icons.Default.MarkEmailRead else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (otpSent) ZulvazaOrange else ZulvazaNavy
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (otpSent) "Verify Email OTP" else "Update Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (otpSent) {
                                "Enter the 6-digit code sent to $otpEmail"
                            } else {
                                "Verify current password, choose a strong password, then confirm via Email OTP"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!otpSent) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showCurrent = !showCurrent }) {
                                Icon(
                                    imageVector = if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showCurrent) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(currentPwRequester)
                            .onFocusEvent { if (it.isFocused) scope.launch { delay(180); currentPwRequester.bringIntoView() } }
                            .testTag("current_password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showNew = !showNew }) {
                                Icon(
                                    imageVector = if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showNew) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(newPwRequester)
                            .onFocusEvent { if (it.isFocused) scope.launch { delay(180); newPwRequester.bringIntoView() } }
                            .testTag("new_password_input")
                    )

                    if (newPassword.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val (label, color) = when (strength) {
                            PasswordStrength.STRONG -> "Strong password" to SuccessGreen
                            PasswordStrength.MEDIUM -> "Medium strength" to WarningAmber
                            else -> "Weak (min 8 chars)" to ErrorRed
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            repeat(3) { idx ->
                                val filled = idx < strength.level
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) color else MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                            Text("${newPassword.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PasswordRuleRow("Minimum 8 characters", lengthOk)
                        PasswordRuleRow("Letters & numbers", hasLetterAndNumber)
                        PasswordRuleRow("Special symbol (!@#$%^&*)", hasSymbol)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showConfirm = !showConfirm }) {
                                Icon(
                                    imageVector = if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showConfirm) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(confirmPwRequester)
                            .onFocusEvent { if (it.isFocused) scope.launch { delay(180); confirmPwRequester.bringIntoView() } }
                            .testTag("confirm_password_input")
                    )

                    if (confirmPassword.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val matched = newPassword == confirmPassword
                        Text(
                            text = if (matched) "Passwords match" else "Passwords do not match",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (matched) SuccessGreen else ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            when {
                                currentPassword.isEmpty() -> onToast("Enter your current password", true)
                                newPassword.length < 8 -> onToast("New password must be at least 8 characters", true)
                                newPassword != confirmPassword -> onToast("New passwords do not match", true)
                                currentPassword == newPassword -> onToast("New password must be different from current password", true)
                                else -> onRequestPasswordOtp(currentPassword) { mode, email ->
                                    otpMode = mode
                                    otpEmail = email
                                    otpCode = ""
                                    resendSeconds = 60
                                    otpSent = true
                                }
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("send_password_otp_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Send Verification Code", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Surface(
                        color = ZulvazaOrange.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "A 6-digit security verification code has been sent to your registered email ($otpEmail). Enter it below to complete your password update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OtpCodeBoxes(
                        code = otpCode,
                        onCodeChange = { otpCode = it },
                        onComplete = { entered ->
                            if (!isBusy) {
                                onConfirmPasswordChange(newPassword, entered, otpMode, otpEmail) {
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    otpCode = ""
                                    otpSent = false
                                }
                            }
                        },
                        enabled = !isBusy
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            onConfirmPasswordChange(newPassword, otpCode, otpMode, otpEmail) {
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                otpCode = ""
                                otpSent = false
                            }
                        },
                        enabled = otpCode.length == 6 && !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("confirm_password_otp_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Verify OTP & Update Password", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                otpSent = false
                                otpCode = ""
                            },
                            enabled = !isBusy
                        ) {
                            Text("Change Details")
                        }
                        TextButton(
                            onClick = {
                                onRequestPasswordOtp(currentPassword) { mode, email ->
                                    otpMode = mode
                                    otpEmail = email
                                    resendSeconds = 60
                                }
                            },
                            enabled = resendSeconds == 0 && !isBusy
                        ) {
                            Text(if (resendSeconds > 0) "Resend (${resendSeconds}s)" else "Resend Code")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordRuleRow(label: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceLoginsPage(
    devices: List<ActiveDeviceSession>,
    isBusy: Boolean,
    onRefreshDevices: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        onRefreshDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("devices_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Device Logins", style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedButton(
                onClick = onRefreshDevices,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sync", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        devices.forEach { device ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (device.isCurrentDevice) ZulvazaOrange.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val deviceDescriptor = "${device.deviceName} ${device.osVersion}".lowercase()
                    val deviceIcon = when {
                        listOf("windows", "mac", "linux", "desktop", "laptop", "pc").any { it in deviceDescriptor } -> Icons.Default.LaptopMac
                        listOf("web", "browser", "chrome", "safari", "firefox", "edge").any { it in deviceDescriptor } -> Icons.Default.Language
                        else -> Icons.Default.PhoneAndroid
                    }
                    Surface(
                        shape = CircleShape,
                        color = (if (device.isOnline) SuccessGreen else MaterialTheme.colorScheme.outline).copy(alpha = 0.14f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = deviceIcon,
                                contentDescription = null,
                                tint = if (device.isOnline) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (device.isOnline) SuccessGreen else MaterialTheme.colorScheme.outline)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (device.isOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (device.isOnline) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            if (device.isCurrentDevice) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(
                                    text = "This device",
                                    bg = ZulvazaOrange.copy(alpha = 0.14f),
                                    fg = ZulvazaOrange
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${device.deviceName} • ${device.osVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IP: ${device.ipAddress}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = device.cityCountry,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!device.isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onRemoveDevice(device.deviceId) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("remove_device_${device.deviceId}")
                        ) {
                            Text("Remove", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationDetailsPage(
    verification: SellerVerification,
    resolveDocUrl: (String) -> String?,
    resolveFaceUrl: (String) -> String?,
    onOpenEditor: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val d = verification.details
    val statusColor = when (verification.status) {
        VerificationStatus.APPROVED -> SuccessGreen
        VerificationStatus.PENDING -> ZulvazaOrange
        VerificationStatus.REJECTED, VerificationStatus.SUSPENDED -> ErrorRed
        VerificationStatus.UNSUBMITTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("verification_details_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Verification Details", style = MaterialTheme.typography.headlineSmall)
        }

        // Status Card
        Surface(
            color = statusColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = statusColor)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status: ${verification.status.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (!verification.reason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = verification.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    } else if (!verification.submittedAt.isNullOrBlank()) {
                        Text(
                            text = "Submitted: ${Formatters.formatIsoTimestamp(verification.submittedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Submitted Identity Info
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Identity Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                VerificationDetailRow("Full Name", d.fullName.ifBlank { "—" })
                VerificationDetailRow("CNIC Number", Formatters.formatCnicDisplay(d.cnic).ifBlank { "—" })
                VerificationDetailRow("Date of Birth", Formatters.formatIsoToDmyDisplay(d.dateOfBirth).ifBlank { "—" })
                VerificationDetailRow("Gender", d.gender.replaceFirstChar { it.uppercase() }.ifBlank { "—" })
                VerificationDetailRow("Issue Date", Formatters.formatIsoToDmyDisplay(d.issueDate).ifBlank { "—" })
                VerificationDetailRow("Expiry Date", Formatters.formatIsoToDmyDisplay(d.expiryDate).ifBlank { "—" })
                VerificationDetailRow("Mobile Phone", d.phone.ifBlank { "—" })
                VerificationDetailRow("WhatsApp", d.whatsapp.ifBlank { "—" })
            }
        }

        // Submitted Address Info
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pickup & Business Address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                VerificationDetailRow("City", d.city.ifBlank { "—" })
                VerificationDetailRow("Province / State", d.state.ifBlank { "—" })
                VerificationDetailRow("Street / Block", d.street.ifBlank { "—" })
                VerificationDetailRow("Address", d.address.ifBlank { "—" })
                if (d.address2.isNotBlank()) {
                    VerificationDetailRow("Address Line 2", d.address2)
                }
                VerificationDetailRow("Postal Code", d.zipCode.ifBlank { "—" })
            }
        }

        // Verified Face Photo Preview
        if (d.facePhoto.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignedRemoteImage(
                        url = resolveFaceUrl(d.facePhoto),
                        contentDescription = "Verified face photo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Live Face Liveness Photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Verified 5-angle selfie submitted for KYC review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // CNIC Photos Preview
        if (d.cnicFront.isNotBlank() || d.cnicBack.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Submitted CNIC Documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Front Side", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            SignedRemoteImage(
                                url = resolveDocUrl(d.cnicFront),
                                contentDescription = "CNIC Front",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Back Side", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            SignedRemoteImage(
                                url = resolveDocUrl(d.cnicBack),
                                contentDescription = "CNIC Back",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }
        }

        if (verification.status == VerificationStatus.REJECTED || verification.status == VerificationStatus.UNSUBMITTED) {
            Button(
                onClick = onOpenEditor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag("resubmit_verification_button")
            ) {
                Text("Update & Resubmit Verification", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VerificationDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun UpdatesSubScreen(
    updateStatus: AppUpdateStatus,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("updates_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("App Updates", style = MaterialTheme.typography.headlineSmall)
        }

        // Current Installed Version & Check Button
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = ZulvazaOrange.copy(alpha = 0.14f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = ZulvazaOrange)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ZULVAZA Seller Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "App Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusPill(
                            text = if (BuildConfig.DEBUG) "Debug Signed Build" else "Release Signed Build",
                            bg = if (BuildConfig.DEBUG) WarningAmberSoft else SuccessGreenSoft,
                            fg = if (BuildConfig.DEBUG) WarningAmber else SuccessGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCheckForUpdate,
                    enabled = updateStatus !is AppUpdateStatus.Checking && updateStatus !is AppUpdateStatus.Downloading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .testTag("check_updates_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Updates", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // What's New / Installed Release Notes Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "What's New in v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                SupabaseConfig.CURRENT_VERSION_CHANGELOG.forEach { note ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (updateStatus) {
            AppUpdateStatus.Idle -> Unit

            AppUpdateStatus.Checking -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = ZulvazaOrange)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Checking for latest release...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is AppUpdateStatus.UpToDate -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SuccessGreenSoft,
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "You are up to date (v${updateStatus.currentVersion})",
                                style = MaterialTheme.typography.titleSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "This is the latest available version of ZULVAZA Seller Center.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            is AppUpdateStatus.UpdateAvailable -> {
                val rel = updateStatus.release
                val sizeMb = if (rel.apkSizeBytes > 0) {
                    String.format("%.1f MB", rel.apkSizeBytes / (1024.0 * 1024.0))
                } else {
                    "App Package"
                }
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, ZulvazaOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "Update Available: v${rel.versionName}",
                                bg = ZulvazaOrange.copy(alpha = 0.14f),
                                fg = ZulvazaOrange
                            )
                            Text(sizeMb, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(rel.releaseTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            rel.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!rel.isCompatibleSignature) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = WarningAmberSoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Signing mismatch: This update APK has a different signing key than your installed build. If Android rejects updating over it, open Releases and reinstall cleanly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningAmber
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onDownloadUpdate,
                            enabled = !rel.apkDownloadUrl.isNullOrBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (rel.apkDownloadUrl.isNullOrBlank()) "Installer unavailable" else "Download & Install Update",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (rel.htmlUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { runCatching { uriHandler.openUri(rel.htmlUrl) } },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 46.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Release Page", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            is AppUpdateStatus.Downloading -> {
                val pct = (updateStatus.progress * 100).toInt().coerceIn(0, 100)
                val dlMb = updateStatus.downloadedBytes / (1024.0 * 1024.0)
                val totalMb = updateStatus.totalBytes / (1024.0 * 1024.0)
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading v${updateStatus.release.versionName}...", style = MaterialTheme.typography.titleSmall)
                            Text("$pct%", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = ZulvazaOrange)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            color = ZulvazaOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (updateStatus.totalBytes > 0) {
                                String.format("%.1f / %.1f MB", dlMb, totalMb)
                            } else {
                                String.format("%.1f MB downloaded", dlMb)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is AppUpdateStatus.ReadyToInstall -> {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessGreenSoft),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Update v${updateStatus.release.versionName} Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onInstallUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Update", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (updateStatus.release.htmlUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { runCatching { uriHandler.openUri(updateStatus.release.htmlUrl) } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Having trouble? Download from Release Page", color = SuccessGreen)
                            }
                        }
                    }
                }
            }

            is AppUpdateStatus.Error -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ErrorRedSoft,
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                updateStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed
                            )
                        }
                        if (!updateStatus.htmlUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { runCatching { uriHandler.openUri(updateStatus.htmlUrl) } },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Release Page")
                            }
                        }
                    }
                }
            }
        }
    }
}
