package com.chemecador.secretaria.notes

internal fun List<Note>.moveNote(fromIndex: Int, toIndex: Int): List<Note> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) {
        return normalizeNoteOrder()
    }

    val mutableNotes = toMutableList()
    val movedNote = mutableNotes.removeAt(fromIndex)
    mutableNotes.add(toIndex, movedNote)
    return mutableNotes.normalizeNoteOrder()
}

internal fun List<Note>.applyNoteOrder(noteIdsInOrder: List<String>): List<Note>? {
    if (size != noteIdsInOrder.size || noteIdsInOrder.distinct().size != size) {
        return null
    }

    val notesById = associateBy(Note::id)
    if (notesById.size != size || noteIdsInOrder.any { it !in notesById }) {
        return null
    }

    return noteIdsInOrder.mapIndexed { index, noteId ->
        val note = notesById.getValue(noteId)
        if (note.order == index) {
            note
        } else {
            note.copy(order = index)
        }
    }
}

internal fun List<Note>.normalizeNoteOrder(): List<Note> =
    mapIndexed { index, note ->
        if (note.order == index) {
            note
        } else {
            note.copy(order = index)
        }
    }
