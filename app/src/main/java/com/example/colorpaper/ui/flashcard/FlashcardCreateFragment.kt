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
import com.example.colorpaper.databinding.FragmentFlashcardCreateBinding
import com.example.colorpaper.ui.flashcard.data.FlashcardDatabase
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashcardCreateFragment : Fragment() {

    private var _binding: FragmentFlashcardCreateBinding? = null
    private val binding get() = _binding!!

    private val cardViewsList = mutableListOf<View>()

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
        addCardField()

        binding.btnNextCard.setOnClickListener {
            addCardField()
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

        val selectedChipId = binding.chipGroupVisibility.checkedChipId
        val visibility = binding.chipGroupVisibility.findViewById<com.google.android.material.chip.Chip>(selectedChipId)?.text.toString()

        lifecycleScope.launch {
            val dao = FlashcardDatabase.getDatabase(requireContext()).flashcardDao()

            withContext(Dispatchers.IO) {
                val newSet = com.example.colorpaper.ui.flashcard.data.FlashcardSet(
                    title = if (setTitle.startsWith("#")) setTitle else "#$setTitle",
                    visibility = visibility
                )
                val generatedSetId = dao.insertSet(newSet)

                val itemsToInsert = mutableListOf<com.example.colorpaper.ui.flashcard.data.FlashcardItem>()
                for (view in cardViewsList) {
                    val question = view.findViewById<EditText>(R.id.etQuestion).text.toString().trim()
                    val answer = view.findViewById<EditText>(R.id.etAnswer).text.toString().trim()

                    if (question.isNotEmpty() && answer.isNotEmpty()) {
                        itemsToInsert.add(
                            com.example.colorpaper.ui.flashcard.data.FlashcardItem(
                                setId = generatedSetId,
                                question = question,
                                answer = answer
                            )
                        )
                    }
                }

                if (itemsToInsert.isNotEmpty()) {
                    dao.insertAllItems(itemsToInsert)
                }
            }

            Toast.makeText(requireContext(), "$setTitle 저장 완료!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}