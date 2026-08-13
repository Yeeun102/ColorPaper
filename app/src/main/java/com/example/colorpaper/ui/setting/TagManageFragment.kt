package com.example.colorpaper.ui.setting

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.colorpaper.databinding.FragmentTagManageBinding
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.android.material.chip.Chip

class TagManageFragment : Fragment() {

    private var _binding: FragmentTagManageBinding? = null
    private val binding get() = _binding!!

    private var colorIndex = 0

    companion object {
        private const val PREF_NAME = "tag_prefs"
        private const val KEY_TAGS = "saved_tags"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 뒤로가기 버튼
        binding.ivBackTag.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 2. 태그 추가 버튼 (+) 클릭 -> 입력 팝업
        binding.ivAddTag.setOnClickListener {
            showAddTagDialog()
        }

        // 3. 테마 색상 적용
        applyTheme(view)

        // 4. 저장된 태그 불러오기
        loadSavedTags()
    }

    private fun applyTheme(root: View) {
        val palette = ThemeManager.currentPalette(requireContext())
        val background = ContextCompat.getColor(requireContext(), palette.screenBackground)
        val text = ContextCompat.getColor(requireContext(), palette.primaryText)

        root.setBackgroundColor(background)
        binding.ivBackTag.imageTintList = ColorStateList.valueOf(text)
        binding.ivAddTag.imageTintList = ColorStateList.valueOf(text)

        tintTextRecursively(root, text)
    }

    private fun tintTextRecursively(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintTextRecursively(view.getChildAt(index), color)
            }
        }
    }

    // 팝업창을 띄워서 사용자에게 태그 이름을 입력받는 함수
    private fun showAddTagDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("새 태그 추가")

        val input = EditText(requireContext()).apply {
            hint = "태그 이름을 입력하세요 (예: 공부)"
            setPadding(48, 32, 48, 32)
        }
        builder.setView(input)

        builder.setPositiveButton("추가") { _, _ ->
            val tagName = input.text.toString().trim()
            if (tagName.isNotBlank()) {
                val finalName = if (tagName.startsWith("#")) tagName else "#$tagName"
                addNewTag(finalName, saveToPrefs = true)
            } else {
                Toast.makeText(requireContext(), "태그 이름을 입력해주세요!", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    // 태그를 화면에 붙이는 함수 (saveToPrefs가 true면 기기에 저장)
    private fun addNewTag(tagName: String, saveToPrefs: Boolean) {
        val palette = ThemeManager.currentPalette(requireContext())

        val themeTagColors = listOf(
            palette.screenBackground,
            palette.todo,
            palette.accent
        )

        val chip = Chip(requireContext()).apply {
            text = tagName
            setTextColor(ContextCompat.getColor(requireContext(), palette.primaryText))
            textSize = 14.spToPx()
            chipStrokeWidth = 0f

            val colorRes = themeTagColors[colorIndex % themeTagColors.size]
            chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
            colorIndex++

            // 칩을 길게 누르면 삭제되는 기능 추가! (디테일 챙기기)
            setOnLongClickListener {
                binding.cgTags.removeView(this)
                saveAllTagsToPrefs()
                Toast.makeText(requireContext(), "태그가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                true
            }
        }

        binding.cgTags.addView(chip)

        if (saveToPrefs) {
            saveAllTagsToPrefs()
            Toast.makeText(requireContext(), "태그가 저장되었습니다!", Toast.LENGTH_SHORT).show()
        }
    }

    // 현재 화면에 있는 모든 태그들을 SharedPreferences에 저장
    private fun saveAllTagsToPrefs() {
        val sharedPrefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val tagSet = mutableSetOf<String>()

        for (i in 0 until binding.cgTags.childCount) {
            val chip = binding.cgTags.getChildAt(i) as? Chip
            chip?.text?.toString()?.let { tagSet.add(it) }
        }

        sharedPrefs.edit().putStringSet(KEY_TAGS, tagSet).apply()
    }

    // 기기에 저장된 태그들을 불러와서 화면에 칩으로 생성
    private fun loadSavedTags() {
        val sharedPrefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val tagSet = sharedPrefs.getStringSet(KEY_TAGS, emptySet()) ?: emptySet()

        binding.cgTags.removeAllViews()
        colorIndex = 0

        for (tagName in tagSet) {
            addNewTag(tagName, saveToPrefs = false)
        }
    }

    private fun Int.spToPx(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            resources.displayMetrics
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}