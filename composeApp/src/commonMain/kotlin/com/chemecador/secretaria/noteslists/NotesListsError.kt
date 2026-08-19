package com.chemecador.secretaria.noteslists

/**
 * Validation errors produced by [NotesListsViewModel] itself, as opposed to the ones coming from
 * the repository. They are typed instead of plain messages so the screen can resolve them through
 * `Res.string`, like `login.AuthError` does.
 */
enum class NotesListsError {
    NOT_OWNER,
    NOT_GROUP_OWNER,
    NO_ACCESS,
    GROUP_INSIDE_GROUP,
    TARGET_IS_NOT_A_GROUP,
}

internal class NotesListsValidationException(
    val error: NotesListsError,
) : IllegalStateException(error.name)
