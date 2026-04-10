package com.chemecador.secretaria

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Secretaria",
        state = rememberWindowState(size = DpSize(900.dp, 700.dp)),
    ) {
        App()
    }
}