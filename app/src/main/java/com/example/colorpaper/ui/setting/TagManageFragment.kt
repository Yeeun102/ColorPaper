package com.example.colorpaper.ui.setting

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.databinding.FragmentTagManageBinding
import com.google.android.material.chip.Chip

class TagManageFragment : Fragment() {

    private var _binding: FragmentTagManageBinding? = null
    private val binding get() = _binding!!

    // 태그에 입힐 색상들
    private val tagColors = listOf(
        "#E6D0D0", // 핑크베이지
        "#D0E6D0", // 민트
        "#D0D0E6", // 연보라
        "#E6E6D0", // 연노랑
        "#FAD0C4"  // 피치
    )
    private var colorIndex = 0 // 색상을 순서대로 꺼내기 위한 순번

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
            Toast.makeText(requireContext(), "뒤로 가기 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 2. 태그 추가 버튼 (+) 클릭 -> 입력 팝업
        binding.ivAddTag.setOnClickListener {
            showAddTagDialog()
        }
    }

    // 팝업창을 띄워서 사용자에게 태그 이름을 입력받는 함수
    private fun showAddTagDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("새 태그 추가")

        // 팝업 안에 들어갈 입력창 생성
        val input = EditText(requireContext()).apply {
            hint = "태그 이름을 입력하세요 (예: 공부)"
            // 패딩을 살짝 줘서 예쁘게 만들기
            setPadding(48, 32, 48, 32)
        }
        builder.setView(input)

        // 추가 버튼을 눌렀을 때
        builder.setPositiveButton("추가") { _, _ ->
            val tagName = input.text.toString().trim()
            if (tagName.isNotBlank()) {
                // 사용자가 '#'을 안 붙였다면 알아서 앞에 붙여주기!
                val finalName = if (tagName.startsWith("#")) tagName else "#$tagName"
                addNewTag(finalName)
            } else {
                Toast.makeText(requireContext(), "태그 이름을 입력해주세요!", Toast.LENGTH_SHORT).show()
            }
        }

        // 취소 버튼을 눌렀을 때
        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    // 입력받은 이름으로 태그를 만들어서 색깔 입히고 화면에 붙이는 함수
    private fun addNewTag(tagName: String) {
        val chip = Chip(requireContext()).apply {
            text = tagName
            setTextColor(Color.parseColor("#333333"))
            textSize = 14spToPx() // 글자 크기
            chipStrokeWidth = 0f

            // 색상 리스트에서 순서대로 배경색 칠하기
            val colorHex = tagColors[colorIndex % tagColors.size]
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(colorHex))

            colorIndex++
        }

        // + 버튼 바로 앞에 새 태그 쏙 넣기
        val insertIndex = binding.cgTags.childCount
        binding.cgTags.addView(chip, insertIndex)
    }

    // sp 단위를 px로 변경 (에러 방지용)
    private fun Int.spToPx(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            resources.displayMetrics
        )
    }
}