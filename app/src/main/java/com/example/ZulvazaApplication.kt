package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.Options
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ZulvazaApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .components {
                add(SupabaseSignedUrlKeyer())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(50L * 1024L * 1024L)
                    .build()
            }
            .crossfade(180)
            .build()
    }
}

/**
 * Strips short-lived `?token=...` query parameters from Supabase signed storage URLs so Coil
 * serves cached store banners, logos, and product thumbnails instantaneously across sessions.
 */
private class SupabaseSignedUrlKeyer : Keyer<String> {
    override fun key(data: String, options: Options): String? {
        if (data.contains("/storage/v1/object/sign/")) {
            val base = data.substringBefore('?')
            if (base.isNotBlank()) return base
        }
        return null
    }
}
