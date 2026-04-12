@file:OptIn(ExperimentalForeignApi::class)

package com.chemecador.secretaria.firestore

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSPropertyListSerialization

internal fun resolveIosFirebaseProjectId(): String {
    val path = NSBundle.mainBundle.pathForResource("GoogleService-Info", ofType = "plist")
        ?: error(
            "GoogleService-Info.plist not found in app bundle. " +
                    "Add it to the iosApp target in Xcode.",
        )
    val data = NSFileManager.defaultManager.contentsAtPath(path)
        ?: error("Unable to read GoogleService-Info.plist")
    val plist = NSPropertyListSerialization.propertyListWithData(
        data = data,
        options = 0u,
        format = null,
        error = null,
    )
    val dict = plist as? Map<*, *>
        ?: error("GoogleService-Info.plist is not a valid dictionary")
    return dict["PROJECT_ID"] as? String
        ?: error("PROJECT_ID not found in GoogleService-Info.plist")
}
