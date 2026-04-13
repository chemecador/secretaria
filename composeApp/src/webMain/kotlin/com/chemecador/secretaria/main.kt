package com.chemecador.secretaria

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

private const val WEB_GOOGLE_CLIENT_ID_ATTRIBUTE = "data-secretaria-google-web-client-id"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(
            googleServerClientId = document.documentElement
                ?.getAttribute(WEB_GOOGLE_CLIENT_ID_ATTRIBUTE)
                ?.takeUnless { it.isBlank() },
        )
    }
}
