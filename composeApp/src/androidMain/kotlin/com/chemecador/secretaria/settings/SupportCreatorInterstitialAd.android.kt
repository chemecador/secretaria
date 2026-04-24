package com.chemecador.secretaria.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.settings_support_ads_fullscreen_description
import secretaria.composeapp.generated.resources.settings_support_ads_load_error
import secretaria.composeapp.generated.resources.settings_support_ads_loading
import secretaria.composeapp.generated.resources.settings_support_ads_not_configured
import secretaria.composeapp.generated.resources.settings_support_ads_preview
import secretaria.composeapp.generated.resources.settings_support_ads_show_fullscreen

@Composable
internal actual fun SupportCreatorInterstitialAd(modifier: Modifier) {
    val context = LocalContext.current

    if (LocalInspectionMode.current) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_preview),
            modifier = modifier,
        )
        return
    }

    val appId = remember(context) { resolveManifestMetadata(context, ADMOB_APP_ID_METADATA) }
    val interstitialAdUnitId = remember(context) {
        resolveManifestMetadata(context, INTERSTITIAL_AD_UNIT_ID_METADATA)
    }

    if (appId.isNullOrBlank() || interstitialAdUnitId.isNullOrBlank()) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_not_configured),
            modifier = modifier,
        )
        return
    }

    val activity = remember(context) { context.findActivity() }
    val interstitialAdState = remember(interstitialAdUnitId) {
        mutableStateOf<InterstitialAd?>(null)
    }
    var isInitialized by remember { mutableStateOf(AndroidSupportCreatorAds.isInitialized()) }
    var isLoading by remember(interstitialAdUnitId) { mutableStateOf(false) }
    var loadFailed by remember(interstitialAdUnitId) { mutableStateOf(false) }
    var isShowing by remember(interstitialAdUnitId) { mutableStateOf(false) }
    var reloadKey by remember(interstitialAdUnitId) { mutableStateOf(0) }

    LaunchedEffect(context, appId, interstitialAdUnitId) {
        AndroidSupportCreatorAds.initialize(context) {
            isInitialized = true
        }
    }

    DisposableEffect(context, interstitialAdUnitId, isInitialized, reloadKey) {
        if (!isInitialized) {
            onDispose {}
        } else {
            var disposed = false
            isLoading = true
            loadFailed = false
            interstitialAdState.value = null

            InterstitialAd.load(
                context,
                interstitialAdUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (disposed) return
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                interstitialAdState.value = null
                                isShowing = false
                                reloadKey += 1
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                interstitialAdState.value = null
                                isShowing = false
                                loadFailed = true
                                reloadKey += 1
                            }

                            override fun onAdShowedFullScreenContent() {
                                isShowing = true
                            }
                        }
                        interstitialAdState.value = ad
                        isLoading = false
                        loadFailed = false
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        if (disposed) return
                        interstitialAdState.value = null
                        isLoading = false
                        loadFailed = true
                    }
                },
            )

            onDispose {
                disposed = true
                interstitialAdState.value?.fullScreenContentCallback = null
                interstitialAdState.value = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_support_ads_fullscreen_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val ad = interstitialAdState.value
                if (ad != null && activity != null) {
                    ad.show(activity)
                }
            },
            enabled = interstitialAdState.value != null && activity != null && !isShowing,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.settings_support_ads_show_fullscreen))
            }
        }
        when {
            !isInitialized || isLoading -> {
                Text(
                    text = stringResource(Res.string.settings_support_ads_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            loadFailed || activity == null -> {
                Text(
                    text = stringResource(Res.string.settings_support_ads_load_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
