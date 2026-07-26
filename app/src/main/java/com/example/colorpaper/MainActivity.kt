package com.example.colorpaper

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.colorpaper.ui.diary.DiaryFragment
import com.example.colorpaper.ui.flashcard.FlashcardFragment
import com.example.colorpaper.ui.home.HomeFragment
import com.example.colorpaper.ui.profile.ProfileFragment
import com.example.colorpaper.ui.setting.SettingFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private var selectedNavigationId = R.id.nav_home

    private val navItems by lazy {
        listOf(
            NavItem(R.id.nav_diary, R.id.icon_diary),
            NavItem(R.id.nav_flashcard, R.id.icon_flashcard),
            NavItem(R.id.nav_home, R.id.icon_home),
            NavItem(R.id.nav_profile, R.id.icon_profile),
            NavItem(R.id.nav_setting, R.id.icon_setting)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        applyThemeToNavigation()
        bindNavigation()
        bindBackNavigation()

        if (savedInstanceState == null) {
            showScreen(HomeFragment(), R.id.nav_home)
        } else {
            selectNavigation(currentNavigationId())
        }
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.nav_diary).setOnClickListener {
            showScreen(DiaryFragment(), R.id.nav_diary)
        }
        findViewById<View>(R.id.nav_flashcard).setOnClickListener {
            showScreen(FlashcardFragment(), R.id.nav_flashcard)
        }
        findViewById<View>(R.id.nav_home).setOnClickListener {
            showScreen(HomeFragment(), R.id.nav_home)
        }
        findViewById<View>(R.id.nav_profile).setOnClickListener {
            showScreen(ProfileFragment(), R.id.nav_profile)
        }
        findViewById<View>(R.id.nav_setting).setOnClickListener {
            showScreen(SettingFragment(), R.id.nav_setting)
        }
    }

    private fun showScreen(fragment: Fragment, selectedId: Int) {
        supportFragmentManager.popBackStackImmediate(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        selectNavigation(selectedId)
    }

    private fun applyThemeToNavigation() {
        val palette = ThemeManager.currentPalette(this)
        val background = ContextCompat.getColor(this, palette.screenBackground)
        val navBackground = ContextCompat.getColor(this, palette.checklist)
        val stroke = ContextCompat.getColor(this, palette.stroke)

        findViewById<View>(R.id.main).setBackgroundColor(background)
        findViewById<MaterialCardView>(R.id.bottom_navigation_bar).apply {
            setCardBackgroundColor(navBackground)
            strokeColor = stroke
        }
    }

    private fun selectNavigation(selectedId: Int) {
        selectedNavigationId = selectedId
        val palette = ThemeManager.currentPalette(this)
        val selectedColor = ContextCompat.getColor(this, palette.stroke)
        val unselectedColor = ColorUtils.setAlphaComponent(selectedColor, 145)
        val selectionColor = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(this, palette.accent),
            38
        )

        navItems.forEach { item ->
            val selected = item.containerId == selectedId
            val color = if (selected) selectedColor else unselectedColor
            findViewById<ImageView>(item.iconId).imageTintList = ColorStateList.valueOf(color)
            findViewById<LinearLayout>(item.containerId).background = if (selected) {
                GradientDrawable().apply {
                    setColor(selectionColor)
                    cornerRadius = 18f * resources.displayMetrics.density
                }
            } else null
        }
    }

    private fun currentNavigationId(): Int = when (
        supportFragmentManager.findFragmentById(R.id.fragment_container)
    ) {
        is DiaryFragment -> R.id.nav_diary
        is FlashcardFragment -> R.id.nav_flashcard
        is ProfileFragment -> R.id.nav_profile
        is SettingFragment -> R.id.nav_setting
        else -> R.id.nav_home
    }

    private fun bindBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    supportFragmentManager.backStackEntryCount > 0 -> {
                        supportFragmentManager.popBackStack()
                    }
                    selectedNavigationId != R.id.nav_home -> {
                        showScreen(HomeFragment(), R.id.nav_home)
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private data class NavItem(
        val containerId: Int,
        val iconId: Int
    )
}
