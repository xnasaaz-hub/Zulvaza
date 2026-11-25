package com.example.data.remote

object SupabaseConfig {
    const val SUPABASE_URL = "https://zsyrtjtnoowxebdddtmf.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_NMKSRzQjd5RTRmin8abGNg_XSdB8Yvb"

    // Storage buckets (private; paths must start with "<user_id>/")
    const val BUCKET_PRODUCT_IMAGES = "product-images"
    const val BUCKET_PRODUCT_VIDEOS = "product-videos"
    const val BUCKET_STORE_MEDIA = "store-media"
    const val BUCKET_SELLER_KYC = "seller-kyc"
    const val BUCKET_SELLER_FACES = "seller-faces"

    val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val ALLOWED_VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov")

    const val WEB_BASE_URL = "https://zulvaza.com"
    const val SELLER_CENTER_HOST = "sellercenter.zulvaza.com"
    const val DEEP_LINK_SCHEME = "zulvazaseller"
    const val DEFAULT_GITHUB_REPO = "xnasaaz-hub/Zulvaza-Seller-Center"

    val CURRENT_VERSION_CHANGELOG = listOf(
        "Smart CNIC Card + Face Detection scanner: detects the physical card and printed face (left or right) to capture clean photos instantly",
        "Background CNIC OCR & server match verification runs automatically after upload",
        "Automatic GitHub Release publishing and improved in-app APK updater with signature matching",
        "5-step verification flow with directional face liveness, store logo/banner cropper, and real-time notifications"
    )
}
