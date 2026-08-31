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
            val intent = Intent(context, PlaylistDetailActivity::class.java)
            intent.putExtra("skillId", skillId)
            context.startActivity(intent)
        }
    }
}