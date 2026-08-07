package com.example.colorpaper.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.google.android.material.card.MaterialCardView

data class HighlightItem(
    val id: String,
    val date: String, // 예: "08/07" 또는 "08.07"
    val imageUrl: String? = null
)

class HighlightAdapter(
    private val items: List<HighlightItem>,
    private val onItemClick: (HighlightItem) -> Unit,
    private val onMoreClick: () -> Unit
) : RecyclerView.Adapter<HighlightAdapter.HighlightViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_MORE = 1
        private const val MAX_SHOW_WITHOUT_MORE = 5 // 5개 이하는 더보기 버튼 없음
    }

    private val showMoreButton: Boolean get() = items.size > MAX_SHOW_WITHOUT_MORE

    override fun getItemViewType(position: Int): Int {
        return if (showMoreButton && position == MAX_SHOW_WITHOUT_MORE) TYPE_MORE else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HighlightViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_highlight, parent, false)
        return HighlightViewHolder(view)
    }

    override fun onBindViewHolder(holder: HighlightViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_MORE) {
            holder.tvMoreText.visibility = View.VISIBLE
            holder.tvHighlightDate.visibility = View.GONE
            holder.ivHighlightImage.visibility = View.GONE
            holder.cardView.setCardBackgroundColor(0xFFF5E6E6.toInt())
            holder.itemView.setOnClickListener { onMoreClick() }
        } else {
            val item = items[position]
            holder.tvMoreText.visibility = View.GONE
            holder.cardView.setCardBackgroundColor(0xFFE8D3D3.toInt())

            // 🌟 썸네일 날짜 바인딩
            holder.tvHighlightDate.visibility = View.VISIBLE
            holder.tvHighlightDate.text = item.date

            if (!item.imageUrl.isNullOrEmpty()) {
                holder.ivHighlightImage.visibility = View.VISIBLE
            } else {
                holder.ivHighlightImage.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int {
        return if (showMoreButton) MAX_SHOW_WITHOUT_MORE + 1 else items.size
    }

    class HighlightViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cvHighlightItem)
        val tvMoreText: TextView = itemView.findViewById(R.id.tvMoreText)
        val tvHighlightDate: TextView = itemView.findViewById(R.id.tvHighlightDate)
        val ivHighlightImage: ImageView = itemView.findViewById(R.id.ivHighlightImage)
    }
}