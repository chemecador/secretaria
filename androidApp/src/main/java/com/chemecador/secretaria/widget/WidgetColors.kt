package com.chemecador.secretaria.widget

import androidx.compose.ui.graphics.Color

/**
 * Copia de la paleta de `SecretariaTheme`, que es privada del modulo compartido. El widget no
 * usa `GlanceTheme` ni color dinamico a proposito: la app tiene un tema claro fijo, y un widget
 * tenido por el fondo de pantalla no se pareceria a la pantalla que abre.
 */
internal object WidgetColors {
    val Background = Color(0xFFE7E1D7)
    val Surface = Color(0xFFF2ECE3)
    val OnSurface = Color(0xFF26221D)
    val OnSurfaceVariant = Color(0xFF5F584F)
    val Primary = Color(0xFF5D6F86)
    val TopBar = Color(0xFF1B452A)
    val OnTopBar = Color(0xFFF5F1E8)
    val SecondaryContainer = Color(0xFFE1E7EF)
    val OnSecondaryContainer = Color(0xFF2C3847)
    val ErrorContainer = Color(0xFFF3D7D4)
    val OnErrorContainer = Color(0xFF4D201D)
}
