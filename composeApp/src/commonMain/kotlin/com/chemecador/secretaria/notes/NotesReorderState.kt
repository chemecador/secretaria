package com.chemecador.secretaria.notes

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Controlador compartido de reordenacion manual: lo usan `notes`, `noteslists` y `reminders`
 * pese a vivir en este paquete.
 *
 * La traslacion NO se acumula. Se recalcula en cada frame como "donde deberia estar el dedo"
 * menos "donde esta ahora el elemento en el layout". Compensar el intercambio sumando la
 * diferencia de offsets asumia que todos los elementos miden lo mismo, y con alturas distintas
 * el elemento arrastrado saltaba exactamente esa diferencia en cada intercambio.
 */
internal class NotesReorderState(
    private val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /** Desplazamiento total del dedo desde que empezo el arrastre. */
    private var draggingDistance by mutableFloatStateOf(0f)

    /** Offset del elemento en el momento de empezar el arrastre. */
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    val isDragging: Boolean
        get() = draggingItemIndex != null

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    fun startDrag(index: Int) {
        draggingItemIndex = index
        draggingDistance = 0f
        draggingItemInitialOffset = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.offset
            ?: 0
    }

    fun dragBy(dragDeltaY: Float) {
        val currentIndex = draggingItemIndex ?: return
        draggingDistance += dragDeltaY

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + translationFor(currentIndex)
        val draggedItemCenter = startOffset + (draggingItem.size / 2f)

        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex &&
                draggedItemCenter >= item.offset &&
                draggedItemCenter < item.offset + item.size
        } ?: return

        onMove(currentIndex, targetItem.index)
        draggingItemIndex = targetItem.index
    }

    fun translationFor(index: Int): Float {
        if (index != draggingItemIndex) return 0f
        val currentOffset = draggingItemLayoutInfo?.offset ?: draggingItemInitialOffset
        return draggingItemInitialOffset + draggingDistance - currentOffset
    }

    fun endDrag() {
        draggingItemIndex = null
        draggingDistance = 0f
        draggingItemInitialOffset = 0
    }
}
