package com.example.colorpaper.ui.setting

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.example.colorpaper.databinding.FragmentTagManageBinding

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

        // 2. 태그 추가 버튼 (+) 클릭
        binding.ivAddTag.setOnClickListener {
            // TODO: 원래는 팝업을 띄워서 이름을 입력받아야 하지만, 지금은 테스트용으로 임시 태그 생성!
            val newTagName = "#새태그${(1..100).random()}"
            addNewTag(newTagName)
        }
    }

    // 새로운 태그를 만들어서 색깔 입히고 화면에 붙이는 함수
    private fun addNewTag(tagName: String) {
        // 새로운 태그 만들기
        val chip = Chip(requireContext()).apply {
            text = tagName
            setTextColor(Color.parseColor("#333333")) // 글자색
            chipStrokeWidth = 0f // 테두리 없애기

            // 색상 리스트에서 하나씩 꺼내서 배경색으로 칠하기
            val colorHex = tagColors[colorIndex % tagColors.size]
            chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(colorHex))

            // 다음 태그를 위해 순번 1 증가
            colorIndex++
        }

        val insertIndex = binding.cgTags.childCount - 1
        binding.cgTags.addView(chip, insertIndex)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}