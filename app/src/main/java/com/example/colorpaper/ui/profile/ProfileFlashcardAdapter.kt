package com.example.colorpaper.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.data.model.FolderEntity
import com.example.colorpaper.databinding.ItemProfileSharedFlashcardBinding

class ProfileFlashcardAdapter(
    private var setList: List<FolderEntity>,
    private val onStartClick: (FolderEntity) -> Unit,
    private val onItemLongClick: (FolderEntity) -> Unit
) : RecyclerView.Adapter<ProfileFlashcardAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemProfileSharedFlashcardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProfileSharedFlashcardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = setList[position]

        holder.binding.tvSetTitle.text = item.folderName

        holder.binding.root.setOnLongClickListener {
            onItemLongClick(item)
            true
        }

        holder.binding.btnStart.setOnClickListener {
            onStartClick(item)
        }
    }

    override fun getItemCount(): Int = setList.size

    fun updateData(newSets: List<FolderEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = setList.size
            override fun getNewListSize(): Int = newSets.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return setList[oldItemPosition].folderId == newSets[newItemPosition].folderId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return setList[oldItemPosition] == newSets[newItemPosition]
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.setList = newSets
        diffResult.dispatchUpdatesTo(this)
    }
}