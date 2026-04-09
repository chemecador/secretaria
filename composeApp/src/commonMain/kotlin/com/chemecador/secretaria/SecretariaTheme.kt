package com.chemecador.secretaria

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SecretariaColorScheme = lightColorScheme(
    primary = Color(0xFF5D6F86),
    onPrimary = Color(0xFFF9FAFB),
    primaryContainer = Color(0xFFD9E2EE),
    onPrimaryContainer = Color(0xFF243548),
    secondary = Color(0xFF72839A),
    onSecondary = Color(0xFFF9FAFB),
    secondaryContainer = Color(0xFFE1E7EF),
    onSecondaryContainer = Color(0xFF2C3847),
    tertiary = Color(0xFF8A7B6A),
    onTertiary = Color(0xFFFDF8F2),
    tertiaryContainer = Color(0xFFE9DDD1),
    onTertiaryContainer = Color(0xFF3C3127),
    background = Color(0xFFE7E1D7),
    onBackground = Color(0xFF26221D),
    surface = Color(0xFFF2ECE3),
    onSurface = Color(0xFF26221D),
    surfaceVariant = Color(0xFFD7D1C6),
    onSurfaceVariant = Color(0xFF5F584F),
    surfaceTint = Color(0xFF5D6F86),
    outline = Color(0xFF8A8278),
    outlineVariant = Color(0xFFBEB6AB),
    error = Color(0xFFAF4F48),
    onError = Color(0xFFFFFBF9),
    errorContainer = Color(0xFFF3D7D4),
    onErrorContainer = Color(0xFF4D201D),
)

internal val SecretariaTopBarColor = Color(0xFF1B452A)
internal val SecretariaTopBarContentColor = Color(0xFFF5F1E8)

@Composable
fun SecretariaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SecretariaColorScheme,
        content = content,
    )
}
