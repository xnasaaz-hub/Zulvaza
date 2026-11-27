package com.example.ui.state

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionStore
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.DeepLinkDestination
import com.example.data.model.MfaFactor
import com.example.data.model.MfaTotpEnrollment
import com.example.data.model.NotificationType
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.data.model.SellerIdentityDetails
import com.example.data.model.SellerNotificationItem
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.UserSession
import com.example.data.model.VerificationEvent
import com.example.data.model.VerificationStatus
import com.example.data.remote.AppUpdateStatus
import com.example.data.remote.GitHubUpdateManager
import com.example.data.remote.PasswordChangeOtpMode
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseException
import com.example.ui.theme.ThemeMode
import com.example.util.CnicMatchValidationResult
import com.example.util.CnicMatchValidator
import com.example.util.ExtractedCnicDetails
import com.example.util.Formatters
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class RootDestination {
    SPLASH,
    WELCOME,
    SIGNUP,
    LOGIN,
    VERIFICATION,
    MAIN
}

enum class MainTab {
    HOME,
    PRODUCTS,
    ADD_PRODUCT,
    MY_STORE
}

data class ToastBanner(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.currentTimeMillis()
)

enum class CnicOcrStatus {
    IDLE,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class CnicOcrBackgroundState(
    val status: CnicOcrStatus = CnicOcrStatus.IDLE,
    val frontText: String = "",
    val backText: String = "",
    val extracted: ExtractedCnicDetails = ExtractedCnicDetails(),
    val frontFaceDetected: Boolean = false,
    val isVerifyingMatch: Boolean = false,
    val matchResult: CnicMatchValidationResult? = null,
    val errorMessage: String? = null
)

enum class RecoveryStep {
    NONE,
    EMAIL,
    OTP,
    NEW_PASSWORD,
    SUCCESS
}

data class SellerUiState(
    val rootDestination: RootDestination = RootDestination.SPLASH,
    val activeTab: MainTab = MainTab.HOME,
    val splashStep: Int = 0,
    val splashStatusText: String = "Checking account",
    val session: UserSession? = null,
    val verification: SellerVerification = SellerVerification(),
    val verificationEvents: List<VerificationEvent> = emptyList(),
    val store: SellerStore? = null,
    val products: List<ProductItem> = emptyList(),
    val drafts: List<ProductDraft> = emptyList(),
    val signedUrls: Map<String, String> = emptyMap(),
    val localPreviewUrls: Map<String, String> = emptyMap(),
    val mfaFactors: List<MfaFactor> = emptyList(),
    val activeDevices: List<ActiveDeviceSession> = emptyList(),
    val pendingMfaSession: UserSession? = null,
    val pendingMfaFactorId: String? = null,
    val mfaEnrollment: MfaTotpEnrollment? = null,
    val otpPendingEmail: String? = null,
    val signupNeedsEmailLoginOtp: Boolean = false,
    val loginOtpPendingEmail: String? = null,
    val recoveryStep: RecoveryStep = RecoveryStep.NONE,
    val recoveryEmail: String = "",
    val recoveryAccessToken: String? = null,
    val recoveryErrorMessage: String? = null,
    val isBusy: Boolean = false,
    val isDataLoading: Boolean = false,
    val isOnline: Boolean = true,
    val isOfflineMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val toast: ToastBanner? = null,
    val openStoreProfileRequest: Boolean = false,
    val openVerificationFromAccount: Boolean = false,
    val githubRepo: String = SupabaseConfig.DEFAULT_GITHUB_REPO,
    val appUpdateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val notifications: List<SellerNotificationItem> = emptyList(),
    val permissionsPromptCompleted: Boolean = true,
    val usernameConflictNonce: Int = 0,
    val cnicOcrState: CnicOcrBackgroundState = CnicOcrBackgroundState(),
    val hasAppPin: Boolean = false,
    val isAppPinEnabled: Boolean = false,
    val isAppLocked: Boolean = false,
    val showPinSetupPrompt: Boolean = false
) {
    val unreadNotificationCount: Int
        get() = notifications.count { !it.isRead }
}

class SellerViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = SessionStore(application.applicationContext)
    private val supabase = SupabaseClient()

    private val _uiState = MutableStateFlow(SellerUiState())
    val uiState: StateFlow<SellerUiState> = _uiState.asStateFlow()

    private var pendingDeepLink: DeepLinkDestination? = null
    private var cnicOcrJob: Job? = null
    private val notificationMutex = Mutex()
    private val refreshResumeMutex = Mutex()

    init {
        NotificationHelper.ensureChannel(application.applicationContext)
        viewModelScope.launch {
            val savedRepo = sessionStore.getGithubRepo()
            val savedNotifs = sessionStore.loadNotifications()
            val permsDone = sessionStore.isPermissionsPromptDone()
            val pinExists = sessionStore.hasAppPin()
            val pinEnabled = sessionStore.isAppPinEnabled()
            _uiState.update {
                it.copy(
                    githubRepo = savedRepo,
                    notifications = savedNotifs,
                    permissionsPromptCompleted = permsDone,
                    hasAppPin = pinExists,
                    isAppPinEnabled = pinEnabled
                )
            }
        }
        viewModelScope.launch {
            NetworkMonitor.observeIsOnline(application.applicationContext).collect { online ->
                val prevOfflineMode = _uiState.value.isOfflineMode
                _uiState.update { it.copy(isOnline = online) }
                val activeSession = _uiState.value.session
                if (online && prevOfflineMode && activeSession != null) {
                    runCatching {
                        withValidSession { valid -> loadSellerWorkspace(valid) }
                    }
                }
            }
        }
        viewModelScope.launch {
            sessionStore.themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        bootstrapSession()
    }

    fun markPermissionsPromptCompleted() {
        viewModelScope.launch {
            sessionStore.setPermissionsPromptDone()
            _uiState.update { it.copy(permissionsPromptCompleted = true) }
        }
    }

    fun onNotificationPermissionGranted() {
        viewModelScope.launch {
            NotificationHelper.ensureChannel(getApplication())
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(toast = ToastBanner(message = message, isError = isError)) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            sessionStore.setThemeMode(mode)
        }
    }

    fun navigateRoot(dest: RootDestination) {
        _uiState.update { it.copy(rootDestination = dest) }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                openStoreProfileRequest = false,
                openVerificationFromAccount = false
            )
        }
    }

    fun consumeStoreProfileRequest() {
        _uiState.update { it.copy(openStoreProfileRequest = false) }
    }

    fun handleDeepLink(destination: DeepLinkDestination?) {
        if (destination == null) return
        val current = _uiState.value
        if (current.rootDestination == RootDestination.SPLASH) {
            pendingDeepLink = destination
            return
        }
        applyDeepLink(destination)
    }

    private fun applyDeepLink(destination: DeepLinkDestination) {
        val current = _uiState.value
        val isAuthenticated = current.session != null && current.pendingMfaSession == null
        when (destination) {
            DeepLinkDestination.LOGIN -> {
                if (!isAuthenticated) navigateRoot(RootDestination.LOGIN)
            }
            DeepLinkDestination.SIGNUP -> {
                if (!isAuthenticated) navigateRoot(RootDestination.SIGNUP)
            }
            DeepLinkDestination.ONBOARDING -> {
                if (isAuthenticated) {
                    navigateRoot(RootDestination.VERIFICATION)
                } else {
                    pendingDeepLink = destination
                    navigateRoot(RootDestination.LOGIN)
                }
            }
            DeepLinkDestination.DASHBOARD -> {
                if (isAuthenticated) {
                    if (current.verification.status == VerificationStatus.UNSUBMITTED) {
                        navigateRoot(RootDestination.VERIFICATION)
                    } else {
                        _uiState.update { it.copy(rootDestination = RootDestination.MAIN, activeTab = MainTab.HOME) }
                    }
                } else {
                    pendingDeepLink = destination
                    navigateRoot(RootDestination.LOGIN)
                }
            }
            DeepLinkDestination.PRODUCTS -> {
                if (isAuthenticated) {
                    if (current.verification.status == VerificationStatus.UNSUBMITTED) {
                        navigateRoot(RootDestination.VERIFICATION)
                    } else {
                        _uiState.update { it.copy(rootDestination = RootDestination.MAIN, activeTab = MainTab.PRODUCTS) }
                    }
                } else {
                    pendingDeepLink = destination
                    navigateRoot(RootDestination.LOGIN)
                }
            }
            DeepLinkDestination.STORE -> {
                if (isAuthenticated) {
                    if (current.verification.status == VerificationStatus.UNSUBMITTED) {
                        navigateRoot(RootDestination.VERIFICATION)
                    } else {
                        _uiState.update {
                            it.copy(
                                rootDestination = RootDestination.MAIN,
                                activeTab = MainTab.MY_STORE,
                                openStoreProfileRequest = false
                            )
                        }
                    }
                } else {
                    pendingDeepLink = destination
                    navigateRoot(RootDestination.LOGIN)
                }
            }
        }
    }

    fun bootstrapSession() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    rootDestination = RootDestination.SPLASH,
                    splashStep = 0,
                    splashStatusText = ""
                )
            }
            delay(850)

            val saved = sessionStore.loadSession()
            if (saved == null) {
                val target = when (pendingDeepLink) {
                    DeepLinkDestination.SIGNUP -> RootDestination.SIGNUP
                    else -> RootDestination.LOGIN
                }
                pendingDeepLink = null
                _uiState.update { it.copy(rootDestination = target) }
                return@launch
            }

            val pinLocked = sessionStore.isAppPinEnabled()
            if (pinLocked) {
                _uiState.update { it.copy(isAppLocked = true, hasAppPin = true, isAppPinEnabled = true) }
            }

            val cached = sessionStore.loadWorkspaceCache()
            if (!_uiState.value.isOnline) {
                openOfflineCachedWorkspace(saved, cached)
                return@launch
            }

            val validSession = try {
                if (saved.isExpired()) {
                    val refreshed = supabase.refreshSession(saved.refreshToken)
                    sessionStore.saveSession(refreshed)
                    refreshed
                } else {
                    saved
                }
            } catch (e: SupabaseException) {
                if (e.statusCode in listOf(400, 401, 403)) {
                    sessionStore.clearSession()
                    _uiState.update { it.copy(rootDestination = RootDestination.LOGIN, session = null) }
                    return@launch
                }
                openOfflineCachedWorkspace(saved, cached)
                return@launch
            } catch (_: Exception) {
                openOfflineCachedWorkspace(saved, cached)
                return@launch
            }

            _uiState.update { it.copy(session = validSession) }

            val roleOk = verifySellerRoleOrSignOut(validSession, allowOfflineFallback = cached != null)
            if (!roleOk) return@launch

            loadSellerWorkspace(validSession)
        }
    }

    private fun openOfflineCachedWorkspace(
        session: UserSession,
        cached: com.example.data.local.OfflineWorkspaceCache?
    ) {
        val ver = cached?.verification ?: SellerVerification(
            sellerId = session.userId,
            status = VerificationStatus.PENDING
        )
        val dest = if (cached != null && ver.status == VerificationStatus.UNSUBMITTED) {
            RootDestination.VERIFICATION
        } else {
            RootDestination.MAIN
        }
        _uiState.update {
            it.copy(
                session = session,
                verification = ver,
                store = cached?.store,
                products = cached?.products ?: emptyList(),
                isOfflineMode = true,
                rootDestination = dest,
                isDataLoading = false,
                isBusy = false
            )
        }
    }

    private suspend fun verifySellerRoleOrSignOut(
        session: UserSession,
        allowOfflineFallback: Boolean = false
    ): Boolean {
        return try {
            val role = supabase.fetchUserRole(session)
            if (role != null && !role.equals("seller", ignoreCase = true)) {
                supabase.signOut(session)
                sessionStore.clearSession()
                _uiState.update {
                    it.copy(
                        session = null,
                        rootDestination = RootDestination.LOGIN,
                        isBusy = false
                    )
                }
                showToast("This app is for sellers only", isError = true)
                false
            } else if (role == null) {
                delay(700)
                val retryRole = supabase.fetchUserRole(session)
                if (retryRole == null || !retryRole.equals("seller", ignoreCase = true)) {
                    supabase.signOut(session)
                    sessionStore.clearSession()
                    _uiState.update {
                        it.copy(session = null, rootDestination = RootDestination.LOGIN, isBusy = false)
                    }
                    showToast("This app is for sellers only", isError = true)
                    false
                } else {
                    true
                }
            } else {
                true
            }
        } catch (e: SupabaseException) {
            if (e.statusCode == 0 && allowOfflineFallback) {
                val cached = sessionStore.loadWorkspaceCache()
                openOfflineCachedWorkspace(session, cached)
                return false
            }
            showToast(e.message ?: "Unable to verify account role", isError = true)
            _uiState.update { it.copy(rootDestination = RootDestination.LOGIN, isBusy = false) }
            false
        } catch (e: Exception) {
            if (allowOfflineFallback) {
                val cached = sessionStore.loadWorkspaceCache()
                openOfflineCachedWorkspace(session, cached)
                return false
            }
            showToast(e.message ?: "Unable to verify account role", isError = true)
            _uiState.update { it.copy(rootDestination = RootDestination.LOGIN, isBusy = false) }
            false
        }
    }

    private suspend fun <T> withValidSession(block: suspend (UserSession) -> T): T {
        val current = _uiState.value.session ?: throw IllegalStateException("Not logged in")
        val usable = if (current.isExpired()) {
            val refreshed = supabase.refreshSession(current.refreshToken)
            sessionStore.saveSession(refreshed)
            _uiState.update { it.copy(session = refreshed) }
            refreshed
        } else {
            current
        }
        return try {
            block(usable)
        } catch (e: com.example.data.remote.SupabaseException) {
            if (e.statusCode == 401) {
                val refreshed = supabase.refreshSession(usable.refreshToken)
                sessionStore.saveSession(refreshed)
                _uiState.update { it.copy(session = refreshed) }
                block(refreshed)
            } else {
                throw e
            }
        }
    }

    private suspend fun loadSellerWorkspace(session: UserSession) {
        _uiState.update { it.copy(isDataLoading = true) }
        try {
            val verification = supabase.fetchVerification(session)
            val events = runCatching { supabase.fetchVerificationEvents(session) }.getOrDefault(emptyList())
            val store = runCatching { supabase.fetchStore(session) }.getOrNull()
            val products = runCatching { supabase.fetchProducts(session) }.getOrDefault(emptyList())
            val drafts = runCatching { supabase.fetchDrafts(session) }.getOrDefault(emptyList())
            val factors = runCatching { supabase.getMfaFactors(session) }.getOrDefault(emptyList())
            val deviceId = sessionStore.getOrCreateDeviceId()
            val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android Device" }
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            val (devices, wasRevoked) = runCatching {
                supabase.syncActiveDevices(session, deviceId, deviceName, osVersion)
            }.getOrDefault(emptyList<ActiveDeviceSession>() to false)

            if (wasRevoked) {
                supabase.signOut(session)
                sessionStore.clearSession()
                _uiState.update {
                    SellerUiState(
                        rootDestination = RootDestination.LOGIN,
                        isOnline = it.isOnline,
                        themeMode = ThemeMode.LIGHT,
                        githubRepo = it.githubRepo
                    )
                }
                showToast("This device session was removed", isError = true)
                return
            }

            val dest = if (verification.status == VerificationStatus.UNSUBMITTED) {
                RootDestination.VERIFICATION
            } else {
                RootDestination.MAIN
            }

            sessionStore.saveWorkspaceCache(verification, store, products)
            val userNotifs = sessionStore.loadNotifications(session.userId)
            val pinExists = sessionStore.hasAppPin()
            val pinEnabled = sessionStore.isAppPinEnabled()
            val pinOffered = sessionStore.wasPinSetupOffered(session.userId)
            val shouldOfferPin = verification.status != VerificationStatus.UNSUBMITTED &&
                !pinExists &&
                !pinOffered

            _uiState.update {
                it.copy(
                    session = session,
                    verification = verification,
                    verificationEvents = events,
                    store = store,
                    products = products,
                    drafts = drafts,
                    mfaFactors = factors,
                    activeDevices = devices,
                    notifications = userNotifs,
                    hasAppPin = pinExists,
                    isAppPinEnabled = pinEnabled,
                    showPinSetupPrompt = shouldOfferPin,
                    isOfflineMode = false,
                    rootDestination = dest,
                    isDataLoading = false,
                    isBusy = false
                )
            }

            // Detect alerts: new device logins, verification decision, and app updates
            evaluateDeviceLoginNotifications(devices, deviceId, session.userId)
            evaluateVerificationStatusNotification(verification, session.userId)
            checkForAppUpdateSilent()

            // Pre-sign ALL store, product, draft & KYC media in parallel for instant image rendering
            preSignWorkspaceMedia(session, verification, store, products, drafts)

            val dl = pendingDeepLink
            if (dl != null) {
                pendingDeepLink = null
                applyDeepLink(dl)
            }
        } catch (e: Exception) {
            val cached = sessionStore.loadWorkspaceCache()
            if (cached != null) {
                openOfflineCachedWorkspace(session, cached)
            } else {
                _uiState.update { it.copy(isDataLoading = false, isBusy = false) }
                showToast(e.message ?: "Failed to load seller data", isError = true)
            }
        }
    }

    private fun preSignWorkspaceMedia(
        session: UserSession,
        verification: SellerVerification,
        store: SellerStore?,
        products: List<ProductItem>,
        drafts: List<ProductDraft>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingSigned = _uiState.value.signedUrls
            val requests = linkedSetOf<Pair<String, String>>()
            store?.logoPath?.takeIf { it.isNotBlank() }?.let { requests += SupabaseConfig.BUCKET_STORE_MEDIA to it }
            store?.bannerPath?.takeIf { it.isNotBlank() }?.let { requests += SupabaseConfig.BUCKET_STORE_MEDIA to it }
            products.forEach { product ->
                product.imagePaths.filter { it.isNotBlank() }.forEach { path ->
                    requests += SupabaseConfig.BUCKET_PRODUCT_IMAGES to path
                }
                product.videoPath?.takeIf { it.isNotBlank() }?.let { path ->
                    requests += SupabaseConfig.BUCKET_PRODUCT_VIDEOS to path
                }
            }
            drafts.forEach { draft ->
                draft.imagePaths.filter { it.isNotBlank() }.forEach { path ->
                    requests += SupabaseConfig.BUCKET_PRODUCT_IMAGES to path
                }
                draft.videoPath?.takeIf { it.isNotBlank() }?.let { path ->
                    requests += SupabaseConfig.BUCKET_PRODUCT_VIDEOS to path
                }
            }
            if (verification.details.cnicFront.isNotBlank()) {
                requests += SupabaseConfig.BUCKET_SELLER_KYC to verification.details.cnicFront
            }
            if (verification.details.cnicBack.isNotBlank()) {
                requests += SupabaseConfig.BUCKET_SELLER_KYC to verification.details.cnicBack
            }
            if (verification.details.facePhoto.isNotBlank()) {
                requests += SupabaseConfig.BUCKET_SELLER_FACES to verification.details.facePhoto
            }

            val missing = requests.filter { (bucket, path) ->
                !path.startsWith("http") &&
                    !path.startsWith("content://") &&
                    !path.startsWith("file://") &&
                    existingSigned["$bucket:$path"] == null
            }
            if (missing.isEmpty()) return@launch

            val signedPairs = coroutineScope {
                missing.map { (bucket, path) ->
                    async {
                        val url = runCatching {
                            supabase.createSignedUrl(session, bucket, path, expiresInSec = 3600)
                        }.getOrNull()
                        if (url != null) ("$bucket:$path" to url) else null
                    }
                }.awaitAll().filterNotNull()
            }
            if (signedPairs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(signedUrls = state.signedUrls + signedPairs.toMap())
                }
            }
        }
    }

    private fun resolveMediaUrl(bucket: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") ||
            path.startsWith("https://") ||
            path.startsWith("content://") ||
            path.startsWith("file://")
        ) {
            return path
        }
        val key = "$bucket:$path"
        val state = _uiState.value
        state.localPreviewUrls[key]?.let { return it }
        val existingSigned = state.signedUrls[key]
        if (existingSigned == null) {
            viewModelScope.launch {
                runCatching {
                    withValidSession { session ->
                        ensureSignedUrl(session, bucket, path)
                    }
                }
            }
        }
        return existingSigned
    }

    fun resolveProductImageUrl(path: String): String? =
        resolveMediaUrl(SupabaseConfig.BUCKET_PRODUCT_IMAGES, path)

    fun resolveProductVideoUrl(path: String): String? =
        resolveMediaUrl(SupabaseConfig.BUCKET_PRODUCT_VIDEOS, path)

    fun resolveStoreMediaUrl(path: String?): String? =
        resolveMediaUrl(SupabaseConfig.BUCKET_STORE_MEDIA, path)

    fun resolveVerificationDocUrl(path: String): String? =
        resolveMediaUrl(SupabaseConfig.BUCKET_SELLER_KYC, path)

    fun resolveFacePhotoUrl(path: String): String? =
        resolveMediaUrl(SupabaseConfig.BUCKET_SELLER_FACES, path)

    private suspend fun ensureSignedUrl(session: UserSession, bucket: String, path: String): String? {
        val key = "$bucket:$path"
        _uiState.value.signedUrls[key]?.let { return it }
        val signed = supabase.createSignedUrl(session, bucket, path, expiresInSec = 3600)
        if (signed != null) {
            cacheSignedUrl(bucket, path, signed)
        }
        return signed
    }

    private fun cacheSignedUrl(bucket: String, path: String, url: String) {
        _uiState.update { state ->
            state.copy(signedUrls = state.signedUrls + ("$bucket:$path" to url))
        }
    }

    private fun cacheLocalPreviewUrl(bucket: String, path: String, localUrl: String) {
        _uiState.update { state ->
            state.copy(localPreviewUrls = state.localPreviewUrls + ("$bucket:$path" to localUrl))
        }
    }

    // ==================== AUTH ACTIONS ====================

    fun signUp(email: String, password: String) {
        val cleanEmail = email.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val immediateSession = supabase.signUp(cleanEmail, password)
                if (immediateSession != null) {
                    // Never bypass OTP verification on signup
                    runCatching { supabase.signOut(immediateSession) }
                    supabase.sendEmailLoginOtp(cleanEmail)
                    _uiState.update {
                        it.copy(
                            otpPendingEmail = cleanEmail,
                            signupNeedsEmailLoginOtp = true,
                            isBusy = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            otpPendingEmail = cleanEmail,
                            signupNeedsEmailLoginOtp = false,
                            isBusy = false
                        )
                    }
                }
                showToast("6-digit verification code sent to $cleanEmail. Check inbox or spam.")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Sign up failed", isError = true)
            }
        }
    }

    fun verifySignupOtp(email: String, code: String) {
        val needsEmailOtp = _uiState.value.signupNeedsEmailLoginOtp
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val session = if (needsEmailOtp) {
                    supabase.verifyEmailLoginOtp(email, code).session
                } else {
                    supabase.verifySignupOtp(email, code)
                }
                sessionStore.saveSession(session)
                val ok = verifySellerRoleOrSignOut(session)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            session = session,
                            otpPendingEmail = null,
                            signupNeedsEmailLoginOtp = false,
                            isBusy = false
                        )
                    }
                    loadSellerWorkspace(session)
                    showToast("Email verified! Complete your seller identity.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid or expired code", isError = true)
            }
        }
    }

    fun resendSignupOtp(email: String) {
        val needsEmailOtp = _uiState.value.signupNeedsEmailLoginOtp
        viewModelScope.launch {
            try {
                if (needsEmailOtp) {
                    supabase.sendEmailLoginOtp(email)
                } else {
                    supabase.resendSignupOtp(email)
                }
                showToast("New 6-digit code sent to $email. Check inbox or spam.")
            } catch (e: Exception) {
                showToast(e.message ?: "Failed to resend code", isError = true)
            }
        }
    }

    fun clearOtpPending() {
        _uiState.update { it.copy(otpPendingEmail = null, signupNeedsEmailLoginOtp = false) }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val result = supabase.signInWithPassword(email, password)
                if (result.requiresMfa && result.verifiedTotpFactorId != null) {
                    _uiState.update {
                        it.copy(
                            pendingMfaSession = result.session,
                            pendingMfaFactorId = result.verifiedTotpFactorId,
                            isBusy = false
                        )
                    }
                } else {
                    completeSignIn(result.session)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Login failed", isError = true)
            }
        }
    }

    fun verifyLoginMfa(code: String) {
        val tempSession = _uiState.value.pendingMfaSession ?: return
        val factorId = _uiState.value.pendingMfaFactorId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val elevated = supabase.mfaChallengeAndVerify(tempSession, factorId, code)
                _uiState.update { it.copy(pendingMfaSession = null, pendingMfaFactorId = null) }
                completeSignIn(elevated)
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid authenticator code", isError = true)
            }
        }
    }

    fun cancelLoginMfa() {
        _uiState.update { it.copy(pendingMfaSession = null, pendingMfaFactorId = null) }
    }

    fun sendEmailLoginOtp(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                supabase.sendEmailLoginOtp(email)
                _uiState.update { it.copy(loginOtpPendingEmail = email.trim(), isBusy = false) }
                showToast("6-digit sign-in code sent to ${email.trim()}")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Could not send login code", isError = true)
            }
        }
    }

    fun verifyEmailLoginOtp(email: String, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val result = supabase.verifyEmailLoginOtp(email, code)
                _uiState.update { it.copy(loginOtpPendingEmail = null) }
                if (result.requiresMfa && result.verifiedTotpFactorId != null) {
                    _uiState.update {
                        it.copy(
                            pendingMfaSession = result.session,
                            pendingMfaFactorId = result.verifiedTotpFactorId,
                            isBusy = false
                        )
                    }
                } else {
                    completeSignIn(result.session)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid or expired code", isError = true)
            }
        }
    }

    fun clearLoginOtpPending() {
        _uiState.update { it.copy(loginOtpPendingEmail = null) }
    }

    fun openForgotPassword(prefillEmail: String = "") {
        _uiState.update {
            it.copy(
                recoveryStep = RecoveryStep.EMAIL,
                recoveryEmail = prefillEmail.trim(),
                recoveryAccessToken = null,
                recoveryErrorMessage = null
            )
        }
    }

    private fun mapPasswordResetError(e: Exception): String {
        if (e is SupabaseException) {
            val msgLower = e.message.orEmpty().lowercase(Locale.US)
            if (e.statusCode == 429 ||
                msgLower.contains("over_email_send_rate_limit") ||
                msgLower.contains("rate limit") ||
                msgLower.contains("too many")
            ) {
                return "Too many requests. Please wait a minute and try again."
            }
        }
        return "Something went wrong. Please try again."
    }

    fun sendPasswordResetOtp(email: String) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, recoveryErrorMessage = null) }
            try {
                supabase.sendPasswordReset(cleanEmail)
                _uiState.update {
                    it.copy(
                        recoveryStep = RecoveryStep.OTP,
                        recoveryEmail = cleanEmail,
                        recoveryAccessToken = null,
                        recoveryErrorMessage = null,
                        isBusy = false
                    )
                }
                showToast("Password reset code sent to $cleanEmail")
            } catch (e: Exception) {
                val msg = mapPasswordResetError(e)
                _uiState.update {
                    it.copy(
                        recoveryErrorMessage = msg,
                        isBusy = false
                    )
                }
            }
        }
    }

    fun resendPasswordResetOtp() {
        val targetEmail = _uiState.value.recoveryEmail.trim().lowercase(Locale.US)
        if (targetEmail.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, recoveryErrorMessage = null) }
            try {
                supabase.sendPasswordReset(targetEmail)
                _uiState.update { it.copy(isBusy = false, recoveryErrorMessage = null) }
                showToast("Reset code resent to $targetEmail")
            } catch (e: Exception) {
                val msg = mapPasswordResetError(e)
                _uiState.update { it.copy(isBusy = false, recoveryErrorMessage = msg) }
            }
        }
    }

    fun verifyRecoveryOtpCode(code: String) {
        val targetEmail = _uiState.value.recoveryEmail.trim().lowercase(Locale.US)
        val cleanCode = code.filter { it.isDigit() }
        if (targetEmail.isBlank() || cleanCode.length != 6) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, recoveryErrorMessage = null) }
            try {
                val verifiedSession = supabase.verifyRecoveryOtp(targetEmail, cleanCode)
                _uiState.update {
                    it.copy(
                        recoveryAccessToken = verifiedSession.accessToken,
                        recoveryStep = RecoveryStep.NEW_PASSWORD,
                        recoveryErrorMessage = null,
                        isBusy = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        recoveryErrorMessage = "Invalid or expired code",
                        isBusy = false
                    )
                }
            }
        }
    }

    fun saveRecoveredPassword(newPassword: String) {
        val token = _uiState.value.recoveryAccessToken
        if (token.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    recoveryStep = RecoveryStep.OTP,
                    recoveryErrorMessage = "Invalid or expired code"
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, recoveryErrorMessage = null) }
            try {
                supabase.updatePasswordWithRecoveryToken(token, newPassword)
                _uiState.update {
                    it.copy(
                        recoveryAccessToken = null,
                        recoveryStep = RecoveryStep.SUCCESS,
                        recoveryErrorMessage = null,
                        isBusy = false
                    )
                }
            } catch (e: Exception) {
                val msg = if (e is SupabaseException && e.message?.isNotBlank() == true) {
                    e.message!!
                } else {
                    "Something went wrong. Please try again."
                }
                _uiState.update {
                    it.copy(
                        recoveryErrorMessage = msg,
                        isBusy = false
                    )
                }
            }
        }
    }

    fun changeRecoveryEmail() {
        _uiState.update {
            it.copy(
                recoveryStep = RecoveryStep.EMAIL,
                recoveryAccessToken = null,
                recoveryErrorMessage = null
            )
        }
    }

    fun clearRecoveryPending() {
        _uiState.update {
            it.copy(
                recoveryStep = RecoveryStep.NONE,
                recoveryEmail = "",
                recoveryAccessToken = null,
                recoveryErrorMessage = null
            )
        }
    }

    private suspend fun completeSignIn(session: UserSession) {
        sessionStore.saveSession(session)
        val ok = verifySellerRoleOrSignOut(session)
        if (ok) {
            loadSellerWorkspace(session)
        }
    }

    fun signOut() {
        cnicOcrJob?.cancel()
        viewModelScope.launch {
            _uiState.value.session?.let { supabase.signOut(it) }
            sessionStore.clearSession()
            val pinExists = sessionStore.hasAppPin()
            val pinEnabled = sessionStore.isAppPinEnabled()
            _uiState.update {
                SellerUiState(
                    rootDestination = RootDestination.LOGIN,
                    isOnline = it.isOnline,
                    themeMode = it.themeMode,
                    githubRepo = it.githubRepo,
                    permissionsPromptCompleted = it.permissionsPromptCompleted,
                    hasAppPin = pinExists,
                    isAppPinEnabled = pinEnabled,
                    isAppLocked = false
                )
            }
            showToast("Logged out safely")
        }
    }

    // ==================== 5-DIGIT APP LOCK PIN ====================

    suspend fun verifyCurrentAppPin(pin: String): Boolean {
        return sessionStore.verifyAppPin(pin)
    }

    fun unlockAppWithPin(pin: String, onWrongPin: () -> Unit) {
        viewModelScope.launch {
            val ok = sessionStore.verifyAppPin(pin)
            if (ok) {
                _uiState.update { it.copy(isAppLocked = false) }
            } else {
                onWrongPin()
            }
        }
    }

    fun setupAppPin(pin: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching {
                sessionStore.setAppPin(pin, enabled = true)
                _uiState.value.session?.userId?.let { sessionStore.markPinSetupOffered(it) }
                _uiState.update {
                    it.copy(
                        hasAppPin = true,
                        isAppPinEnabled = true,
                        isAppLocked = false,
                        showPinSetupPrompt = false
                    )
                }
                showToast("5-digit App Lock PIN enabled")
                onDone?.invoke()
            }.onFailure { e ->
                showToast(e.message ?: "Unable to save PIN", isError = true)
            }
        }
    }

    fun dismissPinSetupPrompt() {
        viewModelScope.launch {
            _uiState.value.session?.userId?.let { sessionStore.markPinSetupOffered(it) }
            _uiState.update { it.copy(showPinSetupPrompt = false) }
        }
    }

    fun toggleAppPinWithCurrentPin(
        currentPin: String,
        targetEnabled: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val valid = sessionStore.verifyAppPin(currentPin)
            if (!valid) {
                onResult(false)
                return@launch
            }
            val wasEnabled = _uiState.value.isAppPinEnabled
            sessionStore.setAppPinEnabled(targetEnabled)
            _uiState.update { it.copy(isAppPinEnabled = targetEnabled) }
            if (wasEnabled != targetEnabled) {
                showToast(if (targetEnabled) "App Lock PIN enabled" else "App Lock PIN disabled")
            }
            onResult(true)
        }
    }

    fun deleteAppPinWithCurrentPin(
        currentPin: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val valid = sessionStore.verifyAppPin(currentPin)
            if (!valid) {
                onResult(false)
                return@launch
            }
            sessionStore.deleteAppPin()
            _uiState.update {
                it.copy(
                    hasAppPin = false,
                    isAppPinEnabled = false,
                    isAppLocked = false
                )
            }
            showToast("App Lock PIN deleted")
            onResult(true)
        }
    }

    // ==================== GITHUB APP UPDATES ====================

    fun saveGithubRepo(repo: String) {
        val cleaned = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        if (!cleaned.contains("/") || cleaned.split("/").size != 2) {
            showToast("Enter GitHub repository as owner/repo", isError = true)
            return
        }
        viewModelScope.launch {
            sessionStore.setGithubRepo(cleaned)
            _uiState.update { it.copy(githubRepo = cleaned, appUpdateStatus = AppUpdateStatus.Idle) }
            showToast("GitHub repository saved")
        }
    }

    fun checkForAppUpdate() {
        val repo = _uiState.value.githubRepo
        viewModelScope.launch {
            _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.Checking) }
            try {
                val release = GitHubUpdateManager.checkLatestRelease(repo)
                if (release.isNewerThanCurrent) {
                    _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.UpdateAvailable(release)) }
                    notifyIfNewUpdate(release)
                } else {
                    _uiState.update {
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.UpToDate(
                                currentVersion = com.example.BuildConfig.VERSION_NAME,
                                checkedTag = release.tagName
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        appUpdateStatus = AppUpdateStatus.Error(
                            e.message ?: "Unable to check for updates"
                        )
                    )
                }
            }
        }
    }

    private fun checkForAppUpdateSilent() {
        val repo = _uiState.value.githubRepo
        viewModelScope.launch {
            runCatching {
                val release = GitHubUpdateManager.checkLatestRelease(repo)
                if (release.isNewerThanCurrent) {
                    _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.UpdateAvailable(release)) }
                    notifyIfNewUpdate(release)
                }
            }
        }
    }

    private suspend fun notifyIfNewUpdate(release: com.example.data.remote.GitHubReleaseInfo) {
        val lastTag = sessionStore.getLastNotifiedUpdateTag()
        if (lastTag == release.tagName) return
        sessionStore.setLastNotifiedUpdateTag(release.tagName)
        pushNotification(
            title = "App Update Available (v${release.versionName})",
            message = "A new version of ZULVAZA Seller Center is ready to download and install.",
            type = NotificationType.APP_UPDATE,
            targetRoute = "updates"
        )
    }

    private suspend fun evaluateDeviceLoginNotifications(
        devices: List<ActiveDeviceSession>,
        currentDeviceId: String,
        userId: String? = _uiState.value.session?.userId
    ) {
        val newlySeen = notificationMutex.withLock {
            val known = sessionStore.loadKnownDeviceIds(userId)
            val remoteIds = devices.map { it.deviceId }.toSet()
            if (known == null) {
                // Seed known devices on first sync without flooding alerts for existing sessions
                sessionStore.saveKnownDeviceIds(remoteIds + currentDeviceId, userId)
                return@withLock emptyList()
            }
            val unseen = devices.filter { !it.isCurrentDevice && it.deviceId !in known }
            if (unseen.isNotEmpty() || !known.containsAll(remoteIds)) {
                sessionStore.saveKnownDeviceIds(known + remoteIds + currentDeviceId, userId)
            }
            unseen
        }
        newlySeen.forEach { dev ->
            pushNotification(
                title = "New Device Login Detected",
                message = "${dev.deviceName} (${dev.osVersion}) signed in from ${dev.cityCountry}.",
                type = NotificationType.DEVICE_LOGIN,
                targetRoute = "devices",
                userId = userId
            )
        }
    }

    fun refreshNotificationsAndStatusOnResume() {
        if (_uiState.value.session == null) return
        if (!_uiState.value.isOnline) return
        viewModelScope.launch {
            if (!refreshResumeMutex.tryLock()) return@launch
            try {
                runCatching {
                    withValidSession { session ->
                        val verification = supabase.fetchVerification(session)
                        val events = runCatching { supabase.fetchVerificationEvents(session) }.getOrDefault(_uiState.value.verificationEvents)
                        val deviceId = sessionStore.getOrCreateDeviceId()
                        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { "Android Device" }
                        val (devices, wasRevoked) = runCatching {
                            supabase.syncActiveDevices(session, deviceId, deviceName, "Android ${Build.VERSION.RELEASE}")
                        }.getOrDefault(_uiState.value.activeDevices to false)
                        if (wasRevoked) {
                            signOut()
                            return@withValidSession
                        }
                        _uiState.update {
                            it.copy(
                                verification = verification,
                                verificationEvents = events,
                                activeDevices = devices
                            )
                        }
                        evaluateVerificationStatusNotification(verification, session.userId)
                        evaluateDeviceLoginNotifications(devices, deviceId, session.userId)
                        checkForAppUpdateSilent()
                    }
                }
            } finally {
                refreshResumeMutex.unlock()
            }
        }
    }

    private suspend fun evaluateVerificationStatusNotification(
        verification: SellerVerification,
        userId: String? = _uiState.value.session?.userId
    ) {
        val statusToNotify: VerificationStatus? = notificationMutex.withLock {
            val prevRaw = sessionStore.getLastKnownVerificationStatus(userId)
            val currRaw = verification.status.raw
            if (prevRaw == currRaw) return@withLock null
            if (prevRaw == null && verification.status == VerificationStatus.UNSUBMITTED) {
                sessionStore.setLastKnownVerificationStatus(currRaw, userId)
                return@withLock null
            }
            sessionStore.setLastKnownVerificationStatus(currRaw, userId)
            verification.status
        }
        when (statusToNotify) {
            VerificationStatus.PENDING -> pushNotification(
                title = "Verification Under Review",
                message = "Your CNIC, face verification & store details are under review. We'll notify you once approved.",
                type = NotificationType.VERIFICATION,
                targetRoute = "verification",
                userId = userId
            )
            VerificationStatus.APPROVED -> pushNotification(
                title = "Seller Verification Approved",
                message = "Congratulations! Your CNIC, face & store verification has been approved.",
                type = NotificationType.VERIFICATION,
                targetRoute = "verification",
                userId = userId
            )
            VerificationStatus.REJECTED -> pushNotification(
                title = "Verification Needs Attention",
                message = verification.reason?.takeIf { it.isNotBlank() }
                    ?: "Your verification submission was rejected. Please review and resubmit.",
                type = NotificationType.VERIFICATION,
                targetRoute = "verification",
                userId = userId
            )
            else -> Unit
        }
    }

    private suspend fun pushNotification(
        title: String,
        message: String,
        type: NotificationType,
        targetRoute: String? = null,
        userId: String? = _uiState.value.session?.userId
    ) {
        val (item, shouldNotify) = notificationMutex.withLock {
            val now = System.currentTimeMillis()
            val recentDuplicate = _uiState.value.notifications.any { existing ->
                existing.type == type &&
                    existing.title == title &&
                    existing.message == message &&
                    (now - existing.createdAtEpochMs) < 10 * 60 * 1000L
            }
            if (recentDuplicate) {
                return@withLock null to false
            }
            val created = SellerNotificationItem(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = type,
                createdAtEpochMs = now,
                isRead = false,
                targetRoute = targetRoute
            )
            val updated = (listOf(created) + _uiState.value.notifications).take(100)
            sessionStore.saveNotifications(updated, userId)
            _uiState.update { it.copy(notifications = updated) }
            created to true
        }
        if (shouldNotify && item != null) {
            val stableTrayId = "${type.raw}:${title.lowercase(Locale.US)}".hashCode()
            NotificationHelper.postLocalNotification(
                context = getApplication(),
                notificationId = stableTrayId,
                title = title,
                body = message
            )
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            val uid = _uiState.value.session?.userId
            val updated = _uiState.value.notifications.map {
                if (it.id == id) it.copy(isRead = true) else it
            }
            sessionStore.saveNotifications(updated, uid)
            _uiState.update { it.copy(notifications = updated) }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val uid = _uiState.value.session?.userId
            val updated = _uiState.value.notifications.map { it.copy(isRead = true) }
            sessionStore.saveNotifications(updated, uid)
            _uiState.update { it.copy(notifications = updated) }
        }
    }

    fun deleteNotifications(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val uid = _uiState.value.session?.userId
            val prevCount = _uiState.value.notifications.size
            val updated = _uiState.value.notifications.filterNot { it.id in ids }
            sessionStore.saveNotifications(updated, uid)
            _uiState.update { it.copy(notifications = updated) }
            showToast(
                if (ids.size >= prevCount || updated.isEmpty()) {
                    "Notifications cleared"
                } else {
                    "Deleted ${ids.size} notification(s)"
                }
            )
        }
    }

    fun downloadAndInstallUpdate() {
        val release = when (val st = _uiState.value.appUpdateStatus) {
            is AppUpdateStatus.UpdateAvailable -> st.release
            is AppUpdateStatus.Error -> return
            else -> return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    appUpdateStatus = AppUpdateStatus.Downloading(
                        release = release,
                        progress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = release.apkSizeBytes
                    )
                )
            }
            try {
                val apkFile = GitHubUpdateManager.downloadApk(
                    context = getApplication(),
                    release = release
                ) { progress, downloaded, total ->
                    _uiState.update {
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.Downloading(
                                release = release,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }
                _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.ReadyToInstall(release, apkFile)) }
                val started = GitHubUpdateManager.launchApkInstaller(getApplication(), apkFile)
                if (!started) {
                    showToast("Allow 'Install unknown apps' for ZULVAZA Seller Center, then tap Install")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        appUpdateStatus = AppUpdateStatus.Error(
                            message = e.message ?: "Download failed",
                            htmlUrl = release.htmlUrl.takeIf { url -> url.isNotBlank() }
                        )
                    )
                }
            }
        }
    }

    fun installDownloadedUpdate() {
        val ready = _uiState.value.appUpdateStatus as? AppUpdateStatus.ReadyToInstall ?: return
        val started = GitHubUpdateManager.launchApkInstaller(getApplication(), ready.apkFile)
        if (!started) {
            showToast("Grant permission to install updates, then tap Install APK")
        }
    }

    // ==================== SECURITY (PASSWORD & TOTP) ====================

    fun requestPasswordChangeOtp(
        currentPassword: String,
        onOtpSent: (PasswordChangeOtpMode, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                require(currentPassword.isNotEmpty()) { "Enter your current password" }
                val activeSession = withValidSession { it }
                val email = activeSession.email.trim()
                require(email.isNotBlank()) { "Account email unavailable" }

                // Verify current password against Supabase without overwriting an elevated AAL2 session
                val verifiedLogin = try {
                    supabase.signInWithPassword(email, currentPassword)
                } catch (e: SupabaseException) {
                    throw IllegalStateException(
                        if (e.statusCode in listOf(400, 401)) "Current password is incorrect"
                        else e.message ?: "Unable to verify current password"
                    )
                }

                val currentAal = supabase.decodeJwtAal(activeSession.accessToken)
                val sessionForOtp = if (currentAal == "aal2") {
                    activeSession
                } else {
                    sessionStore.saveSession(verifiedLogin.session)
                    _uiState.update { it.copy(session = verifiedLogin.session) }
                    verifiedLogin.session
                }

                val otpMode = supabase.sendReauthenticateNonce(sessionForOtp)
                _uiState.update { it.copy(isBusy = false) }
                showToast("6-digit verification code sent to $email")
                onOtpSent(otpMode, email)
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to verify current password", isError = true)
            }
        }
    }

    fun confirmPasswordChangeWithOtp(
        newPassword: String,
        otpCode: String,
        mode: PasswordChangeOtpMode,
        email: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val cleanOtp = otpCode.filter { it.isDigit() }
                require(cleanOtp.length == 6) { "Enter the 6-digit verification code" }
                require(newPassword.length >= 8) { "New password must be at least 8 characters" }

                when (mode) {
                    PasswordChangeOtpMode.REAUTHENTICATE_NONCE -> {
                        withValidSession { session ->
                            supabase.updatePassword(session, newPassword, reauthNonce = cleanOtp)
                        }
                    }
                    PasswordChangeOtpMode.EMAIL_OTP -> {
                        val verified = supabase.verifyEmailLoginOtp(email, cleanOtp)
                        val activeAal = _uiState.value.session?.let { supabase.decodeJwtAal(it.accessToken) }
                        if (!verified.requiresMfa) {
                            supabase.updatePassword(verified.session, newPassword)
                            sessionStore.saveSession(verified.session)
                            _uiState.update { it.copy(session = verified.session) }
                        } else if (activeAal == "aal2") {
                            withValidSession { session ->
                                supabase.updatePassword(session, newPassword)
                            }
                        } else {
                            throw IllegalStateException("Please complete 2FA verification to update password")
                        }
                    }
                }

                _uiState.update { it.copy(isBusy = false) }
                pushNotification(
                    title = "Password Updated",
                    message = "Your ZULVAZA Seller password was changed after OTP verification.",
                    type = NotificationType.GENERAL,
                    targetRoute = "devices"
                )
                showToast("Password updated successfully")
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to verify OTP or update password", isError = true)
            }
        }
    }

    fun startTotpEnrollment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val enrollment = withValidSession { session -> supabase.mfaEnrollTotp(session) }
                _uiState.update { it.copy(mfaEnrollment = enrollment, isBusy = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Could not start authenticator setup", isError = true)
            }
        }
    }

    fun confirmTotpEnrollment(code: String) {
        val enroll = _uiState.value.mfaEnrollment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val updatedSession = withValidSession { session ->
                    supabase.mfaChallengeAndVerify(session, enroll.factorId, code)
                }
                sessionStore.saveSession(updatedSession)
                val factors = supabase.getMfaFactors(updatedSession)
                _uiState.update {
                    it.copy(
                        session = updatedSession,
                        mfaFactors = factors,
                        mfaEnrollment = null,
                        isBusy = false
                    )
                }
                showToast("Authenticator enabled")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid 6-digit code", isError = true)
            }
        }
    }

    fun cancelTotpEnrollment() {
        val enroll = _uiState.value.mfaEnrollment
        _uiState.update { it.copy(mfaEnrollment = null) }
        if (enroll != null) {
            viewModelScope.launch {
                runCatching {
                    withValidSession { session -> supabase.mfaUnenroll(session, enroll.factorId) }
                }
            }
        }
    }

    fun disableTotpWithCode(factorId: String, currentCode: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val updated = withValidSession { session ->
                    // Strictly verify a fresh 6-digit TOTP code before disabling or removing 2FA
                    val verifiedSession = supabase.mfaChallengeAndVerify(session, factorId, currentCode)
                    supabase.mfaUnenroll(verifiedSession, factorId)
                    verifiedSession
                }
                sessionStore.saveSession(updated)
                val factors = supabase.getMfaFactors(updated)
                _uiState.update {
                    it.copy(session = updated, mfaFactors = factors, isBusy = false)
                }
                showToast("Authenticator removed")
                onDone()
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid 6-digit authenticator code", isError = true)
            }
        }
    }

    fun refreshActiveDevices() {
        viewModelScope.launch {
            try {
                withValidSession { session ->
                    val deviceId = sessionStore.getOrCreateDeviceId()
                    val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android Device" }
                    val (devices, wasRevoked) = supabase.syncActiveDevices(
                        session = session,
                        currentDeviceId = deviceId,
                        deviceName = deviceName,
                        osVersion = "Android ${Build.VERSION.RELEASE}"
                    )
                    if (wasRevoked) {
                        signOut()
                    } else {
                        _uiState.update { it.copy(activeDevices = devices) }
                        evaluateDeviceLoginNotifications(devices, deviceId)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun removeDeviceSession(targetDeviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val remaining = withValidSession { session ->
                    supabase.removeOtherDevice(session, targetDeviceId)
                }
                _uiState.update { it.copy(activeDevices = remaining, isBusy = false) }
                showToast("Device session removed")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Could not remove device", isError = true)
            }
        }
    }

    // ==================== VERIFICATION ====================

    fun processUploadedCnicOcrInBackground(
        frontBytes: ByteArray,
        backBytes: ByteArray,
        frontHasFace: Boolean
    ) {
        cnicOcrJob?.cancel()
        cnicOcrJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cnicOcrState = CnicOcrBackgroundState(
                        status = CnicOcrStatus.PROCESSING,
                        frontFaceDetected = frontHasFace
                    )
                )
            }
            val resultState = runCnicOcrOnBytes(
                frontBytes = frontBytes,
                backBytes = backBytes,
                frontFaceAlreadyDetected = frontHasFace
            )
            _uiState.update { it.copy(cnicOcrState = resultState) }
        }
    }

    suspend fun verifyIdentityMatchWithBackendOcr(
        fullName: String,
        cnicDigits: String,
        issueDateIso: String,
        expiryDateIso: String,
        cnicFrontPath: String,
        cnicBackPath: String
    ): CnicMatchValidationResult {
        _uiState.update { st ->
            st.copy(cnicOcrState = st.cnicOcrState.copy(isVerifyingMatch = true))
        }
        return try {
            cnicOcrJob?.join()
            var ocrState = _uiState.value.cnicOcrState

            // If OCR hasn't run locally (e.g., resuming verification after restarting app),
            // fetch the uploaded CNIC front & back from backend storage and run OCR now.
            if ((ocrState.frontText.isBlank() && ocrState.backText.isBlank()) &&
                cnicFrontPath.isNotBlank() && cnicBackPath.isNotBlank()
            ) {
                val downloadedOcr = runCatching {
                    withValidSession { session ->
                        val frontBytes = supabase.downloadAuthenticatedStorageBytes(
                            session = session,
                            bucket = SupabaseConfig.BUCKET_SELLER_KYC,
                            objectPath = cnicFrontPath
                        )
                        val backBytes = supabase.downloadAuthenticatedStorageBytes(
                            session = session,
                            bucket = SupabaseConfig.BUCKET_SELLER_KYC,
                            objectPath = cnicBackPath
                        )
                        runCnicOcrOnBytes(frontBytes, backBytes, frontFaceAlreadyDetected = false)
                    }
                }.getOrNull()
                if (downloadedOcr != null) {
                    ocrState = downloadedOcr
                }
            }

            val validation = CnicMatchValidator.validateAgainstEnteredDetails(
                fullName = fullName,
                cnicDigits = cnicDigits,
                issueDateIso = issueDateIso,
                expiryDateIso = expiryDateIso,
                frontText = ocrState.frontText,
                backText = ocrState.backText
            )
            _uiState.update { st ->
                st.copy(
                    cnicOcrState = ocrState.copy(
                        isVerifyingMatch = false,
                        matchResult = validation
                    )
                )
            }
            validation
        } catch (e: Exception) {
            _uiState.update { st ->
                st.copy(cnicOcrState = st.cnicOcrState.copy(isVerifyingMatch = false))
            }
            CnicMatchValidationResult(isMatch = true)
        }
    }

    private suspend fun runCnicOcrOnBytes(
        frontBytes: ByteArray,
        backBytes: ByteArray,
        frontFaceAlreadyDetected: Boolean
    ): CnicOcrBackgroundState = withContext(Dispatchers.Default) {
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
        )
        try {
            val frontBmp = BitmapFactory.decodeByteArray(frontBytes, 0, frontBytes.size)
            val backBmp = BitmapFactory.decodeByteArray(backBytes, 0, backBytes.size)

            val frontText = if (frontBmp != null) {
                runCatching {
                    Tasks.await(
                        textRecognizer.process(InputImage.fromBitmap(frontBmp, 0)),
                        6000,
                        TimeUnit.MILLISECONDS
                    ).text
                }.getOrDefault("")
            } else ""

            val backText = if (backBmp != null) {
                runCatching {
                    Tasks.await(
                        textRecognizer.process(InputImage.fromBitmap(backBmp, 0)),
                        6000,
                        TimeUnit.MILLISECONDS
                    ).text
                }.getOrDefault("")
            } else ""

            val hasFace = frontFaceAlreadyDetected || if (frontBmp != null) {
                runCatching {
                    Tasks.await(
                        faceDetector.process(InputImage.fromBitmap(frontBmp, 0)),
                        2500,
                        TimeUnit.MILLISECONDS
                    ).isNotEmpty()
                }.getOrDefault(false)
            } else false

            val extracted = CnicMatchValidator.extractCnicDetails(
                frontText = frontText,
                backText = backText
            )
            CnicOcrBackgroundState(
                status = CnicOcrStatus.COMPLETED,
                frontText = frontText,
                backText = backText,
                extracted = extracted,
                frontFaceDetected = hasFace
            )
        } catch (e: Exception) {
            CnicOcrBackgroundState(
                status = CnicOcrStatus.FAILED,
                frontFaceDetected = frontFaceAlreadyDetected,
                errorMessage = e.message ?: "Background OCR failed"
            )
        } finally {
            runCatching { textRecognizer.close() }
            runCatching { faceDetector.close() }
        }
    }

    suspend fun uploadCnicCapture(bytes: ByteArray, side: String): String {
        require(bytes.isNotEmpty()) { "Captured CNIC image is empty" }
        val previewFile = withContext(Dispatchers.IO) {
            val file = File(getApplication<Application>().cacheDir, "kyc_preview_${side}_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file
        }
        return withValidSession { session ->
            val authUserId = supabase.getAuthenticatedUserId(session)
            val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
            val filePath = buildStoragePath(authUserId, "jpg")
            val savedPath = supabase.uploadVerificationDocument(authedSession, filePath, bytes, "image/jpeg")
            cacheLocalPreviewUrl(SupabaseConfig.BUCKET_SELLER_KYC, savedPath, Uri.fromFile(previewFile).toString())
            runCatching { ensureSignedUrl(authedSession, SupabaseConfig.BUCKET_SELLER_KYC, savedPath) }
            savedPath
        }
    }

    suspend fun uploadFacePhoto(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) {
            "Captured face photo must be between 1 byte and 5 MB"
        }
        val previewFile = withContext(Dispatchers.IO) {
            val file = File(getApplication<Application>().cacheDir, "face_preview_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file
        }
        return withValidSession { session ->
            val authUserId = supabase.getAuthenticatedUserId(session)
            val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
            val filePath = buildStoragePath(authUserId, "jpg")
            val savedPath = supabase.uploadFacePhoto(authedSession, filePath, bytes)
            cacheLocalPreviewUrl(SupabaseConfig.BUCKET_SELLER_FACES, savedPath, Uri.fromFile(previewFile).toString())
            runCatching { ensureSignedUrl(authedSession, SupabaseConfig.BUCKET_SELLER_FACES, savedPath) }
            savedPath
        }
    }

    private fun isDuplicateKeyError(e: Throwable): Boolean {
        if (e is SupabaseException && e.pgCode == "23505") return true
        val msg = e.message.orEmpty()
        return msg.contains("23505") ||
            msg.contains("duplicate key", ignoreCase = true) ||
            msg.contains("already taken", ignoreCase = true) ||
            msg.contains("seller_stores_slug", ignoreCase = true)
    }

    suspend fun checkStoreUsername(username: String): String {
        return withValidSession { session ->
            supabase.checkStoreUsername(session, username)
        }
    }

    fun submitVerification(details: SellerIdentityDetails, storeSetup: SellerStore? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                withValidSession { session ->
                    supabase.submitSellerIdentity(session, details)
                    if (storeSetup != null && storeSetup.shopName.isNotBlank()) {
                        val slugClean = storeSetup.slug.trim().lowercase().ifBlank {
                            Formatters.generateStoreUsernameFromName(storeSetup.shopName).ifBlank { "seller-store" }
                        }
                        val storeToSave = storeSetup.copy(
                            sellerId = session.userId,
                            slug = slugClean
                        )
                        supabase.saveSellerStore(session, storeToSave)
                    }
                    val refreshed = supabase.fetchVerification(session)
                    val refreshedStore = runCatching { supabase.fetchStore(session) }.getOrNull() ?: storeSetup ?: _uiState.value.store
                    val events = runCatching { supabase.fetchVerificationEvents(session) }.getOrDefault(emptyList())
                    sessionStore.saveWorkspaceCache(refreshed, refreshedStore, _uiState.value.products)
                    val shouldOfferPin = !sessionStore.hasAppPin() && !sessionStore.wasPinSetupOffered(session.userId)
                    _uiState.update {
                        it.copy(
                            verification = refreshed,
                            verificationEvents = events,
                            store = refreshedStore,
                            showPinSetupPrompt = shouldOfferPin,
                            rootDestination = RootDestination.MAIN,
                            activeTab = MainTab.HOME,
                            openVerificationFromAccount = false,
                            isBusy = false
                        )
                    }
                    evaluateVerificationStatusNotification(refreshed, session.userId)
                }
                showToast("Verification submitted! Status is now Under Review.")
            } catch (e: Exception) {
                if (isDuplicateKeyError(e)) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            usernameConflictNonce = it.usernameConflictNonce + 1
                        )
                    }
                    showToast("This store username is already taken. Choose another username.", isError = true)
                } else {
                    _uiState.update { it.copy(isBusy = false) }
                    showToast(e.message ?: "Verification submission failed", isError = true)
                }
            }
        }
    }

    fun openVerificationEditor() {
        _uiState.update {
            it.copy(
                rootDestination = RootDestination.VERIFICATION,
                openVerificationFromAccount = true
            )
        }
    }

    fun closeVerificationEditorToMain() {
        if (_uiState.value.verification.status != VerificationStatus.UNSUBMITTED) {
            _uiState.update {
                it.copy(
                    rootDestination = RootDestination.MAIN,
                    openVerificationFromAccount = false
                )
            }
        }
    }

    // ==================== STORE ====================

    suspend fun uploadStoreMedia(uri: Uri, prefix: String, onProgress: (Float) -> Unit): String {
        val media = readUriMedia(uri, isVideo = false)
        return uploadStoreMediaBytes(media.bytes, media.extension, media.mimeType, uri.toString(), onProgress)
    }

    suspend fun uploadStoreMediaBytes(
        bytes: ByteArray,
        extension: String = "jpg",
        mimeType: String = "image/jpeg",
        previewUriString: String? = null,
        onProgress: (Float) -> Unit = {}
    ): String {
        val localUrl = previewUriString ?: withContext(Dispatchers.IO) {
            val file = File(getApplication<Application>().cacheDir, "store_crop_${System.currentTimeMillis()}.$extension")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }
        return withValidSession { session ->
            val authUserId = supabase.getAuthenticatedUserId(session)
            val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
            val filePath = buildStoragePath(authUserId, extension)
            val uploaded = supabase.uploadBytesWithProgress(
                session = authedSession,
                bucket = SupabaseConfig.BUCKET_STORE_MEDIA,
                objectPath = filePath,
                bytes = bytes,
                mimeType = mimeType,
                onProgress = onProgress
            )
            cacheLocalPreviewUrl(SupabaseConfig.BUCKET_STORE_MEDIA, uploaded, localUrl)
            runCatching { ensureSignedUrl(authedSession, SupabaseConfig.BUCKET_STORE_MEDIA, uploaded) }
            uploaded
        }
    }

    fun saveStoreMediaOnly(isLogo: Boolean, uploadedPath: String) {
        val state = _uiState.value
        val sessionUserId = state.session?.userId.orEmpty()
        val currentStore = state.store
        val fallbackName = state.verification.details.fullName.takeIf { it.isNotBlank() }
            ?: state.session?.email?.substringBefore("@")
            ?: "My Store"
        val baseStore = currentStore ?: SellerStore(
            sellerId = sessionUserId,
            shopName = fallbackName,
            slug = Formatters.generateStoreUsernameFromName(fallbackName).ifBlank { "seller-store" }
        )
        val updated = if (isLogo) {
            baseStore.copy(logoPath = uploadedPath)
        } else {
            baseStore.copy(bannerPath = uploadedPath)
        }
        val oldPath = if (isLogo) currentStore?.logoPath else currentStore?.bannerPath
        viewModelScope.launch {
            _uiState.update { it.copy(store = updated, isBusy = true) }
            try {
                withValidSession { session ->
                    if (!oldPath.isNullOrBlank() && oldPath != uploadedPath) {
                        runCatching { supabase.deleteStorageObject(session, SupabaseConfig.BUCKET_STORE_MEDIA, oldPath) }
                    }
                    supabase.saveSellerStore(session, updated)
                    val refreshed = supabase.fetchStore(session) ?: updated
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, refreshed, _uiState.value.products)
                    _uiState.update { it.copy(store = refreshed, isBusy = false) }
                    refreshed.logoPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
                    refreshed.bannerPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
                }
                showToast(if (isLogo) "Store logo updated" else "Cover banner updated")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to save store media", isError = true)
            }
        }
    }

    fun removeStoreMedia(isLogo: Boolean) {
        val currentStore = _uiState.value.store ?: return
        val oldPath = if (isLogo) currentStore.logoPath else currentStore.bannerPath
        val updated = if (isLogo) currentStore.copy(logoPath = null) else currentStore.copy(bannerPath = null)
        val cacheKey = oldPath?.let { "${SupabaseConfig.BUCKET_STORE_MEDIA}:$it" }
        _uiState.update { st ->
            st.copy(
                store = updated,
                isBusy = true,
                localPreviewUrls = if (cacheKey != null) st.localPreviewUrls - cacheKey else st.localPreviewUrls,
                signedUrls = if (cacheKey != null) st.signedUrls - cacheKey else st.signedUrls
            )
        }
        viewModelScope.launch {
            try {
                withValidSession { session ->
                    if (!oldPath.isNullOrBlank()) {
                        runCatching { supabase.deleteStorageObject(session, SupabaseConfig.BUCKET_STORE_MEDIA, oldPath) }
                    }
                    runCatching { supabase.saveSellerStore(session, updated) }
                    runCatching { supabase.clearStoreMediaColumn(session, isLogo) }
                    val remote = runCatching { supabase.fetchStore(session) }.getOrNull()
                    val finalStore = (remote ?: updated).let { st ->
                        if (isLogo) st.copy(logoPath = null) else st.copy(bannerPath = null)
                    }
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, finalStore, _uiState.value.products)
                    _uiState.update { it.copy(store = finalStore, isBusy = false) }
                }
                showToast(if (isLogo) "Store logo deleted" else "Cover banner deleted")
            } catch (e: Exception) {
                _uiState.update { it.copy(store = currentStore, isBusy = false) }
                showToast(e.message ?: "Failed to delete media", isError = true)
            }
        }
    }

    fun saveStore(store: SellerStore, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                withValidSession { session ->
                    supabase.saveSellerStore(session, store)
                    val refreshed = supabase.fetchStore(session) ?: store.copy(sellerId = session.userId)
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, refreshed, _uiState.value.products)
                    _uiState.update { it.copy(store = refreshed, isBusy = false) }
                    refreshed.logoPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
                    refreshed.bannerPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
                }
                showToast("Store profile saved")
                onSuccess()
            } catch (e: Exception) {
                if (isDuplicateKeyError(e)) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            usernameConflictNonce = it.usernameConflictNonce + 1
                        )
                    }
                    showToast("This store username is already taken. Choose another username.", isError = true)
                } else {
                    _uiState.update { it.copy(isBusy = false) }
                    showToast(e.message ?: "Failed to save store", isError = true)
                }
            }
        }
    }

    // ==================== PRODUCTS & DRAFTS ====================

    fun refreshWorkspace() {
        viewModelScope.launch {
            try {
                withValidSession { session ->
                    loadSellerWorkspace(session)
                }
            } catch (e: Exception) {
                showToast(e.message ?: "Refresh failed", isError = true)
            }
        }
    }

    suspend fun uploadProductImage(uri: Uri, onProgress: (Float) -> Unit): String {
        val media = readUriMedia(uri, isVideo = false)
        return withValidSession { session ->
            val authUserId = supabase.getAuthenticatedUserId(session)
            val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
            val filePath = buildStoragePath(authUserId, media.extension)
            val uploaded = supabase.uploadBytesWithProgress(
                session = authedSession,
                bucket = SupabaseConfig.BUCKET_PRODUCT_IMAGES,
                objectPath = filePath,
                bytes = media.bytes,
                mimeType = media.mimeType,
                onProgress = onProgress
            )
            cacheLocalPreviewUrl(SupabaseConfig.BUCKET_PRODUCT_IMAGES, uploaded, uri.toString())
            runCatching { ensureSignedUrl(authedSession, SupabaseConfig.BUCKET_PRODUCT_IMAGES, uploaded) }
            uploaded
        }
    }

    suspend fun uploadProductVideo(uri: Uri, onProgress: (Float) -> Unit): String {
        val media = readUriMedia(uri, isVideo = true)
        return withValidSession { session ->
            val authUserId = supabase.getAuthenticatedUserId(session)
            val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
            val filePath = buildStoragePath(authUserId, media.extension)
            val uploaded = supabase.uploadBytesWithProgress(
                session = authedSession,
                bucket = SupabaseConfig.BUCKET_PRODUCT_VIDEOS,
                objectPath = filePath,
                bytes = media.bytes,
                mimeType = media.mimeType,
                onProgress = onProgress
            )
            cacheLocalPreviewUrl(SupabaseConfig.BUCKET_PRODUCT_VIDEOS, uploaded, uri.toString())
            runCatching { ensureSignedUrl(authedSession, SupabaseConfig.BUCKET_PRODUCT_VIDEOS, uploaded) }
            uploaded
        }
    }

    fun submitProduct(
        editingProduct: ProductItem?,
        name: String,
        category: String,
        price: Double,
        stock: Int,
        description: String,
        imagePaths: List<String>,
        videoPath: String?,
        attributes: Map<String, String>,
        draftIdToDelete: String? = null,
        onSuccess: () -> Unit
    ) {
        if (editingProduct != null) {
            val unchanged = editingProduct.name.trim() == name.trim() &&
                editingProduct.category.ifBlank { "Electronics" } == category.ifBlank { "Electronics" } &&
                editingProduct.price == price &&
                editingProduct.stock == stock &&
                editingProduct.description.trim() == description.trim() &&
                editingProduct.imagePaths == imagePaths &&
                editingProduct.videoPath == videoPath &&
                editingProduct.attributes == attributes
            if (unchanged) {
                showToast("No changes detected — product was not resubmitted")
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                withValidSession { session ->
                    val authUserId = supabase.getAuthenticatedUserId(session)
                    val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
                    val code = editingProduct?.productCode?.takeIf { it.length == 10 }
                        ?: Formatters.generateProductCode10()
                    val item = ProductItem(
                        id = editingProduct?.id ?: "",
                        sellerId = authUserId,
                        name = name,
                        description = description,
                        price = price,
                        stock = stock,
                        sales = editingProduct?.sales ?: 0,
                        status = editingProduct?.status ?: com.example.data.model.ProductStatus.PENDING,
                        imagePaths = imagePaths,
                        videoPath = videoPath,
                        productCode = code,
                        category = category.ifBlank { "Electronics" },
                        attributes = attributes
                    )
                    if (editingProduct == null) {
                        supabase.insertProduct(authedSession, item)
                    } else {
                        supabase.updateProduct(authedSession, item)
                    }
                    if (!draftIdToDelete.isNullOrBlank()) {
                        runCatching { supabase.deleteDraft(authedSession, draftIdToDelete) }
                    }
                    val updatedList = supabase.fetchProducts(authedSession)
                    val updatedDrafts = runCatching { supabase.fetchDrafts(authedSession) }.getOrDefault(_uiState.value.drafts)
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, _uiState.value.store, updatedList)
                    _uiState.update {
                        it.copy(products = updatedList, drafts = updatedDrafts, isBusy = false)
                    }
                }
                showToast(
                    if (editingProduct == null) "Product submitted for approval"
                    else "Product updated and queued for review"
                )
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to submit product", isError = true)
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                withValidSession { session ->
                    supabase.deleteProduct(session, productId)
                    val updated = supabase.fetchProducts(session)
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, _uiState.value.store, updated)
                    _uiState.update { it.copy(products = updated, isBusy = false) }
                }
                showToast("Product deleted")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to delete product", isError = true)
            }
        }
    }

    fun quickAdjustStock(product: ProductItem, delta: Int) {
        val nextStock = (product.stock + delta).coerceAtLeast(0)
        if (nextStock == product.stock) return
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                state.copy(
                    products = state.products.map {
                        if (it.id == product.id) it.copy(stock = nextStock) else it
                    }
                )
            }
            try {
                withValidSession { session ->
                    val updated = supabase.updateProductStock(session, product.id, nextStock)
                    if (updated != null) {
                        _uiState.update { state ->
                            state.copy(
                                products = state.products.map {
                                    if (it.id == updated.id) updated else it
                                }
                            )
                        }
                    }
                }
                showToast("Stock updated to $nextStock")
            } catch (e: Exception) {
                // Rollback on failure
                _uiState.update { state ->
                    state.copy(
                        products = state.products.map {
                            if (it.id == product.id) it.copy(stock = product.stock) else it
                        }
                    )
                }
                showToast(e.message ?: "Failed to update stock", isError = true)
            }
        }
    }

    suspend fun saveOrAutosaveDraft(draft: ProductDraft, silent: Boolean): ProductDraft? {
        return try {
            val saved = withValidSession { session ->
                val authUserId = supabase.getAuthenticatedUserId(session)
                val authedSession = if (session.userId == authUserId) session else session.copy(userId = authUserId)
                val res = supabase.saveDraft(authedSession, draft.copy(sellerId = authUserId))
                val all = supabase.fetchDrafts(authedSession)
                _uiState.update { it.copy(drafts = all) }
                res
            }
            if (!silent) showToast("Draft saved")
            saved
        } catch (e: Exception) {
            if (!silent) showToast(e.message ?: "Could not save draft", isError = true)
            null
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            try {
                withValidSession { session ->
                    supabase.deleteDraft(session, draftId)
                    val all = supabase.fetchDrafts(session)
                    _uiState.update { it.copy(drafts = all) }
                }
                showToast("Draft removed")
            } catch (e: Exception) {
                showToast(e.message ?: "Failed to remove draft", isError = true)
            }
        }
    }

    private data class MediaPayload(
        val bytes: ByteArray,
        val extension: String,
        val mimeType: String
    )

    private fun buildStoragePath(userId: String, extension: String): String {
        val cleanUserId = userId.trim().trim('/')
        require(cleanUserId.isNotBlank()) { "Seller is not authenticated" }
        val ext = extension.lowercase(Locale.US).trimStart('.')
        val photoUuid = UUID.randomUUID().toString().lowercase(Locale.US)
        return "$cleanUserId/$photoUuid.$ext"
    }

    private suspend fun readUriMedia(uri: Uri, isVideo: Boolean): MediaPayload =
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val detectedMime = resolver.getType(uri)?.lowercase(Locale.US).orEmpty()
            var displayName: String? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = cursor.getString(idx)
                }
            }
            val rawExt = (displayName ?: uri.lastPathSegment.orEmpty())
                .substringAfterLast('.', "")
                .lowercase(Locale.US)

            val (ext, mime) = if (isVideo) {
                val chosenExt = when {
                    rawExt in SupabaseConfig.ALLOWED_VIDEO_EXTENSIONS -> rawExt
                    detectedMime.contains("webm") -> "webm"
                    detectedMime.contains("quicktime") || detectedMime.contains("mov") -> "mov"
                    else -> "mp4"
                }
                val chosenMime = when (chosenExt) {
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    else -> "video/mp4"
                }
                chosenExt to chosenMime
            } else {
                val chosenExt = when {
                    rawExt in SupabaseConfig.ALLOWED_IMAGE_EXTENSIONS -> rawExt
                    detectedMime.contains("png") -> "png"
                    detectedMime.contains("webp") -> "webp"
                    detectedMime.contains("jpeg") -> "jpeg"
                    else -> "jpg"
                }
                val chosenMime = when (chosenExt) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                chosenExt to chosenMime
            }

            val declaredSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst() && sizeIdx >= 0) c.getLong(sizeIdx) else -1L
            } ?: -1L
            val maxBytes = if (isVideo) 50L * 1024L * 1024L else 12L * 1024L * 1024L
            if (declaredSize > maxBytes) {
                throw IllegalStateException(
                    if (isVideo) "Product video must be under 50 MB" else "Image must be under 12 MB"
                )
            }

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected file")
            if (bytes.isEmpty()) {
                throw IllegalStateException("Selected file is empty")
            }
            if (bytes.size.toLong() > maxBytes) {
                throw IllegalStateException(
                    if (isVideo) "Product video must be under 50 MB" else "Image must be under 12 MB"
                )
            }
            MediaPayload(bytes = bytes, extension = ext, mimeType = mime)
        }
}
