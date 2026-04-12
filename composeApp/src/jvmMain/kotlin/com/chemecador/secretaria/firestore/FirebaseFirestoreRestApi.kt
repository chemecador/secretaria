package com.chemecador.secretaria.firestore

import com.chemecador.secretaria.login.FirebaseIdTokenProvider
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.time.Instant

internal class FirebaseFirestoreRestApi(
    private val projectId: String,
    private val tokenProvider: FirebaseIdTokenProvider,
    private val transport: FirebaseFirestoreTransport = HttpClientFirebaseFirestoreTransport(),
) {

    suspend fun listDocuments(
        collectionPath: String,
        orderBy: String? = null,
    ): List<FirestoreDocument> {
        val response = request(
            FirebaseFirestoreRequest(
                method = "GET",
                url = buildString {
                    append(documentsUrl(collectionPath))
                    orderBy?.let {
                        append("?orderBy=")
                        append(URLEncoder.encode(it, StandardCharsets.UTF_8))
                    }
                },
            ),
        )
        val root = parseJsonObject(response.body)
        val documents = root["documents"]?.jsonArray ?: JsonArray(emptyList())
        return documents.mapNotNull { it.asFirestoreDocumentOrNull() }
    }

    suspend fun createDocument(
        parentPath: String,
        collectionId: String,
        fields: JsonObject,
    ): FirestoreDocument {
        val response = request(
            FirebaseFirestoreRequest(
                method = "POST",
                url = "${documentsUrl(parentPath)}/$collectionId",
                body = buildJsonObject {
                    put("fields", fields)
                }.toString(),
            ),
        )
        return parseJsonObject(response.body).asFirestoreDocument()
    }

    suspend fun patchDocument(
        documentPath: String,
        fields: JsonObject,
        updateMask: List<String>,
    ): FirestoreDocument {
        val query = updateMask.joinToString("&") { field ->
            "updateMask.fieldPaths=${URLEncoder.encode(field, StandardCharsets.UTF_8)}"
        }
        val response = request(
            FirebaseFirestoreRequest(
                method = "PATCH",
                url = "${documentsUrl(documentPath)}?$query",
                body = buildJsonObject {
                    put("fields", fields)
                }.toString(),
            ),
        )
        return parseJsonObject(response.body).asFirestoreDocument()
    }

    suspend fun deleteDocument(documentPath: String) {
        request(
            FirebaseFirestoreRequest(
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
            FirebaseFirestoreRequest(
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

    private suspend fun request(request: FirebaseFirestoreRequest): FirebaseFirestoreHttpResponse {
        val idToken = tokenProvider.getFreshIdToken()
        val response = try {
            transport.execute(
                request.copy(
                    headers = request.headers + ("Authorization" to "Bearer $idToken"),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        }

        if (response.statusCode in 200..299) {
            return response
        }

        throw IllegalStateException(
            "Firestore request failed (${response.statusCode}): ${extractFirestoreErrorMessage(response.body)}",
        )
    }

    private fun documentsUrl(path: String): String =
        if (path.isBlank()) {
            "$databaseUrl/documents"
        } else {
            "$databaseUrl/documents/$path"
        }

    private fun fullDocumentName(path: String): String =
        "projects/$projectId/databases/(default)/documents/$path"

    private val databaseUrl: String
        get() = "$BASE_URL/projects/$projectId/databases/(default)"

    private companion object {
        const val BASE_URL = "https://firestore.googleapis.com/v1"
    }
}

internal data class FirestoreDocument(
    val name: String,
    val fields: JsonObject,
) {
    val id: String
        get() = name.substringAfterLast('/')
}

internal data class FirebaseFirestoreRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

internal data class FirebaseFirestoreHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface FirebaseFirestoreTransport {
    suspend fun execute(request: FirebaseFirestoreRequest): FirebaseFirestoreHttpResponse
}

internal class HttpClientFirebaseFirestoreTransport(
    private val client: HttpClient = HttpClient.newHttpClient(),
) : FirebaseFirestoreTransport {

    override suspend fun execute(request: FirebaseFirestoreRequest): FirebaseFirestoreHttpResponse =
        withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url))
                .header("Accept", "application/json")
            request.headers.forEach { (name, value) ->
                builder.header(name, value)
            }
            if (request.body != null) {
                builder.header("Content-Type", "application/json; charset=utf-8")
                builder.method(
                    request.method,
                    HttpRequest.BodyPublishers.ofString(request.body, StandardCharsets.UTF_8),
                )
            } else {
                builder.method(request.method, HttpRequest.BodyPublishers.noBody())
            }
            val response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
            FirebaseFirestoreHttpResponse(
                statusCode = response.statusCode(),
                body = response.body(),
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

private fun kotlinx.serialization.json.JsonElement.asFirestoreDocumentOrNull(): FirestoreDocument? =
    runCatching { asFirestoreDocument() }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.asFirestoreDocument(): FirestoreDocument {
    val objectValue = jsonObject
    return FirestoreDocument(
        name = objectValue["name"]?.jsonPrimitive?.content
            ?: error("Missing Firestore document name"),
        fields = objectValue["fields"]?.jsonObject ?: JsonObject(emptyMap()),
    )
}

private fun parseJsonObject(body: String): JsonObject =
    Json.parseToJsonElement(body).jsonObject

private fun extractFirestoreErrorMessage(body: String): String =
    runCatching {
        Json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
    }.getOrNull() ?: body
