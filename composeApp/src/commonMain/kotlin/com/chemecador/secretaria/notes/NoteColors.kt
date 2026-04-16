package com.chemecador.secretaria.notes

import androidx.compose.ui.graphics.Color

internal fun noteColor(value: Long): Color = Color(value.toInt())

internal fun noteColorNeedsDarkForeground(value: Long): Boolean {
    val red = ((value shr 16) and 0xFF).toDouble() / 255.0
    val green = ((value shr 8) and 0xFF).toDouble() / 255.0
    val blue = (value and 0xFF).toDouble() / 255.0
    val brightness = (0.299 * red) + (0.587 * green) + (0.114 * blue)
    return brightness > 0.7
}

internal val noteColorPalette = listOf(
    0xFFFFFFFFL,
    0xFFE0E0E0L,
    0xFFBDBDBDL,
    0xFF9E9E9EL,
    0xFFFFCDD2L,
    0xFFF8BBD0L,
    0xFFE91E63L,
    0xFFFF5722L,
    0xFFF44336L,
    0xFFE1BEE7L,
    0xFFD1C4E9L,
    0xFF9C27B0L,
    0xFF673AB7L,
    0xFF3F51B5L,
    0xFFBBDEFBL,
    0xFF90CAF9L,
    0xFF2196F3L,
    0xFF1976D2L,
    0xFF0D47A1L,
    0xFFB2DFDBL,
    0xFF80CBC4L,
    0xFF009688L,
    0xFF00BCD4L,
    0xFF0097A7L,
    0xFFC8E6C9L,
    0xFFA5D6A7L,
    0xFF4CAF50L,
    0xFF8BC34AL,
    0xFF689F38L,
    0xFFF0F4C3L,
    0xFFFFF9C4L,
    0xFFFFEB3BL,
    0xFFCDDC39L,
    0xFFAFB42BL,
    0xFFFFE0B2L,
    0xFFFFB74DL,
    0xFFFF9800L,
    0xFFFFC107L,
    0xFFFF8F00L,
    0xFFD7CCC8L,
    0xFFBCAAA4L,
    0xFF795548L,
    0xFF607D8BL,
    0xFF455A64L,
)
