package com.example.sgp.ui.nav

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView

class BottomNavAnimator(
    private val tabs: List<Tab>,
    private val hapticsEnabled: Boolean = true
) {

    data class Tab(
        val root: View,
        val pill: MaterialCardView,
        val icon: ImageView
    )

    private var current: Tab? = null

    private val activeBg = Color.parseColor("#F9F3EF")
    private val inactiveBg = Color.parseColor("#00000000")
    private val activeTint = Color.parseColor("#1B3C53")
    private val inactiveTint = Color.parseColor("#D2C1B6")

    private val evaluator = ArgbEvaluator()
    private val bounceInterpolator = OvershootInterpolator(3.5f)
    private val smoothInterpolator = DecelerateInterpolator(1.5f)

    private val hopDistancePx: Float by lazy {
        tabs.firstOrNull()?.root?.resources?.displayMetrics?.density?.times(6f) ?: 6f
    }

    fun setInitialSelection(tab: Tab) {
        current = tab
        tabs.forEach { t ->
            val isSelected = t === tab
            t.pill.setCardBackgroundColor(if (isSelected) activeBg else inactiveBg)
            t.pill.cardElevation = if (isSelected) 6f else 0f
            t.icon.imageTintList = ColorStateList.valueOf(if (isSelected) activeTint else inactiveTint)
        }
    }

    fun select(target: Tab, onSelected: (() -> Unit)? = null) {
        val previous = current

        if (target === previous) {
            // Re-tapping the active tab: no navigation happens, but the tap
            // should still feel acknowledged.
            bounceOnly(target)
            onSelected?.invoke()
            return
        }

        current = target
        if (hapticsEnabled) {
            target.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        previous?.let { animateOut(it) }
        animateIn(target)
        onSelected?.invoke()
    }

    // ---------- animations ----------

    private fun bounceOnly(tab: Tab) {
        tab.icon.animate().cancel()
        tab.icon.scaleX = 0.85f
        tab.icon.scaleY = 0.85f
        tab.icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(bounceInterpolator)
            .start()
    }

    private fun animateIn(tab: Tab) {
        animateColor(tab.pill, inactiveBg, activeBg)
        animateTint(tab.icon, inactiveTint, activeTint)

        tab.pill.animate().cancel()
        tab.pill.animate()
            .translationZ(6f)
            .setDuration(220)
            .setInterpolator(smoothInterpolator)
            .start()

        // The hop: icon shrinks + tilts, then snaps up and straight with an
        // overshoot, then eases back down to rest — a tiny "jump for joy".
        tab.icon.animate().cancel()
        tab.icon.scaleX = 0.55f
        tab.icon.scaleY = 0.55f
        tab.icon.rotation = -18f
        tab.icon.translationY = 0f
        tab.icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .translationY(-hopDistancePx)
            .setDuration(280)
            .setInterpolator(bounceInterpolator)
            .withEndAction {
                tab.icon.animate()
                    .translationY(0f)
                    .setDuration(160)
                    .setInterpolator(smoothInterpolator)
                    .start()
            }
            .start()
    }

    private fun animateOut(tab: Tab) {
        animateColor(tab.pill, activeBg, inactiveBg)
        animateTint(tab.icon, activeTint, inactiveTint)

        tab.pill.animate().cancel()
        tab.pill.animate()
            .translationZ(0f)
            .setDuration(180)
            .setInterpolator(smoothInterpolator)
            .start()

        // Quick squash-and-settle so the outgoing icon doesn't just vanish in place.
        tab.icon.animate().cancel()
        tab.icon.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(140)
            .setInterpolator(smoothInterpolator)
            .withEndAction {
                tab.icon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun animateColor(card: MaterialCardView, from: Int, to: Int) {
        ValueAnimator.ofObject(evaluator, from, to).apply {
            duration = 220
            interpolator = smoothInterpolator
            addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun animateTint(icon: ImageView, from: Int, to: Int) {
        ValueAnimator.ofObject(evaluator, from, to).apply {
            duration = 220
            interpolator = smoothInterpolator
            addUpdateListener { icon.imageTintList = ColorStateList.valueOf(it.animatedValue as Int) }
            start()
        }
    }
}