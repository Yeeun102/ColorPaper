package com.example.colorpaper.ui.flashcard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.databinding.ItemFlashcardSetBinding
import com.example.colorpaper.ui.flashcard.data.FlashcardSet
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil

class FlashcardSetAdapter(
    private var setList: List<FlashcardSet>,
    private val onStartClick: (FlashcardSet) -> Unit,
    private val onItemLongClick: (FlashcardSet) -> Unit
) : RecyclerView.Adapter<FlashcardSetAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFlashcardSetBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFlashcardSetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = setList[position]

        // 1. 엔티티 데이터(FlashcardSet)를 XML 뷰에 바인딩
        holder.binding.tvSetTitle.text = item.title

        holder.binding.root.setOnLongClickListener {
            onItemLongClick(item) // 💡 길게 누르면 롱클릭 이벤트 전달
            true // 이벤트 소비 완료를 뜻하는 true 반환
        }

        // 2. 카드 위치(position)에 따라 교차 배경색 설정
        val backgroundColorHex = if (position % 2 == 0) {
            "#E4D0D0" // 1, 3, 5번째 카드 배경색
        } else {
            "#F5EBEB" // 2, 4, 6번째 카드 배경색
        }
        holder.binding.root.setCardBackgroundColor(backgroundColorHex.toColorInt())

        // 3. 시작하기 버튼 클릭 이벤트 연동
        holder.binding.btnStart.setOnClickListener {
            onStartClick(item)
        }
    }

    override fun getItemCount(): Int = setList.size

    // 외부(Fragment 등)에서 리스트 데이터를 갱신할 때 사용하는 함수
    fun updateData(newSets: List<FlashcardSet>) {
        // 1. 계산기(Callback)를 돌려 구 리스트와 신 리스트의 차이점을 분석합니다.
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = setList.size
            override fun getNewListSize(): Int = newSets.size

            // 고유 고리 ID(setId)가 같은지 비교해서 같은 아이템인지 판단합니다.
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return setList[oldItemPosition].setId == newSets[newItemPosition].setId
            }

            // 아이템 내부 데이터(내용물)까지 완전히 똑같은지 비교합니다.
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return setList[oldItemPosition] == newSets[newItemPosition]
            }
        }

        // 2. 백그라운드나 메인에서 차이점 계산 결과를 도출합니다.
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        // 3. 내부 데이터를 새 리스트로 교체합니다.
        this.setList = newSets

        // 4. ❌ notifyDataSetChanged() 대신 계산된 최소한의 변경 사항만 리사이클러뷰에 반영합니다!
        diffResult.dispatchUpdatesTo(this)
    }
}