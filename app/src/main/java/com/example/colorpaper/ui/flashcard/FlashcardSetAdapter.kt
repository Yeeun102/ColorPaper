package com.example.colorpaper.ui.flashcard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.databinding.ItemFlashcardSetBinding

class FlashcardSetAdapter(
    private var setList: List<FolderEntity>,
    private val onStartClick: (FolderEntity) -> Unit,
    private val onItemLongClick: (FolderEntity) -> Unit
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

        // 1. 엔티티 데이터(FolderEntity)를 XML 뷰에 바인딩
        holder.binding.tvSetTitle.text = item.folderName

        // 💡 롱클릭 이벤트 (삭제 다이얼로그 호출용)
        holder.binding.root.setOnLongClickListener {
            onItemLongClick(item)
            true
        }

        // 2. 카드 위치(position)에 따라 교차 배경색 설정
        val backgroundColorHex = if (position % 2 == 0) {
            "#E4D0D0" // 홀수 번째 카드 배경색
        } else {
            "#F5EBEB" // 짝수 번째 카드 배경색
        }
        holder.binding.root.setCardBackgroundColor(backgroundColorHex.toColorInt())

        // 3. 시작하기 버튼 클릭 이벤트 연동
        holder.binding.btnStart.setOnClickListener {
            onStartClick(item)
        }
    }

    override fun getItemCount(): Int = setList.size

    // 외부(Fragment 등)에서 파이어베이스/로컬 DB 리스트 데이터를 갱신할 때 사용
    fun updateData(newSets: List<FolderEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = setList.size
            override fun getNewListSize(): Int = newSets.size

            // 고유 ID(folderId)가 같은지 비교해서 동일 아이템 여부 판단
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return setList[oldItemPosition].folderId == newSets[newItemPosition].folderId
            }

            // 아이템 내용물과 위치에 따른 배경색 교차 패턴(Parity) 동시 비교
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val isOldEven = oldItemPosition % 2 == 0
                val isNewEven = newItemPosition % 2 == 0

                val isDataSame = setList[oldItemPosition] == newSets[newItemPosition]
                val isPositionParitySame = (isOldEven == isNewEven)

                return isDataSame && isPositionParitySame
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.setList = newSets
        diffResult.dispatchUpdatesTo(this)
    }
}