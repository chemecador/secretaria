package com.chemecador.secretaria.notes

import com.chemecador.secretaria.login.AuthRepository

actual fun createNotesRepository(authRepository: AuthRepository): NotesRepository =
    FakeNotesRepository()
