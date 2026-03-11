package com.dgurnick.banking.client

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "Banking.GraphQlClient"
private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Sealed result emitted by [BankingGraphQlClient.subscribe]. */
sealed class StreamEvent {
    data class SurfaceUpdate(val payload: SurfaceUpdatePayload) : StreamEvent()
    data class DataModelUpdate(val payload: DataModelUpdatePayload) : StreamEvent()
    data class BeginRendering(val payload: BeginRenderingPayload) : StreamEvent()
    data class DeleteSurface(val payload: DeleteSurfacePayload) : StreamEvent()
    data class StreamError(val message: String, val cause: Throwable? = null) : StreamEvent()
    object StreamClosed : StreamEvent()
}

/**
 * Communicates with the BFF via GraphQL.
 *
 * - Subscriptions: WebSocket using the
 * [graphql-ws protocol](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md)
 * - Mutations / Queries: plain HTTP POST to `/graphql`
 */
class BankingGraphQlClient(private val baseUrl: String) {

    private val subscriptionId = AtomicInteger(1)

    private val httpClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    private val wsClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
    }

    // ── Subscription (WebSocket / graphql-ws protocol) ────────────────────────

    /**
     * Opens a GraphQL subscription to `uiStream` and emits parsed [StreamEvent]s.
     *
     * Internally uses graphql-ws over WebSocket.
     */
    fun subscribe(prompt: String, surfaceId: String = "main"): Flow<StreamEvent> =
            callbackFlow {
                val wsUrl =
                        baseUrl.replace("http://", "ws://").replace("https://", "wss://") +
                                "/subscriptions"

                val id = subscriptionId.getAndIncrement().toString()
                val subscriptionQuery =
                        """
            subscription UiStream(${"$"}prompt: String!, ${"$"}surfaceId: String!) {
              uiStream(prompt: ${"$"}prompt, surfaceId: ${"$"}surfaceId)
            }
        """.trimIndent()

                val variables = buildJsonObject {
                    put("prompt", prompt)
                    put("surfaceId", surfaceId)
                }

                val request =
                        Request.Builder()
                                .url(wsUrl)
                                .addHeader("Sec-WebSocket-Protocol", "graphql-transport-ws")
                                .build()

                val ws =
                        wsClient.newWebSocket(
                                request,
                                object : WebSocketListener() {
                                    override fun onOpen(webSocket: WebSocket, response: Response) {
                                        // graphql-ws: send connection_init
                                        webSocket.send(
                                                """{"type":"connection_init","payload":{}}"""
                                        )
                                    }

                                    override fun onMessage(webSocket: WebSocket, text: String) {
                                        val msg =
                                                try {
                                                    jsonParser.parseToJsonElement(text).jsonObject
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "Failed to parse WS message: $text")
                                                    return
                                                }
                                        when (msg["type"]?.jsonPrimitive?.contentOrNull) {
                                            "connection_ack" -> {
                                                // Send the subscription request
                                                val payload = buildJsonObject {
                                                    put("query", subscriptionQuery)
                                                    put("variables", variables)
                                                }
                                                webSocket.send(
                                                        buildJsonObject {
                                                                    put("type", "subscribe")
                                                                    put("id", id)
                                                                    put("payload", payload)
                                                                }
                                                                .toString()
                                                )
                                            }
                                            "next" -> {
                                                val data =
                                                        msg["payload"]?.jsonObject?.get("data")
                                                                ?.jsonObject
                                                val jsonlLine =
                                                        data?.get("uiStream")
                                                                ?.jsonPrimitive
                                                                ?.contentOrNull
                                                if (jsonlLine != null) {
                                                    val event = parseJsonlLine(jsonlLine)
                                                    if (event != null) trySend(event)
                                                }
                                            }
                                            "error" -> {
                                                val errors =
                                                        msg["payload"]?.jsonArray?.toString()
                                                                ?: "Unknown GraphQL error"
                                                Log.e(TAG, "GraphQL subscription error: $errors")
                                                trySend(StreamEvent.StreamError(errors))
                                                close()
                                            }
                                            "complete" -> {
                                                Log.d(TAG, "Subscription complete (id=$id)")
                                                trySend(StreamEvent.StreamClosed)
                                                close()
                                            }
                                            "ping" -> webSocket.send("""{"type":"pong"}""")
                                        }
                                    }

                                    override fun onFailure(
                                            webSocket: WebSocket,
                                            t: Throwable,
                                            response: Response?
                                    ) {
                                        val msg =
                                                t.message
                                                        ?: response?.message ?: "WebSocket failure"
                                        Log.e(TAG, "WebSocket failure: $msg", t)
                                        trySend(StreamEvent.StreamError(msg, t))
                                        close(t)
                                    }

                                    override fun onClosed(
                                            webSocket: WebSocket,
                                            code: Int,
                                            reason: String
                                    ) {
                                        Log.d(TAG, "WebSocket closed: $code $reason")
                                        trySend(StreamEvent.StreamClosed)
                                        close()
                                    }
                                }
                        )

                awaitClose {
                    // Send stop message before closing
                    ws.send(
                            buildJsonObject {
                                        put("type", "complete")
                                        put("id", id)
                                    }
                                    .toString()
                    )
                    ws.cancel()
                }
            }

    // ── Raw subscription (emits JSON strings for Remote Compose rendering) ───

    /**
     * Opens a GraphQL subscription to `uiStream` and emits raw JSON strings. Each string is a
     * single typed JSON object produced by a BFF agent.
     */
    fun subscribeRaw(prompt: String, surfaceId: String = "main"): Flow<String> = callbackFlow {
        val wsUrl =
                baseUrl.replace("http://", "ws://").replace("https://", "wss://") + "/subscriptions"

        val id = subscriptionId.getAndIncrement().toString()
        val subscriptionQuery =
                """
            subscription UiStream(${"$"}prompt: String!, ${"$"}surfaceId: String!) {
              uiStream(prompt: ${"$"}prompt, surfaceId: ${"$"}surfaceId)
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("prompt", prompt)
            put("surfaceId", surfaceId)
        }

        val request =
                Request.Builder()
                        .url(wsUrl)
                        .addHeader("Sec-WebSocket-Protocol", "graphql-transport-ws")
                        .build()

        val ws =
                wsClient.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                webSocket.send("""{"type":"connection_init","payload":{}}""")
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                val msg =
                                        try {
                                            jsonParser.parseToJsonElement(text).jsonObject
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to parse WS message: $text")
                                            return
                                        }
                                when (msg["type"]?.jsonPrimitive?.contentOrNull) {
                                    "connection_ack" -> {
                                        val payload = buildJsonObject {
                                            put("query", subscriptionQuery)
                                            put("variables", variables)
                                        }
                                        webSocket.send(
                                                buildJsonObject {
                                                            put("type", "subscribe")
                                                            put("id", id)
                                                            put("payload", payload)
                                                        }
                                                        .toString()
                                        )
                                    }
                                    "next" -> {
                                        val data =
                                                msg["payload"]?.jsonObject?.get("data")?.jsonObject
                                        val line =
                                                data?.get("uiStream")?.jsonPrimitive?.contentOrNull
                                        if (line != null) trySend(line)
                                    }
                                    "error" -> {
                                        val errors =
                                                msg["payload"]?.jsonArray?.toString()
                                                        ?: "Unknown GraphQL error"
                                        Log.e(TAG, "GraphQL subscription error: $errors")
                                        close(Exception(errors))
                                    }
                                    "complete" -> {
                                        Log.d(TAG, "Subscription complete (id=$id)")
                                        close()
                                    }
                                    "ping" -> webSocket.send("""{"type":"pong"}""")
                                }
                            }

                            override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                val msg = t.message ?: response?.message ?: "WebSocket failure"
                                Log.e(TAG, "WebSocket failure: $msg", t)
                                close(t)
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                Log.d(TAG, "WebSocket closed: $code $reason")
                                close()
                            }
                        }
                )

        awaitClose {
            ws.send(
                    buildJsonObject {
                                put("type", "complete")
                                put("id", id)
                            }
                            .toString()
            )
            ws.cancel()
        }
    }

    // ── Mutation (HTTP POST) ──────────────────────────────────────────────────

    /** Send a `sendUserAction` mutation to the BFF. */
    suspend fun sendUserAction(action: UserActionPayload) {
        val mutation =
                """
            mutation SendUserAction(${"$"}input: UserActionInput!) {
              sendUserAction(input: ${"$"}input) { status received }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            putJsonObject("input") {
                put("name", action.name)
                put("surfaceId", action.surfaceId)
                put("sourceComponentId", action.sourceComponentId)
                put("timestamp", action.timestamp)
                put("context", action.context.toString())
            }
        }

        graphQlPost(mutation, variables)
    }

    /** Send a `reportError` mutation to the BFF. */
    suspend fun reportError(message: String, componentId: String? = null) {
        val mutation =
                """
            mutation ReportError(${"$"}input: ClientErrorInput!) {
              reportError(input: ${"$"}input) { status }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            putJsonObject("input") {
                put("message", message)
                if (componentId != null) put("componentId", componentId)
            }
        }

        graphQlPost(mutation, variables)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun graphQlPost(query: String, variables: JsonObject): JsonObject? {
        val body =
                buildJsonObject {
                            put("query", query)
                            put("variables", variables)
                        }
                        .toString()
                        .toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url("$baseUrl/graphql").post(body).build()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GraphQL POST failed: ${response.code}")
                        return@use null
                    }
                    response.body?.string()?.let { jsonParser.parseToJsonElement(it).jsonObject }
                }
            } catch (e: IOException) {
                Log.e(TAG, "GraphQL POST error", e)
                null
            }
        }
    }
}

// ─── JSONL line parser ────────────────────────────────────────────────────────

private fun parseJsonlLine(line: String): StreamEvent? {
    return try {
        val obj = jsonParser.parseToJsonElement(line).jsonObject
        when {
            "surfaceUpdate" in obj -> {
                val payload =
                        jsonParser.decodeFromJsonElement<SurfaceUpdatePayload>(
                                obj["surfaceUpdate"]!!
                        )
                StreamEvent.SurfaceUpdate(payload)
            }
            "dataModelUpdate" in obj -> {
                val payload =
                        jsonParser.decodeFromJsonElement<DataModelUpdatePayload>(
                                obj["dataModelUpdate"]!!
                        )
                StreamEvent.DataModelUpdate(payload)
            }
            "beginRendering" in obj -> {
                val payload =
                        jsonParser.decodeFromJsonElement<BeginRenderingPayload>(
                                obj["beginRendering"]!!
                        )
                StreamEvent.BeginRendering(payload)
            }
            "deleteSurface" in obj -> {
                val payload =
                        jsonParser.decodeFromJsonElement<DeleteSurfacePayload>(
                                obj["deleteSurface"]!!
                        )
                StreamEvent.DeleteSurface(payload)
            }
            else -> {
                Log.w(TAG, "Unknown A2UI message: $line")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse JSONL: $line", e)
        null
    }
}
