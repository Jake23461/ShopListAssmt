package com.mmgpt.shoplistgpt

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mmgpt.shoplistgpt.adapter.ItemAdapter

/**
 * Drag-to-reorder (via the drag handle) for the Plan lists, which start and end with
 * an "Add..." row that can't be moved. [onReorder] gets positions without the top
 * "Add..." row. [adapter] is a function because the adapter needs this helper to
 * start drags, so it is created afterwards.
 */
fun reorderDragHelper(
    adapter: () -> ItemAdapter,
    onReorder: (from: Int, to: Int) -> Unit
): ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.adapterPosition
        val to = target.adapterPosition
        val itemFrom = adapter().currentList.getOrNull(from)
        val itemTo = adapter().currentList.getOrNull(to)
        if (itemFrom?.isPlaceholder == true || itemTo?.isPlaceholder == true) return false
        onReorder(from - 1, to - 1)
        adapter().moveItem(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isLongPressDragEnabled(): Boolean = false
})

/** Edit dialog prefilled with [current]; [onSave] gets the trimmed text if it isn't empty. */
fun Fragment.showEditDialog(current: String, onSave: (String) -> Unit) {
    val input = android.widget.EditText(requireContext()).apply { setText(current) }
    AlertDialog.Builder(requireContext())
        .setTitle(getString(R.string.edit))
        .setView(input)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                onSave(text)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
