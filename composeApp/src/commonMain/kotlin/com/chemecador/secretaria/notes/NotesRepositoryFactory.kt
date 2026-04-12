package com.chemecador.secretaria.notes

import com.chemecador.secretaria.login.AuthRepository

expect fun createNotesRepository(authRepository: AuthRepository): NotesRepository
