package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.model.DeepLinkDestination
import com.example.data.model.PasswordStrength
import com.example.data.remote.SupabaseConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Formats 13 raw CNIC digits into 12345-1234567-1 visually while keeping cursor mapping exact.
 */
class CnicVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(13)
        val out = buildString {
            for (i in raw.indices) {
                if (i == 5 || i == 12) append('-')
                append(raw[i])
            }
        }
        val offsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return when {
                    clamped <= 5 -> clamped
                    clamped <= 12 -> clamped + 1
                    else -> clamped + 2
                }.coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, out.length)
                return when {
                    clamped <= 5 -> clamped
                    clamped <= 13 -> clamped - 1
                    else -> clamped - 2
                }.coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out), offsetTranslator)
    }
}

object Formatters {

    fun formatPkr(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-PK"))
        formatter.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        return "Rs. ${formatter.format(amount)}"
    }

    fun sanitizeCnicDigits(input: String): String =
        input.filter { it.isDigit() }.take(13)

    fun formatCnicDisplay(rawInput: String): String {
        val digits = sanitizeCnicDigits(rawInput)
        val sb = StringBuilder()
        digits.forEachIndexed { index, c ->
            if (index == 5 || index == 12) sb.append('-')
            sb.append(c)
        }
        return sb.toString()
    }

    fun isValidCnic(rawInput: String): Boolean =
        sanitizeCnicDigits(rawInput).length == 13

    /**
     * Converts user input into Pakistan E.164 format (+923XXXXXXXXX).
     */
    fun normalizePakistanPhoneToE164(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.startsWith("92") && digits.length >= 12 -> "+$digits"
            digits.startsWith("0") && digits.length >= 11 -> "+92${digits.drop(1)}"
            digits.startsWith("3") && digits.length >= 10 -> "+92$digits"
            input.trim().startsWith("+92") -> "+$digits"
            digits.isEmpty() -> ""
            else -> "+92$digits"
        }
    }

    /**
     * Converts stored E.164 or raw input into 11-digit local Pakistan format (0XXXXXXXXXX).
     */
    fun formatLocalPakistanPhone(e164OrRaw: String): String {
        val digits = e164OrRaw.filter { it.isDigit() }
        return when {
            digits.startsWith("92") && digits.length >= 12 -> "0${digits.drop(2)}".take(11)
            digits.startsWith("0") -> digits.take(11)
            digits.startsWith("3") -> "0$digits".take(11)
            else -> digits.take(11)
        }
    }

    fun sanitizeLocalPakistanPhone(rawInput: String): String {
        return rawInput.filter { it.isDigit() }.take(11)
    }

    fun isValidLocalPakistanPhone(localDigits: String): Boolean =
        Regex("^0\\d{10}$").matches(localDigits)

    /**
     * Strips +92 prefix for clean editing in +92 prefixed fields.
     */
    fun stripPakistanPrefix(e164OrRaw: String): String {
        val digits = e164OrRaw.filter { it.isDigit() }
        return when {
            digits.startsWith("92") -> digits.drop(2).take(10)
            digits.startsWith("0") -> digits.drop(1).take(10)
            else -> digits.take(10)
        }
    }

    fun isValidPakistanE164(e164: String): Boolean =
        Regex("^\\+923\\d{9}$").matches(e164)

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        if (password.length < 8) return PasswordStrength.WEAK
        var score = 0
        if (password.length >= 10) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score >= 3 -> PasswordStrength.STRONG
            score >= 1 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }

    fun slugify(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "item" }
    }

    fun buildProductShareUrl(productName: String, productCode: String): String {
        val slug = slugify(productName)
        val code = productCode.ifBlank { generateProductCode10() }
        return "${SupabaseConfig.WEB_BASE_URL}/$slug-$code"
    }

    fun generateProductCode10(): String {
        val first = Random.nextInt(1, 10)
        val rest = (1..9).map { Random.nextInt(0, 10) }.joinToString("")
        return "$first$rest"
    }

    fun formatIsoTimestamp(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(iso)
                if (date != null) {
                    val out = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                    return out.format(date)
                }
            } catch (_: Exception) {
            }
        }
        return iso.take(16).replace('T', ' ')
    }

    fun formatMillisToIsoDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 2)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix.get(x, y)) AndroidColor.parseColor("#0F1F4B") else AndroidColor.WHITE
                    )
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveQrToGallery(context: Context, content: String): Boolean = withContext(Dispatchers.IO) {
        val qr = generateQrBitmap(content, 720) ?: return@withContext false
        val paddedSize = 800
        val exportBitmap = Bitmap.createBitmap(paddedSize, paddedSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(exportBitmap)
        canvas.drawColor(AndroidColor.WHITE)
        canvas.drawBitmap(qr, 40f, 40f, null)

        val fileName = "ZULVAZA_2FA_QR_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        return@withContext try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ZULVAZA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            var out: OutputStream? = null
            try {
                out = resolver.openOutputStream(uri) ?: return@withContext false
                val compressed = exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                if (!compressed) return@withContext false
            } finally {
                runCatching { out?.close() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun parseDeepLink(uri: Uri?): DeepLinkDestination? {
        if (uri == null) return null
        val fullPath = buildString {
            uri.host?.takeIf { uri.scheme == SupabaseConfig.DEEP_LINK_SCHEME }?.let {
                append("/")
                append(it)
            }
            uri.path?.let { append(it) }
        }.lowercase(Locale.US)

        return when {
            fullPath.contains("/seller/login") || fullPath == "/login" -> DeepLinkDestination.LOGIN
            fullPath.contains("/seller/signup") || fullPath == "/signup" -> DeepLinkDestination.SIGNUP
            fullPath.contains("/onboarding") || fullPath.contains("/verification") -> DeepLinkDestination.ONBOARDING
            fullPath.contains("/products") -> DeepLinkDestination.PRODUCTS
            fullPath.contains("/store") -> DeepLinkDestination.STORE
            fullPath.contains("/dashboard") -> DeepLinkDestination.DASHBOARD
            else -> null
        }
    }
}

data class ReverseGeocodeResult(
    val city: String = "",
    val street: String = "",
    val state: String = "",
    val address: String = "",
    val zipCode: String = ""
)

object LocationHelper {
    private val httpClient = OkHttpClient()

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): ReverseGeocodeResult = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    val city = addr.locality ?: addr.subAdminArea ?: ""
                    val state = addr.adminArea ?: ""
                    val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
                        .joinToString(" ")
                        .ifBlank { addr.featureName ?: "" }
                    val zip = addr.postalCode?.filter { it.isDigit() }?.take(5) ?: ""
                    val full = (0..addr.maxAddressLineIndex)
                        .mapNotNull { addr.getAddressLine(it) }
                        .joinToString(", ")
                    if (city.isNotBlank() || full.isNotBlank()) {
                        return@withContext ReverseGeocodeResult(
                            city = city,
                            street = street,
                            state = state,
                            address = full,
                            zipCode = zip
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Nominatim fallback when Android Geocoder service isn't available
        try {
            val req = Request.Builder()
                .url("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latitude&lon=$longitude")
                .header("User-Agent", "ZulvazaSellerAndroid/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val addressObj = json.optJSONObject("address")
                    val city = addressObj?.optString("city")
                        ?.ifBlank { addressObj.optString("town") }
                        ?.ifBlank { addressObj.optString("county") }
                        .orEmpty()
                    val street = listOf(
                        addressObj?.optString("house_number").orEmpty(),
                        addressObj?.optString("road").orEmpty(),
                        addressObj?.optString("suburb").orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(", ")
                    val state = addressObj?.optString("state").orEmpty()
                    val zip = addressObj?.optString("postcode").orEmpty().filter { it.isDigit() }.take(5)
                    val display = json.optString("display_name")
                    return@withContext ReverseGeocodeResult(
                        city = city,
                        street = street,
                        state = state,
                        address = display,
                        zipCode = zip
                    )
                }
            }
        } catch (_: Exception) {
        }
        ReverseGeocodeResult()
    }
}

object NetworkMonitor {
    fun observeIsOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun checkCurrent(): Boolean {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        trySend(checkCurrent())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(checkCurrent())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback)
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}
