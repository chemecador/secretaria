package com.chemecador.secretaria.notes

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NotesReorderState(
    private val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemOffset by mutableStateOf(0f)

    val isDragging: Boolean
        get() = draggingItemIndex != null

    fun startDrag(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = 0f
    }

    fun dragBy(dragDeltaY: Float) {
        val currentIndex = draggingItemIndex ?: return
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.index == currentIndex } ?: return

        draggingItemOffset += dragDeltaY

        val draggedItemCenter = currentItem.offset + draggingItemOffset + (currentItem.size / 2f)
        val targetItem = visibleItems.firstOrNull { item ->
            item.index != currentIndex &&
                draggedItemCenter >= item.offset &&
                draggedItemCenter < item.offset + item.size
        } ?: return

        onMove(currentIndex, targetItem.index)
        draggingItemIndex = targetItem.index
        draggingItemOffset += currentItem.offset - targetItem.offset
    }

    fun translationFor(index: Int): Float =
        if (index == draggingItemIndex) draggingItemOffset else 0f

    fun endDrag() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }
}
