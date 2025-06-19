package ai.koog.agents.testing.tools

import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message

/**
 * A mock implementation of [DirectToolCallsEnabler] used for testing purposes.
 * This enables direct tool calls without requiring additional authorization.
 */
@OptIn(InternalAgentToolsApi::class)
private object MockToolsEnabler : DirectToolCallsEnabler

/**
 * A mock implementation of [AIAgentEnvironment] used for testing agent behavior.
 *
 * This class provides a controlled environment for testing agents by:
 * 1. Executing tool calls using either mocked responses or actual tool implementations
 * 2. Handling exceptions by re-throwing them for test visibility
 * 3. Optionally delegating termination signals to a base environment
 *
 * It works in conjunction with [MockLLMExecutor] to provide a complete testing framework
 * for agent interactions.
 *
 * @property toolRegistry The registry containing all available tools for the agent
 * @property promptExecutor The executor for handling prompts, typically a [MockLLMExecutor]
 * @property baseEnvironment Optional base environment to delegate certain operations to
 *
 * Example usage:
 * ```kotlin
 * // Create a mock environment for testing
 * val mockEnvironment = MockEnvironment(
 *     toolRegistry = toolRegistry,
 *     promptExecutor = mockLLMExecutor
 * )
 *
 * // Use the mock environment with an agent
 * val agent = AIAgent(
 *     promptExecutor = mockLLMExecutor,
 *     toolRegistry = toolRegistry,
 *     strategy = strategy,
 *     environment = mockEnvironment,
 *     // other parameters...
 * )
 * ```
 */
@OptIn(InternalAgentToolsApi::class)
internal class MockEnvironment(
    internal val toolRegistry: ToolRegistry,
    internal val promptExecutor: PromptExecutor,
    internal val baseEnvironment: AIAgentEnvironment? = null
) : AIAgentEnvironment {
    /**
     * Executes a list of tool calls and returns their results.
     *
     * This method processes each tool call individually by:
     * 1. First checking if there are any mocked responses for the tool call
     * 2. If no mocks are found, executing the actual tool implementation
     *
     * @param toolCalls The list of tool calls to execute
     * @return A list of [ReceivedToolResult] objects containing the results of the tool calls
     */
    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        return toolCalls.map {
            executeTool(it)
        }
    }

    /**
     * Executes a single tool call and returns its result.
     *
     * The execution follows this process:
     * 1. If the prompt executor is a [MockLLMExecutor], check for matching mocked tool actions
     * 2. If a matching mock is found, use it to generate the result
     * 3. Otherwise, retrieve the actual tool from the registry and execute it
     *
     * @param functionCall The tool call to execute
     * @return A [ReceivedToolResult] containing the result of the tool call
     */
    private suspend fun executeTool(functionCall: Message.Tool.Call): ReceivedToolResult {
        if (promptExecutor is MockLLMExecutor) {
            promptExecutor.toolActions
                .find { it.satisfies(functionCall) }
                ?.invokeAndSerialize(functionCall)
                ?.let { (result, content) ->
                    return ReceivedToolResult(
                        id = functionCall.id,
                        tool = functionCall.tool,
                        content = content,
                        result = result
                    )
                }
        }
        val tool = toolRegistry.getTool(functionCall.tool)

        val args = tool.decodeArgsFromString(functionCall.content)
        val result = tool.executeUnsafe(args, MockToolsEnabler)


        return ReceivedToolResult(
            id = functionCall.id,
            tool = functionCall.tool,
            content = tool.encodeResultToStringUnsafe(result),
            result = result
        )
    }

    /**
     * Reports a problem by throwing the exception.
     *
     * In a testing environment, this behavior makes exceptions visible to the test framework,
     * allowing tests to catch and verify expected exceptions.
     *
     * @param exception The exception to report
     * @throws Throwable The same exception that was passed in
     */
    override suspend fun reportProblem(exception: Throwable) {
        throw exception
    }

    /**
     * Sends a termination signal with an optional result.
     *
     * If a base environment is provided, the termination signal is delegated to it.
     * Otherwise, the termination is effectively a no-op in the mock environment.
     *
     * @param result An optional result string to include with the termination
     */
    override suspend fun sendTermination(result: String?) {
        baseEnvironment?.sendTermination(result)
    }
}
