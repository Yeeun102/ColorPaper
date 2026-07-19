package com.example.colorpaper.ui.flashcard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.databinding.FragmentFlashcardStudyBinding
import com.example.colorpaper.ui.flashcard.data.FlashcardItem
import com.example.colorpaper.ui.flashcard.data.FlashcardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashcardStudyFragment : Fragment() {

    private var _binding: FragmentFlashcardStudyBinding? = null
    private val binding get() = _binding!!

    private var isFlipped = false

    private var cardList: List<FlashcardItem> = emptyList()
    private var currentCardIndex = 0

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

        val setId = arguments?.getLong("SET_ID", -1) ?: -1
        val setTitle = arguments?.getString("SET_TITLE") ?: "#알 수 없음"
        binding.tvSetTitle.text = setTitle

        lifecycleScope.launch {
            val dao = FlashcardDatabase.getDatabase(requireContext()).flashcardDao()
            cardList = withContext(Dispatchers.IO) {
                dao.getItemsBySetId(setId)
            }

            currentCardIndex = 0
            showCard()
        }

        binding.cardContainer.setOnClickListener { toggleFlip() }
        binding.tvFlipHint.setOnClickListener { toggleFlip() }

        binding.btnNextCard.setOnClickListener {
            currentCardIndex++
            showCard()
        }

        setupEmojiClickListeners()
    }

    private fun toggleFlip() {
        if (cardList.isNotEmpty() && currentCardIndex < cardList.size) {
            isFlipped = !isFlipped
            updateCardUI()
        }
    }

    private fun showCard() {
        if (cardList.isNotEmpty() && currentCardIndex < cardList.size) {
            isFlipped = false // 새 카드는 항상 앞면부터
            updateCardUI()
        } else {
            binding.tvCardContent.text = "🎉 모든 카드를 학습했습니다!"
            binding.tvFlipHint.text = "목록으로 돌아가려면 뒤로가기를 눌러주세요."
            binding.layoutEmojiButtons.visibility = View.GONE
            binding.btnNextCard.visibility = View.GONE
        }
    }
    private fun updateCardUI() {
        if (cardList.isEmpty() || currentCardIndex >= cardList.size) return

        val currentCard = cardList[currentCardIndex]

        if (isFlipped) {
            binding.tvCardContent.text = currentCard.answer
            binding.tvFlipHint.text = "정답 확인 완료"
            binding.layoutEmojiButtons.visibility = View.VISIBLE
        } else {
            binding.tvCardContent.text = currentCard.question
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
        if (cardList.isEmpty() || currentCardIndex >= cardList.size) return

        val currentCard = cardList[currentCardIndex]

        // 1. 새로운 Ease Factor(난이도 계수) 계산 (Anki SM-2 공식 응용)
        // 2.5f 근처를 유지하되, 어려우면 낮아지고(자주 나옴) 쉬우면 높아짐(가끔 나옴)
        var newEaseFactor = currentCard.easeFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        if (newEaseFactor < 1.3f) newEaseFactor = 1.3f // 최소값 방어선 보장

        // 2. 피드백 점수별 다음 복습 주기(일 단위) 계산
        val nextInterval: Int = when (quality) {
            0 -> 1
            2 -> 2
            4 -> 3
            5 -> 7
            else -> 1
        }

        println("카드 [${currentCard.question}] 변경 사항 -> 다음 주기: ${nextInterval}일 뒤, 난이도 계수: $newEaseFactor")

        currentCardIndex++
        showCard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}