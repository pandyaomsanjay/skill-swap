package com.example.sgp

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Deletes files from Supabase Storage by their public object URL.
 *
 * SECURITY NOTE (read before shipping):
 * Deleting a file that belongs to *another* user normally requires either
 *   (a) that user's own session + an RLS policy letting them delete their
 *       own files, or
 *   (b) the Supabase `service_role` key, which bypasses RLS entirely.
 * Admin "delete any user's video" actions need (b). The service_role key
 * must NEVER ship inside a public APK — anyone can decompile it and get
 * full read/write/delete access to every bucket. SUPABASE_SERVICE_KEY
 * below is a placeholder so the feature works today; before release, move
 * this call behind a Supabase Edge Function (or your own backend) that
 * holds the key server-side, and have the app call that endpoint instead
 * of calling Supabase directly. This mirrors the existing TODO in
 * AdminVideosActivity about Firebase Auth account deletion needing a
 * Cloud Function — same shape of problem, same fix.
 */
object SupabaseStorageHelper {

    private const val TAG = "SupabaseStorageHelper"

    // TODO: pull these from BuildConfig / a secrets mechanism, not source.
    // TODO: replace direct use of SUPABASE_SERVICE_KEY with a call to a
    // server-side endpoint that holds this key instead (see note above).
    private const val SUPABASE_URL = "https://ghrxltlstncjcizyyqfo.supabase.co"
    // Paste the "service_role" key here (Project Settings → API), NOT "anon".
    // Its JWT payload should read "role":"service_role", not "role":"anon".
    private const val SUPABASE_SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdocnhsdGxzdG5jamNpenl5cWZvIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzQ5MTk5MywiZXhwIjoyMDg5MDY3OTkzfQ.pQEgxN733va60HPjUIIiR_1p3n4XQK7_HD9rzxiKkd0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Parses "bucket" and "path within bucket" out of a public Supabase
     * Storage URL, e.g.:
     *   https://xxxx.supabase.co/storage/v1/object/public/videos/u123/clip.mp4
     *   -> ("videos", "u123/clip.mp4")
     * Returns null if the URL doesn't look like a Supabase public object URL
     * (e.g. it's blank, or hosted somewhere else).
     */
    private fun parseBucketAndPath(url: String): Pair<String, String>? {
        val marker = "/storage/v1/object/public/"
        val idx = url.indexOf(marker)
        if (idx == -1) return null
        val rest = url.substring(idx + marker.length) // "videos/u123/clip.mp4?token=..."
        val slash = rest.indexOf('/')
        if (slash == -1) return null
        val bucket = rest.substring(0, slash)
        val rawPath = rest.substring(slash + 1).substringBefore('?')
        if (bucket.isBlank() || rawPath.isBlank()) return null
        return bucket to URLDecoder.decode(rawPath, "UTF-8")
    }

    /**
     * Deletes a batch of file URLs from Supabase Storage. Groups them by
     * bucket (the remove endpoint is per-bucket) and fires one request per
     * bucket. Nulls, blanks, and non-Supabase URLs are skipped and counted
     * as failures so the caller can warn the admin if storage cleanup was
     * incomplete. Safe to call with an empty list.
     *
     * @param onComplete (deletedCount, failedCount) — called on a
     * background thread; hop back to the main thread yourself if you touch
     * views in the callback.
     */
    fun deleteFiles(urls: List<String?>, onComplete: (deleted: Int, failed: Int) -> Unit) {
        val clean = urls.filterNotNull().filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            onComplete(0, 0)
            return
        }

        val byBucket = mutableMapOf<String, MutableList<String>>()
        var unparsed = 0
        clean.forEach { url ->
            val parsed = parseBucketAndPath(url)
            if (parsed == null) {
                unparsed++
                Log.w(TAG, "Skipping non-Supabase or malformed URL: $url")
            } else {
                byBucket.getOrPut(parsed.first) { mutableListOf() }.add(parsed.second)
            }
        }

        if (byBucket.isEmpty()) {
            onComplete(0, unparsed)
            return
        }

        var deleted = 0
        var failed = unparsed
        var remaining = byBucket.size

        byBucket.forEach { (bucket, paths) ->
            removeFromBucket(bucket, paths) { ok, count ->
                synchronized(this) {
                    if (ok) deleted += count else failed += count
                    remaining--
                    if (remaining == 0) onComplete(deleted, failed)
                }
            }
        }
    }

    /** Convenience overload for deleting a single file. */
    fun deleteFile(url: String?, onComplete: (success: Boolean) -> Unit = {}) {
        deleteFiles(listOf(url)) { deleted, _ -> onComplete(deleted > 0) }
    }

    private fun removeFromBucket(bucket: String, paths: List<String>, onDone: (Boolean, Int) -> Unit) {
        Log.d(TAG, "Requesting delete — bucket='$bucket', paths=$paths")

        val body = JSONObject().apply {
            put("prefixes", JSONArray(paths))
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/$bucket")
            .delete(body)
            .addHeader("Authorization", "Bearer $SUPABASE_SERVICE_KEY")
            .addHeader("apikey", SUPABASE_SERVICE_KEY)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Failed to delete from bucket '$bucket': ${e.message}")
                onDone(false, paths.size)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseText = response.body?.string()
                response.close()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Delete from bucket '$bucket' failed: ${response.code} $responseText")
                    onDone(false, paths.size)
                    return
                }

                // IMPORTANT: Supabase's bulk-delete endpoint returns 200 even
                // when a path didn't match any object in the bucket — it's
                // idempotent by design. A 2xx status alone does NOT mean the
                // file was actually there and removed. Count what the "data"
                // array in the response actually reports as deleted.
                val actuallyDeleted = try {
                    val trimmed = (responseText ?: "[]").trim()
                    if (trimmed.startsWith("[")) {
                        // Some API versions return a plain array of deleted objects.
                        JSONArray(trimmed).length()
                    } else {
                        // Others wrap it: {"message": "...", "data": [...]}
                        JSONObject(trimmed).optJSONArray("data")?.length() ?: 0
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't parse delete response for bucket '$bucket': $responseText")
                    0
                }

                Log.d(TAG, "Bucket '$bucket': requested ${paths.size} path(s), server reports $actuallyDeleted actually deleted")

                if (actuallyDeleted < paths.size) {
                    Log.w(TAG, "Bucket '$bucket': ${paths.size - actuallyDeleted} path(s) did NOT match any object — check the path format (paths tried: $paths)")
                }

                onDone(actuallyDeleted == paths.size, paths.size)
            }
        })
    }
}