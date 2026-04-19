package com.chemecador.secretaria

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
