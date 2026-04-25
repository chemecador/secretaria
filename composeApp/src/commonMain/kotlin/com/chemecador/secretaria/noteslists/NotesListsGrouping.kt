package com.chemecador.secretaria.noteslists

internal fun List<NotesListSummary>.moveList(fromIndex: Int, toIndex: Int): List<NotesListSummary> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) {
        return normalizeGroupOrder()
    }

    val mutableLists = toMutableList()
    val movedList = mutableLists.removeAt(fromIndex)
    mutableLists.add(toIndex, movedList)
    return mutableLists.normalizeGroupOrder()
}

internal fun List<NotesListSummary>.applyGroupOrder(listIdsInOrder: List<String>): List<NotesListSummary>? {
    if (size != listIdsInOrder.size || listIdsInOrder.distinct().size != size) {
        return null
    }

    val listsById = associateBy(NotesListSummary::id)
    if (listsById.size != size || listIdsInOrder.any { it !in listsById }) {
        return null
    }

    return listIdsInOrder.mapIndexed { index, listId ->
        val list = listsById.getValue(listId)
        if (list.groupOrder == index) {
            list
        } else {
            list.copy(groupOrder = index)
        }
    }
}

internal fun List<NotesListSummary>.normalizeGroupOrder(): List<NotesListSummary> =
    mapIndexed { index, list ->
        if (list.groupOrder == index) {
            list
        } else {
            list.copy(groupOrder = index)
        }
    }
