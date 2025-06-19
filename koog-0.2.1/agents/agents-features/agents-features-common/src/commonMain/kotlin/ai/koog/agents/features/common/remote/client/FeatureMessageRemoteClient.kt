package ai.koog.agents.features.common.remote.client

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.remote.client.config.ClientConnectionConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.serializer
import kotlin.properties.Delegates

/**
 * Platform-specific HTTP client engine factory provider.
 * Each platform (JVM, Native, JS) implements this function to provide appropriate HTTP client engine.
 *
 * @return HTTP client engine factory for the current platform
 */
internal expect fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig>

/**
 * A remote client implementation for handling feature messages via HTTP and Server-Sent Events (SSE).
 *
 * Note: Please make sure you call [connect] before starting a communication process.
 *       If the server is not available, the method will throw the [IllegalStateException] exception.
 *
 * @property connectionConfig The configuration for connecting to the remote server, including server URL and JSON settings.
 * @property scope The coroutine scope used to manage background tasks.
 * @property requestBuilder An optional HTTP request customization builder applied to HTTP requests.
 */
public class FeatureMessageRemoteClient(
    public val connectionConfig: ClientConnectionConfig,
    private val scope: CoroutineScope,
    baseClient: HttpClient = HttpClient(engineFactoryProvider()),
    private val requestBuilder: HttpRequestBuilder.() -> Unit = {}
) : FeatureMessageClient {

    private companion object {
        private val logger = KotlinLogging.logger {  }
    }

    private var isInitialized = false

    private var session: ClientSSESession by Delegates.notNull()

    private var sseJob: Job? = null

    private val client: HttpClient = baseClient.prepare()


    /**
     * Indicates whether the client is currently connected to the remote feature messaging service.
     *
     * This property reflects the connection state of the `FeatureMessageRemoteClient`.
     * It is `true` if the client has successfully established a valid connection with the remote service,
     * and `false` otherwise. The connectivity is derived from the internal initialization state
     * of the client.
     */
    override val isConnected: Boolean
        get() = isInitialized

    /**
     * A communication channel for receiving feature messages or events.
     *
     * This property is an instance of a [Channel] configured with unlimited capacity, ensuring
     * that it can store any number of incoming `FeatureMessage` instances without blocking the sender.
     * It is used to facilitate asynchronous communication with the remote feature messaging service
     * by receiving and managing the lifecycle of feature-related messages.
     *
     * Key behaviors:
     * - Messages received from the remote service are sent through this channel for processing.
     * - During the lifecycle of the client, messages are collected and handled as they arrive.
     * - Closing the channel indicates the termination of the receiving process when the client shuts down.
     */
    public val receivedMessages: Channel<FeatureMessage> = Channel(Channel.UNLIMITED)

    //region Connect / Stop

    override suspend fun connect() {
        logger.info { "Feature Message Remote Client. Start connecting to server: ${connectionConfig.url}" }

        if (isInitialized) {
            logger.warn { "Feature Message Remote Client. Client is already connected. Skip initialization." }
            return
        }

        createSSESession()

        logger.info { "Feature Message Remote Client. Client is connected successfully to server: ${connectionConfig.url}" }
        isInitialized = true
    }

    override suspend fun close() {
        logger.info { "Feature Message Remote Client. Closing client: ${connectionConfig.url}" }

        if (!isInitialized) {
            logger.warn { "Feature Message Remote Client. Client is already stopped. Skip stopping." }
            return
        }

        logger.debug {
            "Feature Message Remote Client. Closing client status:\n" +
                    "* session.isActive: ${session.isActive}\n  " +
                    "* sseJob.isActive:  ${sseJob?.isActive}\n  " +
                    "* client.isActive:  ${client.isActive}"
        }

        receivedMessages.close()

        if (sseJob?.isActive == true) {
            sseJob?.cancelAndJoin()
        }

        if (session.isActive) {
            session.cancel()
        }

        if (client.isActive) {
            client.close()
        }

        isInitialized = false

        logger.info { "Feature Message Remote Client. Client is successfully closed: ${connectionConfig.url}" }
    }

    //endregion Connect / Stop

    //region Messages

    override suspend fun send(message: FeatureMessage) {
        val messageBody = connectionConfig.jsonConfig.encodeToString(
            serializer = connectionConfig.jsonConfig.serializersModule.serializer(),
            value = message
        )

        val response = post(url = connectionConfig.messageUrl) {
            applyHeaders()
            contentType(ContentType.Application.Json)
            setBody(messageBody)
        }

        if (response.status != HttpStatusCode.OK) {
            val status = response.status.value
            val errorMessage = response.body<String>()
            error("Failed to send message: $message. Response (status: $status, message: $errorMessage)")
        }
    }

    override suspend fun healthCheck() {
        val response = get(url = connectionConfig.healthCheckUrl)

        if (response.status != HttpStatusCode.OK) {
            error("Health check server health. Response status: ${response.status}")
        }
    }

    //endregion Messages

    //region Private Methods

    //region Initialize

    private fun HttpClient.prepare(): HttpClient {
        logger.debug { "Feature Message Remote Client. Create HTTP client" }

        return this.config {
            install(SSE)

            install(ContentNegotiation) {
                json(connectionConfig.jsonConfig)
            }

            install(Logging) {
                val ktorLogger = FeatureMessageRemoteClientKtorLogger()
                level = if (ktorLogger.debugEnabled) LogLevel.ALL else LogLevel.NONE
                logger = ktorLogger
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }

            install(HttpTimeout) {
                connectTimeoutMillis = connectionConfig.connectTimeout?.inWholeMilliseconds
                requestTimeoutMillis = connectionConfig.requestTimeout?.inWholeMilliseconds
            }
        }
    }

    private suspend fun createSSESession() {
        logger.debug { "Feature Message Remote Client. Init SSE Session" }

        session = client.sseSession(
            urlString = connectionConfig.sseUrl,
            reconnectionTime = connectionConfig.reconnectionDelay,
            block = requestBuilder,
        )

        sseJob = scope.launch {
            session.incoming.collect { serverEvent: ServerSentEvent ->
                try {
                    val featureMessage = serverEvent.toFeatureMessage()
                    receivedMessages.send(featureMessage)
                }
                catch (t: CancellationException) {
                    logger.info { "Feature Message Remote Client. Client SSE reading has been cancelled: ${t.message}" }
                    throw t
                }
                catch (t: Throwable) {
                    logger.error(t) { "Feature Message Remote Client. Client SSE reading received an error: ${t.message}" }
                }
            }
        }
    }

    //endregion Initialize

    //region Message

    private suspend fun get(url: String): HttpResponse {
        return client.get(url)
    }

    private suspend fun post(url: String, block: HttpRequestBuilder.() -> Unit): HttpResponse {
        return client.post(urlString = url, block = block)
    }

    private fun ServerSentEvent.toFeatureMessage(): FeatureMessage {
        val dataString = data ?: error("Failed to get data from a message")

        val deserialized: FeatureMessage = when (event) {
            FeatureMessage.Type.Message.value -> {
                connectionConfig.jsonConfig.decodeFromString<FeatureMessage>(string = dataString)
            }

            else -> {
                connectionConfig.jsonConfig.decodeFromString(
                    deserializer = connectionConfig.jsonConfig.serializersModule.serializer(),
                    string = dataString
                )
            }
        }

        return deserialized
    }

    private fun applyHeaders() {
        headers {
            connectionConfig.headers.forEach { (key, value) ->
                append(key, value)
            }
        }
    }

    //endregion Message

    //endregion Private Methods
}
