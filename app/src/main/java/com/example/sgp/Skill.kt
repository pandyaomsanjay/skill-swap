package com.example.sgp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Skill(
    var id: String = "",
    val userId: String = "",
    val userName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val duration: String = "",          // for single video; for playlist: total duration string
    val credits: Int = 0,               // credits to access the whole playlist
    val timestamp: Long = 0,
    val videoUrl: String? = null,       // only for single video
    val skillType: String = "single",   // "single" or "playlist"
    val videos: List<PlaylistVideo>? = null, // only for playlist

    // ---- playlist specific fields ----
    val thumbnailUrl: String? = null,   // cover image URL
    val demoVideoUrl: String? = null,   // preview video (not part of paid videos)
    val videoCount: Int = 0,            // number of videos in the playlist
    val totalDuration: String? = null,  // human‑readable total duration (e.g. "45 min")

    // ---- FEATURE 1: uploader-facing access count ----
    // Denormalized counter of how many people currently have access to this
    // playlist (i.e. have purchased/unlocked it with points). Incremented
    // atomically inside PlaylistManager.purchasePlaylist's transaction —
    // never computed client-side from a purchase subcollection query.
    val accessCount: Int = 0
) : Parcelable

@Parcelize
data class PlaylistVideo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val credits: Int = 0,
    val order: Int = 0,
    val duration: String = "",        // NEW: human‑readable duration
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable