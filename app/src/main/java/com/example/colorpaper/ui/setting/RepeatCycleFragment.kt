// package com.example.colorpaper.ui.setting (맨 윗줄은 네 패키지명 그대로!)

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.colorpaper.databinding.FragmentRepeatCycleBinding

class RepeatCycleFragment : Fragment() {

    private var _binding: FragmentRepeatCycleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatCycleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 뒤로가기 버튼
        binding.ivBackRepeatCycle.setOnClickListener {
            // TODO: 설정 메인 화면으로 돌아가기
            Toast.makeText(requireContext(), "뒤로 가기 (임시)", Toast.LENGTH_SHORT).show()
        }

        // 2. 사용자 지정 추가 버튼 (+)
        binding.ivAddCustomCycle.setOnClickListener {
            // TODO: 8.2.1 반복주기 편집 화면으로 넘어가거나 아이템 추가 로직
            Toast.makeText(requireContext(), "새로운 주기 추가! (임시)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}