package com.example.colorpaper.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.databinding.ItemHighlightBinding

class HighlightAdapter(
    private var items: List<HighlightItem>,
    private val onItemClick: (HighlightItem) -> Unit
) : RecyclerView.Adapter<HighlightAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHighlightBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHighlightBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 날짜 및 하이라이트 텍스트 바인딩
        holder.binding.tvHighlightDate.text = item.date

        // item_highlight.xml에 텍스트뷰(예: tvHighlightText)가 있다면 바인딩
        // holder.binding.tvHighlightText?.text = item.highlightedText

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<HighlightItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}