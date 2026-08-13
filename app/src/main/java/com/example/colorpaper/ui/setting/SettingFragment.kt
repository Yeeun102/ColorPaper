package com.example.colorpaper.ui.setting

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.example.colorpaper.MainActivity
import com.example.colorpaper.R
import com.example.colorpaper.ui.profile.ProfileEditFragment
import com.example.colorpaper.ui.setting.TagManageFragment
import com.example.colorpaper.ui.theme.AppTheme
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.card.MaterialCardView

class SettingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.iv_back_setting).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.card_theme_rose).setOnClickListener {
            selectTheme(AppTheme.ROSE)
        }
        view.findViewById<View>(R.id.card_theme_sage).setOnClickListener {
            selectTheme(AppTheme.SAGE)
        }
        view.findViewById<View>(R.id.card_theme_sky).setOnClickListener {
            selectTheme(AppTheme.SKY)
        }
        view.findViewById<View>(R.id.ll_repeat_cycle).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RepeatCycleFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.ll_personal_info)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PersonalInfoFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.ll_tag_manage)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TagManageFragment())
                .addToBackStack(null)
                .commit()
        }

        applyTheme(view)
    }

    private fun selectTheme(theme: AppTheme) {
        if (ThemeManager.currentTheme(requireContext()) != theme) {
            ThemeManager.setTheme(requireContext(), theme)
        }
        applyTheme(requireView())
        (activity as? MainActivity)?.refreshThemeChrome()
    }

    private fun applyTheme(root: View) {
        val palette = ThemeManager.currentPalette(requireContext())
        val background = color(palette.screenBackground)
        val text = color(palette.primaryText)
        val surface = color(palette.todo)
        val selectedSurface = color(palette.checklist)
        val outline = ColorUtils.setAlphaComponent(text, 38)

        root.setBackgroundColor(background)
        tintTextRecursively(root, text)
        root.findViewById<ImageView>(R.id.iv_back_setting).imageTintList =
            ColorStateList.valueOf(text)

        listOf(
            root.findViewById<MaterialCardView>(R.id.cv_setting_group),
            root.findViewById<MaterialCardView>(R.id.cv_decorate_group)
        ).forEach { card ->
            card.setCardBackgroundColor(surface)
            card.strokeColor = outline
            card.strokeWidth = dp(1)
        }
        root.findViewById<MaterialCardView>(R.id.cv_premium)
            .setCardBackgroundColor(color(palette.yearsAgo))

        val currentTheme = ThemeManager.currentTheme(requireContext())
        styleThemeOption(
            root.findViewById(R.id.card_theme_rose),
            root.findViewById(R.id.tv_rose_selected),
            currentTheme == AppTheme.ROSE,
            surface,
            selectedSurface,
            outline
        )
        styleThemeOption(
            root.findViewById(R.id.card_theme_sage),
            root.findViewById(R.id.tv_sage_selected),
            currentTheme == AppTheme.SAGE,
            surface,
            selectedSurface,
            outline
        )
        styleThemeOption(
            root.findViewById(R.id.card_theme_sky),
            root.findViewById(R.id.tv_sky_selected),
            currentTheme == AppTheme.SKY,
            surface,
            selectedSurface,
            outline
        )

        val secondaryText = ColorUtils.setAlphaComponent(text, 170)
        root.findViewById<TextView>(R.id.tv_setting_subtitle).setTextColor(secondaryText)
        root.findViewById<TextView>(R.id.tv_decorate_subtitle).setTextColor(secondaryText)
        root.findViewById<TextView>(R.id.tv_theme_description).setTextColor(secondaryText)
    }

    private fun styleThemeOption(
        card: MaterialCardView,
        selectedLabel: TextView,
        selected: Boolean,
        surface: Int,
        selectedSurface: Int,
        outline: Int
    ) {
        card.setCardBackgroundColor(if (selected) selectedSurface else surface)
        card.strokeColor = outline
        card.strokeWidth = dp(1)
        selectedLabel.visibility = if (selected) View.VISIBLE else View.GONE
    }

    private fun tintTextRecursively(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintTextRecursively(view.getChildAt(index), color)
            }
        }
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(requireContext(), colorRes)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
