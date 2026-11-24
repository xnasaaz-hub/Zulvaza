package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.data.model.VerificationStatus
import com.example.ui.components.OfflineBanner
import com.example.ui.components.ShareProductBottomSheet
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.copyToClipboard
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AccountSubScreen
import com.example.ui.screens.AddEditProductScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditStoreSheetContent
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProductDetailView
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StoreProfileScreen
import com.example.ui.screens.VerificationScreen
import com.example.ui.state.MainTab
import com.example.ui.state.RootDestination
import com.example.ui.state.SellerUiState
import com.example.ui.state.SellerViewModel
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.example.ui.theme.ZulvazaSellerTheme
import com.example.util.Formatters
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: SellerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.handleDeepLink(Formatters.parseDeepLink(intent?.data))

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.toast?.id) {
                if (state.toast != null) {
                    delay(3200)
                    viewModel.clearToast()
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationsAndStatusOnResume()
            }

            ZulvazaSellerTheme(themeMode = state.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OfflineBanner(isOnline = state.isOnline)
                        Box(modifier = Modifier.weight(1f)) {
                            ZulvazaRootContent(
                                state = state,
                                viewModel = viewModel
                            )

                            if (!state.permissionsPromptCompleted &&
                                state.rootDestination != RootDestination.SPLASH
                            ) {
                                PermissionsOnboardingOverlay(
                                    onComplete = viewModel::markPermissionsPromptCompleted
                                )
                            }
                        }
                    }

                    // Floating Toast Banner
                    AnimatedVisibility(
                        visible = state.toast != null,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        val toast = state.toast
                        if (toast != null) {
                            Surface(
                                color = if (toast.isError) ErrorRed else SuccessGreen,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (toast.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = toast.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleDeepLink(Formatters.parseDeepLink(intent.data))
    }
}

@Composable
private fun ZulvazaRootContent(
    state: SellerUiState,
    viewModel: SellerViewModel
) {
    when (state.rootDestination) {
        RootDestination.SPLASH -> {
            SplashScreen(
                currentStep = state.splashStep,
                statusText = state.splashStatusText
            )
        }
        RootDestination.SIGNUP -> {
            SignUpScreen(
                otpPendingEmail = state.otpPendingEmail,
                isBusy = state.isBusy,
                onSignUp = viewModel::signUp,
                onVerifyOtp = viewModel::verifySignupOtp,
                onResendOtp = viewModel::resendSignupOtp,
                onCancelOtp = viewModel::clearOtpPending,
                onGoToLogin = { viewModel.navigateRoot(RootDestination.LOGIN) },
                onBack = { viewModel.navigateRoot(RootDestination.LOGIN) }
            )
        }
        RootDestination.WELCOME, RootDestination.LOGIN -> {
            LoginScreen(
                requiresMfa = state.pendingMfaSession != null,
                loginOtpPendingEmail = state.loginOtpPendingEmail,
                recoveryPendingEmail = state.recoveryPendingEmail,
                isBusy = state.isBusy,
                onLogin = viewModel::signIn,
                onSendEmailOtp = viewModel::sendEmailLoginOtp,
                onVerifyEmailOtp = viewModel::verifyEmailLoginOtp,
                onCancelEmailOtp = viewModel::clearLoginOtpPending,
                onSendPasswordReset = viewModel::sendPasswordResetOtp,
                onVerifyRecoveryReset = viewModel::verifyRecoveryAndSetPassword,
                onCancelRecovery = viewModel::clearRecoveryPending,
                onVerifyMfa = viewModel::verifyLoginMfa,
                onCancelMfa = viewModel::cancelLoginMfa,
                onGoToSignUp = { viewModel.navigateRoot(RootDestination.SIGNUP) },
                onBack = {}
            )
        }
        RootDestination.VERIFICATION -> {
            VerificationScreen(
                verification = state.verification,
                store = state.store,
                canNavigateBack = state.openVerificationFromAccount &&
                    state.verification.status != VerificationStatus.UNSUBMITTED,
                isBusy = state.isBusy,
                resolveDocUrl = viewModel::resolveVerificationDocUrl,
                resolveFaceUrl = viewModel::resolveFacePhotoUrl,
                resolveStoreMedia = viewModel::resolveStoreMediaUrl,
                onUploadCnic = viewModel::uploadCnicCapture,
                onUploadFacePhoto = viewModel::uploadFacePhoto,
                onUploadStoreMediaBytes = viewModel::uploadStoreMediaBytes,
                onSubmit = viewModel::submitVerification,
                onToast = viewModel::showToast,
                onBack = viewModel::closeVerificationEditorToMain,
                onSignOut = viewModel::signOut
            )
        }
        RootDestination.MAIN -> {
            MainSellerShell(state = state, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSellerShell(
    state: SellerUiState,
    viewModel: SellerViewModel
) {
    val context = LocalContext.current

    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var resumingDraft by remember { mutableStateOf<ProductDraft?>(null) }
    var dashboardDetailProduct by remember { mutableStateOf<ProductItem?>(null) }
    var sharingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showEditStoreSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var settingsInitialSubScreen by remember { mutableStateOf(AccountSubScreen.MENU) }

    LaunchedEffect(state.openStoreProfileRequest) {
        if (state.openStoreProfileRequest) {
            viewModel.selectTab(MainTab.MY_STORE)
            viewModel.consumeStoreProfileRequest()
        }
    }

    if (sharingProduct != null) {
        ShareProductBottomSheet(
            product = sharingProduct!!,
            onDismiss = { sharingProduct = null },
            onCopied = { msg -> viewModel.showToast(msg) }
        )
    }

    if (showEditStoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditStoreSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            EditStoreSheetContent(
                initialStore = state.store,
                isBusy = state.isBusy,
                onSaveStore = { updated, onDone ->
                    viewModel.saveStore(updated) {
                        onDone()
                        showEditStoreSheet = false
                    }
                },
                onToast = viewModel::showToast,
                onDismiss = { showEditStoreSheet = false }
            )
        }
    }

    // Full-screen Notifications Center overlay
    if (showNotifications) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            NotificationsScreen(
                notifications = state.notifications,
                onMarkAllRead = viewModel::markAllNotificationsRead,
                onDeleteSelected = viewModel::deleteNotifications,
                onNotificationClick = { item ->
                    viewModel.markNotificationRead(item.id)
                    when (item.targetRoute) {
                        "updates" -> {
                            showNotifications = false
                            settingsInitialSubScreen = AccountSubScreen.UPDATES
                            showSettings = true
                        }
                        "devices" -> {
                            showNotifications = false
                            settingsInitialSubScreen = AccountSubScreen.SECURITY_DEVICES
                            showSettings = true
                        }
                        "verification" -> {
                            showNotifications = false
                            settingsInitialSubScreen = AccountSubScreen.VERIFICATION
                            showSettings = true
                        }
                    }
                },
                onBack = { showNotifications = false }
            )
        }
        return
    }

    // Full-screen Settings overlay
    if (showSettings) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AccountScreen(
                session = state.session,
                verification = state.verification,
                store = state.store,
                mfaFactors = state.mfaFactors,
                mfaEnrollment = state.mfaEnrollment,
                activeDevices = state.activeDevices,
                appUpdateStatus = state.appUpdateStatus,
                isBusy = state.isBusy,
                initialSubScreen = settingsInitialSubScreen,
                resolveStoreMedia = viewModel::resolveStoreMediaUrl,
                resolveDocUrl = viewModel::resolveVerificationDocUrl,
                resolveFaceUrl = viewModel::resolveFacePhotoUrl,
                onOpenVerificationEditor = {
                    showSettings = false
                    viewModel.openVerificationEditor()
                },
                onUpdatePassword = viewModel::updatePassword,
                onStartTotpEnroll = viewModel::startTotpEnrollment,
                onConfirmTotpEnroll = viewModel::confirmTotpEnrollment,
                onCancelTotpEnroll = viewModel::cancelTotpEnrollment,
                onDisableTotp = viewModel::disableTotpWithCode,
                onRefreshDevices = viewModel::refreshActiveDevices,
                onRemoveDevice = viewModel::removeDeviceSession,
                onCheckForUpdate = viewModel::checkForAppUpdate,
                onDownloadUpdate = viewModel::downloadAndInstallUpdate,
                onInstallUpdate = viewModel::installDownloadedUpdate,
                onSignOut = viewModel::signOut,
                onToast = viewModel::showToast,
                onBack = {
                    showSettings = false
                    settingsInitialSubScreen = AccountSubScreen.MENU
                }
            )
        }
        return
    }

    // Full-screen overlay editors/views inside authenticated shell
    if (editingProduct != null || resumingDraft != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AddEditProductScreen(
                editingProduct = editingProduct,
                resumingDraft = resumingDraft,
                sellerId = state.session?.userId.orEmpty(),
                isBusy = state.isBusy,
                resolveImageUrl = viewModel::resolveProductImageUrl,
                onUploadImage = viewModel::uploadProductImage,
                onUploadVideo = viewModel::uploadProductVideo,
                onSaveDraft = viewModel::saveOrAutosaveDraft,
                onSubmitProduct = { ep, name, cat, price, stock, desc, imgs, vid, attrs, draftId ->
                    viewModel.submitProduct(
                        editingProduct = ep,
                        name = name,
                        category = cat,
                        price = price,
                        stock = stock,
                        description = desc,
                        imagePaths = imgs,
                        videoPath = vid,
                        attributes = attrs,
                        draftIdToDelete = draftId
                    ) {
                        editingProduct = null
                        resumingDraft = null
                        viewModel.selectTab(MainTab.PRODUCTS)
                    }
                },
                onToast = viewModel::showToast,
                onBack = {
                    editingProduct = null
                    resumingDraft = null
                }
            )
        }
        return
    }

    if (dashboardDetailProduct != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ProductDetailView(
                product = dashboardDetailProduct!!,
                resolveImageUrl = viewModel::resolveProductImageUrl,
                resolveVideoUrl = viewModel::resolveProductVideoUrl,
                onBack = { dashboardDetailProduct = null },
                onEdit = {
                    editingProduct = dashboardDetailProduct
                    dashboardDetailProduct = null
                },
                onShare = { sharingProduct = dashboardDetailProduct },
                onCopyCode = { code ->
                    copyToClipboard(context, "Product ID", code)
                    viewModel.showToast("Product ID $code copied")
                }
            )
        }
        return
    }

    val storeDisplayName = state.store?.shopName?.takeIf { it.isNotBlank() }
        ?: state.verification.details.fullName.takeIf { it.isNotBlank() }
        ?: state.session?.email?.substringBefore("@")
        ?: "Seller Store"
    val storeLogoUrl = viewModel.resolveStoreMediaUrl(state.store?.logoPath)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useRail = maxWidth >= 720.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Show Top App Bar ONLY on Home tab (hidden on All Products, Add Product, My Store)
            if (state.activeTab == MainTab.HOME) {
                SellerTopAppBar(
                    storeName = storeDisplayName,
                    unreadNotificationCount = state.unreadNotificationCount,
                    onLogoClick = { viewModel.selectTab(MainTab.HOME) },
                    onNotificationsClick = { showNotifications = true },
                    onSettingsClick = {
                        settingsInitialSubScreen = AccountSubScreen.MENU
                        showSettings = true
                    }
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val railItems = listOf(
                            Triple(MainTab.HOME, "Home", Pair(Icons.Filled.Home, Icons.Outlined.Home)),
                            Triple(MainTab.PRODUCTS, "All Products", Pair(Icons.Filled.Inventory2, Icons.Outlined.Inventory2)),
                            Triple(MainTab.ADD_PRODUCT, "Add Product", Pair(Icons.Filled.AddBox, Icons.Outlined.AddBox)),
                            Triple(MainTab.MY_STORE, "My Store", Pair(Icons.Filled.Storefront, Icons.Outlined.Storefront))
                        )
                        railItems.forEach { (tab, label, icons) ->
                            val selected = state.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    StoreTabNavIcon(
                                        isMyStore = tab == MainTab.MY_STORE,
                                        storeLogoUrl = storeLogoUrl,
                                        selected = selected,
                                        fallbackIcon = if (selected) icons.first else icons.second,
                                        label = label
                                    )
                                },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ZulvazaOrange,
                                    selectedTextColor = ZulvazaOrange,
                                    indicatorColor = ZulvazaOrange.copy(alpha = 0.14f)
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (state.activeTab) {
                        MainTab.HOME -> DashboardScreen(
                            verification = state.verification,
                            products = state.products,
                            isDataLoading = state.isDataLoading,
                            resolveProductImage = viewModel::resolveProductImageUrl,
                            onAddProduct = { viewModel.selectTab(MainTab.ADD_PRODUCT) },
                            onOpenProducts = { viewModel.selectTab(MainTab.PRODUCTS) },
                            onFixVerification = viewModel::openVerificationEditor,
                            onOpenProductDetail = { p -> dashboardDetailProduct = p },
                            onQuickRestock = viewModel::quickAdjustStock
                        )
                        MainTab.PRODUCTS -> ProductsScreen(
                            products = state.products,
                            drafts = state.drafts,
                            resolveImageUrl = viewModel::resolveProductImageUrl,
                            resolveVideoUrl = viewModel::resolveProductVideoUrl,
                            onAddProduct = { viewModel.selectTab(MainTab.ADD_PRODUCT) },
                            onEditProduct = { p -> editingProduct = p },
                            onAdjustStock = viewModel::quickAdjustStock,
                            onDeleteProduct = viewModel::deleteProduct,
                            onResumeDraft = { d -> resumingDraft = d },
                            onDeleteDraft = viewModel::deleteDraft,
                            onToast = { msg -> viewModel.showToast(msg) }
                        )
                        MainTab.ADD_PRODUCT -> AddEditProductScreen(
                            editingProduct = null,
                            resumingDraft = null,
                            sellerId = state.session?.userId.orEmpty(),
                            isBusy = state.isBusy,
                            resolveImageUrl = viewModel::resolveProductImageUrl,
                            onUploadImage = viewModel::uploadProductImage,
                            onUploadVideo = viewModel::uploadProductVideo,
                            onSaveDraft = viewModel::saveOrAutosaveDraft,
                            onSubmitProduct = { ep, name, cat, price, stock, desc, imgs, vid, attrs, draftId ->
                                viewModel.submitProduct(
                                    editingProduct = ep,
                                    name = name,
                                    category = cat,
                                    price = price,
                                    stock = stock,
                                    description = desc,
                                    imagePaths = imgs,
                                    videoPath = vid,
                                    attributes = attrs,
                                    draftIdToDelete = draftId
                                ) {
                                    viewModel.selectTab(MainTab.PRODUCTS)
                                }
                            },
                            onToast = viewModel::showToast,
                            onBack = null
                        )
                        MainTab.MY_STORE -> StoreProfileScreen(
                            store = state.store,
                            products = state.products,
                            resolveStoreMedia = viewModel::resolveStoreMediaUrl,
                            onUploadStoreMediaBytes = viewModel::uploadStoreMediaBytes,
                            onSaveStoreMedia = viewModel::saveStoreMediaOnly,
                            onDeleteStoreMedia = viewModel::removeStoreMedia,
                            onEditStore = { showEditStoreSheet = true },
                            onToast = viewModel::showToast
                        )
                    }
                }
            }

            // Uniform 4-Tab Bottom Navigation Bar (Home, All Products, Add Product, My Store)
            if (!useRail) {
                SellerBottomNavBar(
                    activeTab = state.activeTab,
                    storeLogoUrl = storeLogoUrl,
                    onSelectTab = viewModel::selectTab
                )
            }
        }
    }
}

@Composable
private fun StoreTabNavIcon(
    isMyStore: Boolean,
    storeLogoUrl: String?,
    selected: Boolean,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    if (isMyStore && !storeLogoUrl.isNullOrBlank()) {
        SignedRemoteImage(
            url = storeLogoUrl,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) ZulvazaOrange else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = label,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SellerTopAppBar(
    storeName: String,
    unreadNotificationCount: Int,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Brand Icon + Constant Greeting + Store Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onLogoClick)
                            .testTag("topbar_logo")
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_zulvaza_app_icon),
                            contentDescription = "ZULVAZA Seller Center",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assalam-o-Alaikum,",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = storeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("topbar_title")
                        )
                    }
                }

                // Right: Notification Bell + Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier.testTag("topbar_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge(
                                        containerColor = ZulvazaOrange,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun PermissionsOnboardingOverlay(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val allAlreadyGranted = remember(context) {
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(allAlreadyGranted) {
        if (allAlreadyGranted) {
            onComplete()
        }
    }

    if (allAlreadyGranted) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permissions_onboarding_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZulvazaOrange.copy(alpha = 0.14f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = ZulvazaOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Permissions Needed for Seller Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Allow these permissions once so verification, security alerts, and updates work smoothly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                PermissionExplainRow(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera",
                    subtitle = "Scan your CNIC front and back automatically during seller verification."
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionExplainRow(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    subtitle = "Detect your city/region for login security and store address verification."
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionExplainRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications",
                    subtitle = "Receive instant alerts for new device logins, KYC approval, and app updates."
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Product & store media use Android's built-in Photo Picker (no broad gallery permission required).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { launcher.launch(requiredPermissions.toTypedArray()) },
                    colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("allow_permissions_button")
                ) {
                    Text(
                        text = "Allow Permissions",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("skip_permissions_button")
                ) {
                    Text("Ask Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PermissionExplainRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ZulvazaOrange.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SellerBottomNavBar(
    activeTab: MainTab,
    storeLogoUrl: String?,
    onSelectTab: (MainTab) -> Unit
) {
    val navItems = listOf(
        Triple(MainTab.HOME, "Home", Pair(Icons.Filled.Home, Icons.Outlined.Home)),
        Triple(MainTab.PRODUCTS, "All Products", Pair(Icons.Filled.Inventory2, Icons.Outlined.Inventory2)),
        Triple(MainTab.ADD_PRODUCT, "Add Product", Pair(Icons.Filled.AddBox, Icons.Outlined.AddBox)),
        Triple(MainTab.MY_STORE, "My Store", Pair(Icons.Filled.Storefront, Icons.Outlined.Storefront))
    )
    val navTags = mapOf(
        MainTab.HOME to "nav_home",
        MainTab.PRODUCTS to "nav_products",
        MainTab.ADD_PRODUCT to "nav_add_product",
        MainTab.MY_STORE to "nav_my_store"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            navItems.forEach { (tab, label, icons) ->
                val selected = activeTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    alwaysShowLabel = true,
                    icon = {
                        StoreTabNavIcon(
                            isMyStore = tab == MainTab.MY_STORE,
                            storeLogoUrl = storeLogoUrl,
                            selected = selected,
                            fallbackIcon = if (selected) icons.first else icons.second,
                            label = label
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ZulvazaOrange,
                        selectedTextColor = ZulvazaOrange,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = ZulvazaOrange.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.testTag(navTags[tab] ?: tab.name)
                )
            }
        }
    }
}
