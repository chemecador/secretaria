package com.chemecador.secretaria.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.settings_support_ads_tab_banner
import secretaria.composeapp.generated.resources.settings_support_ads_tab_interstitial
import secretaria.composeapp.generated.resources.settings_support_ads_tab_native

@Composable
internal fun SupportCreatorAdsWall(modifier: Modifier = Modifier) {
    var selectedPage by remember { mutableStateOf(SupportCreatorAdPage.Banner) }

    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = selectedPage.ordinal) {
            SupportCreatorAdPage.entries.forEach { page ->
                Tab(
                    selected = page == selectedPage,
                    onClick = { selectedPage = page },
                    text = { Text(stringResource(page.titleResource)) },
                    icon = {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (selectedPage) {
                    SupportCreatorAdPage.Banner -> {
                        SupportCreatorAdBanner(modifier = Modifier.fillMaxWidth())
                    }

                    SupportCreatorAdPage.Interstitial -> {
                        SupportCreatorInterstitialAd(modifier = Modifier.fillMaxWidth())
                    }

                    SupportCreatorAdPage.Native -> {
                        SupportCreatorNativeAd(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private enum class SupportCreatorAdPage(
    val titleResource: StringResource,
    val icon: ImageVector,
) {
    Banner(
        titleResource = Res.string.settings_support_ads_tab_banner,
        icon = Icons.Default.CropLandscape,
    ),
    Interstitial(
        titleResource = Res.string.settings_support_ads_tab_interstitial,
        icon = Icons.Default.OpenInFull,
    ),
    Native(
        titleResource = Res.string.settings_support_ads_tab_native,
        icon = Icons.Default.ViewAgenda,
    ),
}
