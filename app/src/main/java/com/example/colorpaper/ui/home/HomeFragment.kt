package com.example.colorpaper.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.colorpaper.R
import com.example.colorpaper.data.model.WidgetEntity
import com.example.colorpaper.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var adapter: HomeWidgetAdapter
    private var editMode = false
    private var allWidgets: List<WidgetEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_home_widgets)
        val editButton = view.findViewById<TextView>(R.id.btn_edit_layout)
        val dateTitle = view.findViewById<TextView>(R.id.tv_date_title)
        val palette = ThemeManager.currentPalette(requireContext())

        view.findViewById<View>(R.id.home_root).setBackgroundColor(
            ContextCompat.getColor(requireContext(), palette.screenBackground)
        )
        dateTitle.setTextColor(ContextCompat.getColor(requireContext(), palette.primaryText))
        editButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), palette.accent)
        )

        dateTitle.text = SimpleDateFormat("M월 d일", Locale.KOREAN).format(Date())
        allWidgets = defaultWidgets()
        adapter = HomeWidgetAdapter(emptyList(), palette)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = editMode

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = adapter.moveItem(
                viewHolder.bindingAdapterPosition,
                target.bindingAdapterPosition
            )

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        editButton.setOnClickListener {
            if (editMode) saveLayout(editButton) else enterEditMode(editButton)
        }

        showHomeWidgets()
    }

    private fun enterEditMode(editButton: TextView) {
        editMode = true
        editButton.text = "저장하기"
        adapter.setEditMode(true)
        adapter.updateData(allWidgets)
    }

    private fun saveLayout(editButton: TextView) {
        allWidgets = adapter.currentWidgets().mapIndexed { index, widget ->
            widget.copy(order = index)
        }
        editMode = false
        editButton.text = "편집"
        showHomeWidgets()
    }

    private fun showHomeWidgets() {
        adapter.setEditMode(false)
        adapter.updateData(allWidgets.filter { it.isVisible })
    }

    private fun defaultWidgets(): List<WidgetEntity> = listOf(
        WidgetEntity(userId = DEMO_USER_ID, type = "CALENDAR", isVisible = true, order = 0),
        WidgetEntity(userId = DEMO_USER_ID, type = "CHECKLIST", isVisible = true, order = 1),
        WidgetEntity(userId = DEMO_USER_ID, type = "TODO_LIST", isVisible = true, order = 2),
        WidgetEntity(userId = DEMO_USER_ID, type = "YEARS_AGO", isVisible = true, order = 3),
        WidgetEntity(userId = DEMO_USER_ID, type = "REMINDER", isVisible = true, order = 4)
    )

    companion object {
        private const val DEMO_USER_ID = 1
    }
}
