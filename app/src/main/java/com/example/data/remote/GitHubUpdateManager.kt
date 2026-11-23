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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
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
    data class Error(val message: String) : AppUpdateStatus()
}

object GitHubUpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatestRelease(repoSlug: String): GitHubReleaseInfo = withContext(Dispatchers.IO) {
        val cleanSlug = repoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        require(cleanSlug.contains("/") && cleanSlug.split("/").size == 2) {
            "Enter a valid GitHub repository (owner/repo)"
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZulvazaSellerCenter/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) {
                throw IllegalStateException("No published app release found yet.")
            }
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Update check failed (${resp.code})"
                throw IllegalStateException(msg)
            }

            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val remoteVersion = normalizeVersion(tag)
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)
            val releaseTitle = json.optString("name").ifBlank { tag }
            val releaseNotes = json.optString("body").ifBlank { "Bug fixes and performance improvements." }
            val publishedAt = json.optString("published_at")
            val htmlUrl = json.optString("html_url")

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        // Prefer release APK if both debug & release exist
                        if (apkUrl == null || name.contains("release", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                        }
                    }
                }
            }

            GitHubReleaseInfo(
                tagName = tag,
                versionName = remoteVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                isNewerThanCurrent = compareVersions(remoteVersion, currentVersion) > 0
            )
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkDownloadUrl
            ?: throw IllegalStateException("No APK installer attached to release ${release.tagName}")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean previous APKs
        dir.listFiles()?.forEach { old -> if (old.isFile) old.delete() }
        val targetName = (release.apkFileName ?: "zulvaza-seller-${release.versionName}.apk")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(dir, targetName)

        val request = Request.Builder()
            .url(url)
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
