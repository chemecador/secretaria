package com.chemecador.secretaria.firestore

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.firestoreStringList(field: String): List<String> =
    get(field)
        ?.jsonObject
        ?.get("arrayValue")
        ?.jsonObject
        ?.get("values")
        ?.jsonArray
        ?.mapNotNull { value ->
            value.jsonObject["stringValue"]?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
