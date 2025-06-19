package com.jetbrains.example.kotlin_agents_demo_app.agents.calculator

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.local.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.AgentProvider
import com.jetbrains.example.kotlin_agents_demo_app.agents.common.ExitTool
import com.jetbrains.example.kotlin_agents_demo_app.settings.AppSettings

/**
 * Factory for creating calculator agents
 */
object CalculatorAgentProvider : AgentProvider {
    override val title: String = "Calculator"
    override val description: String = "Hi, I'm a calculator agent, I can do math"

    override suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent {
        val openAiToken = appSettings.getCurrentSettings().openAiToken
        require(openAiToken.isNotEmpty()) { "OpenAI token is not configured." }

        val executor = simpleOpenAIExecutor(openAiToken)

        // Create tool registry with calculator tools
        val toolRegistry = ToolRegistry {
            tool(CalculatorTools.PlusTool)
            tool(CalculatorTools.MinusTool)
            tool(CalculatorTools.DivideTool)
            tool(CalculatorTools.MultiplyTool)

            tool(ExitTool)
        }

        val strategy = strategy(title) {
            val nodeRequestLLM by nodeLLMRequestMultiple()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }
            val nodeExecuteToolMultiple by nodeExecuteMultipleTools(parallelTools = true)
            val nodeSendToolResultMultiple by nodeLLMSendMultipleToolResults()
            val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

            edge(nodeStart forwardTo nodeRequestLLM)

            edge(
                nodeRequestLLM forwardTo nodeExecuteToolMultiple
                    onMultipleToolCalls { true }
            )

            edge(
                nodeRequestLLM forwardTo nodeAssistantMessage
                    transformed { it.first() }
                    onAssistantMessage { true }
            )

            edge(nodeAssistantMessage forwardTo nodeRequestLLM)

            // Finish condition - if exit tool is called, go to nodeFinish with tool call result.
            edge(
                nodeExecuteToolMultiple forwardTo nodeFinish
                    onCondition  { it.singleOrNull()?.tool == ExitTool.name }
                    transformed { it.single().result!!.toStringDefault() }
            )

            edge(
                (nodeExecuteToolMultiple forwardTo nodeCompressHistory)
                    onCondition { _ -> llm.readSession { prompt.messages.size > 100 } }
            )

            edge(nodeCompressHistory forwardTo nodeSendToolResultMultiple)

            edge(
                (nodeExecuteToolMultiple forwardTo nodeSendToolResultMultiple)
                    onCondition { _ -> llm.readSession { prompt.messages.size <= 100 } }
            )

            edge(
                (nodeSendToolResultMultiple forwardTo nodeExecuteToolMultiple)
                    onMultipleToolCalls { true }
            )

            edge(
                nodeSendToolResultMultiple forwardTo nodeAssistantMessage
                    transformed { it.first() }
                    onAssistantMessage { true }
            )

        }

        // Create agent config with proper prompt
        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system(
                    """
                    You are a calculator.
                    You will be provided math problems by the user.
                    Use tools at your disposal to solve them.
                    Provide the answer and ask for the next problem until the user asks to stop.
                    """.trimIndent()
                )
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 50
        )

        // Create the runner
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCall { tool: Tool<*, *>, toolArgs: Tool.Args ->
                    onToolCallEvent("Tool ${tool.name}, args $toolArgs")
                }

                onAgentRunError { strategyName: String, throwable: Throwable ->
                    onErrorEvent("${throwable.message}")
                }

                onAgentFinished { strategyName: String, result: String? ->
                    // Skip finish event handling
                }
            }
        }
    }
}