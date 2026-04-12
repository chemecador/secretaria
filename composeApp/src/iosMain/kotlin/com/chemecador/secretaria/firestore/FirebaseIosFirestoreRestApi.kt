@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.chemecador.secretaria.firestore

import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
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
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.addValue
import platform.Foundation.appendData
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

internal class FirebaseIosFirestoreRestApi(
    private val projectId: String,
    private val tokenProvider: FirebaseIosIdTokenProvider,
    private val transport: FirebaseIosFirestoreTransport = NSURLSessionFirebaseIosFirestoreTransport(),
) {

    suspend fun listDocuments(
        collectionPath: String,
        orderBy: String? = null,
    ): List<FirestoreIosDocument> {
        val response = request(
            FirebaseIosFirestoreRequest(
                method = "GET",
                url = buildString {
                    append(documentsUrl(collectionPath))
                    orderBy?.let {
                        append("?orderBy=")
                        append(percentEncode(it))
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
    ): FirestoreIosDocument {
        val response = request(
            FirebaseIosFirestoreRequest(
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
    ): FirestoreIosDocument {
        val query = updateMask.joinToString("&") { field ->
            "updateMask.fieldPaths=${percentEncode(field)}"
        }
        val response = request(
            FirebaseIosFirestoreRequest(
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
            FirebaseIosFirestoreRequest(
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
            FirebaseIosFirestoreRequest(
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

    private suspend fun request(request: FirebaseIosFirestoreRequest): FirebaseIosFirestoreHttpResponse {
        val idToken = tokenProvider.getFreshIdToken()
        val response = transport.execute(
            request.copy(
                headers = request.headers + ("Authorization" to "Bearer $idToken"),
            ),
        )

        if (response.statusCode in 200..299) {
            return response
        }

        error(
            "Firestore request failed (${response.statusCode}): ${
                extractFirestoreErrorMessage(
                    response.body
                )
            }"
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

internal data class FirestoreIosDocument(
    val name: String,
    val fields: JsonObject,
) {
    val id: String
        get() = name.substringAfterLast('/')
}

internal data class FirebaseIosFirestoreRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

internal data class FirebaseIosFirestoreHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal interface FirebaseIosFirestoreTransport {
    suspend fun execute(request: FirebaseIosFirestoreRequest): FirebaseIosFirestoreHttpResponse
}

internal class NSURLSessionFirebaseIosFirestoreTransport : FirebaseIosFirestoreTransport {

    override suspend fun execute(request: FirebaseIosFirestoreRequest): FirebaseIosFirestoreHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val nsUrl = NSURL.URLWithString(request.url)
                ?: throw IllegalArgumentException("Invalid URL: ${request.url}")
            val urlRequest = NSMutableURLRequest(uRL = nsUrl)
            urlRequest.HTTPMethod = request.method
            request.body?.let { body ->
                @Suppress("CAST_NEVER_SUCCEEDS")
                urlRequest.HTTPBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                urlRequest.addValue(
                    "application/json; charset=utf-8",
                    forHTTPHeaderField = "Content-Type"
                )
            }
            urlRequest.addValue("application/json", forHTTPHeaderField = "Accept")
            request.headers.forEach { (name, value) ->
                urlRequest.addValue(value, forHTTPHeaderField = name)
            }

            val delegate = FirestoreTaskDelegate(continuation)
            val session = NSURLSession.sessionWithConfiguration(
                configuration = NSURLSessionConfiguration.defaultSessionConfiguration,
                delegate = delegate,
                delegateQueue = null,
            )

            val task = session.dataTaskWithRequest(urlRequest)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
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

private class FirestoreTaskDelegate(
    private val continuation: CancellableContinuation<FirebaseIosFirestoreHttpResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {

    private val receivedData = NSMutableData()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        receivedData.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        session.finishTasksAndInvalidate()

        if (didCompleteWithError != null) {
            continuation.resumeWithException(
                Exception(didCompleteWithError.localizedDescription),
            )
            return
        }

        val httpResponse = task.response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode?.toInt() ?: 0
        val responseBody = receivedData.toKotlinString()

        continuation.resume(FirebaseIosFirestoreHttpResponse(statusCode, responseBody))
    }
}

private fun NSData.toKotlinString(): String {
    val length = this.length.toInt()
    if (length == 0) {
        return ""
    }
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes.decodeToString()
}

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append(byte.toUByte().toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.asFirestoreDocumentOrNull(): FirestoreIosDocument? =
    runCatching { asFirestoreDocument() }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.asFirestoreDocument(): FirestoreIosDocument {
    val objectValue = jsonObject
    return FirestoreIosDocument(
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
