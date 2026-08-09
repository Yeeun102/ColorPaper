package com.example.colorpaper.ui.flashcard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.WordEntity
import com.example.colorpaper.databinding.FragmentFlashcardStudyBinding
import com.example.colorpaper.ui.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FlashcardStudyFragment : Fragment() {

    private var _binding: FragmentFlashcardStudyBinding? = null
    private val binding get() = _binding!!

    private var isFlipped = false
    private val studyQueue = mutableListOf<WordEntity>()
    private var currentCardIndex = 0

    private val failCountMap = mutableMapOf<Int, Int>()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val palette by lazy { ThemeManager.currentPalette(requireContext()) }

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

        loadCards(folderId)

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
        applyThemeColor()
    }
    private fun applyThemeColor() {
        val themeColor = ContextCompat.getColor(requireContext(), palette.yearsAgo)
        val strokeColor = ContextCompat.getColor(requireContext(), palette.stroke)
        val strokeColorStateList = ColorStateList.valueOf(strokeColor)

        binding.cardContainer.setCardBackgroundColor(themeColor)
        binding.tvFlipHint.setTextColor(strokeColor)
        binding.tvFlipHint.strokeColor = strokeColorStateList
        binding.btnNextCard.setTextColor(strokeColor)
        binding.btnNextCard.strokeColor = strokeColorStateList

        binding.btnAgain.strokeColor = strokeColorStateList
        binding.btnHard.strokeColor = strokeColorStateList
        binding.btnGood.strokeColor = strokeColorStateList
        binding.btnEasy.strokeColor = strokeColorStateList

        binding.btnExitStudy.setTextColor(strokeColor)
        binding.btnExitStudy.strokeColor = strokeColorStateList

    }

    // 🌟 로컬 DB 및 Firebase Firestore 카드 데이터 동기화 불러오기
    private fun loadCards(folderId: Int) {
        val currentUserId = auth.currentUser?.uid ?: ""

        lifecycleScope.launch {
            val safeContext = context ?: return@launch
            val db = AppDatabase.getDatabase(safeContext)

            // 1. Room 로컬 DB에서 1차 조회
            var localCards = withContext(Dispatchers.IO) {
                db.flashcardDao().getItemsBySetId(folderId.toLong())
            }

            // 2. 로컬 DB가 비어있고 로그인 유저인 경우 Firestore에서 동기화 Fetch
            if (localCards.isEmpty() && currentUserId.isNotEmpty()) {
                try {
                    val folderQuery = firestore.collection("folders")
                        .whereEqualTo("userId", currentUserId)
                        .whereEqualTo("folderId", folderId)
                        .get()
                        .await()

                    if (!folderQuery.isEmpty) {
                        val folderDoc = folderQuery.documents.first()
                        val wordsSnapshot = folderDoc.reference.collection("words").get().await()

                        val fetchedWords = wordsSnapshot.documents.mapNotNull { doc ->
                            val question = doc.getString("wordQuestion") ?: ""
                            val answer = doc.getString("wordAnswer") ?: ""
                            if (question.isNotEmpty() && answer.isNotEmpty()) {
                                WordEntity(
                                    folderId = folderId,
                                    wordQuestion = question,
                                    wordAnswer = answer,
                                    isMemorized = doc.getBoolean("isMemorized") ?: false,
                                    interval = doc.getLong("interval")?.toInt() ?: 1,
                                    easeFactor = doc.getDouble("easeFactor")?.toFloat() ?: 2.5f,
                                    repetitions = doc.getLong("repetitions")?.toInt() ?: 0,
                                    nextReviewAt = doc.getLong("nextReviewAt") ?: 0L,
                                    lastReviewedAt = doc.getLong("lastReviewedAt") ?: 0L
                                )
                            } else null
                        }

                        if (fetchedWords.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                db.flashcardDao().insertAllItems(fetchedWords)
                                localCards = db.flashcardDao().getItemsBySetId(folderId.toLong())
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            studyQueue.clear()
            studyQueue.addAll(localCards)
            currentCardIndex = 0
            showCard()
        }
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
            binding.tvFlipHint.text = getString(R.string.seeFlashcardBack)
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
        if (newEaseFactor < 1.3f) newEaseFactor = 1.3f

        // 2. 피드백 결과에 따른 Repetitions 및 Interval 계산
        val newRepetitions: Int
        val newInterval: Int

        if (quality < 3) {
            newRepetitions = 0
            newInterval = 1

            val currentFails = (failCountMap[currentCard.wordId] ?: 0) + 1
            failCountMap[currentCard.wordId] = currentFails

            if (currentFails < 3) {
                studyQueue.add(currentCard)
            }
        } else {
            newRepetitions = currentCard.repetitions + 1
            newInterval = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> (currentCard.interval * newEaseFactor).toInt().coerceAtLeast(currentCard.interval + 1)
            }
        }

        // 3. 다음 복습 타임스탬프 계산
        val newNextReviewAt = currentTime + (newInterval * oneDayInMillis)

        // 4. 엔티티 객체 업데이트
        val updatedCard = currentCard.copy(
            interval = newInterval,
            easeFactor = newEaseFactor,
            repetitions = newRepetitions,
            nextReviewAt = newNextReviewAt,
            lastReviewedAt = currentTime,
            isMemorized = newRepetitions >= 2
        )

        val currentUserId = auth.currentUser?.uid ?: ""

        // 5. 🌟 Room DB & Firebase Firestore 양쪽에 진행률 저장
        lifecycleScope.launch(Dispatchers.IO) {
            val safeContext = context ?: return@launch
            val dao = AppDatabase.getDatabase(safeContext).flashcardDao()
            dao.updateWord(updatedCard)

            // Firestore 문서 업데이트
            if (currentUserId.isNotEmpty()) {
                try {
                    val folderQuery = firestore.collection("folders")
                        .whereEqualTo("userId", currentUserId)
                        .whereEqualTo("folderId", updatedCard.folderId)
                        .get()
                        .await()

                    if (!folderQuery.isEmpty) {
                        val folderDoc = folderQuery.documents.first()
                        val wordDocs = folderDoc.reference.collection("words")
                            .whereEqualTo("wordQuestion", updatedCard.wordQuestion)
                            .get()
                            .await()

                        for (wordDoc in wordDocs.documents) {
                            wordDoc.reference.update(
                                mapOf(
                                    "interval" to updatedCard.interval,
                                    "easeFactor" to updatedCard.easeFactor,
                                    "repetitions" to updatedCard.repetitions,
                                    "nextReviewAt" to updatedCard.nextReviewAt,
                                    "lastReviewedAt" to updatedCard.lastReviewedAt,
                                    "isMemorized" to updatedCard.isMemorized
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
