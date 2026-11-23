package com.example.ui.state

import android.app.Application
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
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseException
import com.example.ui.theme.ThemeMode
import com.example.util.Formatters
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

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
    val recoveryPendingEmail: String? = null,
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
    val permissionsPromptCompleted: Boolean = true
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

    init {
        NotificationHelper.ensureChannel(application.applicationContext)
        viewModelScope.launch {
            val savedRepo = sessionStore.getGithubRepo()
            val savedNotifs = sessionStore.loadNotifications()
            val permsDone = sessionStore.isPermissionsPromptDone()
            _uiState.update {
                it.copy(
                    githubRepo = savedRepo,
                    notifications = savedNotifs,
                    permissionsPromptCompleted = permsDone
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
                    isOfflineMode = false,
                    rootDestination = dest,
                    isDataLoading = false,
                    isBusy = false
                )
            }

            // Detect alerts: new device logins, verification decision, and app updates
            evaluateDeviceLoginNotifications(devices, deviceId)
            evaluateVerificationStatusNotification(verification)
            checkForAppUpdateSilent()

            // Pre-sign thumbnails, store logo/banner, and CNIC previews in background
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
        viewModelScope.launch {
            store?.logoPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
            store?.bannerPath?.let { ensureSignedUrl(session, SupabaseConfig.BUCKET_STORE_MEDIA, it) }
            products.forEach { product ->
                product.imagePaths.firstOrNull()?.let {
                    ensureSignedUrl(session, SupabaseConfig.BUCKET_PRODUCT_IMAGES, it)
                }
            }
            drafts.forEach { draft ->
                draft.imagePaths.firstOrNull()?.let {
                    ensureSignedUrl(session, SupabaseConfig.BUCKET_PRODUCT_IMAGES, it)
                }
            }
            if (verification.details.cnicFront.isNotBlank()) {
                ensureSignedUrl(session, SupabaseConfig.BUCKET_SELLER_KYC, verification.details.cnicFront)
            }
            if (verification.details.cnicBack.isNotBlank()) {
                ensureSignedUrl(session, SupabaseConfig.BUCKET_SELLER_KYC, verification.details.cnicBack)
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
                showToast("6-digit verification code sent via support.zulvaza.com to $cleanEmail")
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
                showToast("New 6-digit code sent via support.zulvaza.com to $email")
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

    fun sendPasswordResetOtp(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                supabase.sendPasswordReset(email)
                _uiState.update { it.copy(recoveryPendingEmail = email.trim(), isBusy = false) }
                showToast("Recovery code sent to ${email.trim()}")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Could not send recovery email", isError = true)
            }
        }
    }

    fun verifyRecoveryAndSetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val session = supabase.verifyRecoveryOtpAndSetPassword(email, code, newPassword)
                _uiState.update { it.copy(recoveryPendingEmail = null) }
                showToast("Password updated! Signing you in...")
                completeSignIn(session)
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Invalid recovery code or password", isError = true)
            }
        }
    }

    fun clearRecoveryPending() {
        _uiState.update { it.copy(recoveryPendingEmail = null) }
    }

    private suspend fun completeSignIn(session: UserSession) {
        sessionStore.saveSession(session)
        val ok = verifySellerRoleOrSignOut(session)
        if (ok) {
            loadSellerWorkspace(session)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value.session?.let { supabase.signOut(it) }
            sessionStore.clearSession()
            _uiState.update {
                SellerUiState(
                    rootDestination = RootDestination.LOGIN,
                    isOnline = it.isOnline,
                    themeMode = it.themeMode,
                    githubRepo = it.githubRepo
                )
            }
            showToast("Logged out safely")
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
        currentDeviceId: String
    ) {
        val known = sessionStore.loadKnownDeviceIds()
        val remoteIds = devices.map { it.deviceId }.toSet()
        if (known == null) {
            // Seed with current device so any other active/new device triggers an alert
            sessionStore.saveKnownDeviceIds(remoteIds + currentDeviceId)
            val others = devices.filter { !it.isCurrentDevice }
            others.forEach { dev ->
                pushNotification(
                    title = "Another Device Signed In",
                    message = "${dev.deviceName} (${dev.cityCountry}) is active on your seller account.",
                    type = NotificationType.DEVICE_LOGIN,
                    targetRoute = "devices"
                )
            }
            return
        }
        val newlySeen = devices.filter { !it.isCurrentDevice && it.deviceId !in known }
        if (newlySeen.isNotEmpty()) {
            sessionStore.saveKnownDeviceIds(known + remoteIds + currentDeviceId)
            newlySeen.forEach { dev ->
                pushNotification(
                    title = "New Device Login Detected",
                    message = "${dev.deviceName} (${dev.osVersion}) signed in from ${dev.cityCountry}.",
                    type = NotificationType.DEVICE_LOGIN,
                    targetRoute = "devices"
                )
            }
        } else if (!known.containsAll(remoteIds)) {
            sessionStore.saveKnownDeviceIds(known + remoteIds + currentDeviceId)
        }
    }

    private suspend fun evaluateVerificationStatusNotification(verification: SellerVerification) {
        val prevRaw = sessionStore.getLastKnownVerificationStatus()
        val currRaw = verification.status.raw
        sessionStore.setLastKnownVerificationStatus(currRaw)
        if (prevRaw == null || prevRaw == currRaw) return
        when (verification.status) {
            VerificationStatus.APPROVED -> pushNotification(
                title = "Seller Verification Approved",
                message = "Congratulations! Your CNIC & store verification has been approved.",
                type = NotificationType.VERIFICATION,
                targetRoute = "verification"
            )
            VerificationStatus.REJECTED -> pushNotification(
                title = "Verification Needs Attention",
                message = verification.reason?.takeIf { it.isNotBlank() }
                    ?: "Your verification submission was rejected. Please review and resubmit.",
                type = NotificationType.VERIFICATION,
                targetRoute = "verification"
            )
            else -> Unit
        }
    }

    private suspend fun pushNotification(
        title: String,
        message: String,
        type: NotificationType,
        targetRoute: String? = null
    ) {
        val item = SellerNotificationItem(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = type,
            createdAtEpochMs = System.currentTimeMillis(),
            isRead = false,
            targetRoute = targetRoute
        )
        val updated = (listOf(item) + _uiState.value.notifications).take(100)
        sessionStore.saveNotifications(updated)
        _uiState.update { it.copy(notifications = updated) }
        NotificationHelper.postLocalNotification(
            context = getApplication(),
            notificationId = item.id.hashCode(),
            title = title,
            body = message
        )
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            val updated = _uiState.value.notifications.map {
                if (it.id == id) it.copy(isRead = true) else it
            }
            sessionStore.saveNotifications(updated)
            _uiState.update { it.copy(notifications = updated) }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val updated = _uiState.value.notifications.map { it.copy(isRead = true) }
            sessionStore.saveNotifications(updated)
            _uiState.update { it.copy(notifications = updated) }
        }
    }

    fun deleteNotifications(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val prevCount = _uiState.value.notifications.size
            val updated = _uiState.value.notifications.filterNot { it.id in ids }
            sessionStore.saveNotifications(updated)
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
                    it.copy(appUpdateStatus = AppUpdateStatus.Error(e.message ?: "Download failed"))
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

    fun updatePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val currentSession = _uiState.value.session ?: throw IllegalStateException("Not logged in")
                val email = currentSession.email
                require(email.isNotBlank()) { "Account email unavailable" }
                // Verify current password before permitting update
                val verifiedLogin = try {
                    supabase.signInWithPassword(email, currentPassword)
                } catch (e: SupabaseException) {
                    throw IllegalStateException(
                        if (e.statusCode in listOf(400, 401)) "Current password is incorrect"
                        else e.message ?: "Unable to verify current password"
                    )
                }
                sessionStore.saveSession(verifiedLogin.session)
                _uiState.update { it.copy(session = verifiedLogin.session) }
                supabase.updatePassword(verifiedLogin.session, newPassword)
                _uiState.update { it.copy(isBusy = false) }
                showToast("Password updated")
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to update password", isError = true)
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

    fun submitVerification(details: SellerIdentityDetails) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                withValidSession { session ->
                    supabase.submitSellerIdentity(session, details)
                    val refreshed = supabase.fetchVerification(session)
                    val events = runCatching { supabase.fetchVerificationEvents(session) }.getOrDefault(emptyList())
                    sessionStore.saveWorkspaceCache(refreshed, _uiState.value.store, _uiState.value.products)
                    _uiState.update {
                        it.copy(
                            verification = refreshed,
                            verificationEvents = events,
                            rootDestination = RootDestination.MAIN,
                            activeTab = MainTab.HOME,
                            openVerificationFromAccount = false,
                            isBusy = false
                        )
                    }
                }
                showToast("Identity submitted for review")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Verification submission failed", isError = true)
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

    fun removeStoreMedia(isLogo: Boolean) {
        val currentStore = _uiState.value.store ?: return
        val updated = if (isLogo) currentStore.copy(logoPath = null) else currentStore.copy(bannerPath = null)
        viewModelScope.launch {
            runCatching {
                withValidSession { session ->
                    supabase.saveSellerStore(session, updated)
                    val refreshed = supabase.fetchStore(session) ?: updated
                    sessionStore.saveWorkspaceCache(_uiState.value.verification, refreshed, _uiState.value.products)
                    _uiState.update { it.copy(store = refreshed) }
                }
            }.onSuccess {
                showToast(if (isLogo) "Store logo deleted" else "Store banner deleted")
            }.onFailure { e ->
                showToast(e.message ?: "Failed to remove media", isError = true)
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
                _uiState.update { it.copy(isBusy = false) }
                showToast(e.message ?: "Failed to save store", isError = true)
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
                    val code = editingProduct?.productCode?.takeIf { it.length == 10 }
                        ?: Formatters.generateProductCode10()
                    val item = ProductItem(
                        id = editingProduct?.id ?: "",
                        sellerId = session.userId,
                        name = name,
                        description = description,
                        price = price,
                        stock = stock,
                        sales = editingProduct?.sales ?: 0,
                        status = editingProduct?.status ?: com.example.data.model.ProductStatus.PENDING,
                        imagePaths = imagePaths,
                        videoPath = videoPath,
                        productCode = code,
                        category = category,
                        attributes = attributes
                    )
                    if (editingProduct == null) {
                        supabase.insertProduct(session, item)
                    } else {
                        supabase.updateProduct(session, item)
                    }
                    if (!draftIdToDelete.isNullOrBlank()) {
                        runCatching { supabase.deleteDraft(session, draftIdToDelete) }
                    }
                    val updatedList = supabase.fetchProducts(session)
                    val updatedDrafts = runCatching { supabase.fetchDrafts(session) }.getOrDefault(_uiState.value.drafts)
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
                val res = supabase.saveDraft(session, draft)
                val all = supabase.fetchDrafts(session)
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

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected file")
            if (bytes.isEmpty()) {
                throw IllegalStateException("Selected file is empty")
            }
            MediaPayload(bytes = bytes, extension = ext, mimeType = mime)
        }
}
