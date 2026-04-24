package com.chemecador.secretaria.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds

internal const val ADMOB_APP_ID_METADATA = "com.google.android.gms.ads.APPLICATION_ID"
internal const val BANNER_AD_UNIT_ID_METADATA = "com.chemecador.secretaria.ads.BANNER_AD_UNIT_ID"
internal const val INTERSTITIAL_AD_UNIT_ID_METADATA =
    "com.chemecador.secretaria.ads.INTERSTITIAL_AD_UNIT_ID"
internal const val NATIVE_AD_UNIT_ID_METADATA = "com.chemecador.secretaria.ads.NATIVE_AD_UNIT_ID"

internal fun resolveManifestMetadata(
    context: Context,
    key: String,
): String? = try {
    @Suppress("DEPRECATION")
    val appInfo = context.packageManager.getApplicationInfo(
        context.packageName,
        PackageManager.GET_META_DATA,
    )
    appInfo.metaData?.getString(key)
        ?.trim()
        ?.takeUnless(String::isBlank)
} catch (_: Throwable) {
    null
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal object AndroidSupportCreatorAds {
    @Volatile
    private var initialized = false

    fun isInitialized(): Boolean = initialized

    fun initialize(
        context: Context,
        onInitialized: () -> Unit,
    ) {
        if (initialized) {
            onInitialized()
            return
        }

        MobileAds.initialize(context.applicationContext) {
            initialized = true
            Handler(Looper.getMainLooper()).post(onInitialized)
        }
    }
}
