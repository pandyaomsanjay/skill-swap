package com.example.sgp

import android.content.Context
import android.content.Intent

/**
 * Alias for PlaylistActivity.
 * Use PlaylistActivity directly.
 */
class PlaylistDetailActivity : PlaylistActivity() {
    companion object {
        @JvmStatic
        fun start(context: Context, skillId: String) {
            PlaylistActivity.start(context, skillId)
        }
    }
}