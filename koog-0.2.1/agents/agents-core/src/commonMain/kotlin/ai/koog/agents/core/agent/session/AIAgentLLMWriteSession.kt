package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.utils.ActiveProperty
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.StructuredDataDefinition
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.reflect.KClass

/**
 * A session for managing interactions with a language learning model (LLM)
 * and tools in an agent environment. This class provides functionality for executing
 * LLM requests, managing tools, and customizing prompts dynamically within a specific
 * session context.
 *
 * @property environment The agent environment that provides the session with tool execution
 * and error handling capabilities.
 * @property toolRegistry The registry containing tools available for use within the session.
 * @property clock The clock used for message timestamps
 */
public class AIAgentLLMWriteSession internal constructor(
    @PublishedApi internal val environment: AIAgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    @PublishedApi internal val toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    config: AIAgentConfigBase,
    public val clock: Clock
) : AIAgentLLMSession(executor, tools, prompt, model, config) {
    /**
     * Represents the prompt object used within the session. The prompt can be accessed or
     * modified only when the session is in an active state, as determined by the `isActive` predicate.
     *
     * This property uses the [ActiveProperty] delegate to enforce the validation of the session's
     * active state before any read or write operations.
     */
    override var prompt: Prompt by ActiveProperty(prompt) { isActive }

    /**
     * Represents a collection of tools that are available for the session.
     * The tools can be accessed or modified only if the session is in an active state.
     *
     * This property uses an [ActiveProperty] delegate to enforce the session's active state
     * as a prerequisite for accessing or mutating the tools list.
     *
     * The list contains tool descriptors, which define the tools' metadata, such as their
     * names, descriptions, and parameter requirements.
     */
    override var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }

    /**
     * Represents an override property `model` of type [LLModel].
     * This property is backed by an `ActiveProperty`, which ensures the property value is dynamically updated
     * based on the active state determined by the `isActive` parameter.
     *
     * This implementation allows for reactive behavior, ensuring that the `model` value is updated or resolved
     * only when the `isActive` condition changes.
     */
    override var model: LLModel by ActiveProperty(model) { isActive }

    /**
     * Executes the specified tool with the given arguments and returns the result within a [SafeTool.Result] wrapper.
     *
     * @param TArgs the type of arguments required by the tool, extending `Tool.Args`.
     * @param TResult the type of result returned by the tool, implementing `ToolResult`.
     * @param tool the tool to be executed.
     * @param args the arguments required to execute the tool.
     * @return a `SafeTool.Result` containing the tool's execution result of type `TResult`.
     */
    public suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        tool: Tool<TArgs, TResult>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        return findTool(tool::class).execute(args)
    }

    /**
     * Executes a tool by its name with the provided arguments.
     *
     * @param toolName The name of the tool to be executed.
     * @param args The arguments required to execute the tool, which must be a subtype of [Tool.Args].
     * @return A [SafeTool.Result] containing the result of the tool execution, which is a subtype of [ToolResult].
     */
    public suspend inline fun <reified TArgs : Tool.Args> callTool(
        toolName: String,
        args: TArgs
    ): SafeTool.Result<out ToolResult> {
        return findToolByName<TArgs>(toolName).execute(args)
    }

    /**
     * Executes a tool identified by its name with the provided arguments and returns the raw string result.
     *
     * @param toolName The name of the tool to be executed.
     * @param args The arguments to be passed to the tool, conforming to the [Tool.Args] type.
     * @return The raw result of the tool's execution as a String.
     */
    public suspend inline fun <reified TArgs : Tool.Args> callToolRaw(
        toolName: String,
        args: TArgs
    ): String {
        return findToolByName<TArgs>(toolName).executeRaw(args)
    }

    /**
     * Executes a tool operation based on the provided tool class and arguments.
     *
     * @param TArgs The type of arguments required by the tool.
     * @param TResult The type of result produced by the tool.
     * @param toolClass The class of the tool to be executed.
     * @param args The arguments to be passed to the tool for its execution.
     * @return A result wrapper containing either the successful result of the tool's execution or an error.
     */
    public suspend inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> callTool(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        args: TArgs
    ): SafeTool.Result<TResult> {
        val tool = findTool(toolClass)
        return tool.execute(args)
    }

    /**
     * Finds and retrieves a tool of the specified type from the tool registry.
     *
     * @param TArgs The type of arguments the tool accepts, extending from Tool.Args.
     * @param TResult The type of result the tool produces, extending from ToolResult.
     * @param toolClass The KClass reference that specifies the type of tool to find.
     * @return A SafeTool instance wrapping the found tool and its environment.
     * @throws IllegalArgumentException if the specified tool is not found in the tool registry.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findTool(toolClass: KClass<out Tool<TArgs, TResult>>): SafeTool<TArgs, TResult> {
        @Suppress("UNCHECKED_CAST")
        val tool = (toolRegistry.tools.find(toolClass::isInstance) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool with type ${toolClass.simpleName} is not defined"))

        return SafeTool(tool, environment, clock)
    }

    /**
     * Invokes a tool of the specified type with the provided arguments.
     *
     * @param args The input arguments required for the tool execution, represented as an instance of `Tool.Args`.
     * @return A `SafeTool.Result` containing the outcome of the tool's execution, which may be of any type that extends `ToolResult`.
     */
    public suspend inline fun <reified ToolT : Tool<*, *>> callTool(
        args: Tool.Args
    ): SafeTool.Result<out ToolResult> {
        val tool = findTool<ToolT>()
        return tool.executeUnsafe(args)
    }

    /**
     * Finds and retrieves a tool of the specified type from the current stage of the tool registry.
     * If no tool of the given type is found, an exception is thrown.
     *
     * @return An instance of SafeTool wrapping the tool of the specified type and the current environment.
     * @throws IllegalArgumentException if a tool of the given type is not defined in the tool registry.
     */
    public inline fun <reified ToolT : Tool<*, *>> findTool(): SafeTool<*, *> {
        val tool = toolRegistry.tools.find(ToolT::class::isInstance) as? ToolT
            ?: throw IllegalArgumentException("Tool with type ${ToolT::class.simpleName} is not defined")

        return SafeTool(tool, environment, clock)
    }

    /**
     * Transforms a flow of arguments into a flow of results by asynchronously executing the given tool in parallel.
     *
     * @param TArgs the type of the arguments required by the tool, extending Tool.Args.
     * @param TResult the type of the result produced by the tool, extending ToolResult.
     * @param safeTool the tool to be executed for each input argument.
     * @param concurrency the maximum number of parallel executions allowed. Defaults to 16.
     * @return a flow of results wrapped in SafeTool.Result for each input argument.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.execute(args))
        }
    }

    /**
     * Executes a flow of tool arguments in parallel by invoking the provided tool's raw execution method.
     * Converts each argument in the flow into a string result returned from the tool.
     *
     * @param safeTool The tool to execute, wrapped in a SafeTool to ensure safety during execution.
     * @param concurrency The maximum number of parallel calls to the tool. Default is 16.
     * @return A flow of string results derived from executing the tool's raw method.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        safeTool: SafeTool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<String> = flatMapMerge(concurrency) { args ->
        flow {
            emit(safeTool.executeRaw(args))
        }
    }

    /**
     * Executes the given tool in parallel for each element in the flow of arguments, up to the specified level of concurrency.
     *
     * @param TArgs The type of arguments consumed by the tool.
     * @param TResult The type of result produced by the tool.
     * @param tool The tool instance to be executed in parallel.
     * @param concurrency The maximum number of concurrent executions. Default value is 16.
     * @return A flow emitting the results of the tool executions wrapped in a SafeTool.Result object.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        tool: Tool<TArgs, TResult>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> = flatMapMerge(concurrency) { args ->
        val safeTool = findTool(tool::class)
        flow {
            emit(safeTool.execute(args))
        }
    }

    /**
     * Transforms a Flow of tool argument objects into a Flow of parallel tool execution results, using the specified tool class.
     *
     * @param TArgs The type of the tool arguments that the Flow emits.
     * @param TResult The type of the results produced by the tool.
     * @param toolClass The class of the tool to be invoked in parallel for processing the arguments.
     * @param concurrency The maximum number of parallel executions allowed. Default is 16.
     * @return A Flow containing the results of the tool executions, wrapped in `SafeTool.Result`.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCalls(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<SafeTool.Result<TResult>> {
        val tool = findTool(toolClass)
        return toParallelToolCalls(tool, concurrency)
    }

    /**
     * Converts a flow of arguments into a flow of raw string results by executing the corresponding tool calls in parallel.
     *
     * @param TArgs the type of arguments required by the tool.
     * @param TResult the type of result produced by the tool.
     * @param toolClass the class of the tool to be invoked.
     * @param concurrency the number of concurrent tool calls to be executed. Defaults to 16.
     * @return a flow of raw string results from the parallel tool calls.
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> Flow<TArgs>.toParallelToolCallsRaw(
        toolClass: KClass<out Tool<TArgs, TResult>>,
        concurrency: Int = 16
    ): Flow<String> {
        val tool = findTool(toolClass)
        return toParallelToolCallsRaw(tool, concurrency)
    }

    /**
     * Finds and retrieves a tool by its name and argument/result types.
     *
     * This function looks for a tool in the tool registry by its name and ensures that the tool
     * is compatible with the specified argument and result types. If no matching tool is found,
     * or if the specified types are incompatible, an exception is thrown.
     *
     * @param toolName the name of the tool to retrieve
     * @return the tool that matches the specified name and types
     * @throws IllegalArgumentException if the tool is not defined or the types are incompatible
     */
    public inline fun <reified TArgs : Tool.Args, reified TResult : ToolResult> findToolByNameAndArgs(toolName: String): Tool<TArgs, TResult> =
        @Suppress("UNCHECKED_CAST")
        (toolRegistry.getTool(toolName) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

    /**
     * Finds a tool by its name and ensures its arguments are compatible with the specified type.
     *
     * @param toolName The name of the tool to be retrieved.
     * @return A SafeTool instance wrapping the tool with the specified argument type.
     * @throws IllegalArgumentException If the tool with the specified name is not defined or its arguments
     * are incompatible with the expected type.
     */
    public inline fun <reified TArgs : Tool.Args> findToolByName(toolName: String): SafeTool<TArgs, *> {
        @Suppress("UNCHECKED_CAST")
        val tool = (toolRegistry.getTool(toolName) as? Tool<TArgs, *>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments"))

        return SafeTool(tool, environment, clock)
    }

    /**
     * Updates the current prompt by applying modifications defined in the provided block.
     * The modifications are applied using a `PromptBuilder` instance, allowing for
     * customization of the prompt's content, structure, and associated messages.
     *
     * @param body A lambda with a receiver of type `PromptBuilder` that defines
     *             the modifications to be applied to the current prompt.
     */
    public fun updatePrompt(body: PromptBuilder.() -> Unit) {
        prompt = prompt(prompt, clock, body)
    }

    /**
     * Rewrites the current prompt by applying a transformation function.
     *
     * @param body A lambda function that receives the current prompt and returns a modified prompt.
     */
    public fun rewritePrompt(body: (prompt: Prompt) -> Prompt) {
        prompt = body(prompt)
    }

    /**
     * Updates the underlying model in the current prompt with the specified new model.
     *
     * @param newModel The new LLModel to replace the existing model in the prompt.
     */
    public fun changeModel(newModel: LLModel) {
        model = newModel
    }

    /**
     * Updates the language model's parameters used in the current session prompt.
     *
     * @param newParams The new set of LLMParams to replace the existing parameters in the prompt.
     */
    public fun changeLLMParams(newParams: LLMParams): Unit = rewritePrompt {
        prompt.withParams(newParams)
    }

    /**
     * Sends a request to the Language Model (LLM) without including any tools, processes the response,
     * and updates the prompt with the returned message.
     *
     * LLM might answer only with a textual assistant message.
     *
     * @return the response from the LLM after processing the request, as a [Message.Response].
     */
    override suspend fun requestLLMWithoutTools(): Message.Response {
        return super.requestLLMWithoutTools().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests a response from the Language Learning Model (LLM) while also processing
     * the response by updating the current prompt with the received message.
     *
     * @return The response received from the Language Learning Model (LLM).
     */
    override suspend fun requestLLMOnlyCallingTools(): Message.Response {
        return super.requestLLMOnlyCallingTools().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests an LLM (Large Language Model) to forcefully utilize a specific tool during its operation.
     *
     * @param tool A descriptor object representing the tool to be enforced for use by the LLM.
     * @return A response message received from the LLM after executing the enforced tool request.
     */
    override suspend fun requestLLMForceOneTool(tool: ToolDescriptor): Message.Response {
        return super.requestLLMForceOneTool(tool).also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests the execution of a single specified tool, enforcing its use,
     * and updates the prompt based on the generated response.
     *
     * @param tool The tool that will be enforced and executed. It contains the input and output types.
     * @return The response generated after executing the provided tool.
     */
    override suspend fun requestLLMForceOneTool(tool: Tool<*, *>): Message.Response {
        return super.requestLLMForceOneTool(tool).also { response -> updatePrompt { message(response) } }
    }

    /**
     * Makes an asynchronous request to a Large Language Model (LLM) and updates the current prompt
     * with the response received from the LLM.
     *
     * @return A [Message.Response] object containing the response from the LLM.
     */
    override suspend fun requestLLM(): Message.Response {
        return super.requestLLM().also { response -> updatePrompt { message(response) } }
    }

    /**
     * Requests multiple responses from the LLM and updates the prompt with the received responses.
     *
     * This method invokes the superclass implementation to fetch a list of LLM responses. Each
     * response is subsequently used to update the session's prompt. The prompt updating mechanism
     * allows stateful interactions with the LLM, maintaining context across multiple requests.
     *
     * @return A list of `Message.Response` containing the results from the LLM.
     */
    override suspend fun requestLLMMultiple(): List<Message.Response> {
        return super.requestLLMMultiple().also { responses ->
            updatePrompt {
                responses.forEach { message(it) }
            }
        }
    }

    /**
     * Requests an LLM (Language Model) to generate a structured output based on the provided structure.
     * The response is post-processed to update the prompt with the raw response.
     *
     * @param structure The structured data definition specifying the expected structured output format, schema, and parsing logic.
     * @param retries The number of retry attempts to allow in case of generation failures.
     * @param fixingModel The language model to use for re-parsing or error correction during retries.
     * @return A structured response containing both the parsed structure and the raw response text.
     */
    override suspend fun <T> requestLLMStructured(
        structure: StructuredData<T>,
        retries: Int,
        fixingModel: LLModel
    ): Result<StructuredResponse<T>> {
        return super.requestLLMStructured(structure, retries, fixingModel).also {
            it.onSuccess { response ->
                updatePrompt {
                    assistant(response.raw)
                }
            }
        }
    }

    /**
     * Streams the result of a request to a language model.
     *
     * @param definition an optional parameter to define a structured data format. When provided, it will be used
     * in constructing the prompt for the language model request.
     * @return a flow of strings that streams the responses from the language model.
     */
    public suspend fun requestLLMStreaming(definition: StructuredDataDefinition? = null): Flow<String> {
        if (definition != null) {
            val prompt = prompt(prompt, clock) {
                user {
                    definition.definition(this)
                }
            }
            this.prompt = prompt
        }

        return executor.executeStreaming(prompt, model)
    }

    /**
     * Sends a request to the LLM using the given structured data and expects a structured response in one attempt.
     * Updates the prompt with the raw response received from the LLM.
     *
     * @param structure The structured data defining the schema, examples, and parsing logic for the response.
     * @return A structured response containing both the parsed data and the raw response text from the LLM.
     */
    override suspend fun <T> requestLLMStructuredOneShot(structure: StructuredData<T>): StructuredResponse<T> {
        return super.requestLLMStructuredOneShot(structure).also { response ->
            updatePrompt {
                assistant(response.raw)
            }
        }
    }
}
