package com.example.sgp // ← match your actual package name

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

object BottomNav {

    const val DASHBOARD = 0
    const val USERS = 1
    const val SWAPS = 2
    const val REPORTS = 3
    const val FEEDBACK = 4

    private val ACTIVE_BG = Color.parseColor("#F9F3EF")
    private val INACTIVE_BG = Color.parseColor("#00000000")
    private val ACTIVE_FG = Color.parseColor("#1B3C53")
    private val INACTIVE_FG = Color.parseColor("#FFFFFF")

    private val evaluator = ArgbEvaluator()
    private val bounceInterpolator = OvershootInterpolator(3.5f)
    private val smoothInterpolator = DecelerateInterpolator(1.5f)

    private const val COLOR_DURATION = 220L
    private const val HOP_DURATION = 280L
    private const val SETTLE_DURATION = 160L
    private const val TAP_BOUNCE_DURATION = 220L

    fun setup(activity: AppCompatActivity, selectedIndex: Int) {

        val containers = arrayOf<LinearLayout>(
            activity.findViewById(R.id.navDashboard),
            activity.findViewById(R.id.navUsers),
            activity.findViewById(R.id.navSwaps),
            activity.findViewById(R.id.navReports),
            activity.findViewById(R.id.navFeedback)
        )
        val cards = arrayOf<MaterialCardView>(
            activity.findViewById(R.id.cardDashboardIcon),
            activity.findViewById(R.id.cardUsersIcon),
            activity.findViewById(R.id.cardSwapsIcon),
            activity.findViewById(R.id.cardReportsIcon),
            activity.findViewById(R.id.cardFeedbackIcon)
        )
        val icons = arrayOf<ImageView>(
            activity.findViewById(R.id.ivDashboardIcon),
            activity.findViewById(R.id.ivUsersIcon),
            activity.findViewById(R.id.ivSwapsIcon),
            activity.findViewById(R.id.ivReportsIcon),
            activity.findViewById(R.id.ivFeedbackIcon)
        )

        containers.indices.forEach { i ->
            if (i != selectedIndex) {
                setInactiveInstantly(cards[i], icons[i])
            }
        }
        animateLanding(cards[selectedIndex], icons[selectedIndex])

        val targets = arrayOf(
            AdminDashboardActivity::class.java,
            AdminUsersActivity::class.java,
            AdminTradesActivity::class.java,
            AdminReportsActivity::class.java,
            AdminFeedbackActivity::class.java
        )

        containers.indices.forEach { i ->
            containers[i].setOnClickListener {
                if (i == selectedIndex) return@setOnClickListener

                tapBounce(cards[i])
                if (containers[i].isHapticFeedbackEnabled) {
                    containers[i].performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                navigateTo(activity, targets[i])
            }
        }
    }

    // ---------- painting ----------

    private fun setInactiveInstantly(card: MaterialCardView, icon: ImageView) {
        card.setCardBackgroundColor(INACTIVE_BG)
        card.cardElevation = 0f
        icon.imageTintList = ColorStateList.valueOf(INACTIVE_FG)
    }

    private fun animateLanding(card: MaterialCardView, icon: ImageView) {
        animateColor(card, INACTIVE_BG, ACTIVE_BG)
        animateTint(icon, INACTIVE_FG, ACTIVE_FG)

        card.cardElevation = 0f
        card.animate().cancel()
        ValueAnimator.ofFloat(0f, 6f).apply {
            duration = COLOR_DURATION
            interpolator = smoothInterpolator
            addUpdateListener { card.cardElevation = it.animatedValue as Float }
            start()
        }

        icon.animate().cancel()
        icon.scaleX = 0.55f
        icon.scaleY = 0.55f
        icon.rotation = -18f
        icon.translationY = 0f
        val hopUpPx = -(6f * icon.resources.displayMetrics.density)
        icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .translationY(hopUpPx)
            .setDuration(HOP_DURATION)
            .setInterpolator(bounceInterpolator)
            .withEndAction {
                icon.animate()
                    .translationY(0f)
                    .setDuration(SETTLE_DURATION)
                    .setInterpolator(smoothInterpolator)
                    .start()
            }
            .start()
    }

    private fun tapBounce(card: MaterialCardView) {
        card.animate().cancel()
        card.scaleX = 0.88f
        card.scaleY = 0.88f
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(TAP_BOUNCE_DURATION)
            .setInterpolator(bounceInterpolator)
            .start()
    }

    private fun animateColor(card: MaterialCardView, from: Int, to: Int) {
        ValueAnimator.ofObject(evaluator, from, to).apply {
            duration = COLOR_DURATION
            interpolator = smoothInterpolator
            addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun animateTint(icon: ImageView, from: Int, to: Int) {
        ValueAnimator.ofObject(evaluator, from, to).apply {
            duration = COLOR_DURATION
            interpolator = smoothInterpolator
            addUpdateListener { icon.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
            start()
        }
    }

    private fun navigateTo(activity: AppCompatActivity, target: Class<*>) {

        val intent = Intent(activity, target)
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
    }
}