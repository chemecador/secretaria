@file:OptIn(ExperimentalForeignApi::class)

package com.chemecador.secretaria.firestore

import com.chemecador.secretaria.requireIosGoogleServiceInfoString
import kotlinx.cinterop.ExperimentalForeignApi

internal fun resolveIosFirebaseProjectId(): String =
    requireIosGoogleServiceInfoString("PROJECT_ID")
