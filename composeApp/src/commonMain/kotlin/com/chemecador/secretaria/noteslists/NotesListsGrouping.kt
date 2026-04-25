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

internal fun List<NotesListSummary>.applyGroupOrder(listKeysInOrder: List<NotesListKey>): List<NotesListSummary>? {
    if (size != listKeysInOrder.size || listKeysInOrder.distinct().size != size) {
        return null
    }

    val listsByKey = associateBy(NotesListSummary::key)
    if (listsByKey.size != size || listKeysInOrder.any { it !in listsByKey }) {
        return null
    }

    return listKeysInOrder.mapIndexed { index, listKey ->
        val list = listsByKey.getValue(listKey)
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
