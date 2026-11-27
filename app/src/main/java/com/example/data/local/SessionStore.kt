package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.NotificationType
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerIdentityDetails
import com.example.data.model.SellerNotificationItem
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.SocialProfile
import com.example.data.model.UserSession
import com.example.data.model.VerificationStatus
import com.example.data.remote.SupabaseConfig
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.sellerDataStore: DataStore<Preferences> by preferencesDataStore(name = "zulvaza_seller_prefs")

data class OfflineWorkspaceCache(
    val verification: SellerVerification = SellerVerification(),
    val store: SellerStore? = null,
    val products: List<ProductItem> = emptyList()
)

class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CACHE_VERIFICATION = stringPreferencesKey("cache_verification")
        val CACHE_STORE = stringPreferencesKey("cache_store")
        val CACHE_PRODUCTS = stringPreferencesKey("cache_products")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val NOTIFICATIONS_JSON = stringPreferencesKey("notifications_json")
        val KNOWN_DEVICE_IDS = stringPreferencesKey("known_device_ids")
        val LAST_VERIFICATION_STATUS = stringPreferencesKey("last_verification_status")
        val LAST_NOTIFIED_UPDATE_TAG = stringPreferencesKey("last_notified_update_tag")
        val PERMISSIONS_PROMPT_DONE = booleanPreferencesKey("permissions_prompt_done")
        val APP_PIN_HASH = stringPreferencesKey("app_pin_hash")
        val APP_PIN_ENABLED = booleanPreferencesKey("app_pin_enabled")
    }

    private fun scopedKey(base: String, userId: String?): Preferences.Key<String> {
        val clean = userId?.trim().orEmpty()
        return if (clean.isNotBlank()) stringPreferencesKey("${base}_$clean") else stringPreferencesKey(base)
    }

    private fun pinOfferedKey(userId: String): Preferences.Key<Boolean> =
        booleanPreferencesKey("pin_setup_offered_${userId.trim()}")

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("zulvaza_seller_pin_v1:${pin.trim()}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    val themeModeFlow: Flow<ThemeMode> = context.sellerDataStore.data.map { ThemeMode.LIGHT }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.sellerDataStore.data.first()
        val existing = prefs[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        context.sellerDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun getGithubRepo(): String {
        val prefs = context.sellerDataStore.data.first()
        return prefs[Keys.GITHUB_REPO]?.takeIf { it.contains("/") } ?: SupabaseConfig.DEFAULT_GITHUB_REPO
    }

    suspend fun setGithubRepo(repo: String) {
        val cleaned = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        context.sellerDataStore.edit { prefs ->
            if (cleaned.contains("/")) {
                prefs[Keys.GITHUB_REPO] = cleaned
            }
        }
    }

    suspend fun saveSession(session: UserSession) {
        context.sellerDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.EXPIRES_AT] = session.expiresAtEpochSec
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
        }
    }

    suspend fun loadSession(): UserSession? {
        val prefs = context.sellerDataStore.data.first()
        val access = prefs[Keys.ACCESS_TOKEN] ?: return null
        val refresh = prefs[Keys.REFRESH_TOKEN] ?: return null
        val userId = prefs[Keys.USER_ID] ?: return null
        val email = prefs[Keys.USER_EMAIL] ?: ""
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0L
        if (access.isBlank() || userId.isBlank()) return null
        return UserSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSec = expiresAt,
            userId = userId,
            email = email
        )
    }

    suspend fun clearSession() {
        context.sellerDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.EXPIRES_AT)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.CACHE_VERIFICATION)
            prefs.remove(Keys.CACHE_STORE)
            prefs.remove(Keys.CACHE_PRODUCTS)
        }
    }

    suspend fun isPermissionsPromptDone(): Boolean {
        val prefs = context.sellerDataStore.data.first()
        return prefs[Keys.PERMISSIONS_PROMPT_DONE] ?: false
    }

    suspend fun setPermissionsPromptDone() {
        context.sellerDataStore.edit { it[Keys.PERMISSIONS_PROMPT_DONE] = true }
    }

    // ==================== 5-DIGIT APP LOCK PIN ====================

    suspend fun hasAppPin(): Boolean {
        val hash = context.sellerDataStore.data.first()[Keys.APP_PIN_HASH]
        return !hash.isNullOrBlank()
    }

    suspend fun isAppPinEnabled(): Boolean {
        val prefs = context.sellerDataStore.data.first()
        val hash = prefs[Keys.APP_PIN_HASH]
        val enabled = prefs[Keys.APP_PIN_ENABLED] ?: false
        return enabled && !hash.isNullOrBlank()
    }

    suspend fun setAppPin(pin: String, enabled: Boolean = true) {
        val clean = pin.filter { it.isDigit() }
        require(clean.length == 5) { "PIN must be 5 digits" }
        context.sellerDataStore.edit { prefs ->
            prefs[Keys.APP_PIN_HASH] = hashPin(clean)
            prefs[Keys.APP_PIN_ENABLED] = enabled
        }
    }

    suspend fun verifyAppPin(pin: String): Boolean {
        val clean = pin.filter { it.isDigit() }
        if (clean.length != 5) return false
        val stored = context.sellerDataStore.data.first()[Keys.APP_PIN_HASH] ?: return false
        return stored == hashPin(clean)
    }

    suspend fun setAppPinEnabled(enabled: Boolean) {
        context.sellerDataStore.edit { prefs ->
            val hasHash = !prefs[Keys.APP_PIN_HASH].isNullOrBlank()
            prefs[Keys.APP_PIN_ENABLED] = enabled && hasHash
        }
    }

    suspend fun deleteAppPin() {
        context.sellerDataStore.edit { prefs ->
            prefs.remove(Keys.APP_PIN_HASH)
            prefs[Keys.APP_PIN_ENABLED] = false
        }
    }

    suspend fun wasPinSetupOffered(userId: String): Boolean {
        if (userId.isBlank()) return true
        return context.sellerDataStore.data.first()[pinOfferedKey(userId)] ?: false
    }

    suspend fun markPinSetupOffered(userId: String) {
        if (userId.isBlank()) return
        context.sellerDataStore.edit { it[pinOfferedKey(userId)] = true }
    }

    suspend fun loadNotifications(userId: String? = null): List<SellerNotificationItem> {
        val prefs = context.sellerDataStore.data.first()
        val uid = userId ?: prefs[Keys.USER_ID]
        val raw = prefs[scopedKey("notifications_json", uid)]
            ?: prefs[Keys.NOTIFICATIONS_JSON]
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        SellerNotificationItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            message = obj.optString("message"),
                            type = NotificationType.fromRaw(obj.optString("type")),
                            createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                            isRead = obj.optBoolean("isRead", false),
                            targetRoute = obj.optString("targetRoute").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.sortedByDescending { it.createdAtEpochMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveNotifications(items: List<SellerNotificationItem>, userId: String? = null) {
        val arr = JSONArray().apply {
            items.take(100).forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("message", n.message)
                    put("type", n.type.raw)
                    put("createdAtEpochMs", n.createdAtEpochMs)
                    put("isRead", n.isRead)
                    put("targetRoute", n.targetRoute ?: "")
                })
            }
        }
        context.sellerDataStore.edit { prefs ->
            val uid = userId ?: prefs[Keys.USER_ID]
            prefs[scopedKey("notifications_json", uid)] = arr.toString()
        }
    }

    suspend fun loadKnownDeviceIds(userId: String? = null): Set<String>? {
        val prefs = context.sellerDataStore.data.first()
        val uid = userId ?: prefs[Keys.USER_ID]
        val raw = prefs[scopedKey("known_device_ids", uid)]
            ?: prefs[Keys.KNOWN_DEVICE_IDS]
            ?: return null
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank()) add(id)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveKnownDeviceIds(ids: Set<String>, userId: String? = null) {
        val arr = JSONArray().apply { ids.forEach { put(it) } }
        context.sellerDataStore.edit { prefs ->
            val uid = userId ?: prefs[Keys.USER_ID]
            prefs[scopedKey("known_device_ids", uid)] = arr.toString()
        }
    }

    suspend fun getLastKnownVerificationStatus(userId: String? = null): String? {
        val prefs = context.sellerDataStore.data.first()
        val uid = userId ?: prefs[Keys.USER_ID]
        return prefs[scopedKey("last_verification_status", uid)]
            ?: prefs[Keys.LAST_VERIFICATION_STATUS]
    }

    suspend fun setLastKnownVerificationStatus(rawStatus: String, userId: String? = null) {
        context.sellerDataStore.edit { prefs ->
            val uid = userId ?: prefs[Keys.USER_ID]
            prefs[scopedKey("last_verification_status", uid)] = rawStatus
        }
    }

    suspend fun getLastNotifiedUpdateTag(): String? {
        return context.sellerDataStore.data.first()[Keys.LAST_NOTIFIED_UPDATE_TAG]
    }

    suspend fun setLastNotifiedUpdateTag(tag: String) {
        context.sellerDataStore.edit { it[Keys.LAST_NOTIFIED_UPDATE_TAG] = tag }
    }

    suspend fun saveWorkspaceCache(
        verification: SellerVerification,
        store: SellerStore?,
        products: List<ProductItem>
    ) {
        val verJson = JSONObject().apply {
            put("sellerId", verification.sellerId)
            put("status", verification.status.raw)
            put("reason", verification.reason ?: "")
            put("fullName", verification.details.fullName)
            put("cnic", verification.details.cnic)
            put("gender", verification.details.gender)
            put("dateOfBirth", verification.details.dateOfBirth)
            put("issueDate", verification.details.issueDate)
            put("expiryDate", verification.details.expiryDate)
            put("cnicFront", verification.details.cnicFront)
            put("cnicBack", verification.details.cnicBack)
            put("facePhoto", verification.details.facePhoto)
            put("phone", verification.details.phone)
            put("whatsapp", verification.details.whatsapp)
            put("city", verification.details.city)
            put("street", verification.details.street)
            put("state", verification.details.state)
            put("address", verification.details.address)
            put("address2", verification.details.address2)
            put("zipCode", verification.details.zipCode)
        }.toString()

        val storeJson = store?.let { st ->
            JSONObject().apply {
                put("sellerId", st.sellerId)
                put("shopName", st.shopName)
                put("slug", st.slug)
                put("bio", st.bio)
                put("introduction", st.introduction)
                put("logoPath", st.logoPath ?: "")
                put("bannerPath", st.bannerPath ?: "")
                val socials = JSONArray()
                st.socialProfiles.forEach { sp ->
                    socials.put(JSONObject().apply {
                        put("platform", sp.platform)
                        put("url", sp.url)
                        put("active", sp.active)
                    })
                }
                put("socialProfiles", socials)
            }.toString()
        }

        val productsArr = JSONArray().apply {
            products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("sellerId", p.sellerId)
                    put("name", p.name)
                    put("description", p.description)
                    put("price", p.price)
                    put("stock", p.stock)
                    put("sales", p.sales)
                    put("status", p.status.raw)
                    put("rejectionReason", p.rejectionReason ?: "")
                    put("imagePaths", JSONArray(p.imagePaths))
                    put("videoPath", p.videoPath ?: "")
                    put("productCode", p.productCode)
                    put("category", p.category)
                    put("attributes", JSONObject(p.attributes))
                    put("createdAt", p.createdAt ?: "")
                })
            }
        }.toString()

        context.sellerDataStore.edit { prefs ->
            prefs[Keys.CACHE_VERIFICATION] = verJson
            if (storeJson != null) prefs[Keys.CACHE_STORE] = storeJson else prefs.remove(Keys.CACHE_STORE)
            prefs[Keys.CACHE_PRODUCTS] = productsArr
        }
    }

    suspend fun loadWorkspaceCache(): OfflineWorkspaceCache? {
        val prefs = context.sellerDataStore.data.first()
        val verStr = prefs[Keys.CACHE_VERIFICATION] ?: return null
        return try {
            val vObj = JSONObject(verStr)
            val verification = SellerVerification(
                sellerId = vObj.optString("sellerId"),
                status = VerificationStatus.fromRaw(vObj.optString("status")),
                reason = vObj.optString("reason").takeIf { it.isNotBlank() },
                details = SellerIdentityDetails(
                    fullName = vObj.optString("fullName"),
                    cnic = vObj.optString("cnic"),
                    gender = vObj.optString("gender", "male"),
                    dateOfBirth = vObj.optString("dateOfBirth"),
                    issueDate = vObj.optString("issueDate"),
                    expiryDate = vObj.optString("expiryDate"),
                    cnicFront = vObj.optString("cnicFront"),
                    cnicBack = vObj.optString("cnicBack"),
                    facePhoto = vObj.optString("facePhoto"),
                    phone = vObj.optString("phone"),
                    whatsapp = vObj.optString("whatsapp"),
                    city = vObj.optString("city"),
                    street = vObj.optString("street"),
                    state = vObj.optString("state"),
                    address = vObj.optString("address"),
                    address2 = vObj.optString("address2"),
                    zipCode = vObj.optString("zipCode")
                )
            )
            val store = prefs[Keys.CACHE_STORE]?.let { stStr ->
                val sObj = JSONObject(stStr)
                val sArr = sObj.optJSONArray("socialProfiles") ?: JSONArray()
                val socials = buildList {
                    for (i in 0 until sArr.length()) {
                        val sp = sArr.optJSONObject(i) ?: continue
                        add(SocialProfile(sp.optString("platform"), sp.optString("url"), sp.optBoolean("active", true)))
                    }
                }
                SellerStore(
                    sellerId = sObj.optString("sellerId"),
                    shopName = sObj.optString("shopName"),
                    slug = sObj.optString("slug"),
                    bio = sObj.optString("bio"),
                    introduction = sObj.optString("introduction"),
                    logoPath = sObj.optString("logoPath").takeIf { it.isNotBlank() },
                    bannerPath = sObj.optString("bannerPath").takeIf { it.isNotBlank() },
                    socialProfiles = socials
                )
            }
            val products = prefs[Keys.CACHE_PRODUCTS]?.let { pStr ->
                val pArr = JSONArray(pStr)
                buildList {
                    for (i in 0 until pArr.length()) {
                        val pObj = pArr.optJSONObject(i) ?: continue
                        val imgArr = pObj.optJSONArray("imagePaths") ?: JSONArray()
                        val imgs = buildList {
                            for (j in 0 until imgArr.length()) {
                                val s = imgArr.optString(j)
                                if (s.isNotBlank()) add(s)
                            }
                        }
                        val attrsObj = pObj.optJSONObject("attributes")
                        val attrs = buildMap {
                            if (attrsObj != null) {
                                val keys = attrsObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    put(k, attrsObj.optString(k))
                                }
                            }
                        }
                        add(
                            ProductItem(
                                id = pObj.optString("id"),
                                sellerId = pObj.optString("sellerId"),
                                name = pObj.optString("name"),
                                description = pObj.optString("description"),
                                price = pObj.optDouble("price", 0.0),
                                stock = pObj.optInt("stock", 0),
                                sales = pObj.optInt("sales", 0),
                                status = ProductStatus.fromRaw(pObj.optString("status")),
                                rejectionReason = pObj.optString("rejectionReason").takeIf { it.isNotBlank() },
                                imagePaths = imgs,
                                videoPath = pObj.optString("videoPath").takeIf { it.isNotBlank() },
                                productCode = pObj.optString("productCode"),
                                category = pObj.optString("category"),
                                attributes = attrs,
                                createdAt = pObj.optString("createdAt").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }.orEmpty()
            OfflineWorkspaceCache(verification = verification, store = store, products = products)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.sellerDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = ThemeMode.LIGHT.name
        }
    }
}
