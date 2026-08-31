package com.example.sgp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.example.sgp.ui.nav.BottomNavAnimator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

enum class BottomNavItem { HOME, EXPLORE, ADD_SKILL, TRADES, PROFILE }

object BottomNavHelper {

    fun setup(activity: AppCompatActivity, selected: BottomNavItem) {
        val navHome = activity.findViewById<LinearLayout>(R.id.navHome)
        val navExplore = activity.findViewById<LinearLayout>(R.id.navExplore)
        val navAddSkill = activity.findViewById<LinearLayout>(R.id.navAddSkill)
        val navTrades = activity.findViewById<LinearLayout>(R.id.navTrades)
        val navProfile = activity.findViewById<LinearLayout>(R.id.navProfile)

        val cardHome = activity.findViewById<MaterialCardView>(R.id.cardHomeIcon)
        val cardExplore = activity.findViewById<MaterialCardView>(R.id.cardExploreIcon)
        val cardAddSkill = activity.findViewById<MaterialCardView>(R.id.cardAddSkillIcon)
        val cardTrades = activity.findViewById<MaterialCardView>(R.id.cardTradesIcon)
        val cardProfile = activity.findViewById<MaterialCardView>(R.id.cardProfileIcon)

        val iconHome = activity.findViewById<ImageView>(R.id.ivHomeIcon)
        val iconExplore = activity.findViewById<ImageView>(R.id.ivExploreIcon)
        val iconAddSkill = activity.findViewById<ImageView>(R.id.ivAddSkillIcon)
        val iconTrades = activity.findViewById<ImageView>(R.id.ivTradesIcon)
        val iconProfile = activity.findViewById<ImageView>(R.id.ivProfileIcon)

        // White border around every nav item's icon card.
        val strokeColor = Color.parseColor("#FFFFFF")
        val strokeWidthPx = (activity.resources.displayMetrics.density * 1f).toInt() // 1dp

        listOf(cardHome, cardExplore, cardAddSkill, cardTrades, cardProfile).forEach { card ->
            card.strokeColor = strokeColor
            card.strokeWidth = strokeWidthPx
        }

        val tabMap = mapOf(
            BottomNavItem.HOME to BottomNavAnimator.Tab(navHome, cardHome, iconHome),
            BottomNavItem.EXPLORE to BottomNavAnimator.Tab(navExplore, cardExplore, iconExplore),
            BottomNavItem.ADD_SKILL to BottomNavAnimator.Tab(navAddSkill, cardAddSkill, iconAddSkill),
            BottomNavItem.TRADES to BottomNavAnimator.Tab(navTrades, cardTrades, iconTrades),
            BottomNavItem.PROFILE to BottomNavAnimator.Tab(navProfile, cardProfile, iconProfile)
        )

        val animator = BottomNavAnimator(tabs = tabMap.values.toList())
        tabMap[selected]?.let { animator.setInitialSelection(it) }

        BottomNavItem.entries.forEach { item ->
            tabMap[item]?.root?.setOnClickListener {
                val targetTab = tabMap[item] ?: return@setOnClickListener
                animator.select(targetTab) {
                    navigateTo(activity, item, selected)
                }
            }
        }
    }

    private fun navigateTo(activity: AppCompatActivity, target: BottomNavItem, current: BottomNavItem) {
        if (target == current) return

        val destination = when (target) {
            BottomNavItem.HOME -> Home::class.java
            BottomNavItem.EXPLORE -> ExploreActivity::class.java
            BottomNavItem.ADD_SKILL -> AddSkillActivity::class.java
            BottomNavItem.TRADES -> MyTradesActivity::class.java
            BottomNavItem.PROFILE -> Profile::class.java
        }

        // Create the Intent with our custom transition flags
        val intent = Intent(activity, destination)

        // Set a beautiful Fade + Scale transition (No Slides)
        activity.startActivity(intent)
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        if (target == BottomNavItem.HOME) activity.finish()
    }
}

/**
 * Helper class to animate the CONTENT of each Activity's layout
 * as it comes in, making it feel "premium" and smooth.
 */
object PageTransitionHelper {

    fun animateContent(activity: Activity, vararg views: View) {
        // Ensure the activity window has an exit animation
        activity.window.enterTransition = null
        activity.window.exitTransition = null
        activity.window.reenterTransition = null

        // Staggered entrance
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.scaleX = 0.95f
            view.scaleY = 0.95f

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 80L) // Stagger by 80ms
                .setDuration(450)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }
}