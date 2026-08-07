package com.example.colorpaper.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.databinding.ItemHighlightBinding

class HighlightAdapter(
    private val items: List<HighlightItem>,
    private val onItemClick: (HighlightItem) -> Unit,
    private val onMoreClick: () -> Unit
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
        holder.binding.tvHighlightDate.text = item.date

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}