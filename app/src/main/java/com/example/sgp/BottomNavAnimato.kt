package com.example.sgp.ui.nav

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    private val activeTint = Color.parseColor("#1B3C53")   // navy — selected icon
    private val inactiveTint = Color.parseColor("#FFFFFF") // white — unselected icon

    private val borderColor = Color.parseColor("#FFFFFF")
    private val borderWidthPx: Int by lazy {
        (tabs.firstOrNull()?.root?.resources?.displayMetrics?.density?.times(1f) ?: 1f).toInt()
    }

    private val evaluator = ArgbEvaluator()
    private val smoothInterpolator = DecelerateInterpolator(1.8f)

    fun setInitialSelection(tab: Tab) {
        current = tab
        tabs.forEach { t ->
            val isSelected = t === tab
            t.pill.setCardBackgroundColor(if (isSelected) activeBg else inactiveBg)
            t.pill.cardElevation = if (isSelected) 4f else 0f
            t.pill.strokeColor = borderColor
            t.pill.strokeWidth = borderWidthPx
            // Clear any XML-defined tint first, then force the correct one.
            t.icon.imageTintList = null
            t.icon.imageTintList = ColorStateList.valueOf(if (isSelected) activeTint else inactiveTint)
        }
    }

    fun select(target: Tab, onSelected: (() -> Unit)? = null) {
        val previous = current

        if (target === previous) {
            subtlePulse(target)
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

    // ---------- animations (NO JUMPING) ----------

    private fun subtlePulse(tab: Tab) {
        tab.icon.animate().cancel()
        tab.icon.scaleX = 0.92f
        tab.icon.scaleY = 0.92f
        tab.icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(smoothInterpolator)
            .start()
    }

    private fun animateIn(tab: Tab) {
        animateColor(tab.pill, inactiveBg, activeBg)
        animateTint(tab.icon, inactiveTint, activeTint)

        tab.pill.animate().cancel()
        tab.pill.animate()
            .translationZ(4f)
            .setDuration(220)
            .setInterpolator(smoothInterpolator)
            .start()

        // Smooth "pop" in without moving position (NO JUMP)
        tab.icon.animate().cancel()
        tab.icon.scaleX = 0.75f
        tab.icon.scaleY = 0.75f
        tab.icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(smoothInterpolator)
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

        // Smooth shrink out without moving position
        tab.icon.animate().cancel()
        tab.icon.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
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