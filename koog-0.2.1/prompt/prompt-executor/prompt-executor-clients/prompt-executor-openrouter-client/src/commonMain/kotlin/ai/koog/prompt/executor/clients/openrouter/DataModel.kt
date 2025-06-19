package ai.koog.prompt.executor.clients.openrouter

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline

@Serializable
internal data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double? = null,
    val tools: List<OpenRouterTool>? = null,
    val stream: Boolean = false,
    val toolChoice: OpenRouterToolChoice? = null
)

@Serializable
internal data class OpenRouterMessage(
    val role: String,
    @Serializable(with = ContentSerializer::class)
    val content: Content? = null,
    val toolCalls: List<OpenRouterToolCall>? = null,
    val name: String? = null,
    val toolCallId: String? = null
)

@Serializable
internal sealed interface Content {
    fun text(): String

    @JvmInline
    value class Text(val value: String) : Content {
        override fun text(): String = value
    }

    @JvmInline
    value class Parts(val value: List<ContentPart>) : Content {
        override fun text(): String = value
            .filterIsInstance<ContentPart.Text>()
            .joinToString("\n") { it.text }
    }
}

@Serializable
internal sealed interface ContentPart {
    val type: String

    @Serializable
    data class Text(val text: String, override val type: String = "text") : ContentPart

    @Serializable
    data class Image(val imageUrl: ImageUrl, override val type: String = "image_url") : ContentPart

    @Serializable
    data class ImageUrl(val url: String)

    @Serializable
    data class Audio(val inputAudio: InputAudio, override val type: String = "input_audio") : ContentPart

    /**
     * @property data Base64 encoded audio data
     * @property format The format of the encoded audio data. Currently, it supports "wav" and "mp3"
     */
    @Serializable
    data class InputAudio(val data: String, val format: String)

    @Serializable
    data class File(val file: FileData, override val type: String = "file") : ContentPart

    @Serializable
    data class FileData(val fileData: String?, val fileId: String? = null, val filename: String? = null)
}

@Serializable
internal data class OpenRouterToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenRouterFunction
)

@Serializable
internal data class OpenRouterFunction(
    val name: String,
    val arguments: String
)

@Serializable
internal data class OpenRouterTool(
    val type: String = "function",
    val function: OpenRouterToolFunction
)

@Serializable
internal data class OpenRouterToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
internal data class OpenRouterResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage? = null
)

@Serializable
internal data class OpenRouterChoice(
    val index: Int,
    val message: OpenRouterMessage,
    val finishReason: String? = null
)

@Serializable
internal data class OpenRouterUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
internal data class OpenRouterStreamResponse(
    val id: String,
    @SerialName("object") val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenRouterStreamChoice>
)

@Serializable
internal data class OpenRouterStreamChoice(
    val index: Int,
    val delta: OpenRouterStreamDelta,
    val finishReason: String? = null
)

@Serializable
internal data class OpenRouterStreamDelta(
    val role: String? = null,
    val content: String? = null,
    val toolCalls: List<OpenRouterToolCall>? = null
)


@Serializable
internal sealed interface OpenRouterToolChoice {
    @JvmInline
    @Serializable
    value class Choice internal constructor(val value: String) : OpenRouterToolChoice

    @Serializable
    data class FunctionName(val name: String)

    @Serializable
    data class Function(val name: FunctionName) : OpenRouterToolChoice {
        val type: String = "function"
    }


    companion object {
        // OpenAI api is too "dynamic", have to inline value here, so alas, no proper classes hierarchy, creating "objects" instead
        val Auto = Choice("auto")
        val Required = Choice("required")
        val None = Choice("none")
    }
}

internal object ContentSerializer : KSerializer<Content?> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Content", PolymorphicKind.SEALED)

    override fun serialize(encoder: Encoder, value: Content?) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            null -> jsonEncoder.encodeNull()
            is Content.Text -> jsonEncoder.encodeString(value.value)
            is Content.Parts -> jsonEncoder.encodeSerializableValue(
                ListSerializer(ContentPart.serializer()),
                value.value
            )
        }
    }

    override fun deserialize(decoder: Decoder): Content? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> Content.Text(element.content)
            is JsonArray -> Content.Parts(
                jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(ContentPart.serializer()),
                    element
                )
            )

            else -> throw SerializationException("Content must be either a string or an array")
        }
    }
}
