package com.chemecador.secretaria.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.settings_support_ads_unavailable

@Composable
internal actual fun SupportCreatorAdBanner(modifier: Modifier) {
    SupportCreatorAdMessage(
        text = stringResource(Res.string.settings_support_ads_unavailable),
        modifier = modifier,
    )
}
