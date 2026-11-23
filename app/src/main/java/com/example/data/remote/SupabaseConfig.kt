package com.example.data.remote

object SupabaseConfig {
    const val SUPABASE_URL = "https://zsyrtjtnoowxebdddtmf.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_NMKSRzQjd5RTRmin8abGNg_XSdB8Yvb"

    // Storage buckets (private; paths must start with "<user_id>/")
    const val BUCKET_PRODUCT_IMAGES = "product-images"
    const val BUCKET_PRODUCT_VIDEOS = "product-videos"
    const val BUCKET_STORE_MEDIA = "store-media"
    const val BUCKET_SELLER_KYC = "seller-kyc"

    val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val ALLOWED_VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov")

    const val WEB_BASE_URL = "https://zulvaza.com"
    const val SELLER_CENTER_HOST = "sellercenter.zulvaza.com"
    const val DEEP_LINK_SCHEME = "zulvazaseller"
    const val DEFAULT_GITHUB_REPO = "zulvaza/zulvaza-seller-center"

    val CURRENT_VERSION_CHANGELOG = listOf(
        "Smart CNIC live card detection with OCR field matching before submission",
        "Interactive Store Logo (1:1) & Cover Banner (16:9) cropper with preview, delete & change controls",
        "New Notifications Center with alerts for device logins, KYC approval/rejection & app updates",
        "Verification details viewer inside Settings and current password check on password change",
        "Redesigned Product Studio with digits-only price & stock and Publish workflow"
    )
}
