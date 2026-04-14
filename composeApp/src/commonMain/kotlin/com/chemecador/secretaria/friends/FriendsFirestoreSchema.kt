package com.chemecador.secretaria.friends

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal object FriendsFirestoreSchema {
    const val USERS = "users"
    const val USERCODES = "usercodes"
    const val USERCODE = "usercode"
    const val COUNTER = "counter"
    const val FRIENDSHIPS = "friendships"
    const val SENDER_ID = "senderId"
    const val RECEIVER_ID = "receiverId"
    const val RECEIVER_CODE = "receiverCode"
    const val SENDER_NAME = "senderName"
    const val RECEIVER_NAME = "receiverName"
    const val REQUEST_DATE = "requestDate"
    const val ACCEPTANCE_DATE = "acceptanceDate"
}

internal fun collectionQuery(
    collectionId: String,
    vararg filters: JsonObject,
    orderBy: List<JsonObject> = emptyList(),
    limit: Int? = null,
): JsonObject =
    buildJsonObject {
        put(
            "from",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("collectionId", JsonPrimitive(collectionId))
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

internal fun equalsFilter(
    field: String,
    value: JsonObject,
): JsonObject =
    buildJsonObject {
        put(
            "fieldFilter",
            buildJsonObject {
                put("field", fieldReference(field))
                put("op", JsonPrimitive("EQUAL"))
                put("value", value)
            },
        )
    }

internal fun isNullFilter(field: String): JsonObject =
    buildJsonObject {
        put(
            "unaryFilter",
            buildJsonObject {
                put("op", JsonPrimitive("IS_NULL"))
                put("field", fieldReference(field))
            },
        )
    }

internal fun isNotNullFilter(field: String): JsonObject =
    buildJsonObject {
        put(
            "unaryFilter",
            buildJsonObject {
                put("op", JsonPrimitive("IS_NOT_NULL"))
                put("field", fieldReference(field))
            },
        )
    }

internal fun orderBy(
    field: String,
    descending: Boolean = false,
): JsonObject =
    buildJsonObject {
        put("field", fieldReference(field))
        put(
            "direction",
            JsonPrimitive(if (descending) "DESCENDING" else "ASCENDING"),
        )
    }

private fun fieldReference(field: String): JsonObject =
    buildJsonObject {
        put("fieldPath", JsonPrimitive(field))
    }
