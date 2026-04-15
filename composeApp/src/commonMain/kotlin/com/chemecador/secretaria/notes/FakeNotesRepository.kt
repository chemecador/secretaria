package com.chemecador.secretaria.notes

import kotlin.time.Clock
import kotlin.time.Instant

class FakeNotesRepository : NotesRepository {

    private val notes = mutableMapOf<String, MutableList<Note>>()
    private var seeded = false

    override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> {
        if (!seeded) {
            seedNotes.forEach { (id, list) ->
                notes[id] = list.toMutableList()
            }
            seeded = true
        }
        return Result.success(notes[notesKey(ownerId, listId)].orEmpty())
    }

    override suspend fun createNote(
        ownerId: String,
        listId: String,
        title: String,
        content: String,
    ): Result<Note> {
        val list = notes.getOrPut(notesKey(ownerId, listId)) { mutableListOf() }
        val newNote = Note(
            id = "$listId-${list.size + 1}",
            title = title,
            content = content,
            createdAt = Clock.System.now(),
            order = list.size,
            creator = "Alex",
        )
        list.add(newNote)
        return Result.success(newNote)
    }

    override suspend fun deleteNote(ownerId: String, listId: String, noteId: String): Result<Unit> {
        notes[notesKey(ownerId, listId)]?.removeAll { it.id == noteId }
        return Result.success(Unit)
    }

    override suspend fun updateNote(
        ownerId: String,
        listId: String,
        noteId: String,
        title: String,
        content: String,
    ): Result<Note> {
        val list = notes[notesKey(ownerId, listId)]
            ?: return Result.failure(IllegalStateException("List not found"))
        val index = list.indexOfFirst { it.id == noteId }
        if (index == -1) return Result.failure(IllegalStateException("Note not found"))
        val updated = list[index].copy(title = title, content = content)
        list[index] = updated
        return Result.success(updated)
    }

    companion object {
        val seedNotes: Map<String, List<Note>> = mapOf(
            notesKey("Alex", "shopping") to listOf(
                Note(
                    id = "shopping-1",
                    title = "Leche",
                    content = "2 litros, semidesnatada",
                    createdAt = Instant.parse("2026-03-28T12:05:00Z"),
                    completed = true,
                    creator = "Alex",
                ),
                Note(
                    id = "shopping-2",
                    title = "Pan",
                    content = "Barra rústica del horno de la esquina",
                    createdAt = Instant.parse("2026-03-28T12:06:00Z"),
                    completed = false,
                    creator = "Alex",
                ),
                Note(
                    id = "shopping-3",
                    title = "Huevos",
                    content = "Docena camperos",
                    createdAt = Instant.parse("2026-03-28T12:07:00Z"),
                    completed = false,
                    creator = "Alex",
                ),
                Note(
                    id = "shopping-4",
                    title = "Tomates",
                    content = "Para ensalada, 1 kg",
                    createdAt = Instant.parse("2026-03-28T12:08:00Z"),
                    completed = false,
                    creator = "Alex",
                ),
            ),
            notesKey("Alex", "work") to listOf(
                Note(
                    id = "work-1",
                    title = "Email cliente X",
                    content = "Confirmar alcance del sprint y pedir acceso al repo",
                    createdAt = Instant.parse("2026-03-22T12:10:00Z"),
                    order = 0,
                    creator = "Alex",
                ),
                Note(
                    id = "work-2",
                    title = "Preparar reunión del lunes",
                    content = "Agenda, demo del módulo compartido y preguntas abiertas",
                    createdAt = Instant.parse("2026-03-22T12:11:00Z"),
                    order = 1,
                    creator = "Alex",
                ),
                Note(
                    id = "work-3",
                    title = "Revisar PR",
                    content = "PR del refactor de autenticación, comentar antes del jueves",
                    createdAt = Instant.parse("2026-03-22T12:12:00Z"),
                    order = 2,
                    creator = "Alex",
                ),
            ),
            notesKey("Alex", "travel") to listOf(
                Note(
                    id = "travel-1",
                    title = "Reservar hotel en Tokio",
                    content = "Barrio Shinjuku, 4 noches",
                    createdAt = Instant.parse("2026-03-30T12:15:00Z"),
                    creator = "Alex",
                ),
                Note(
                    id = "travel-2",
                    title = "Sacar JR Pass",
                    content = "7 días, comprarlo antes de salir",
                    createdAt = Instant.parse("2026-03-30T12:16:00Z"),
                    creator = "Alex",
                ),
                Note(
                    id = "travel-3",
                    title = "Seguro de viaje",
                    content = "Cobertura médica y cancelación",
                    createdAt = Instant.parse("2026-03-30T12:17:00Z"),
                    creator = "Alex",
                ),
            ),
            notesKey("Marta", "books") to listOf(
                Note(
                    id = "books-1",
                    title = "Los pilares de la tierra",
                    content = "Ken Follett",
                    createdAt = Instant.parse("2026-02-18T12:20:00Z"),
                    order = 0,
                    creator = "Marta",
                ),
                Note(
                    id = "books-2",
                    title = "El nombre del viento",
                    content = "Patrick Rothfuss",
                    createdAt = Instant.parse("2026-02-18T12:21:00Z"),
                    order = 1,
                    creator = "Marta",
                ),
                Note(
                    id = "books-3",
                    title = "Dune",
                    content = "Frank Herbert",
                    createdAt = Instant.parse("2026-02-18T12:22:00Z"),
                    order = 2,
                    creator = "Marta",
                ),
            ),
        )

        private fun notesKey(ownerId: String, listId: String): String = "$ownerId:$listId"
    }
}
