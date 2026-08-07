package com.example.colorpaper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.colorpaper.reminder.ReminderIntents
import com.example.colorpaper.ui.diary.DiaryDayFragmentFactory
import com.example.colorpaper.ui.diary.DiaryDetailFragment
import com.example.colorpaper.ui.diary.DiaryFragment
import com.example.colorpaper.ui.flashcard.FlashcardFragment
import com.example.colorpaper.ui.home.HomeFragment
import com.example.colorpaper.ui.login.LoginFragment
import com.example.colorpaper.ui.login.RegisterFragment
import com.example.colorpaper.ui.profile.ProfileFragment
import com.example.colorpaper.ui.setting.SettingFragment
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
        requestNotificationPermissionIfNeeded()

        // 🌟 진입 시 로그인 상태에 따른 화면 이동 처리
        if (savedInstanceState == null) {
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                // 비로그인 ➔ 로그인 화면 표시 (필요 시 RegisterFragment()로 변경 가능)
                showScreen(LoginFragment(), R.id.nav_home)
            } else {
                // 로그인 완료 ➔ 메인 홈 화면 표시
                showScreen(homeFragmentFromIntent(intent), R.id.nav_home)
            }
        } else {
            selectNavigation(currentNavigationId())
        }

        val db = FirebaseFirestore.getInstance()
        val testUser = hashMapOf("userId" to 1, "nickname" to "개발자")
        db.collection("users").add(testUser)
            .addOnSuccessListener { Log.d("FIREBASE_TEST", "성공! ID: ${it.id}") }
            .addOnFailureListener { Log.e("FIREBASE_TEST", "실패", it) }
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
            // 오늘 날짜 구하기
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

            // 오늘 날짜로 '작성 모드(isEditMode = true / 사진 2)' 화면 생성 후 이동
            showScreen(DiaryDetailFragment.newInstance(today, isEditMode = true), R.id.nav_diary)
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

        // 로그인/회원가입 화면일 때는 하단 네비게이션 바 숨김 처리
        val bottomNav = findViewById<MaterialCardView>(R.id.bottom_navigation_bar)
        if (fragment is LoginFragment || fragment is RegisterFragment) {
            bottomNav?.visibility = View.GONE
        } else {
            bottomNav?.visibility = View.VISIBLE
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        selectNavigation(selectedId)
    }

    fun openDiaryDate(dateKey: String) {
        showScreen(DiaryDayFragmentFactory.create(dateKey), R.id.nav_diary)
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
        is DiaryFragment, is DiaryDetailFragment -> R.id.nav_diary
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