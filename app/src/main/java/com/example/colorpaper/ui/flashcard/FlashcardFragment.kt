package com.example.colorpaper.ui.flashcard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentFlashcardBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.FlashcardSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FlashcardFragment : Fragment() {
    private var _binding: FragmentFlashcardBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FlashcardSetAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FlashcardSetAdapter(
            emptyList(),
            onStartClick = { selectedSet ->
                // 기존 클릭 시 학습 화면 이동 로직
                val bundle = Bundle().apply {
                    putLong("SET_ID", selectedSet.setId)
                    putString("SET_TITLE", selectedSet.title)
                }
                val studyFragment = FlashcardStudyFragment().apply { arguments = bundle }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.main, studyFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onItemLongClick = { selectedSet ->
                // 길게 눌렀을 때 삭제 확인 다이얼로그 띄우기
                showDeleteDialog(selectedSet)
            }
        )

        // 2. 리사이클러뷰에 어댑터와 '레이아웃 매니저'를 유기적으로 함께 결합
        binding.rvFlashcardSets.apply {
            this.adapter = this@FlashcardFragment.adapter
            this.layoutManager = LinearLayoutManager(requireContext())

            this.setHasFixedSize(true)
        }

        binding.btnCreateSet.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, FlashcardCreateFragment())
                .addToBackStack(null)
                .commit()
        }

    }

    override fun onResume() {
        super.onResume()
        loadFlashcardSets()
    }

    private fun loadFlashcardSets() {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).flashcardDao()
            val sets = withContext(Dispatchers.IO) {
                dao.getAllSets()
            }

            withContext(Dispatchers.Main) {
                adapter.updateData(sets)
            }
            adapter.updateData(sets)
        }
    }

private fun showDeleteDialog(flashcardSet: FlashcardSet) {
    android.app.AlertDialog.Builder(requireContext())
        .setTitle("단어장 삭제")
        .setMessage("${flashcardSet.title} 단어장을 정말 삭제하시겠습니까?\n내부 카드들도 함께 삭제됩니다.")
        .setPositiveButton("삭제") { _, _ ->

            lifecycleScope.launch {
                val dao = AppDatabase.getDatabase(requireContext()).flashcardDao()

                withContext(Dispatchers.IO) {
                    dao.deleteSet(flashcardSet)
                }

                Toast.makeText(requireContext(), "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                loadFlashcardSets()
            }
        }
        .setNegativeButton("취소", null)
        .show()
}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}