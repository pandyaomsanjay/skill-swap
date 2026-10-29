
package com.example.sgp

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseImageUploader {

    private val mediaBucket
        get() = SupabaseClient.client.storage.from("media")

    /**
     * Uploads a profile image to profile_images/.
     * Deletes the old image first if an old path is provided.
     * Returns Pair(publicUrl, storagePath).
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
                // Ignore old image deletion errors.
            }
        }

        val bytes = context.contentResolver
            .openInputStream(imageUri)
            ?.use { it.readBytes() }
            ?: throw IllegalStateException(
                "Could not read selected image"
            )

        val newPath =
            "profile_images/${userId}_${System.currentTimeMillis()}.jpg"

        mediaBucket.upload(newPath, bytes)

        val publicUrl = mediaBucket.publicUrl(newPath)

        Pair(publicUrl, newPath)
    }

    /**
     * Uploads a chat recovery screenshot (maximum 5 MB)
     * to the media bucket under recovery/.
     * Returns the public URL.
     */
    suspend fun uploadRecoveryScreenshot(
        context: Context,
        imageUri: Uri,
        userId: String
    ): String = withContext(Dispatchers.IO) {

        val bytes = context.contentResolver
            .openInputStream(imageUri)
            ?.use { it.readBytes() }
            ?: throw IllegalStateException(
                "Could not read selected image"
            )

        if (bytes.size > 5 * 1024 * 1024) {
            throw IllegalStateException(
                "Screenshot is larger than 5 MB"
            )
        }

        val path =
            "recovery/${userId}_${System.currentTimeMillis()}.jpg"

        mediaBucket.upload(path, bytes)

        mediaBucket.publicUrl(path)
    }
}
