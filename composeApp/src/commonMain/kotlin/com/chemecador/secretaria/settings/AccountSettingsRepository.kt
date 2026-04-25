package com.chemecador.secretaria.settings

import com.chemecador.secretaria.notes.DEFAULT_NOTE_COLOR
import com.chemecador.secretaria.notes.noteColorPalette

interface AccountSettingsRepository {
    suspend fun getDefaultNoteColor(): Result<Long>
    suspend fun setDefaultNoteColor(color: Long): Result<Unit>
}

class FakeAccountSettingsRepository(
    initialDefaultNoteColor: Long = randomDefaultNoteColor(),
) : AccountSettingsRepository {
    private var defaultNoteColor = initialDefaultNoteColor

    override suspend fun getDefaultNoteColor(): Result<Long> =
        Result.success(defaultNoteColor)

    override suspend fun setDefaultNoteColor(color: Long): Result<Unit> {
        defaultNoteColor = color
        return Result.success(Unit)
    }
}

internal object AccountSettingsFirestoreSchema {
    const val USERS = "users"
    const val DEFAULT_NOTE_COLOR = "defaultNoteColor"
}

internal fun randomDefaultNoteColor(): Long {
    val randomizableColors = noteColorPalette.filter { it != DEFAULT_NOTE_COLOR }
    return if (randomizableColors.isEmpty()) {
        DEFAULT_NOTE_COLOR
    } else {
        randomizableColors.random()
    }
}
