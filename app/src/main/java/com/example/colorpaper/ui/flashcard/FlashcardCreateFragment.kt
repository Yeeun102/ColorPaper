package com.example.colorpaper.ui.flashcard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorpaper.R
import com.example.colorpaper.data.local.AppDatabase
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.data.model.WordEntity
import com.example.colorpaper.databinding.FragmentFlashcardCreateBinding
import com.example.colorpaper.util.AuthUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FlashcardCreateFragment : Fragment() {

    private var _binding: FragmentFlashcardCreateBinding? = null
    private val binding get() = _binding!!

    private val cardViewsList = mutableListOf<View>()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                readCsvFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addCardField()

        binding.btnNextCard.setOnClickListener {
            if (canAddNewCard()) {
                addCardField()
            } else {
                Toast.makeText(requireContext(), "현재 카드의 질문과 정답을 먼저 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnImportCsv.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }

        binding.btnSave.setOnClickListener {
            saveFlashcardSet()
        }
    }

    private fun canAddNewCard(): Boolean {
        if (cardViewsList.isEmpty()) return true

        val lastCardView = cardViewsList.last()
        val question = lastCardView.findViewById<EditText>(R.id.etQuestion).text.toString().trim()
        val answer = lastCardView.findViewById<EditText>(R.id.etAnswer).text.toString().trim()

        return question.isNotBlank() && answer.isNotBlank()
    }

    private fun addCardField(initialQuestion: String = "", initialAnswer: String = "") {
        val inflater = LayoutInflater.from(requireContext())
        val cardView = inflater.inflate(R.layout.item_create_card_field, binding.layoutCardContainer, false)

        cardViewsList.add(cardView)

        val tvCardNumber = cardView.findViewById<TextView>(R.id.tvCardNumber)
        tvCardNumber.text = getString(R.string.card_number_format, cardViewsList.size)

        if (initialQuestion.isNotEmpty()) cardView.findViewById<EditText>(R.id.etQuestion).setText(initialQuestion)
        if (initialAnswer.isNotEmpty()) cardView.findViewById<EditText>(R.id.etAnswer).setText(initialAnswer)

        binding.layoutCardContainer.addView(cardView)
    }

    private fun readCsvFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            binding.layoutCardContainer.removeAllViews()
            cardViewsList.clear()

            while (reader.readLine().also { line = it } != null) {
                val tokens = line?.split(",")
                if (tokens != null && tokens.size >= 2) {
                    val question = tokens[0].trim()
                    val answer = tokens[1].trim()
                    addCardField(question, answer)
                }
            }
            reader.close()
            Toast.makeText(requireContext(), "CSV 데이터를 성공적으로 불러왔습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "파일을 파싱하는 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFlashcardSet() {
        val setTitle = binding.etSetTitle.text.toString().trim()
        if (setTitle.isEmpty()) {
            Toast.makeText(requireContext(), "단어장 제목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 입력된 단어 검증
        val validWords = mutableListOf<Pair<String, String>>()
        for (view in cardViewsList) {
            val question = view.findViewById<EditText>(R.id.etQuestion).text.toString().trim()
            val answer = view.findViewById<EditText>(R.id.etAnswer).text.toString().trim()

            if (question.isNotEmpty() && answer.isNotEmpty()) {
                validWords.add(Pair(question, answer))
            }
        }

        if (validWords.isEmpty()) {
            Toast.makeText(requireContext(), "최소 1개 이상의 카드에 질문과 정답을 입력해야 저장할 수 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedChipId = binding.chipGroupVisibility.checkedChipId
        val visibility = binding.chipGroupVisibility.findViewById<com.google.android.material.chip.Chip>(selectedChipId)?.text.toString()

        val currentUid = auth.currentUser?.uid ?: AuthUtils.getCurrentUserId()
        val formattedTitle = if (setTitle.startsWith("#")) setTitle else "#$setTitle"

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).flashcardDao()

            withContext(Dispatchers.IO) {
                // 1. Room Local DB에 저장
                val newFolder = FolderEntity(
                    userId = currentUid,
                    folderName = formattedTitle,
                    visibility = visibility
                )
                val generatedFolderId = dao.insertFolder(newFolder).toInt()

                val itemsToInsert = validWords.map { (q, a) ->
                    WordEntity(
                        folderId = generatedFolderId,
                        wordQuestion = q,
                        wordAnswer = a
                    )
                }
                dao.insertAllItems(itemsToInsert)

                // 2. Firebase Firestore에 저장 및 동기화
                if (currentUid.isNotEmpty()) {
                    try {
                        val folderDocRef = firestore.collection("folders").document()

                        val folderMap = hashMapOf(
                            "documentId" to folderDocRef.id,
                            "folderId" to generatedFolderId,
                            "userId" to currentUid,
                            "folderName" to formattedTitle,
                            "visibility" to visibility,
                            "isAutoGenerated" to false,
                            "wordCount" to validWords.size,
                            "createdAt" to System.currentTimeMillis()
                        )
                        folderDocRef.set(folderMap).await()

                        // 하위 단어 카드들 저장
                        val wordsBatch = firestore.batch()
                        validWords.forEach { (q, a) ->
                            val wordDocRef = folderDocRef.collection("words").document()
                            val wordMap = hashMapOf(
                                "folderId" to generatedFolderId,
                                "wordQuestion" to q,
                                "wordAnswer" to a,
                                "isMemorized" to false
                            )
                            wordsBatch.set(wordDocRef, wordMap)
                        }
                        wordsBatch.commit().await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Toast.makeText(requireContext(), "$formattedTitle 저장 완료!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}