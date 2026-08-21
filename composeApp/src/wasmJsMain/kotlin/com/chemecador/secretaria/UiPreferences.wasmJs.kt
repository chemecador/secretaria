package com.chemecador.secretaria

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberUiPreferences(): UiPreferences = NoOpUiPreferences
