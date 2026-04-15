package com.chemecador.secretaria.firestore

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun collectionQuery(
    collectionId: String,
    vararg filters: JsonObject,
    orderBy: List<JsonObject> = emptyList(),
    limit: Int? = null,
    allDescendants: Boolean = false,
): JsonObject =
    buildJsonObject {
        put(
            "from",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("collectionId", JsonPrimitive(collectionId))
                        if (allDescendants) {
                            put("allDescendants", JsonPrimitive(true))
                        }
                    },
                )
            },
        )
        when (filters.size) {
            0 -> Unit
            1 -> put("where", filters.first())
            else -> put(
                "where",
                buildJsonObject {
                    put(
                        "compositeFilter",
                        buildJsonObject {
                            put("op", JsonPrimitive("AND"))
                            put("filters", buildJsonArray { filters.forEach(::add) })
                        },
                    )
                },
            )
        }
        if (orderBy.isNotEmpty()) {
            put("orderBy", buildJsonArray { orderBy.forEach(::add) })
        }
        if (limit != null) {
            put("limit", JsonPrimitive(limit))
        }
    }

internal fun arrayContainsFilter(
    field: String,
    value: JsonObject,
): JsonObject =
    buildJsonObject {
        put(
            "fieldFilter",
            buildJsonObject {
                put("field", fieldReference(field))
                put("op", JsonPrimitive("ARRAY_CONTAINS"))
                put("value", value)
            },
        )
    }

private fun fieldReference(field: String): JsonObject =
    buildJsonObject {
        put("fieldPath", JsonPrimitive(field))
    }
