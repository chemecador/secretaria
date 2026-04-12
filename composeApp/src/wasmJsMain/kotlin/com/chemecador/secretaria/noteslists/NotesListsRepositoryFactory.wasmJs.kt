package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.login.AuthRepository

actual fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository =
    FakeNotesListsRepository()
