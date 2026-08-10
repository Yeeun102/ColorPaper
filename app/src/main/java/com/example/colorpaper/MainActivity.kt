package com.example.colorpaper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.colorpaper.reminder.ReminderIntents
import com.example.colorpaper.ui.diary.DiaryDayFragmentFactory
import com.example.colorpaper.ui.diary.DiaryDetailFragment
import com.example.colorpaper.ui.diary.DiaryFragment
import com.example.colorpaper.ui.diary.FriendDiaryDetailFragment
import com.example.colorpaper.ui.flashcard.FlashcardFragment
import com.example.colorpaper.ui.friend.FriendListFragment
import com.example.colorpaper.ui.home.HomeFragment
import com.example.colorpaper.ui.login.LoginFragment
import com.example.colorpaper.ui.login.RegisterFragment
import com.example.colorpaper.ui.profile.ProfileFragment
import com.example.colorpaper.ui.profile.ProfileEditFragment
import com.example.colorpaper.ui.profile.ProfileSetupFragment
import com.example.colorpaper.ui.reminder.ReminderHistoryFragment
import com.example.colorpaper.ui.setting.SettingFragment
import com.example.colorpaper.ui.theme.SoftUiStyler
import com.example.colorpaper.ui.theme.ProfileThemeStyler
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var selectedNavigationId = R.id.nav_home
    private var quickActionsOpen = false

    private val navItems by lazy {
        listOf(
            NavItem(R.id.nav_diary, R.id.icon_diary, R.id.indicator_diary),
            NavItem(R.id.nav_flashcard, R.id.icon_flashcard, R.id.indicator_flashcard),
            NavItem(R.id.nav_home, R.id.icon_home, R.id.indicator_home),
            NavItem(R.id.nav_profile, R.id.icon_profile, R.id.indicator_profile),
            NavItem(R.id.nav_setting, R.id.icon_setting, R.id.indicator_setting)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fragmentManager: androidx.fragment.app.FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?
                ) {
                    SoftUiStyler.apply(view)
                    if (
                        fragment is ProfileFragment ||
                        fragment is ProfileEditFragment ||
                        fragment is ProfileSetupFragment ||
                        fragment is FriendListFragment ||
                        fragment is FriendDiaryDetailFragment
                    ) {
                        ProfileThemeStyler.applyScreen(view)
                    }
                }
            },
            true
        )
        SoftUiStyler.apply(findViewById(R.id.main))

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        applyThemeToNavigation()
        bindNavigation()
        bindBackNavigation()
        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null) {
            val currentUser = try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: SecurityException) {
                Log.e("MainActivity", "FirebaseAuth currentUser 접근 실패", e)
                null
            }

            if (currentUser == null) {
                showScreen(LoginFragment(), R.id.nav_home)
            } else {
                showScreen(homeFragmentFromIntent(intent), R.id.nav_home)
            }
        } else {
            selectNavigation(currentNavigationId())
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val diaryId = intent.getIntExtra(ReminderIntents.EXTRA_DIARY_ID, -1)
        if (diaryId > 0) showScreen(homeFragmentFromIntent(intent), R.id.nav_home)
    }

    private fun homeFragmentFromIntent(intent: Intent): HomeFragment {
        val diaryId = intent.getIntExtra(ReminderIntents.EXTRA_DIARY_ID, -1)
        val stage = intent.getIntExtra(ReminderIntents.EXTRA_STAGE, -1)
        return if (diaryId > 0 && stage >= 0) {
            HomeFragment.newInstance(diaryId, stage)
        } else {
            HomeFragment()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.nav_diary).setOnClickListener {
            hideQuickActions()
            showScreen(DiaryFragment(), R.id.nav_diary)
        }
        findViewById<View>(R.id.nav_flashcard).setOnClickListener {
            toggleQuickActions()
        }
        findViewById<View>(R.id.card_quick_actions).setOnClickListener {
            hideQuickActions()
        }
        findViewById<View>(R.id.quick_action_reminder).setOnClickListener {
            hideQuickActions()
            showScreen(ReminderHistoryFragment(), R.id.nav_flashcard)
        }
        findViewById<View>(R.id.quick_action_flashcard).setOnClickListener {
            hideQuickActions()
            showScreen(FlashcardFragment(), R.id.nav_flashcard)
        }
        bindCircularQuickActionPress(R.id.quick_action_reminder, R.id.icon_quick_reminder)
        bindCircularQuickActionPress(R.id.quick_action_flashcard, R.id.icon_quick_flashcard)
        findViewById<View>(R.id.nav_home).setOnClickListener {
            hideQuickActions()
            showScreen(HomeFragment(), R.id.nav_home)
        }
        findViewById<View>(R.id.nav_profile).setOnClickListener {
            hideQuickActions()
            showScreen(ProfileFragment(), R.id.nav_profile)
        }
        findViewById<View>(R.id.nav_setting).setOnClickListener {
            hideQuickActions()
            showScreen(SettingFragment(), R.id.nav_setting)
        }
    }

    private fun toggleQuickActions() {
        if (quickActionsOpen) hideQuickActions() else showQuickActions()
    }

    private fun showQuickActions() {
        quickActionsOpen = true
        applyThemeToNavigation()
        val menu = findViewById<MaterialCardView>(R.id.card_quick_actions)
        val launcherIcon = findViewById<ImageView>(R.id.icon_flashcard)
        menu.animate().cancel()
        launcherIcon.animate().cancel()
        menu.visibility = View.VISIBLE
        menu.alpha = 0f
        menu.scaleX = 0.92f
        menu.scaleY = 0.92f
        menu.translationY = dp(12).toFloat()
        menu.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(OvershootInterpolator(1.7f))
            .start()
        launcherIcon.scaleX = 0.82f
        launcherIcon.scaleY = 1.16f
        launcherIcon.animate().rotation(360f).scaleX(1f).scaleY(1f)
            .setDuration(280L)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun bindCircularQuickActionPress(rowId: Int, iconId: Int) {
        val row = findViewById<View>(rowId)
        val icon = findViewById<ImageView>(iconId)
        row.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val palette = ThemeManager.currentPalette(this)
                    val pressedColor = ColorUtils.setAlphaComponent(
                        ContextCompat.getColor(this, palette.accent),
                        72
                    )
                    row.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(26).toFloat()
                        setColor(pressedColor)
                    }
                    icon.animate().cancel()
                    icon.animate()
                        .scaleX(0.82f)
                        .scaleY(0.82f)
                        .setDuration(75L)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    icon.animate().cancel()
                    icon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(240L)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .withEndAction { row.background = null }
                        .start()
                }
            }
            false
        }
    }

    private fun hideQuickActions(animate: Boolean = true) {
        val menu = findViewById<MaterialCardView>(R.id.card_quick_actions)
        if (!quickActionsOpen && menu.visibility != View.VISIBLE) return
        quickActionsOpen = false
        val launcherIcon = findViewById<ImageView>(R.id.icon_flashcard)
        menu.animate().cancel()
        launcherIcon.animate().cancel()
        launcherIcon.animate().rotation(0f).scaleX(1f).scaleY(1f)
            .setDuration(if (animate) 230L else 0L)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
        if (!animate) {
            menu.visibility = View.GONE
            menu.alpha = 0f
            menu.scaleX = 0.92f
            menu.scaleY = 0.92f
            return
        }
        menu.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(dp(12).toFloat())
            .setDuration(170L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { menu.visibility = View.GONE }
            .start()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showScreen(fragment: Fragment, selectedId: Int) {
        if (quickActionsOpen) hideQuickActions()
        supportFragmentManager.popBackStackImmediate(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val bottomNav = findViewById<MaterialCardView>(R.id.bottom_navigation_bar)
        if (fragment is LoginFragment || fragment is RegisterFragment) {
            bottomNav?.visibility = View.GONE
        } else {
            bottomNav?.visibility = View.VISIBLE
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.screen_morph_enter, R.animator.screen_morph_exit)
            .replace(R.id.fragment_container, fragment)
            .commit()
        selectNavigation(selectedId)
    }

    fun openDiaryDate(dateKey: String) {
        showScreen(DiaryDayFragmentFactory.create(dateKey), R.id.nav_diary)
    }

    // 💡 하단 네비게이션 바 숨김/표시 제어용 메서드
    fun setBottomNavVisibility(isVisible: Boolean) {
        val bottomNav = findViewById<MaterialCardView>(R.id.bottom_navigation_bar)
        bottomNav?.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun refreshThemeChrome() {
        applyThemeToNavigation()
        selectNavigation(currentNavigationId())
    }

    private fun applyThemeToNavigation() {
        val palette = ThemeManager.currentPalette(this)
        val background = ContextCompat.getColor(this, palette.screenBackground)
        val navBackground = ContextCompat.getColor(this, palette.checklist)
        val outline = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(this, palette.primaryText),
            38
        )

        findViewById<View>(R.id.main).setBackgroundColor(background)
        window.navigationBarColor = background
        findViewById<MaterialCardView>(R.id.bottom_navigation_bar).apply {
            setCardBackgroundColor(navBackground)
            strokeColor = outline
            strokeWidth = dp(1)
            bringToFront()
        }
        findViewById<MaterialCardView>(R.id.card_quick_actions).apply {
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val textColor = ContextCompat.getColor(this, palette.primaryText)
        val quickActionBackground = background
        val quickActionMaxAlpha = 230
        val quickActionColors = intArrayOf(
            ColorUtils.setAlphaComponent(quickActionBackground, quickActionMaxAlpha),
            ColorUtils.setAlphaComponent(quickActionBackground, quickActionMaxAlpha * 2 / 3),
            ColorUtils.setAlphaComponent(quickActionBackground, quickActionMaxAlpha / 3),
            ColorUtils.setAlphaComponent(quickActionBackground, 0)
        )
        findViewById<View>(R.id.layout_quick_action_content).background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            quickActionColors
        )
        listOf(R.id.icon_quick_reminder, R.id.icon_quick_flashcard).forEach { iconId ->
            findViewById<ImageView>(iconId).imageTintList = ColorStateList.valueOf(textColor)
        }
        listOf(
            R.id.tv_quick_reminder,
            R.id.tv_quick_flashcard
        ).forEach { textId ->
            findViewById<android.widget.TextView>(textId).setTextColor(textColor)
        }
    }

    private fun selectNavigation(selectedId: Int) {
        selectedNavigationId = selectedId
        val palette = ThemeManager.currentPalette(this)
        val selectedColor = ContextCompat.getColor(this, palette.primaryText)
        val unselectedColor = ColorUtils.setAlphaComponent(selectedColor, 145)
        val selectionColor = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(this, palette.accent),
            38
        )

        navItems.forEach { item ->
            val selected = item.containerId == selectedId
            val color = if (selected) selectedColor else unselectedColor
            val icon = findViewById<ImageView>(item.iconId)
            val indicator = findViewById<View>(item.indicatorId)
            val wasSelected = indicator.background != null
            icon.imageTintList = ColorStateList.valueOf(color)
            findViewById<LinearLayout>(item.containerId).background = null
            icon.background = null
            indicator.animate().cancel()
            icon.animate().cancel()
            if (selected) {
                indicator.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(selectionColor)
                }
                if (!wasSelected) {
                    indicator.alpha = 0f
                    indicator.scaleX = 0.45f
                    indicator.scaleY = 0.45f
                    indicator.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300L)
                        .setInterpolator(OvershootInterpolator(2.4f))
                        .start()
                    icon.scaleX = 0.82f
                    icon.scaleY = 1.14f
                    icon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300L)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
            } else if (wasSelected) {
                indicator.animate()
                    .alpha(0f)
                    .scaleX(0.45f)
                    .scaleY(0.45f)
                    .setDuration(130L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (selectedNavigationId != item.containerId) {
                            indicator.background = null
                            indicator.alpha = 1f
                            indicator.scaleX = 1f
                            indicator.scaleY = 1f
                        }
                    }
                    .start()
            } else {
                indicator.background = null
            }
        }
    }

    private fun currentNavigationId(): Int = when (
        supportFragmentManager.findFragmentById(R.id.fragment_container)
    ) {
        is DiaryFragment, is DiaryDetailFragment -> R.id.nav_diary
        is FlashcardFragment, is ReminderHistoryFragment -> R.id.nav_flashcard
        is ProfileFragment -> R.id.nav_profile
        is SettingFragment -> R.id.nav_setting
        else -> R.id.nav_home
    }

    private fun bindBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    quickActionsOpen -> hideQuickActions()
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
        val iconId: Int,
        val indicatorId: Int
    )
}
