package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.LoginResult
import com.example.data.model.MfaFactor
import com.example.data.model.MfaTotpEnrollment
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerIdentityDetails
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.SocialProfile
import com.example.data.model.UserSession
import com.example.data.model.VerificationEvent
import com.example.data.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SupabaseException(
    message: String,
    val statusCode: Int = 0
) : Exception(message)

class SupabaseClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun baseHeaders(accessToken: String? = null): Request.Builder {
        val builder = Request.Builder()
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${accessToken ?: SupabaseConfig.SUPABASE_ANON_KEY}")
        return builder
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        if (body.isBlank()) return fallback
        return try {
            val json = JSONObject(body)
            listOf("msg", "error_description", "message", "error", "hint")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private suspend fun executeRaw(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                response.code to text
            }
        } catch (e: IOException) {
            throw SupabaseException("Network error: check your internet connection.")
        }
    }

    private suspend fun executeJson(
        method: String,
        url: String,
        bodyJson: String? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val reqBody = bodyJson?.toRequestBody(jsonMediaType)
        val builder = baseHeaders(accessToken)
            .url(url)
            .header("Content-Type", "application/json")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(reqBody ?: "{}".toRequestBody(jsonMediaType))
            "PUT" -> builder.put(reqBody ?: "{}".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(reqBody ?: "{}".toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete(reqBody)
        }
        val (code, text) = executeRaw(builder.build())
        if (code !in 200..299) {
            throw SupabaseException(
                parseErrorMessage(text, "Request failed ($code)"),
                statusCode = code
            )
        }
        return text
    }

    // ==================== AUTH ====================

    suspend fun signUp(email: String, password: String): UserSession? {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
            put("data", JSONObject().apply { put("account_type", "seller") })
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/signup",
            bodyJson = payload.toString()
        )
        val json = JSONObject(resp)
        val access = json.optString("access_token")
        return if (access.isNotBlank() && access != "null") parseSession(json) else null
    }

    suspend fun verifySignupOtp(email: String, token: String): UserSession {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("token", token.trim())
            put("type", "signup")
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/verify",
            bodyJson = payload.toString()
        )
        return parseSession(JSONObject(resp))
    }

    suspend fun resendSignupOtp(email: String) {
        val payload = JSONObject().apply {
            put("type", "signup")
            put("email", email.trim())
        }
        executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/resend",
            bodyJson = payload.toString()
        )
    }

    suspend fun signInWithPassword(email: String, password: String): LoginResult {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=password",
            bodyJson = payload.toString()
        )
        val json = JSONObject(resp)
        val session = parseSession(json)
        val aal = decodeJwtAal(session.accessToken)
        val factors = getMfaFactors(session)
        val verifiedTotp = factors.firstOrNull {
            it.factorType.equals("totp", ignoreCase = true) && it.status.equals("verified", ignoreCase = true)
        }
        val needsMfa = verifiedTotp != null && aal != "aal2"
        return LoginResult(
            session = session,
            requiresMfa = needsMfa,
            verifiedTotpFactorId = verifiedTotp?.id
        )
    }

    suspend fun sendEmailLoginOtp(email: String) {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("create_user", false)
        }
        executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/otp",
            bodyJson = payload.toString()
        )
    }

    suspend fun verifyEmailLoginOtp(email: String, token: String): LoginResult {
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("token", token.trim())
            put("type", "email")
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/verify",
            bodyJson = payload.toString()
        )
        val session = parseSession(JSONObject(resp))
        val aal = decodeJwtAal(session.accessToken)
        val factors = getMfaFactors(session)
        val verifiedTotp = factors.firstOrNull {
            it.factorType.equals("totp", ignoreCase = true) && it.status.equals("verified", ignoreCase = true)
        }
        val needsMfa = verifiedTotp != null && aal != "aal2"
        return LoginResult(
            session = session,
            requiresMfa = needsMfa,
            verifiedTotpFactorId = verifiedTotp?.id
        )
    }

    suspend fun sendPasswordReset(email: String) {
        val payload = JSONObject().apply {
            put("email", email.trim())
        }
        executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/recover",
            bodyJson = payload.toString()
        )
    }

    suspend fun verifyRecoveryOtpAndSetPassword(
        email: String,
        token: String,
        newPassword: String
    ): UserSession {
        val verifyPayload = JSONObject().apply {
            put("email", email.trim())
            put("token", token.trim())
            put("type", "recovery")
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/verify",
            bodyJson = verifyPayload.toString()
        )
        val session = parseSession(JSONObject(resp))
        updatePassword(session, newPassword)
        return session
    }

    suspend fun refreshSession(refreshToken: String): UserSession {
        val payload = JSONObject().apply { put("refresh_token", refreshToken) }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token",
            bodyJson = payload.toString()
        )
        return parseSession(JSONObject(resp))
    }

    suspend fun getMfaFactors(session: UserSession): List<MfaFactor> {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            accessToken = session.accessToken
        )
        val json = JSONObject(resp)
        val arr = json.optJSONArray("factors") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    MfaFactor(
                        id = obj.optString("id"),
                        factorType = obj.optString("factor_type"),
                        status = obj.optString("status"),
                        friendlyName = obj.optString("friendly_name").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun mfaEnrollTotp(session: UserSession): MfaTotpEnrollment {
        val payload = JSONObject().apply {
            put("factor_type", "totp")
            put("friendly_name", "ZULVAZA Seller App")
        }
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/factors",
            bodyJson = payload.toString(),
            accessToken = session.accessToken
        )
        val json = JSONObject(resp)
        val totp = json.optJSONObject("totp") ?: JSONObject()
        return MfaTotpEnrollment(
            factorId = json.optString("id"),
            qrCodeSvgUri = totp.optString("qr_code"),
            secret = totp.optString("secret"),
            totpUri = totp.optString("uri")
        )
    }

    suspend fun mfaChallengeAndVerify(
        session: UserSession,
        factorId: String,
        code: String
    ): UserSession {
        val challengeResp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/factors/$factorId/challenge",
            bodyJson = "{}",
            accessToken = session.accessToken
        )
        val challengeId = JSONObject(challengeResp).optString("id")
        val verifyPayload = JSONObject().apply {
            put("challenge_id", challengeId)
            put("code", code.trim())
        }
        val verifyResp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/factors/$factorId/verify",
            bodyJson = verifyPayload.toString(),
            accessToken = session.accessToken
        )
        val json = JSONObject(verifyResp)
        return if (json.has("access_token")) parseSession(json) else session
    }

    suspend fun mfaUnenroll(session: UserSession, factorId: String) {
        executeJson(
            method = "DELETE",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/factors/$factorId",
            accessToken = session.accessToken
        )
    }

    suspend fun updatePassword(session: UserSession, newPassword: String) {
        val payload = JSONObject().apply { put("password", newPassword) }
        executeJson(
            method = "PUT",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            bodyJson = payload.toString(),
            accessToken = session.accessToken
        )
    }

    suspend fun signOut(session: UserSession) {
        try {
            executeJson(
                method = "POST",
                url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/logout",
                bodyJson = "{}",
                accessToken = session.accessToken
            )
        } catch (_: Exception) {
        }
    }

    suspend fun fetchCurrentNetworkGeo(): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        val ip = json.optString("ip").takeIf { it.isNotBlank() } ?: "Detected IP"
                        val city = json.optString("city").takeIf { it.isNotBlank() }
                        val country = json.optString("country").takeIf { it.isNotBlank() }
                        val location = listOfNotNull(city, country).joinToString(", ").ifBlank { "Pakistan" }
                        return@withContext ip to location
                    }
                }
            }
        } catch (_: Exception) {
        }
        val localIp = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
        }.getOrNull() ?: "Local Network"
        localIp to "Pakistan"
    }

    suspend fun syncActiveDevices(
        session: UserSession,
        currentDeviceId: String,
        deviceName: String,
        osVersion: String
    ): Pair<List<ActiveDeviceSession>, Boolean> {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            accessToken = session.accessToken
        )
        val userJson = JSONObject(resp)
        val meta = userJson.optJSONObject("user_metadata") ?: JSONObject()

        val revokedArr = meta.optJSONArray("revoked_device_ids") ?: JSONArray()
        val revokedIds = buildSet {
            for (i in 0 until revokedArr.length()) {
                val id = revokedArr.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
        if (currentDeviceId in revokedIds) {
            return emptyList<ActiveDeviceSession>() to true
        }

        val (ip, cityCountry) = fetchCurrentNetworkGeo()
        val nowSec = System.currentTimeMillis() / 1000L

        val existingArr = meta.optJSONArray("active_devices") ?: JSONArray()
        val devices = mutableListOf<ActiveDeviceSession>()
        for (i in 0 until existingArr.length()) {
            val obj = existingArr.optJSONObject(i) ?: continue
            val dId = obj.optString("device_id")
            if (dId.isBlank() || dId == currentDeviceId || dId in revokedIds) continue
            val lastSeen = obj.optLong("last_active_epoch", 0L)
            devices.add(
                ActiveDeviceSession(
                    deviceId = dId,
                    deviceName = obj.optString("device_name", "Seller Device"),
                    osVersion = obj.optString("os_version", "Android"),
                    ipAddress = obj.optString("ip_address", "Unknown"),
                    cityCountry = obj.optString("city_country", "Pakistan"),
                    isOnline = (nowSec - lastSeen) <= 900L,
                    isCurrentDevice = false,
                    lastActiveEpochSec = lastSeen
                )
            )
        }

        val currentDevice = ActiveDeviceSession(
            deviceId = currentDeviceId,
            deviceName = deviceName,
            osVersion = osVersion,
            ipAddress = ip,
            cityCountry = cityCountry,
            isOnline = true,
            isCurrentDevice = true,
            lastActiveEpochSec = nowSec
        )
        val allDevices = listOf(currentDevice) + devices

        val updatedArr = JSONArray().apply {
            allDevices.forEach { d ->
                put(
                    JSONObject().apply {
                        put("device_id", d.deviceId)
                        put("device_name", d.deviceName)
                        put("os_version", d.osVersion)
                        put("ip_address", d.ipAddress)
                        put("city_country", d.cityCountry)
                        put("last_active_epoch", d.lastActiveEpochSec)
                    }
                )
            }
        }
        val putPayload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("active_devices", updatedArr)
            })
        }
        runCatching {
            executeJson(
                method = "PUT",
                url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
                bodyJson = putPayload.toString(),
                accessToken = session.accessToken
            )
        }

        return allDevices to false
    }

    suspend fun removeOtherDevice(
        session: UserSession,
        targetDeviceId: String
    ): List<ActiveDeviceSession> {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            accessToken = session.accessToken
        )
        val userJson = JSONObject(resp)
        val meta = userJson.optJSONObject("user_metadata") ?: JSONObject()

        val revokedSet = linkedSetOf<String>()
        val oldRevoked = meta.optJSONArray("revoked_device_ids") ?: JSONArray()
        for (i in 0 until oldRevoked.length()) {
            val id = oldRevoked.optString(i)
            if (id.isNotBlank()) revokedSet.add(id)
        }
        revokedSet.add(targetDeviceId)

        val kept = mutableListOf<ActiveDeviceSession>()
        val oldDevices = meta.optJSONArray("active_devices") ?: JSONArray()
        val updatedArr = JSONArray()
        val nowSec = System.currentTimeMillis() / 1000L
        for (i in 0 until oldDevices.length()) {
            val obj = oldDevices.optJSONObject(i) ?: continue
            val dId = obj.optString("device_id")
            if (dId.isBlank() || dId == targetDeviceId) continue
            updatedArr.put(obj)
            val lastSeen = obj.optLong("last_active_epoch", nowSec)
            kept.add(
                ActiveDeviceSession(
                    deviceId = dId,
                    deviceName = obj.optString("device_name", "Seller Device"),
                    osVersion = obj.optString("os_version", "Android"),
                    ipAddress = obj.optString("ip_address", "Unknown"),
                    cityCountry = obj.optString("city_country", "Pakistan"),
                    isOnline = (nowSec - lastSeen) <= 900L,
                    isCurrentDevice = false,
                    lastActiveEpochSec = lastSeen
                )
            )
        }

        val revokedArr = JSONArray().apply { revokedSet.toList().takeLast(25).forEach { put(it) } }
        val putPayload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("active_devices", updatedArr)
                put("revoked_device_ids", revokedArr)
            })
        }
        executeJson(
            method = "PUT",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            bodyJson = putPayload.toString(),
            accessToken = session.accessToken
        )

        // Revoke other refresh tokens in Supabase GoTrue if no other active devices remain or to enforce session cutoff
        runCatching {
            executeJson(
                method = "POST",
                url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/logout?scope=others",
                bodyJson = "{}",
                accessToken = session.accessToken
            )
        }

        return kept
    }

    private fun parseSession(json: JSONObject): UserSession {
        val accessToken = json.optString("access_token")
        val user = json.optJSONObject("user")
        val expiresIn = json.optLong("expires_in", 3600L)
        val now = System.currentTimeMillis() / 1000L
        val expiresAt = json.optLong("expires_at", now + expiresIn)
        val userId = user?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
            ?: decodeJwtClaim(accessToken, "sub")
            ?: ""
        val email = user?.optString("email")?.takeIf { it.isNotBlank() && it != "null" }
            ?: decodeJwtClaim(accessToken, "email")
            ?: ""
        return UserSession(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token"),
            expiresAtEpochSec = expiresAt,
            userId = userId,
            email = email
        )
    }

    private fun decodeJwtClaim(jwt: String, claim: String): String? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            JSONObject(decoded).optString(claim).takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeJwtAal(jwt: String): String {
        return decodeJwtClaim(jwt, "aal") ?: "aal1"
    }

    suspend fun getAuthenticatedUserId(session: UserSession): String {
        if (session.accessToken.isBlank()) {
            throw SupabaseException("Seller is not authenticated", statusCode = 401)
        }
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/user",
            accessToken = session.accessToken
        )
        val id = JSONObject(resp).optString("id").takeIf { it.isNotBlank() && it != "null" }
            ?: session.userId.takeIf { it.isNotBlank() }
            ?: throw SupabaseException("Seller is not authenticated", statusCode = 401)
        return id
    }

    // ==================== ROLES & VERIFICATION ====================

    suspend fun fetchUserRole(session: UserSession): String? {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/user_roles?user_id=eq.${session.userId}&select=user_id,role",
            accessToken = session.accessToken
        )
        val arr = JSONArray(resp)
        if (arr.length() == 0) return null
        return arr.optJSONObject(0)?.optString("role")
    }

    suspend fun fetchVerification(session: UserSession): SellerVerification {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/seller_verifications?seller_id=eq.${session.userId}&select=*",
            accessToken = session.accessToken
        )
        val arr = JSONArray(resp)
        if (arr.length() == 0) {
            return SellerVerification(sellerId = session.userId, status = VerificationStatus.UNSUBMITTED)
        }
        val obj = arr.optJSONObject(0) ?: return SellerVerification(sellerId = session.userId)
        val detailsObj = obj.optJSONObject("details") ?: JSONObject()
        val details = SellerIdentityDetails(
            fullName = detailsObj.optString("full_name"),
            cnic = detailsObj.optString("cnic"),
            gender = detailsObj.optString("gender", "male").ifBlank { "male" },
            dateOfBirth = listOf("dob", "date_of_birth")
                .firstNotNullOfOrNull { k -> detailsObj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                .orEmpty(),
            issueDate = detailsObj.optString("issue_date"),
            expiryDate = detailsObj.optString("expiry_date"),
            cnicFront = detailsObj.optString("cnic_front"),
            cnicBack = detailsObj.optString("cnic_back"),
            facePhoto = detailsObj.optString("face_photo").takeIf { it != "null" }.orEmpty(),
            phone = detailsObj.optString("phone"),
            whatsapp = detailsObj.optString("whatsapp"),
            city = detailsObj.optString("city"),
            street = detailsObj.optString("street"),
            state = detailsObj.optString("state"),
            address = detailsObj.optString("address"),
            address2 = detailsObj.optString("address2"),
            zipCode = detailsObj.optString("zip_code"),
            latitude = if (detailsObj.isNull("latitude")) null else detailsObj.optDouble("latitude").takeIf { !it.isNaN() },
            longitude = if (detailsObj.isNull("longitude")) null else detailsObj.optDouble("longitude").takeIf { !it.isNaN() }
        )
        return SellerVerification(
            sellerId = obj.optString("seller_id", session.userId),
            status = VerificationStatus.fromRaw(obj.optString("status")),
            details = details,
            reason = obj.optString("reason").takeIf { it.isNotBlank() && it != "null" },
            submittedAt = obj.optString("submitted_at").takeIf { it.isNotBlank() && it != "null" },
            reviewedAt = obj.optString("reviewed_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun submitSellerIdentity(session: UserSession, details: SellerIdentityDetails) {
        val detailsJson = JSONObject().apply {
            put("full_name", details.fullName.trim())
            put("cnic", details.cnic.filter { it.isDigit() })
            put("gender", details.gender)
            if (details.dateOfBirth.isNotBlank()) {
                put("dob", details.dateOfBirth.trim())
            }
            put("issue_date", details.issueDate.trim())
            put("expiry_date", details.expiryDate.trim())
            put("cnic_front", details.cnicFront)
            put("cnic_back", details.cnicBack)
            if (details.facePhoto.isNotBlank()) {
                put("face_photo", details.facePhoto)
            }
            put("phone", details.phone.trim())
            put("whatsapp", details.whatsapp.trim())
            put("city", details.city.trim())
            put("street", details.street.trim())
            put("state", details.state.trim())
            put("address", details.address.trim())
            put("address2", details.address2.trim())
            put("zip_code", details.zipCode.filter { it.isDigit() })
            put("latitude", details.latitude ?: JSONObject.NULL)
            put("longitude", details.longitude ?: JSONObject.NULL)
        }
        val payload = JSONObject().apply { put("_details", detailsJson) }
        executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/rpc/submit_seller_identity",
            bodyJson = payload.toString(),
            accessToken = session.accessToken
        )
    }

    suspend fun fetchVerificationEvents(session: UserSession): List<VerificationEvent> {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/seller_verification_events?seller_id=eq.${session.userId}&select=*&order=created_at.desc",
            accessToken = session.accessToken
        )
        val arr = JSONArray(resp)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val status = listOf("status", "new_status", "event_type", "action")
                    .firstNotNullOfOrNull { k -> obj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                    ?: "update"
                val reason = listOf("reason", "note", "notes", "message", "comment")
                    .firstNotNullOfOrNull { k -> obj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                add(
                    VerificationEvent(
                        id = obj.optString("id", i.toString()),
                        status = status,
                        reason = reason,
                        createdAt = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    // ==================== STORE ====================

    suspend fun fetchStore(session: UserSession): SellerStore? {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/seller_stores?seller_id=eq.${session.userId}&select=*",
            accessToken = session.accessToken
        )
        val arr = JSONArray(resp)
        if (arr.length() == 0) return null
        val obj = arr.optJSONObject(0) ?: return null
        val socialArr = obj.optJSONArray("social_profiles") ?: JSONArray()
        val socials = buildList {
            for (i in 0 until socialArr.length()) {
                val s = socialArr.optJSONObject(i) ?: continue
                add(
                    SocialProfile(
                        platform = s.optString("platform", "website"),
                        url = s.optString("url"),
                        active = s.optBoolean("active", true)
                    )
                )
            }
        }
        return SellerStore(
            sellerId = obj.optString("seller_id", session.userId),
            shopName = obj.optString("shop_name"),
            slug = obj.optString("slug"),
            bio = obj.optString("bio"),
            introduction = obj.optString("introduction"),
            logoPath = obj.optString("logo_path").takeIf { it.isNotBlank() && it != "null" },
            bannerPath = obj.optString("banner_path").takeIf { it.isNotBlank() && it != "null" },
            socialProfiles = socials
        )
    }

    suspend fun saveSellerStore(session: UserSession, store: SellerStore) {
        val socialsArr = JSONArray().apply {
            store.socialProfiles.forEach { sp ->
                put(
                    JSONObject().apply {
                        put("platform", sp.platform)
                        put("url", sp.url.trim())
                        put("active", sp.active)
                    }
                )
            }
        }
        val storeObj = JSONObject().apply {
            put("shop_name", store.shopName.trim())
            put("slug", store.slug.trim().lowercase())
            put("bio", store.bio.trim())
            put("introduction", store.introduction.trim())
            put("logo_path", store.logoPath ?: JSONObject.NULL)
            put("banner_path", store.bannerPath ?: JSONObject.NULL)
            put("social_profiles", socialsArr)
        }
        val payload = JSONObject().apply { put("_store", storeObj) }
        executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/rpc/save_seller_store",
            bodyJson = payload.toString(),
            accessToken = session.accessToken
        )
    }

    suspend fun clearStoreMediaColumn(session: UserSession, isLogo: Boolean) {
        val column = if (isLogo) "logo_path" else "banner_path"
        val body = JSONObject().apply { put(column, JSONObject.NULL) }
        executeJson(
            method = "PATCH",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/seller_stores?seller_id=eq.${session.userId}",
            bodyJson = body.toString(),
            accessToken = session.accessToken
        )
    }

    // ==================== PRODUCTS & DRAFTS ====================

    suspend fun fetchProducts(session: UserSession): List<ProductItem> {
        val resp = executeJson(
            method = "GET",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/products?seller_id=eq.${session.userId}&select=*&order=created_at.desc",
            accessToken = session.accessToken
        )
        val arr = JSONArray(resp)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(parseProduct(obj, session.userId))
            }
        }
    }

    suspend fun insertProduct(session: UserSession, product: ProductItem): ProductItem {
        val body = productToJson(session.userId, product)
        val resp = executeJson(
            method = "POST",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/products",
            bodyJson = body.toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val arr = JSONArray(resp)
        return if (arr.length() > 0) parseProduct(arr.getJSONObject(0), session.userId) else product
    }

    suspend fun updateProduct(session: UserSession, product: ProductItem): ProductItem {
        val body = productToJson(session.userId, product)
        val resp = executeJson(
            method = "PATCH",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/products?id=eq.${product.id}&seller_id=eq.${session.userId}",
            bodyJson = body.toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val arr = JSONArray(resp)
        return if (arr.length() > 0) parseProduct(arr.getJSONObject(0), session.userId) else product
    }

    suspend fun deleteProduct(session: UserSession, productId: String) {
        executeJson(
            method = "DELETE",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/products?id=eq.$productId&seller_id=eq.${session.userId}",
            accessToken = session.accessToken
        )
    }

    suspend fun updateProductStock(session: UserSession, productId: String, newStock: Int): ProductItem? {
        val body = JSONObject().apply {
            put("stock", newStock.coerceAtLeast(0))
        }
        val resp = executeJson(
            method = "PATCH",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/products?id=eq.$productId&seller_id=eq.${session.userId}",
            bodyJson = body.toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=representation")
        )
        val arr = JSONArray(resp)
        return if (arr.length() > 0) parseProduct(arr.getJSONObject(0), session.userId) else null
    }

    private fun productToJson(sellerId: String, product: ProductItem): JSONObject {
        val images = JSONArray().apply { product.imagePaths.forEach { put(it) } }
        val attrs = JSONObject().apply { product.attributes.forEach { (k, v) -> put(k, v) } }
        return JSONObject().apply {
            put("seller_id", sellerId)
            put("name", product.name.trim())
            put("description", product.description.trim())
            put("price", product.price)
            put("stock", product.stock)
            put("image_paths", images)
            put("video_path", product.videoPath ?: JSONObject.NULL)
            put("product_code", product.productCode)
            put("category", product.category.ifBlank { "General" })
            put("attributes", attrs)
        }
    }

    private fun parseProduct(obj: JSONObject, fallbackSellerId: String): ProductItem {
        val imgArr = obj.optJSONArray("image_paths") ?: JSONArray()
        val images = buildList {
            for (i in 0 until imgArr.length()) {
                val p = imgArr.optString(i)
                if (p.isNotBlank() && p != "null") add(p)
            }
        }
        val attrsObj = obj.optJSONObject("attributes")
        val attrs = buildMap {
            if (attrsObj != null) {
                val keys = attrsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, attrsObj.optString(k))
                }
            }
        }
        return ProductItem(
            id = obj.optString("id"),
            sellerId = obj.optString("seller_id", fallbackSellerId),
            name = obj.optString("name"),
            description = obj.optString("description"),
            price = obj.optDouble("price", 0.0),
            stock = obj.optInt("stock", 0),
            sales = obj.optInt("sales", 0),
            status = ProductStatus.fromRaw(obj.optString("status")),
            rejectionReason = obj.optString("rejection_reason").takeIf { it.isNotBlank() && it != "null" },
            imagePaths = images,
            videoPath = obj.optString("video_path").takeIf { it.isNotBlank() && it != "null" },
            productCode = obj.optString("product_code"),
            category = obj.optString("category"),
            attributes = attrs,
            createdAt = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun fetchDrafts(session: UserSession): List<ProductDraft> {
        val resp = try {
            executeJson(
                method = "GET",
                url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/product_drafts?seller_id=eq.${session.userId}&select=*&order=updated_at.desc",
                accessToken = session.accessToken
            )
        } catch (_: Exception) {
            executeJson(
                method = "GET",
                url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/product_drafts?seller_id=eq.${session.userId}&select=*&order=created_at.desc",
                accessToken = session.accessToken
            )
        }
        val arr = JSONArray(resp)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(parseDraft(obj, session.userId))
            }
        }
    }

    suspend fun saveDraft(session: UserSession, draft: ProductDraft): ProductDraft {
        val images = JSONArray().apply { draft.imagePaths.forEach { put(it) } }
        val attrs = JSONObject().apply { draft.attributes.forEach { (k, v) -> put(k, v) } }
        val payload = JSONObject().apply {
            put("seller_id", session.userId)
            put("name", draft.name)
            put("description", draft.description)
            put("price", draft.price ?: JSONObject.NULL)
            put("stock", draft.stock ?: JSONObject.NULL)
            put("image_paths", images)
            put("video_path", draft.videoPath ?: JSONObject.NULL)
            put("category", draft.category.ifBlank { "General" })
            put("attributes", attrs)
        }
        val resp = if (draft.id.isNotBlank()) {
            executeJson(
                method = "PATCH",
                url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/product_drafts?id=eq.${draft.id}&seller_id=eq.${session.userId}",
                bodyJson = payload.toString(),
                accessToken = session.accessToken,
                extraHeaders = mapOf("Prefer" to "return=representation")
            )
        } else {
            executeJson(
                method = "POST",
                url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/product_drafts",
                bodyJson = payload.toString(),
                accessToken = session.accessToken,
                extraHeaders = mapOf("Prefer" to "return=representation")
            )
        }
        val arr = JSONArray(resp)
        return if (arr.length() > 0) parseDraft(arr.getJSONObject(0), session.userId) else draft
    }

    suspend fun deleteDraft(session: UserSession, draftId: String) {
        executeJson(
            method = "DELETE",
            url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/product_drafts?id=eq.$draftId&seller_id=eq.${session.userId}",
            accessToken = session.accessToken
        )
    }

    private fun parseDraft(obj: JSONObject, fallbackSellerId: String): ProductDraft {
        val dataObj = obj.optJSONObject("data") ?: obj
        val imgArr = dataObj.optJSONArray("image_paths") ?: obj.optJSONArray("image_paths") ?: JSONArray()
        val images = buildList {
            for (i in 0 until imgArr.length()) {
                val p = imgArr.optString(i)
                if (p.isNotBlank() && p != "null") add(p)
            }
        }
        val attrsObj = dataObj.optJSONObject("attributes") ?: obj.optJSONObject("attributes")
        val attrs = buildMap {
            if (attrsObj != null) {
                val keys = attrsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, attrsObj.optString(k))
                }
            }
        }
        return ProductDraft(
            id = obj.optString("id"),
            sellerId = obj.optString("seller_id", fallbackSellerId),
            name = dataObj.optString("name", obj.optString("name")),
            description = dataObj.optString("description", obj.optString("description")),
            price = if (dataObj.isNull("price")) null else dataObj.optDouble("price"),
            stock = if (dataObj.isNull("stock")) null else dataObj.optInt("stock"),
            category = dataObj.optString("category", obj.optString("category")),
            imagePaths = images,
            videoPath = dataObj.optString("video_path").takeIf { it.isNotBlank() && it != "null" },
            attributes = attrs,
            updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() && it != "null" },
            createdAt = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    // ==================== STORAGE ====================

    suspend fun uploadBytesWithProgress(
        session: UserSession,
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String,
        upsert: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (session.accessToken.isBlank() || session.userId.isBlank()) {
            throw SupabaseException("Seller is not authenticated", statusCode = 401)
        }
        val cleanPath = objectPath.trimStart('/')
        require(cleanPath.startsWith("${session.userId}/")) {
            "Storage path must begin with authenticated seller userId"
        }
        val encodedPath = cleanPath.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val mediaType = mimeType.toMediaType()
        val progressBody = object : RequestBody() {
            override fun contentType(): MediaType = mediaType
            override fun contentLength(): Long = bytes.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                val chunkSize = 8192
                var uploaded = 0L
                val total = bytes.size.toLong().coerceAtLeast(1L)
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(chunkSize, bytes.size - offset)
                    sink.write(bytes, offset, count)
                    offset += count
                    uploaded += count
                    onProgress((uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
        }

        val request = baseHeaders(session.accessToken)
            .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$encodedPath")
            .header("x-upsert", if (upsert) "true" else "false")
            .header("cache-control", "3600")
            .post(progressBody)
            .build()

        val (code, text) = executeRaw(request)
        if (code !in 200..299) {
            Log.e("SupabaseStorage", "Storage upload error [$bucket/$cleanPath] ($code): $text")
            throw SupabaseException(
                parseErrorMessage(text, "Upload failed ($code)"),
                statusCode = code
            )
        }
        onProgress(1f)
        cleanPath
    }

    suspend fun uploadVerificationDocument(
        session: UserSession,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): String {
        return uploadBytesWithProgress(
            session = session,
            bucket = SupabaseConfig.BUCKET_SELLER_KYC,
            objectPath = objectPath,
            bytes = bytes,
            mimeType = mimeType,
            upsert = true
        )
    }

    suspend fun uploadFacePhoto(
        session: UserSession,
        objectPath: String,
        bytes: ByteArray
    ): String {
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) {
            "Face photo must be between 1 byte and 5 MB"
        }
        return uploadBytesWithProgress(
            session = session,
            bucket = SupabaseConfig.BUCKET_SELLER_FACES,
            objectPath = objectPath,
            bytes = bytes,
            mimeType = "image/jpeg",
            upsert = false
        )
    }

    suspend fun deleteStorageObject(
        session: UserSession,
        bucket: String,
        objectPath: String
    ) = withContext(Dispatchers.IO) {
        val cleanPath = objectPath.trimStart('/')
        if (cleanPath.isBlank()) return@withContext
        val prefixes = JSONObject().apply {
            put("prefixes", JSONArray().apply { put(cleanPath) })
        }
        val batchRequest = baseHeaders(session.accessToken)
            .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket")
            .header("Content-Type", "application/json")
            .delete(prefixes.toString().toRequestBody(jsonMediaType))
            .build()
        val (code, text) = executeRaw(batchRequest)
        if (code !in 200..299 && code != 404) {
            Log.w("SupabaseStorage", "Batch delete failed [$bucket/$cleanPath] ($code): $text")
            val encodedPath = cleanPath.split("/").joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val singleRequest = baseHeaders(session.accessToken)
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$bucket/$encodedPath")
                .delete()
                .build()
            val (sCode, sText) = executeRaw(singleRequest)
            if (sCode !in 200..299 && sCode != 404) {
                Log.w("SupabaseStorage", "Single delete failed [$bucket/$cleanPath] ($sCode): $sText")
            }
        }
    }

    suspend fun createSignedUrl(
        session: UserSession,
        bucket: String,
        objectPath: String,
        expiresInSec: Int = 3600
    ): String? {
        if (objectPath.startsWith("http://") ||
            objectPath.startsWith("https://") ||
            objectPath.startsWith("content://") ||
            objectPath.startsWith("file://")
        ) {
            return objectPath
        }
        val cleanPath = objectPath.trimStart('/')
        val encodedPath = cleanPath.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val payload = JSONObject().apply { put("expiresIn", expiresInSec) }
        return try {
            val resp = executeJson(
                method = "POST",
                url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/sign/$bucket/$encodedPath",
                bodyJson = payload.toString(),
                accessToken = session.accessToken
            )
            val json = JSONObject(resp)
            val rawSigned = listOf("signedURL", "signedUrl")
                .firstNotNullOfOrNull { k -> json.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                ?: return null
            when {
                rawSigned.startsWith("http://") || rawSigned.startsWith("https://") -> rawSigned
                rawSigned.startsWith("/storage/v1/") -> "${SupabaseConfig.SUPABASE_URL}$rawSigned"
                rawSigned.startsWith("/") -> "${SupabaseConfig.SUPABASE_URL}/storage/v1$rawSigned"
                else -> "${SupabaseConfig.SUPABASE_URL}/storage/v1/$rawSigned"
            }
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Signed URL error [$bucket/$cleanPath]: ${e.message}")
            null
        }
    }

    suspend fun createSignedVerificationUrl(
        session: UserSession,
        objectPath: String,
        expiresInSec: Int = 3600
    ): String? {
        return createSignedUrl(
            session = session,
            bucket = SupabaseConfig.BUCKET_SELLER_KYC,
            objectPath = objectPath,
            expiresInSec = expiresInSec
        )
    }
}
