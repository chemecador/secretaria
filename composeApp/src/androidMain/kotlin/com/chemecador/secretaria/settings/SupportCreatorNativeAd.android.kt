package com.chemecador.secretaria.settings

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.settings_support_ads_ad_label
import secretaria.composeapp.generated.resources.settings_support_ads_load_error
import secretaria.composeapp.generated.resources.settings_support_ads_loading
import secretaria.composeapp.generated.resources.settings_support_ads_not_configured
import secretaria.composeapp.generated.resources.settings_support_ads_preview
import kotlin.math.roundToInt

@Composable
internal actual fun SupportCreatorNativeAd(modifier: Modifier) {
    val context = LocalContext.current

    if (LocalInspectionMode.current) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_preview),
            modifier = modifier,
        )
        return
    }

    val appId = remember(context) { resolveManifestMetadata(context, ADMOB_APP_ID_METADATA) }
    val nativeAdUnitId = remember(context) { resolveManifestMetadata(context, NATIVE_AD_UNIT_ID_METADATA) }

    if (appId.isNullOrBlank() || nativeAdUnitId.isNullOrBlank()) {
        SupportCreatorAdMessage(
            text = stringResource(Res.string.settings_support_ads_not_configured),
            modifier = modifier,
        )
        return
    }

    val nativeAdState = remember(nativeAdUnitId) { mutableStateOf<NativeAd?>(null) }
    var isInitialized by remember { mutableStateOf(AndroidSupportCreatorAds.isInitialized()) }
    var isLoading by remember(nativeAdUnitId) { mutableStateOf(false) }
    var loadFailed by remember(nativeAdUnitId) { mutableStateOf(false) }

    LaunchedEffect(context, appId, nativeAdUnitId) {
        AndroidSupportCreatorAds.initialize(context) {
            isInitialized = true
        }
    }

    DisposableEffect(context, nativeAdUnitId, isInitialized) {
        if (!isInitialized) {
            onDispose {}
        } else {
            var disposed = false
            isLoading = true
            loadFailed = false
            nativeAdState.value?.destroy()
            nativeAdState.value = null

            val adLoader = AdLoader.Builder(context, nativeAdUnitId)
                .forNativeAd { nativeAd ->
                    if (disposed) {
                        nativeAd.destroy()
                    } else {
                        nativeAdState.value?.destroy()
                        nativeAdState.value = nativeAd
                        isLoading = false
                        loadFailed = false
                    }
                }
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            if (disposed) return
                            nativeAdState.value?.destroy()
                            nativeAdState.value = null
                            isLoading = false
                            loadFailed = true
                        }
                    },
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())

            onDispose {
                disposed = true
                nativeAdState.value?.destroy()
                nativeAdState.value = null
            }
        }
    }

    val nativeAd = nativeAdState.value
    when {
        nativeAd != null -> {
            val adLabel = stringResource(Res.string.settings_support_ads_ad_label)
            val colors = AndroidNativeAdColors(
                onSurface = MaterialTheme.colorScheme.onSurface.toArgb(),
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
                primaryContainer = MaterialTheme.colorScheme.primaryContainer.toArgb(),
                onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            )

            key(nativeAd) {
                AndroidView(
                    modifier = modifier.fillMaxWidth(),
                    factory = { viewContext -> createNativeAdView(viewContext) },
                    update = { adView ->
                        bindNativeAdView(
                            adView = adView,
                            nativeAd = nativeAd,
                            adLabel = adLabel,
                            colors = colors,
                        )
                    },
                )
            }
        }

        !isInitialized || isLoading -> {
            SupportCreatorAdMessage(
                text = stringResource(Res.string.settings_support_ads_loading),
                modifier = modifier,
            )
        }

        loadFailed -> {
            SupportCreatorAdMessage(
                text = stringResource(Res.string.settings_support_ads_load_error),
                modifier = modifier,
            )
        }
    }
}

private data class AndroidNativeAdColors(
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
)

private class NativeAdViewHolder(
    val adLabelView: TextView,
    val headlineView: TextView,
    val iconView: ImageView,
    val mediaView: MediaView,
    val bodyView: TextView,
    val callToActionView: Button,
    var boundNativeAd: NativeAd? = null,
)

private fun createNativeAdView(context: Context): NativeAdView {
    val adView = NativeAdView(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            context.dp(16),
            context.dp(14),
            context.dp(16),
            context.dp(16),
        )
    }
    adView.addView(
        root,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    root.addView(
        header,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    header.addView(
        iconView,
        LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
            marginEnd = context.dp(12)
        },
    )

    val titleColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    header.addView(
        titleColumn,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )

    val adLabelView = TextView(context).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = 12f
        setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
    }
    titleColumn.addView(
        adLabelView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    val headlineView = TextView(context).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = 18f
        maxLines = 2
    }
    titleColumn.addView(
        headlineView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(6)
        },
    )

    val mediaView = MediaView(context)
    root.addView(
        mediaView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(180),
        ).apply {
            topMargin = context.dp(12)
        },
    )

    val bodyView = TextView(context).apply {
        textSize = 14f
        maxLines = 3
    }
    root.addView(
        bodyView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(12)
        },
    )

    val callToActionView = Button(context).apply {
        isAllCaps = false
    }
    root.addView(
        callToActionView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END
            topMargin = context.dp(12)
        },
    )

    adView.headlineView = headlineView
    adView.iconView = iconView
    adView.mediaView = mediaView
    adView.bodyView = bodyView
    adView.callToActionView = callToActionView
    adView.tag = NativeAdViewHolder(
        adLabelView = adLabelView,
        headlineView = headlineView,
        iconView = iconView,
        mediaView = mediaView,
        bodyView = bodyView,
        callToActionView = callToActionView,
    )

    return adView
}

private fun bindNativeAdView(
    adView: NativeAdView,
    nativeAd: NativeAd,
    adLabel: String,
    colors: AndroidNativeAdColors,
) {
    val holder = adView.tag as NativeAdViewHolder
    holder.adLabelView.text = adLabel
    holder.adLabelView.setTextColor(colors.onPrimaryContainer)
    holder.adLabelView.setBackgroundColor(colors.primaryContainer)
    holder.headlineView.text = nativeAd.headline.orEmpty()
    holder.headlineView.setTextColor(colors.onSurface)
    holder.bodyView.text = nativeAd.body.orEmpty()
    holder.bodyView.setTextColor(colors.onSurfaceVariant)
    holder.bodyView.visibility = nativeAd.body.visibleWhenNotBlank()
    holder.callToActionView.text = nativeAd.callToAction.orEmpty()
    holder.callToActionView.visibility = nativeAd.callToAction.visibleWhenNotBlank()

    val icon = nativeAd.icon
    if (icon == null) {
        holder.iconView.visibility = View.GONE
    } else {
        holder.iconView.visibility = View.VISIBLE
        holder.iconView.setImageDrawable(icon.drawable)
    }

    if (holder.boundNativeAd !== nativeAd) {
        holder.boundNativeAd = nativeAd
        adView.setNativeAd(nativeAd)
    }
}

private fun String?.visibleWhenNotBlank(): Int =
    if (isNullOrBlank()) View.GONE else View.VISIBLE

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()
