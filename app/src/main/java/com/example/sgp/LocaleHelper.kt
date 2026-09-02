package com.example.sgp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object LocaleHelper {

    const val LANG_ENGLISH = "English"
    const val LANG_HINDI = "Hindi"
    const val LANG_GUJARATI = "Gujarati"

    const val CODE_ENGLISH = "en"
    const val CODE_HINDI = "hi"
    const val CODE_GUJARATI = "gu"

    const val KEY_LANGUAGE_PROMPTED = "language_prompted_first_time"

    val LANGUAGE_OPTIONS = arrayOf(
        LanguageItem(LANG_ENGLISH, "English", "English", CODE_ENGLISH, "🇬🇧"),
        LanguageItem(LANG_HINDI, "Hindi", "हिन्दी", CODE_HINDI, "🇮🇳"),
        LanguageItem(LANG_GUJARATI, "Gujarati", "ગુજરાતી", CODE_GUJARATI, "🇮🇳")
    )

    data class LanguageItem(
        val key: String,
        val englishName: String,
        val nativeName: String,
        val code: String,
        val flag: String
    ) {
        val displayLabel: String get() = "$nativeName ($englishName)"
    }

    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        return context.createConfigurationContext(config)
    }

    fun normalizeLanguageKey(input: String?): String {
        if (input.isNullOrBlank()) return LANG_ENGLISH
        val trimmed = input.trim()
        return when {
            trimmed.contains("hi", ignoreCase = true) || trimmed.contains("Hindi", ignoreCase = true) || trimmed.contains("हिन्दी") -> LANG_HINDI
            trimmed.contains("gu", ignoreCase = true) || trimmed.contains("Gujarati", ignoreCase = true) || trimmed.contains("ગુજરાતી") -> LANG_GUJARATI
            else -> LANG_ENGLISH
        }
    }

    fun getLanguageCode(context: Context): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val tag = appLocales.get(0)?.language?.lowercase() ?: ""
            if (tag == CODE_HINDI || tag == CODE_GUJARATI || tag == CODE_ENGLISH) {
                return tag
            }
        }
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val langName = prefs.getString(SettingsActivity.KEY_LANGUAGE, LANG_ENGLISH) ?: LANG_ENGLISH
        return when (normalizeLanguageKey(langName)) {
            LANG_HINDI -> CODE_HINDI
            LANG_GUJARATI -> CODE_GUJARATI
            else -> CODE_ENGLISH
        }
    }

    fun getLanguageDisplayName(context: Context): String {
        val code = getLanguageCode(context)
        return when (code) {
            CODE_HINDI -> "हिन्दी"
            CODE_GUJARATI -> "ગુજરાતી"
            else -> "English"
        }
    }

    fun getLanguageKey(context: Context): String {
        val code = getLanguageCode(context)
        return when (code) {
            CODE_HINDI -> LANG_HINDI
            CODE_GUJARATI -> LANG_GUJARATI
            else -> LANG_ENGLISH
        }
    }

    fun saveLanguage(context: Context, languageKey: String) {
        val standardKey = normalizeLanguageKey(languageKey)
        val code = when (standardKey) {
            LANG_HINDI -> CODE_HINDI
            LANG_GUJARATI -> CODE_GUJARATI
            else -> CODE_ENGLISH
        }

        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(SettingsActivity.KEY_LANGUAGE, standardKey)
            .putBoolean(KEY_LANGUAGE_PROMPTED, true)
            .commit()

        val appLocale = LocaleListCompat.forLanguageTags(code)
        AppCompatDelegate.setApplicationLocales(appLocale)

        setLocale(context, code)
    }

    fun isLanguagePrompted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LANGUAGE_PROMPTED, false)
    }

    /**
     * Displays a clean, beautifully styled language selection dialog
     */
    fun showLanguageDialog(
        activity: Activity,
        cancelable: Boolean = true,
        onSelected: ((LanguageItem) -> Unit)? = null
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val navyDark = Color.parseColor("#1B3C53")
        val navyMed = Color.parseColor("#456882")
        val lightBg = Color.parseColor("#EAF1F5")
        val cream = Color.parseColor("#F9F3EF")
        val cardBorder = Color.parseColor("#DCE7ED")

        val currentKey = getLanguageKey(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(22))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.WHITE)
            }
        }

        // Header icon & Title
        val headerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val globeText = TextView(activity).apply {
            text = "🌐"
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
        }

        val titleText = TextView(activity).apply {
            text = activity.getString(R.string.select_language)
            setTextColor(navyDark)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }

        headerLayout.addView(globeText)
        headerLayout.addView(titleText)
        root.addView(headerLayout)

        val subtitleText = TextView(activity).apply {
            text = activity.getString(R.string.choose_preferred_language)
            setTextColor(navyMed)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        root.addView(subtitleText)

        var dialog: AlertDialog? = null

        // Language items
        LANGUAGE_OPTIONS.forEach { item ->
            val isSelected = (item.key == currentKey)

            val itemCard = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }

                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    if (isSelected) {
                        setColor(Color.parseColor("#EFF6FF"))
                        setStroke(dp(2), Color.parseColor("#3B82F6"))
                    } else {
                        setColor(Color.parseColor("#F8FAFC"))
                        setStroke(dp(1), cardBorder)
                    }
                }
                isClickable = true
                isFocusable = true
            }

            val flagView = TextView(activity).apply {
                text = item.flag
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(14) }
            }

            val textContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nativeNameView = TextView(activity).apply {
                text = item.nativeName
                setTextColor(if (isSelected) Color.parseColor("#1E40AF") else navyDark)
                textSize = 15.5f
                setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            }

            val englishNameView = TextView(activity).apply {
                text = item.englishName
                setTextColor(if (isSelected) Color.parseColor("#3B82F6") else navyMed)
                textSize = 12.5f
            }

            textContainer.addView(nativeNameView)
            textContainer.addView(englishNameView)

            val checkMark = TextView(activity).apply {
                text = if (isSelected) "✓" else ""
                setTextColor(Color.parseColor("#3B82F6"))
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            }

            itemCard.addView(flagView)
            itemCard.addView(textContainer)
            itemCard.addView(checkMark)

            itemCard.setOnClickListener {
                saveLanguage(activity, item.key)
                dialog?.dismiss()
                onSelected?.invoke(item)
            }

            root.addView(itemCard)
        }

        if (cancelable) {
            val btnCancel = TextView(activity).apply {
                text = activity.getString(R.string.cancel)
                setTextColor(navyMed)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(lightBg)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { dialog?.dismiss() }
            }
            root.addView(btnCancel)
        }

        val builder = AlertDialog.Builder(activity)
            .setView(root)
            .setCancelable(cancelable)

        dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}