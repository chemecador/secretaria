package com.chemecador.secretaria.noteslists

data class ListSharingFeedback(
    val friendName: String,
    val action: ListSharingAction,
)

enum class ListSharingAction {
    SHARED,
    UNSHARED,
}
