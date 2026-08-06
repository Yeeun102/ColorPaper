package com.example.colorpaper.ui.flashcard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentFlashcardStudyBinding
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashcardStudyFragment : Fragment() {

    private var _binding: FragmentFlashcardStudyBinding? = null
    private val binding get() = _binding!!

    private var isFlipped = false
    private val studyQueue = mutableListOf<WordEntity>()
    private var currentCardIndex = 0

    private val failCountMap = mutableMapOf<Int, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardStudyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val folderId = arguments?.getInt("SET_ID", -1) ?: -1
        val setTitle = arguments?.getString("SET_TITLE") ?: "#알 수 없음"
        binding.tvSetTitle.text = setTitle

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            val initialCards = withContext(Dispatchers.IO) {
                db.flashcardDao().getItemsBySetId(folderId.toLong())
            }

            studyQueue.clear()
            studyQueue.addAll(initialCards)
            currentCardIndex = 0
            showCard()
        }

        binding.cardContainer.setOnClickListener { toggleFlip() }
        binding.tvFlipHint.setOnClickListener { toggleFlip() }

        binding.btnExitStudy.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnNextCard.setOnClickListener {
            currentCardIndex++
            showCard()
        }

        setupEmojiClickListeners()
    }

    private fun toggleFlip() {
        if (studyQueue.isNotEmpty() && currentCardIndex < studyQueue.size) {
            isFlipped = !isFlipped
            updateCardUI()
        }
    }

    private fun showCard() {
        if (studyQueue.isNotEmpty() && currentCardIndex < studyQueue.size) {
            isFlipped = false // 새 카드는 항상 앞면부터
            updateCardUI()
        } else {
            binding.tvCardContent.text = "🎉 모든 카드를 학습했습니다!"
            binding.tvFlipHint.text = "뒤로가기 또는 종료 버튼을 눌러주세요."
            binding.layoutEmojiButtons.visibility = View.GONE
            binding.btnNextCard.visibility = View.GONE
        }
    }
    private fun updateCardUI() {
        if (studyQueue.isEmpty() || currentCardIndex >= studyQueue.size) return

        val currentCard = studyQueue[currentCardIndex]

        if (isFlipped) {
            binding.tvCardContent.text = currentCard.wordAnswer
            binding.tvFlipHint.text = "정답 확인 완료"
            binding.layoutEmojiButtons.visibility = View.VISIBLE
        } else {
            binding.tvCardContent.text = currentCard.wordQuestion
            binding.tvFlipHint.text = getString(R.string.seeFlashcardBack) // 앞면일 땐 원상복구
            binding.layoutEmojiButtons.visibility = View.GONE
        }
    }

    private fun setupEmojiClickListeners() {
        binding.btnAgain.setOnClickListener {
            handleCardFeedback(quality = 0)
        }

        binding.btnHard.setOnClickListener {
            handleCardFeedback(quality = 2)
        }

        binding.btnGood.setOnClickListener {
            handleCardFeedback(quality = 4)
        }

        binding.btnEasy.setOnClickListener {
            handleCardFeedback(quality = 5)
        }

        binding.btnNextCard.setOnClickListener {
            handleCardFeedback(quality = 5)
        }
    }

    /**
     * Anki 피드백 반응(0~5점)에 따라 카드의 주기(Interval)와 난이도 계수(Ease Factor)를 계산하는 함수
     */
    private fun handleCardFeedback(quality: Int) {
        if (studyQueue.isEmpty() || currentCardIndex >= studyQueue.size) return

        val currentCard = studyQueue[currentCardIndex]
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        // 1. 새로운 Ease Factor(난이도 계수) 계산
        var newEaseFactor = currentCard.easeFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        if (newEaseFactor < 1.3f) newEaseFactor = 1.3f // SM-2 최소 하한선

        // 2. 피드백 결과에 따른 Repetitions(연속 성공 횟수) 및 Interval(복습 주기 일수) 계산
        val newRepetitions: Int
        val newInterval: Int

        if (quality < 3) {
            // [Again / Hard] 틀렸거나 어려웠던 경우 -> 연속 성공 초기화 및 1일 뒤 재복습
            newRepetitions = 0
            newInterval = 1

            val currentFails = (failCountMap[currentCard.wordId] ?: 0) + 1
            failCountMap[currentCard.wordId] = currentFails

            // 💡 최대 3번까지만 세션 뒤로 재배치 (3번 넘게 틀리면 오늘 세션에서는 일단 제외)
            if (currentFails < 3) {
                studyQueue.add(currentCard)
            }
        } else {
            // [Good / Easy] 맞춘 경우
            newRepetitions = currentCard.repetitions + 1
            newInterval = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> (currentCard.interval * newEaseFactor).toInt().coerceAtLeast(currentCard.interval + 1)
            }
        }

        // 3. 다음 복습 타임스탬프 계산 (현재 시간 + n일)
        val newNextReviewAt = currentTime + (newInterval * oneDayInMillis)

        // 4. 엔티티 객체 업데이트
        val updatedCard = currentCard.copy(
            interval = newInterval,
            easeFactor = newEaseFactor,
            repetitions = newRepetitions,
            nextReviewAt = newNextReviewAt,
            isMemorized = newRepetitions >= 2 // 2회 연속 성공 시 암기 완료 처리
        )

        // 5. 💡 [핵심] Room DB에 실제 업데이트 수행
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(requireContext()).flashcardDao()
            dao.updateWord(updatedCard)
        }

        // 6. 다음 카드로 이동
        currentCardIndex++
        showCard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}