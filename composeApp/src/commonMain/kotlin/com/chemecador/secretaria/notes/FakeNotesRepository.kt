package com.chemecador.secretaria.notes

import kotlin.time.Instant

class FakeNotesRepository(
    private val resultProvider: suspend (String) -> Result<List<Note>> = { listId ->
        Result.success(seedNotes[listId].orEmpty())
    },
) : NotesRepository {

    override suspend fun getNotesForList(listId: String): Result<List<Note>> =
        resultProvider(listId)

    companion object {
        val seedNotes: Map<String, List<Note>> = mapOf(
            "shopping" to listOf(
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
            "work" to listOf(
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
            "travel" to listOf(
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
            "books" to listOf(
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
    }
}
