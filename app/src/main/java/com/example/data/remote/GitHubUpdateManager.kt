package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseApkAssetCandidate(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

data class SelectedReleaseApk(
    val asset: ReleaseApkAssetCandidate,
    val isDebugAsset: Boolean,
    val isCompatibleSignature: Boolean
)

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val buildCode: Int? = null,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isDebugApkAsset: Boolean = false,
    val isCompatibleSignature: Boolean = true,
    val isNewerThanCurrent: Boolean
)

sealed class AppUpdateStatus {
    data object Idle : AppUpdateStatus()
    data object Checking : AppUpdateStatus()
    data class UpToDate(val currentVersion: String, val checkedTag: String?) : AppUpdateStatus()
    data class UpdateAvailable(val release: GitHubReleaseInfo) : AppUpdateStatus()
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateStatus()
    data class ReadyToInstall(val release: GitHubReleaseInfo, val apkFile: File) : AppUpdateStatus()
    data class Error(val message: String, val htmlUrl: String? = null) : AppUpdateStatus()
}

object GitHubUpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkLatestRelease(repoSlug: String): GitHubReleaseInfo = withContext(Dispatchers.IO) {
        val cleanSlug = repoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        require(cleanSlug.contains("/") && cleanSlug.split("/").size == 2) {
            "Enter a valid GitHub repository (owner/repo)"
        }

        val latestJson = fetchLatestOrFallbackReleaseJson(cleanSlug)
        parseReleaseJson(
            json = latestJson,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            isCurrentDebugBuild = BuildConfig.DEBUG
        )
    }

    private fun fetchLatestOrFallbackReleaseJson(cleanSlug: String): JSONObject {
        val latestReq = Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZulvazaSellerCenter/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(latestReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return JSONObject(body)
            }
            if (resp.code != 404) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Update check failed (${resp.code})"
                throw IllegalStateException(msg)
            }
        }

        // Fallback: check /releases?per_page=10 in case latest wasn't tagged as official latest yet
        val listReq = Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases?per_page=10")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZulvazaSellerCenter/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(listReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 404) {
                    throw IllegalStateException(
                        "Repository releases not found (404) for $cleanSlug. Make sure the GitHub repository is Public and at least one APK release is published."
                    )
                }
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Update check failed (${resp.code})"
                throw IllegalStateException(msg)
            }
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val rel = arr.optJSONObject(i) ?: continue
                if (!rel.optBoolean("draft", false)) {
                    return rel
                }
            }
            throw IllegalStateException("No published app release found yet for $cleanSlug. Run the GitHub APK build workflow first.")
        }
    }

    internal fun parseReleaseJson(
        json: JSONObject,
        currentVersionName: String,
        currentVersionCode: Int,
        isCurrentDebugBuild: Boolean
    ): GitHubReleaseInfo {
        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
        val remoteVersion = normalizeVersion(tag)
        val currentVersion = normalizeVersion(currentVersionName)
        val releaseTitle = json.optString("name").ifBlank { tag }
        val releaseNotes = json.optString("body").ifBlank { "Bug fixes and performance improvements." }
        val publishedAt = json.optString("published_at")
        val htmlUrl = json.optString("html_url")
        val remoteBuildCode = extractBuildCode(tag, releaseTitle)

        val candidates = mutableListOf<ReleaseApkAssetCandidate>()
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    candidates += ReleaseApkAssetCandidate(
                        name = name,
                        downloadUrl = url,
                        sizeBytes = asset.optLong("size", 0L)
                    )
                }
            }
        }

        val selected = selectBestApkAsset(candidates, isCurrentDebugBuild)
        val newer = isReleaseNewer(
            remoteVersion = remoteVersion,
            remoteBuildCode = remoteBuildCode,
            currentVersion = currentVersion,
            currentBuildCode = currentVersionCode
        )

        return GitHubReleaseInfo(
            tagName = tag,
            versionName = remoteVersion,
            buildCode = remoteBuildCode,
            releaseTitle = releaseTitle,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = selected?.asset?.downloadUrl,
            apkFileName = selected?.asset?.name,
            apkSizeBytes = selected?.asset?.sizeBytes ?: 0L,
            isDebugApkAsset = selected?.isDebugAsset ?: false,
            isCompatibleSignature = selected?.isCompatibleSignature ?: true,
            isNewerThanCurrent = newer
        )
    }

    fun selectBestApkAsset(
        candidates: List<ReleaseApkAssetCandidate>,
        isCurrentDebugBuild: Boolean
    ): SelectedReleaseApk? {
        if (candidates.isEmpty()) return null
        val debugAssets = candidates.filter { it.name.contains("debug", ignoreCase = true) }
        val releaseAssets = candidates.filterNot { it.name.contains("debug", ignoreCase = true) }

        return if (isCurrentDebugBuild) {
            val matchingDebug = debugAssets.firstOrNull()
            if (matchingDebug != null) {
                SelectedReleaseApk(
                    asset = matchingDebug,
                    isDebugAsset = true,
                    isCompatibleSignature = true
                )
            } else {
                val fallbackRelease = releaseAssets.first()
                SelectedReleaseApk(
                    asset = fallbackRelease,
                    isDebugAsset = false,
                    isCompatibleSignature = false
                )
            }
        } else {
            val matchingRelease = releaseAssets.firstOrNull {
                it.name.contains("release", ignoreCase = true)
            } ?: releaseAssets.firstOrNull()
            if (matchingRelease != null) {
                SelectedReleaseApk(
                    asset = matchingRelease,
                    isDebugAsset = false,
                    isCompatibleSignature = true
                )
            } else {
                val fallbackDebug = debugAssets.first()
                SelectedReleaseApk(
                    asset = fallbackDebug,
                    isDebugAsset = true,
                    isCompatibleSignature = false
                )
            }
        }
    }

    fun extractBuildCode(tag: String, releaseTitle: String): Int? {
        val tagBuild = Regex("(?i)-b(\\d+)\\b").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (tagBuild != null) return tagBuild
        return Regex("(?i)\\bBuild\\s+(\\d+)\\b").find(releaseTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun isReleaseNewer(
        remoteVersion: String,
        remoteBuildCode: Int?,
        currentVersion: String,
        currentBuildCode: Int
    ): Boolean {
        val cmp = compareVersions(remoteVersion, currentVersion)
        if (cmp > 0) return true
        if (cmp < 0) return false
        return remoteBuildCode != null && remoteBuildCode > currentBuildCode
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkDownloadUrl
            ?: throw IllegalStateException("No APK installer attached to release ${release.tagName}")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { old -> if (old.isFile) old.delete() }
        val targetName = (release.apkFileName ?: "zulvaza-seller-${release.versionName}.apk")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(dir, targetName)

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "ZulvazaSellerCenter/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("APK download failed (${resp.code})")
            }
            val body = resp.body ?: throw IllegalStateException("Empty APK response")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        val progress = if (total > 0L) {
                            (readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(progress, readTotal, total)
                    }
                    output.flush()
                }
            }
        }

        // Verify valid APK (ZIP magic header 'P''K')
        val isValidApk = destFile.exists() && destFile.length() > 65536L && run {
            val header = ByteArray(2)
            destFile.inputStream().use { it.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() }
        }
        if (!isValidApk) {
            destFile.delete()
            throw IllegalStateException("Downloaded update package is invalid or incomplete. Try again or download from Releases.")
        }
        destFile
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    private fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    }

    fun compareVersions(remote: String, current: String): Int {
        val rParts = remote.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }
}
