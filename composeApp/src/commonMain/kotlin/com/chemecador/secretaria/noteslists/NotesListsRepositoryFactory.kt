package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.login.AuthRepository

expect fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository
