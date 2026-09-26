package com.example.sgp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Unified model for every skill-like item in SkillSwap.
 *
 * Supports two broad categories:
 *
 *  1. VIDEO-CENTRIC  (skillType = "single" | "playlist")
 *     - Single videos and playlists built from [videos]
 *
 *  2. RESOURCE-CENTRIC  (skillType = "resource")
 *     - NOTES, PDF, DOCUMENT, COURSE, LIVE_SESSION, OTHER
 *     - Uses [resourceType] plus the resource-specific fields
 *
 * All new fields default to safe values so legacy documents
 * continue to deserialize without any migration.
 */
@Parcelize
data class Skill(
    var id: String = "",

    // ─────────────────────────────────────────────────────────
    // OWNER
    // ─────────────────────────────────────────────────────────
    val userId: String = "",            // email of the uploader
    val userName: String = "",          // display name at upload time

    // ─────────────────────────────────────────────────────────
    // CORE METADATA (shared by all skill types)
    // ─────────────────────────────────────────────────────────
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val duration: String = "",          // human string: "45 min", "2h 30m"
    val credits: Int = 0,               // credit cost to access / trade
    val timestamp: Long = 0,            // millis; used for sorting

    // ─────────────────────────────────────────────────────────
    // TYPE DISCRIMINATOR
    // ─────────────────────────────────────────────────────────
    /**
     * High-level container type.
     *   "single"   → one video
     *   "playlist" → collection of videos
     *   "resource" → notes / pdf / document / course / live session / other
     */
    val skillType: String = "single",

    // ─────────────────────────────────────────────────────────
    // VIDEO-CENTRIC FIELDS
    // ─────────────────────────────────────────────────────────
    val videoUrl: String? = null,               // only for skillType = "single"
    val videos: List<PlaylistVideo>? = null,    // only for skillType = "playlist"

    // Playlist-specific
    val thumbnailUrl: String? = null,           // cover image (or fallback for single)
    val demoVideoUrl: String? = null,           // preview video (playlist)
    val videoCount: Int = 0,                    // number of videos in the playlist
    val totalDuration: String? = null,          // "45 min", "2h 15m"

    // ─────────────────────────────────────────────────────────
    // RESOURCE-CENTRIC FIELDS (skillType = "resource")
    // ─────────────────────────────────────────────────────────
    /**
     * Specific resource type when skillType = "resource".
     * One of: "VIDEO", "NOTES", "PDF", "DOCUMENT", "COURSE", "LIVE_SESSION", "OTHER"
     *
     * Defaults to "VIDEO" so old video/playlist docs still parse.
     */
    val resourceType: String = "VIDEO",

    /** Supabase Storage URL of the uploaded file (PDF, DOC, DOCX, etc.). */
    val resourceUrl: String? = null,

    /** Inline text content — used only when resourceType = "NOTES". */
    val textContent: String? = null,

    /** File size in bytes — used for display. */
    val fileSize: Long = 0L,

    /** MIME type of the uploaded file, e.g. "application/pdf". */
    val mimeType: String? = null,

    // ─────────────────────────────────────────────────────────
    // LIVE SESSION FIELDS (resourceType = "LIVE_SESSION")
    // ─────────────────────────────────────────────────────────

    /** "Google Meet" | "Zoom" | "Microsoft Teams" | "In-person" | "Other". */
    val liveSessionPlatform: String? = null,

    /** "online" | "offline". */
    val liveSessionMode: String? = null,

    /** Session length in minutes (integer, for calculations). */
    val liveSessionDurationMin: Int = 0,

    /**
     * Private meeting link.
     * Must NOT be shown to the counterparty until the trade is finalized
     * (see Trade.shouldExposeMeetingDetails).
     */
    val liveSessionMeetingLink: String? = null,

    /** Physical location — used when liveSessionMode = "offline". */
    val liveSessionLocation: String? = null,

    // ─────────────────────────────────────────────────────────
    // UPLOADER-FACING METRICS
    // ─────────────────────────────────────────────────────────

    /**
     * Denormalized counter of how many people currently have access to this
     * playlist (i.e. have purchased/unlocked it with points). Incremented
     * atomically inside PlaylistManager.purchasePlaylist's transaction —
     * never computed client-side from a purchase subcollection query.
     */
    val accessCount: Int = 0

) : Parcelable {

    // ═════════════════════════════════════════════════════════════
    // TYPED ACCESSORS — safe with legacy or partial documents
    // ═════════════════════════════════════════════════════════════

    /** Typed resource type — falls back to OTHER for unknown values. */
    val resourceTypeEnum: TradeResourceType
        get() = TradeResourceType.fromString(resourceType)

    /** True for single video or playlist. */
    val isVideoCentric: Boolean
        get() = skillType == "single" || skillType == "playlist"

    /** True for notes / pdf / document / course / live session / other. */
    val isResourceCentric: Boolean
        get() = skillType == "resource"

    /** True if this is a text-only notes resource. */
    val isTextNotes: Boolean
        get() = skillType == "resource"
                && resourceType == "NOTES"
                && !textContent.isNullOrBlank()

    /** True if this is a file-backed resource (PDF/DOC/DOCX). */
    val hasAttachedFile: Boolean
        get() = !resourceUrl.isNullOrBlank()

    /** True if this is a live teaching session. */
    val isLiveSession: Boolean
        get() = skillType == "resource"
                && resourceType == "LIVE_SESSION"

    /** Best display thumbnail: cover > video > demo > null. */
    val displayThumbnailUrl: String?
        get() = when {
            !thumbnailUrl.isNullOrBlank() -> thumbnailUrl
            !videoUrl.isNullOrBlank() -> videoUrl
            !demoVideoUrl.isNullOrBlank() -> demoVideoUrl
            else -> null
        }

    /**
     * Human-friendly duration label.
     * Prefers the explicit string, falls back to live-session minutes.
     */
    val displayDuration: String
        get() = when {
            duration.isNotBlank() -> duration
            !totalDuration.isNullOrBlank() -> totalDuration!!
            liveSessionDurationMin > 0 -> "$liveSessionDurationMin min"
            else -> "—"
        }

    /** Human-friendly file size label. */
    val displayFileSize: String
        get() = when {
            fileSize <= 0L -> "—"
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
        }

    /** True if this skill is owned by [email]. */
    fun isOwnedBy(email: String): Boolean =
        email.isNotBlank() && userId.equals(email, ignoreCase = true)

    companion object {
        // ── skillType values ──
        const val TYPE_SINGLE = "single"
        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_RESOURCE = "resource"

        // ── resourceType values (also mirrored in TradeResourceType) ──
        const val RESOURCE_VIDEO = "VIDEO"
        const val RESOURCE_NOTES = "NOTES"
        const val RESOURCE_PDF = "PDF"
        const val RESOURCE_DOCUMENT = "DOCUMENT"
        const val RESOURCE_COURSE = "COURSE"
        const val RESOURCE_LIVE_SESSION = "LIVE_SESSION"
        const val RESOURCE_OTHER = "OTHER"
    }
}

@Parcelize
data class PlaylistVideo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val credits: Int = 0,
    val order: Int = 0,
    val duration: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable