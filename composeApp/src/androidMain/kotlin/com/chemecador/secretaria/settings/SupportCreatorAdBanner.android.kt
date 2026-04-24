package com.chemecador.secretaria.settings

import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.settings_support_ads_loading
import secretaria.composeapp.generated.resources.settings_support_ads_not_configured
import secretaria.composeapp.generated.resources.settings_support_ads_preview

@Composable
internal actual fun SupportCreatorAdBanner(modifier: Modifier) {
    val context = LocalContext.current

    if (LocalInspectionMode.current) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_preview),
            modifier = modifier,
        )
        return
    }

    val appId = remember(context) { resolveManifestMetadata(context, ADMOB_APP_ID_METADATA) }
    val bannerAdUnitId = remember(context) { resolveManifestMetadata(context, BANNER_AD_UNIT_ID_METADATA) }

    if (appId.isNullOrBlank() || bannerAdUnitId.isNullOrBlank()) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_not_configured),
            modifier = modifier,
        )
        return
    }

    var isInitialized by remember { mutableStateOf(AndroidSupportCreatorAds.isInitialized()) }

    LaunchedEffect(context, appId, bannerAdUnitId) {
        AndroidSupportCreatorAds.initialize(context) {
            isInitialized = true
        }
    }

    if (!isInitialized) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_loading),
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bannerWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, bannerWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, bannerWidthDp)
        }
        val adView = remember(context, bannerAdUnitId, adSize) {
            AdView(context).apply {
                adUnitId = bannerAdUnitId
                setAdSize(adSize)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        }

        DisposableEffect(adView) {
            onDispose {
                adView.destroy()
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { adView },
        )
    }
}
