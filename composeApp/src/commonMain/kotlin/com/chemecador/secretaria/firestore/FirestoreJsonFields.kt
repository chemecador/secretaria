package com.chemecador.secretaria.firestore

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

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

internal fun JsonObject.firestoreInstantMap(field: String): Map<String, Instant> =
    get(field)
        ?.jsonObject
        ?.get("mapValue")
        ?.jsonObject
        ?.get("fields")
        ?.jsonObject
        ?.mapNotNull { (key, value) ->
            val valueObject = runCatching { value.jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val timestamp = valueObject["timestampValue"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: return@mapNotNull null
            val instant = runCatching { Instant.parse(timestamp) }.getOrNull()
                ?: return@mapNotNull null
            key to instant
        }
        ?.toMap()
        .orEmpty()

internal fun firestoreInstantMap(values: Map<String, Instant>): JsonObject =
    buildJsonObject {
        put(
            "mapValue",
            buildJsonObject {
                if (values.isNotEmpty()) {
                    put(
                        "fields",
                        buildJsonObject {
                            values.forEach { (key, value) ->
                                put(
                                    key,
                                    buildJsonObject {
                                        put("timestampValue", JsonPrimitive(value.toString()))
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )
    }
