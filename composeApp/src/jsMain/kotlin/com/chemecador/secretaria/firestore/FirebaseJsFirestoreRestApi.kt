package com.chemecador.secretaria.firestore

import com.chemecador.secretaria.login.FirebaseJsIdTokenProvider
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.json
import kotlin.time.Instant

private const val WEB_FIREBASE_PROJECT_ID_ATTRIBUTE = "data-secretaria-firebase-project-id"

internal class FirebaseJsFirestoreRestApi(
    private val projectId: String,
    private val tokenProvider: FirebaseJsIdTokenProvider,
    private val transport: FirebaseJsFirestoreTransport = BrowserFetchFirebaseJsFirestoreTransport(),
) {

    suspend fun listDocuments(
        collectionPath: String,
        orderBy: String? = null,
    ): List<FirestoreJsDocument> {
        val response = request(
            FirebaseJsFirestoreRequest(
                method = "GET",
                url = buildString {
                    append(documentsUrl(collectionPath))
                    orderBy?.let {
                        append("?orderBy=")
                        append(encodeURIComponent(it))
                    }
                },
            ),
        )
        val root = Json.parseToJsonElement(response.body).jsonObject
        val documents = root["documents"]?.jsonArray ?: JsonArray(emptyList())
        return documents.mapNotNull { document ->
            runCatching { document.asFirestoreJsDocument() }.getOrNull()
        }
    }

    suspend fun createDocument(
        parentPath: String,
        collectionId: String,
        fields: JsonObject,
    ): FirestoreJsDocument {
        val response = request(
            FirebaseJsFirestoreRequest(
                method = "POST",
                url = "${documentsUrl(parentPath)}/$collectionId",
                body = buildJsonObject {
                    put("fields", fields)
                }.toString(),
            ),
        )
        return Json.parseToJsonElement(response.body).asFirestoreJsDocument()
    }

    suspend fun getDocumentOrNull(documentPath: String): FirestoreJsDocument? {
        val response = authenticatedRequest(
            FirebaseJsFirestoreRequest(
                method = "GET",
                url = documentsUrl(documentPath),
            ),
        )

        return when (response.statusCode) {
            404 -> null
            in 200..299 -> Json.parseToJsonElement(response.body).asFirestoreJsDocument()
            else -> error(
                "Firestore request failed (${response.statusCode}): ${
                    extractFirestoreErrorMessage(
                        response.body
                    )
                }",
            )
        }
    }

    suspend fun patchDocument(
        documentPath: String,
        fields: JsonObject,
        updateMask: List<String>,
        currentDocument: FirestorePrecondition? = null,
    ): FirestoreJsDocument {
        val queryParams = buildList {
            updateMask.forEach { field ->
                add("updateMask.fieldPaths=${encodeURIComponent(field)}")
            }
            currentDocument?.exists?.let { exists ->
                add("currentDocument.exists=$exists")
            }
            currentDocument?.updateTime?.let { updateTime ->
                add("currentDocument.updateTime=${encodeURIComponent(updateTime)}")
            }
        }
        val query = queryParams.joinToString("&")
        val response = request(
            FirebaseJsFirestoreRequest(
                method = "PATCH",
                url = buildString {
                    append(documentsUrl(documentPath))
                    if (query.isNotBlank()) {
                        append('?')
                        append(query)
                    }
                },
                body = buildJsonObject {
                    put("fields", fields)
                }.toString(),
            ),
        )
        return Json.parseToJsonElement(response.body).asFirestoreJsDocument()
    }

    suspend fun runQuery(
        structuredQuery: JsonObject,
        parentPath: String = "",
    ): List<FirestoreJsDocument> {
        val response = request(
            FirebaseJsFirestoreRequest(
                method = "POST",
                url = runQueryUrl(parentPath),
                body = buildJsonObject {
                    put("structuredQuery", structuredQuery)
                }.toString(),
            ),
        )
        return Json.parseToJsonElement(response.body).jsonArray.mapNotNull { item ->
            item.jsonObject["document"]?.let { document ->
                runCatching { document.asFirestoreJsDocument() }.getOrNull()
            }
        }
    }

    suspend fun deleteDocument(documentPath: String) {
        request(
            FirebaseJsFirestoreRequest(
                method = "DELETE",
                url = documentsUrl(documentPath),
            ),
        )
    }

    suspend fun commitDeletes(documentPaths: List<String>) {
        if (documentPaths.isEmpty()) {
            return
        }
        request(
            FirebaseJsFirestoreRequest(
                method = "POST",
                url = "$databaseUrl/documents:commit",
                body = buildJsonObject {
                    put(
                        "writes",
                        buildJsonArray {
                            documentPaths.forEach { path ->
                                add(
                                    buildJsonObject {
                                        put("delete", JsonPrimitive(fullDocumentName(path)))
                                    },
                                )
                            }
                        },
                    )
                }.toString(),
            ),
        )
    }

    suspend fun commitPatches(patches: List<FirestoreJsDocumentPatch>) {
        if (patches.isEmpty()) {
            return
        }
        request(
            FirebaseJsFirestoreRequest(
                method = "POST",
                url = "$databaseUrl/documents:commit",
                body = buildJsonObject {
                    put(
                        "writes",
                        buildJsonArray {
                            patches.forEach { patch ->
                                add(
                                    buildJsonObject {
                                        put(
                                            "update",
                                            buildJsonObject {
                                                put(
                                                    "name",
                                                    JsonPrimitive(fullDocumentName(patch.documentPath)),
                                                )
                                                put("fields", patch.fields)
                                            },
                                        )
                                        put(
                                            "updateMask",
                                            buildJsonObject {
                                                put(
                                                    "fieldPaths",
                                                    buildJsonArray {
                                                        patch.updateMask.forEach { fieldPath ->
                                                            add(JsonPrimitive(fieldPath))
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    )
                }.toString(),
            ),
        )
    }

    private suspend fun authenticatedRequest(
        request: FirebaseJsFirestoreRequest,
    ): FirebaseJsFirestoreHttpResponse {
        val idToken = tokenProvider.getFreshIdToken()
        return transport.execute(
            request.copy(
                headers = request.headers + ("Authorization" to "Bearer $idToken"),
            ),
        )
    }

    private suspend fun request(request: FirebaseJsFirestoreRequest): FirebaseJsFirestoreHttpResponse {
        val response = authenticatedRequest(request)
        if (response.statusCode in 200..299) {
            return response
        }
        error("Firestore request failed (${response.statusCode}): ${extractFirestoreErrorMessage(response.body)}")
    }

    private fun documentsUrl(path: String): String =
        if (path.isBlank()) {
            "$databaseUrl/documents"
        } else {
            "$databaseUrl/documents/$path"
        }

    private fun fullDocumentName(path: String): String =
        "projects/$projectId/databases/(default)/documents/$path"

    private fun runQueryUrl(parentPath: String): String =
        if (parentPath.isBlank()) {
            "$databaseUrl/documents:runQuery"
        } else {
            "$databaseUrl/documents/$parentPath:runQuery"
        }

    private val databaseUrl: String
        get() = "$BASE_URL/projects/$projectId/databases/(default)"

    private companion object {
        const val BASE_URL = "https://firestore.googleapis.com/v1"
    }
}

internal data class FirestoreJsDocument(
    val name: String,
    val fields: JsonObject,
    val updateTime: String? = null,
) {
    val id: String
        get() = name.substringAfterLast('/')
}

internal data class FirestoreJsDocumentPatch(
    val documentPath: String,
    val fields: JsonObject,
    val updateMask: List<String>,
)

internal data class FirebaseJsFirestoreRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

internal data class FirebaseJsFirestoreHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface FirebaseJsFirestoreTransport {
    suspend fun execute(request: FirebaseJsFirestoreRequest): FirebaseJsFirestoreHttpResponse
}

internal class BrowserFetchFirebaseJsFirestoreTransport : FirebaseJsFirestoreTransport {

    override suspend fun execute(request: FirebaseJsFirestoreRequest): FirebaseJsFirestoreHttpResponse {
        val init = js("{}")
        init.method = request.method
        init.headers = json()
        init.headers["Accept"] = "application/json"
        request.headers.forEach { (name, value) ->
            init.headers[name] = value
        }
        if (request.body != null) {
            init.body = request.body
            init.headers["Content-Type"] = "application/json; charset=utf-8"
        }
        val response = window.fetch(request.url, init).await()
        return FirebaseJsFirestoreHttpResponse(
            statusCode = response.status.toInt(),
            body = response.text().await(),
        )
    }
}

internal fun firestoreString(value: String): JsonObject =
    buildJsonObject { put("stringValue", JsonPrimitive(value)) }

internal fun firestoreBoolean(value: Boolean): JsonObject =
    buildJsonObject { put("booleanValue", JsonPrimitive(value)) }

internal fun firestoreInteger(value: Int): JsonObject =
    buildJsonObject { put("integerValue", JsonPrimitive(value.toString())) }

internal fun firestoreLong(value: Long): JsonObject =
    buildJsonObject { put("integerValue", JsonPrimitive(value.toString())) }

internal fun firestoreTimestamp(value: Instant): JsonObject =
    buildJsonObject { put("timestampValue", JsonPrimitive(value.toString())) }

internal fun firestoreNull(): JsonObject =
    buildJsonObject { put("nullValue", JsonPrimitive("NULL_VALUE")) }

internal fun firestoreArray(vararg values: JsonObject): JsonObject =
    buildJsonObject {
        put(
            "arrayValue",
            buildJsonObject {
                if (values.isNotEmpty()) {
                    put("values", JsonArray(values.toList()))
                }
            },
        )
    }

internal fun JsonObject.firestoreString(field: String): String? =
    get(field)?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull

internal fun JsonObject.firestoreBoolean(field: String): Boolean? =
    get(field)?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.firestoreInt(field: String): Int? =
    get(field)?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

internal fun JsonObject.firestoreLong(field: String): Long? =
    get(field)?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toLongOrNull()

internal fun JsonObject.firestoreInstant(field: String): Instant? =
    get(field)?.jsonObject?.get("timestampValue")?.jsonPrimitive?.contentOrNull?.let(Instant::parse)

internal fun resolveWebFirebaseProjectId(): String =
    document.documentElement
        ?.getAttribute(WEB_FIREBASE_PROJECT_ID_ATTRIBUTE)
        ?.takeUnless { it.isBlank() }
        ?: error(
            "Missing Firebase project id for Web Firestore. " +
                "Set secretaria.firebaseProjectId in local.properties, " +
                "a Gradle property, SECRETARIA_FIREBASE_PROJECT_ID, " +
                "or keep androidApp/google-services.json available locally.",
        )

private external fun encodeURIComponent(value: String): String

private fun kotlinx.serialization.json.JsonElement.asFirestoreJsDocument(): FirestoreJsDocument {
    val objectValue = jsonObject
    return FirestoreJsDocument(
        name = objectValue["name"]?.jsonPrimitive?.content
            ?: error("Missing Firestore document name"),
        fields = objectValue["fields"]?.jsonObject ?: JsonObject(emptyMap()),
        updateTime = objectValue["updateTime"]?.jsonPrimitive?.contentOrNull,
    )
}

private fun extractFirestoreErrorMessage(body: String): String =
    runCatching {
        Json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
    }.getOrNull() ?: body
