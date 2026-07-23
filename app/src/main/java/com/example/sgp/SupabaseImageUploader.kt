package com.example.sgp

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseImageUploader {

    private val mediaBucket get() = SupabaseClient.client.storage.from("media")

    /**
     * Uploads [imageUri] to the "media" bucket under profile_images/, deleting
     * [oldImagePath] first (if provided) so we don't accumulate orphaned files.
     * Returns Pair(publicUrl, storagePath) — store both in Firestore.
     */
    suspend fun uploadProfileImage(
        context: Context,
        imageUri: Uri,
        userId: String,
        oldImagePath: String?
    ): Pair<String, String> = withContext(Dispatchers.IO) {

        if (!oldImagePath.isNullOrEmpty()) {
            try {
                mediaBucket.delete(oldImagePath)
            } catch (e: Exception) {
                // Old file may already be gone (or path malformed) — don't block
                // the new upload just because cleanup of the old one failed.
            }
        }

        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected image")

        val newPath = "profile_images/${userId}_${System.currentTimeMillis()}.jpg"

        mediaBucket.upload(newPath, bytes)

        val publicUrl = mediaBucket.publicUrl(newPath)
        Pair(publicUrl, newPath)
    }
}