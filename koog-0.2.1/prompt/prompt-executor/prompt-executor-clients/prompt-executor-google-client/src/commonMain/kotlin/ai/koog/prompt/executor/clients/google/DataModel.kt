package ai.koog.prompt.executor.clients.google

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Represents the request body for the Google AI.
 *
 * @property contents The content of the current conversation with the model.
 * For single-turn queries, this is a single instance.
 * For multi-turn queries like [chat](https://ai.google.dev/gemini-api/docs/text-generation#chat),
 * this is a repeated field that contains the conversation history and the latest request.
 * @property tools A list of `Tools` the `Model` may use to generate the next response.
 * A `Tool` is a piece of code that enables the system to interact with external systems to perform an action,
 * or set of actions, outside of knowledge and scope of the `Model`.
 * Supported `Tools` are `Function` and `codeExecution`.
 * @property systemInstruction Developer set system instruction(s). Text only.
 * @property generationConfig Configuration options for model generation and outputs.
 */
@Serializable
internal class GoogleRequest(
    val contents: List<GoogleContent>,
    val tools: List<GoogleTool>? = null,
    val systemInstruction: GoogleContent? = null,
    val generationConfig: GoogleGenerationConfig? = null,
    val toolConfig: GoogleToolConfig? = null,
)

/**
 * The base structured datatype containing multipart content of a message.
 *
 * A `Content` includes a `role` field designating the producer of the `Content`
 * and a `parts` field containing multipart data that contains the content of the message turn.
 *
 * @property parts Ordered `Parts` that constitute a single message. Parts may have different MIME types.
 * @property role The producer of the content. Must be either 'user' or 'model'.
 * Useful to set for multi-turn conversations, otherwise it can be left blank or unset.
 */
@Serializable
internal class GoogleContent(
    val parts: List<GooglePart>,
    val role: String? = null,
)

/**
 * A datatype containing media that is part of a multipart `Content` message.
 *
 * A `Part` consists of data which has an associated datatype.
 * A `Part` can only contain one of the accepted types in `Part.data`.
 *
 * A Part must have a fixed IANA MIME type identifying the type and subtype of the media
 * if the `inlineData` field is filled with raw bytes.
 *
 * @property thought Indicates if the part is thought from the model.
 *
 * @property data data can be only one of the following:
 *   - [Text] - inline text.
 *   - [InlineData] - Inline media bytes.
 *   - [FunctionCall] - A predicted `FunctionCall` returned from the model that contains a string representing
 *   the `FunctionDeclaration.name` with the arguments and their values.
 *   - [FunctionResponse] - The result output of a `FunctionCall` that contains a string representing the
 *   `FunctionDeclaration.name` and a structured JSON object containing any output from the function is used as context to the model.
 *
 */
@Serializable(with = GooglePartSerializer::class)
internal sealed interface GooglePart {
    val thought: Boolean?

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        override val thought: Boolean? = null,
    ) : GooglePart

    @Serializable
    @SerialName("inlineData")
    data class InlineData(
        val inlineData: GoogleData.Blob,
        override val thought: Boolean? = null,
    ) : GooglePart

    @Serializable
    @SerialName("fileData")
    data class FileData(
        val fileData: GoogleData.FileData,
        override val thought: Boolean? = null,
    ) : GooglePart

    @Serializable
    @SerialName("functionCall")
    data class FunctionCall(
        val functionCall: GoogleData.FunctionCall,
        override val thought: Boolean? = null,
    ) : GooglePart

    @Serializable
    @SerialName("functionResponse")
    data class FunctionResponse(
        val functionResponse: GoogleData.FunctionResponse,
        override val thought: Boolean? = null,
    ) : GooglePart
}

/**
 * Represents data for [GooglePart]
 */
@Serializable
internal sealed interface GoogleData {

    /**
     * Raw media bytes.
     *
     * Text should not be sent as raw bytes, use the [GooglePart.Text].
     *
     * @property mimeType The IANA standard MIME type of the source data.
     *
     * Examples:
     *   - image/png
     *   - image/jpeg
     *
     * If an unsupported MIME type is provided, an error will be returned.
     * For a complete list of supported types, see [Supported file formats](https://ai.google.dev/gemini-api/docs/prompting_with_media#supported_file_formats).
     *
     * @property data Raw bytes for media formats. A base64-encoded string.
     */
    @Serializable
    class Blob(
        val mimeType: String,
        val data: String
    ) : GoogleData

    @Serializable
    class FileData(
        val mimeType: String,
        val fileUri: String,
    )


    /**
     * A predicted `FunctionCall` returned from the model that contains a string representing
     * the `FunctionDeclaration.name` with the arguments and their values.
     *
     * @property id The unique id of the function call.
     * If populated, the client is to execute the `functionCall` and return the response with the matching `id`.
     * @property name The name of the function to call.
     * Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 63.
     * @property args The function parameters and values in JSON object format.
     */
    @Serializable
    class FunctionCall(
        val id: String? = null,
        val name: String,
        val args: JsonObject? = null,
    ) : GoogleData

    /**
     * The result output from a `FunctionCall` that contains a string representing
     * the `FunctionDeclaration.name` and a structured JSON object containing
     * any output from the function is used as context to the model.
     * This should contain the result of a `FunctionCall` made based on model prediction.
     *
     * @property id The id of the function call this response is for.
     * Populated by the client to match the corresponding function call `id`.
     * @property name The name of the function to call.
     * Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 63.
     * @property response The function response in JSON object format.
     */
    @Serializable
    class FunctionResponse(
        val id: String? = null,
        val name: String,
        val response: JsonObject
    ) : GoogleData
}

/**
 * Tool details that the model may use to generate a response.
 *
 * A `Tool` is a piece of code that enables the system to interact with external systems to perform an action,
 * or set of actions, outside of knowledge and scope of the model.
 *
 * @property functionDeclarations A list of FunctionDeclarations available to the model
 * that can be used for function calling.
 * The model or system does not execute the function.
 * Instead, the defined function may be returned as a `FunctionCall` with arguments to the client side for execution.
 * The model may decide to call a subset of these functions by populating FunctionCall in the response.
 * The next conversation turn may contain a `FunctionResponse` with the `Content.role` "function" generation context for
 * the next model turn.
 */
@Serializable
internal class GoogleTool(
    val functionDeclarations: List<GoogleFunctionDeclaration>? = null,
)

/**
 * Structured representation of a function declaration as defined by the OpenAPI 3.03 specification.
 * Included in this declaration are the function name and parameters.
 * This `FunctionDeclaration` is a representation of a block of code
 * that can be used as a `Tool` by the model and executed by the client.
 *
 * @property name The name of the function.
 * Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 63.
 * @property description A brief description of the function.
 * @property parameters Describes the parameters to this function.
 * Reflects the Open API 3.03 Parameter Object string Key: the name of the parameter.
 * Parameter names are case-sensitive. Schema Value: the Schema defining the type used for the parameter.
 * @property response Describes the output from this function in JSON Schema format.
 * Reflects the Open API 3.03 Response Object. The Schema defines the type used for the response value of the function.
 */
@Serializable
internal class GoogleFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
    val response: JsonObject? = null,
)

/**
 * Configuration options for model generation and outputs. Not all parameters are configurable for every model.
 *
 * @property responseMimeType MIME type of the generated candidate text.
 * Supported MIME types are:
 *   - `text/plain`: (default) Text output.
 *   - `application/json`: JSON response in the response candidates.
 *   - `text/x.enum`: ENUM as a string response in the response candidates.
 * @property responseSchema Output schema of the generated candidate text.
 * Schemas must be a subset of the OpenAPI schema and can be objects, primitives, or arrays.
 * If set, a compatible [responseMimeType] must also be set.
 * Compatible MIME types: `application/json`: Schema for JSON response.
 * @property maxOutputTokens The maximum number of tokens to include in a response candidate.
 * @property temperature Controls the randomness of the output.
 * @property topP The maximum cumulative probability of tokens to consider when sampling.
 * @property topK The maximum number of tokens to consider when sampling.
 */
@Serializable
internal class GoogleGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
)

/**
 * Configuration for tool calling
 *
 * Allows specifying the tool calling mode (AUTO, ANY, NONE)
 *
 * @property functionCallingConfig See [GoogleFunctionCallingConfig]
 */
@Serializable
internal class GoogleToolConfig(
    val functionCallingConfig: GoogleFunctionCallingConfig? = null,
)

/**
 * Configuration for tool calling
 *
 * @property mode AUTO, ANY or NONE
 * @property allowedFunctionNames Allowlist of functions LLMis allowed to call
 */
@Serializable
internal class GoogleFunctionCallingConfig(
    val mode: GoogleFunctionCallingMode? = null,
    val allowedFunctionNames: List<String>? = null,
)

/**
 * Modes of tool calling: [AUTO], [ANY], [NONE]
 */
@Serializable
internal enum class GoogleFunctionCallingMode {
    /**
     * LLM automatically decides whether to call tool or generate text
     */
    @SerialName("auto")
    AUTO,

    /**
     * LLM will only call tools
     */
    @SerialName("any")
    ANY,

    /**
     * LLM will only generate text
     */
    @SerialName("none")
    NONE,
}

/**
 * Represents the response from the Google AI.
 *
 * @property candidates Candidate responses from the model.
 * @property promptFeedback Returns the prompt's feedback related to the content filters.
 * @property usageMetadata Metadata on the generation requests' token usage.
 * @property modelVersion The model version used to generate the response.
 */
@Serializable
internal class GoogleResponse(
    val candidates: List<GoogleCandidate>,
    val promptFeedback: GooglePromptFeedback? = null,
    val usageMetadata: GoogleUsageMetadata? = null,
    val modelVersion: String? = null,
)

/**
 * A response candidate generated from the model.
 *
 * @property content Generated content returned from the model.
 * @property finishReason The reason why the model stopped generating tokens.
 * If empty, the model has not stopped generating tokens.
 * @property safetyRatings List of ratings for the safety of a response candidate.
 * There is at most one rating per category.
 * @property index Index of the candidate in the list of response candidates.
 */
@Serializable
internal class GoogleCandidate(
    val content: GoogleContent? = null,
    val finishReason: String? = null,
    val safetyRatings: List<GoogleSafetyRating>? = null,
    val index: Int? = null,
)

/**
 * Safety rating for a piece of content.
 *
 * @property category The category for this rating
 * @property probability The probability of harm for this content
 * @property blocked Was this content blocked because of this rating?
 */
@Serializable
internal class GoogleSafetyRating(
    val category: String,
    val probability: String,
    val blocked: Boolean? = null,
)

/**
 * A set of the feedback metadata the prompt specified in GenerateContentRequest.content.
 *
 * @property blockReason If set, the prompt was blocked and no candidates are returned. Rephrase the prompt.
 * @property safetyRatings Ratings for safety of the prompt. There is at most one rating per category.
 */
@Serializable
internal class GooglePromptFeedback(
    val blockReason: String? = null,
    val safetyRatings: List<GoogleSafetyRating>? = null,
)

/**
 * Metadata on the generation request's token usage.
 *
 * @property promptTokenCount Number of tokens in the prompt.
 * When cachedContent is set, this is still the total effective prompt size,
 * meaning this includes the number of tokens in the cached content.
 * @property candidatesTokenCount Total number of tokens across all the generated response candidates.
 * @property toolUsePromptTokenCount Number of tokens present in tool-use prompt(s).
 * @property thoughtsTokenCount Number of tokens of thoughts for thinking models.
 * @property totalTokenCount Total token count for the generation request (prompt plus response candidates).
 */
@Serializable
internal class GoogleUsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int? = null,
    val toolUsePromptTokenCount: Int? = null,
    val thoughtsTokenCount: Int? = null,
    val totalTokenCount: Int,
)


internal object GooglePartSerializer : JsonContentPolymorphicSerializer<GooglePart>(GooglePart::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<GooglePart> {
        fun has(field: String) = element.jsonObject.containsKey(field)

        return when {
            has("text") -> GooglePart.Text.serializer()
            has("inlineData") -> GooglePart.InlineData.serializer()
            has("fileData") -> GooglePart.FileData.serializer()
            has("functionCall") -> GooglePart.FunctionCall.serializer()
            has("functionResponse") -> GooglePart.FunctionResponse.serializer()
            else -> throw SerializationException("Unknown Part variant: $element")
        }
    }
}