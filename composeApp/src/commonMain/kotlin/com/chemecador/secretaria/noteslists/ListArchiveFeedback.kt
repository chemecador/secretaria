package com.chemecador.secretaria.noteslists

data class ListArchiveFeedback(
    val action: ListArchiveAction,
    val isSuccess: Boolean,
)

enum class ListArchiveAction {
    ARCHIVED,
    UNARCHIVED,
}
