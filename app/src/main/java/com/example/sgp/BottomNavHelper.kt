package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

enum class BottomNavItem { HOME, EXPLORE, ADD_SKILL, TRADES, PROFILE }

object BottomNavHelper {

    private val selectedBg = Color.parseColor("#FFFFFF")
    private const val unselectedBg = Color.TRANSPARENT
    private val selectedTint = Color.parseColor("#1B3C53")
    private val unselectedTint = Color.parseColor("#FFFFFF")

    fun setup(activity: AppCompatActivity, selected: BottomNavItem) {
        val navHome = activity.findViewById<LinearLayout>(R.id.navHome)
        val navExplore = activity.findViewById<LinearLayout>(R.id.navExplore)
        val navAddSkill = activity.findViewById<LinearLayout>(R.id.navAddSkill)
        val navTrades = activity.findViewById<LinearLayout>(R.id.navTrades)
        val navProfile = activity.findViewById<LinearLayout>(R.id.navProfile)

        val cards = mapOf(
            BottomNavItem.HOME to activity.findViewById<MaterialCardView>(R.id.cardHomeIcon),
            BottomNavItem.EXPLORE to activity.findViewById<MaterialCardView>(R.id.cardExploreIcon),
            BottomNavItem.ADD_SKILL to activity.findViewById<MaterialCardView>(R.id.cardAddSkillIcon),
            BottomNavItem.TRADES to activity.findViewById<MaterialCardView>(R.id.cardTradesIcon),
            BottomNavItem.PROFILE to activity.findViewById<MaterialCardView>(R.id.cardProfileIcon)
        )

        val icons = mapOf(
            BottomNavItem.HOME to activity.findViewById<ImageView>(R.id.ivHomeIcon),
            BottomNavItem.EXPLORE to activity.findViewById<ImageView>(R.id.ivExploreIcon),
            BottomNavItem.ADD_SKILL to activity.findViewById<ImageView>(R.id.ivAddSkillIcon),
            BottomNavItem.TRADES to activity.findViewById<ImageView>(R.id.ivTradesIcon),
            BottomNavItem.PROFILE to activity.findViewById<ImageView>(R.id.ivProfileIcon)
        )

        BottomNavItem.entries.forEach { item ->
            val isSelected = item == selected
            cards[item]?.setCardBackgroundColor(if (isSelected) selectedBg else unselectedBg)
            icons[item]?.setColorFilter(if (isSelected) selectedTint else unselectedTint)
        }

        navHome.setOnClickListener { navigateTo(activity, BottomNavItem.HOME, selected) }
        navExplore.setOnClickListener { navigateTo(activity, BottomNavItem.EXPLORE, selected) }
        navAddSkill.setOnClickListener { navigateTo(activity, BottomNavItem.ADD_SKILL, selected) }
        navTrades.setOnClickListener { navigateTo(activity, BottomNavItem.TRADES, selected) }
        navProfile.setOnClickListener { navigateTo(activity, BottomNavItem.PROFILE, selected) }
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
        activity.startActivity(Intent(activity, destination))
        if (target == BottomNavItem.HOME) activity.finish()
    }
}