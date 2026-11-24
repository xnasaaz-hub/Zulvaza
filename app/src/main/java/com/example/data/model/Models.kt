package com.example.data.model

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val userId: String,
    val email: String
) {
    fun isExpired(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        return nowEpochSec >= (expiresAtEpochSec - 30L)
    }
}

data class MfaFactor(
    val id: String,
    val factorType: String,
    val status: String,
    val friendlyName: String? = null
)

data class MfaTotpEnrollment(
    val factorId: String,
    val qrCodeSvgUri: String,
    val secret: String,
    val totpUri: String
)

data class LoginResult(
    val session: UserSession,
    val requiresMfa: Boolean,
    val verifiedTotpFactorId: String? = null
)

data class ActiveDeviceSession(
    val deviceId: String,
    val deviceName: String,
    val osVersion: String,
    val ipAddress: String,
    val cityCountry: String,
    val isOnline: Boolean = true,
    val isCurrentDevice: Boolean = false,
    val lastActiveEpochSec: Long = System.currentTimeMillis() / 1000L
)

enum class VerificationStatus(val raw: String, val label: String) {
    UNSUBMITTED("unsubmitted", "Unsubmitted"),
    PENDING("pending", "Under Review"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    SUSPENDED("suspended", "Suspended");

    companion object {
        fun fromRaw(raw: String?): VerificationStatus =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: UNSUBMITTED
    }
}

data class SellerIdentityDetails(
    val fullName: String = "",
    val cnic: String = "",
    val gender: String = "male",
    val dateOfBirth: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val cnicFront: String = "",
    val cnicBack: String = "",
    val facePhoto: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val city: String = "",
    val street: String = "",
    val state: String = "",
    val address: String = "",
    val address2: String = "",
    val zipCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class SellerVerification(
    val sellerId: String = "",
    val status: VerificationStatus = VerificationStatus.UNSUBMITTED,
    val details: SellerIdentityDetails = SellerIdentityDetails(),
    val reason: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null
)

data class VerificationEvent(
    val id: String,
    val status: String,
    val reason: String?,
    val createdAt: String?
)

enum class SocialPlatform(val key: String, val displayName: String) {
    FACEBOOK("facebook", "Facebook"),
    INSTAGRAM("instagram", "Instagram"),
    WHATSAPP("whatsapp", "WhatsApp"),
    TIKTOK("tiktok", "TikTok"),
    YOUTUBE("youtube", "YouTube"),
    X("x", "X"),
    LINKEDIN("linkedin", "LinkedIn"),
    WEBSITE("website", "Website");

    companion object {
        fun fromKey(key: String): SocialPlatform =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WEBSITE
    }
}

data class SocialProfile(
    val platform: String,
    val url: String,
    val active: Boolean = true
)

data class SellerStore(
    val sellerId: String = "",
    val shopName: String = "",
    val slug: String = "",
    val bio: String = "",
    val introduction: String = "",
    val logoPath: String? = null,
    val bannerPath: String? = null,
    val socialProfiles: List<SocialProfile> = emptyList()
)

enum class ProductStatus(val raw: String, val label: String) {
    PENDING("pending", "Pending"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    SUSPENDED("suspended", "Suspended");

    companion object {
        fun fromRaw(raw: String?): ProductStatus =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

data class ProductItem(
    val id: String,
    val sellerId: String,
    val name: String,
    val description: String,
    val price: Double,
    val stock: Int,
    val sales: Int,
    val status: ProductStatus,
    val rejectionReason: String? = null,
    val imagePaths: List<String> = emptyList(),
    val videoPath: String? = null,
    val productCode: String = "",
    val category: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val createdAt: String? = null
)

data class ProductDraft(
    val id: String,
    val sellerId: String,
    val name: String = "",
    val description: String = "",
    val price: Double? = null,
    val stock: Int? = null,
    val category: String = "",
    val imagePaths: List<String> = emptyList(),
    val videoPath: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val updatedAt: String? = null,
    val createdAt: String? = null
)

enum class PasswordStrength(val label: String, val level: Int) {
    EMPTY("Enter password", 0),
    WEAK("Weak", 1),
    MEDIUM("Medium", 2),
    STRONG("Strong", 3)
}

enum class DeepLinkDestination {
    LOGIN,
    SIGNUP,
    DASHBOARD,
    PRODUCTS,
    STORE,
    ONBOARDING
}

enum class NotificationType(val raw: String) {
    DEVICE_LOGIN("device_login"),
    VERIFICATION("verification"),
    APP_UPDATE("app_update"),
    GENERAL("general");

    companion object {
        fun fromRaw(raw: String?): NotificationType =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

data class SellerNotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val targetRoute: String? = null
)

data class CnicCaptureResult(
    val jpegBytes: ByteArray,
    val extractedText: String = "",
    val detectedCnicDigits: String? = null,
    val detectedDates: List<String> = emptyList()
)

data class CnicDualScanResult(
    val front: CnicCaptureResult,
    val back: CnicCaptureResult
)
