package com.example.colorpaper.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.WidgetEntity

class HomeWidgetAdapter(
    private var widgetList: List<WidgetEntity> 
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_TODO = 1
        const val TYPE_OTHER = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (widgetList[position].type == "TODO_LIST") {
            TYPE_TODO
        } else {
            TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_TODO) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_todo, parent, false)
            TodoViewHolder(view)
        } else {
            val emptyView = View(parent.context)
            object : RecyclerView.ViewHolder(emptyView) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val widget = widgetList[position] // 이번 칸에 들어갈 위젯 데이터 1개 꺼내기

        if (holder is TodoViewHolder) {
            // TODO: 나중에 체크박스들에 데이터 넣기
            holder.tvTitle.text = "나의 ${widget.type} (진짜 데이터)"
        }
    }

    override fun getItemCount(): Int {
        return widgetList.size
    }

    inner class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_todo_title)
        // 나중에 체크박스들도 데이터 찾아오기
    }
    
    // 데이터가 추가되거나 순서가 바뀌었을 때 어댑터에 알려주기
    fun updateData(newData: List<WidgetEntity>) {
        widgetList = newData
        notifyDataSetChanged()
    }
}