package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventHandlerTest {

    @Test
    fun `test event handler for agent without nodes and tools`() = runTest {

        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = { },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: $agentInput)",
            "OnAfterNode (node: __start__, input: $agentInput, output: $agentInput)",
            "OnStrategyFinished (strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (strategy: $strategyName, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node without tools`() = runTest {

        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = { },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metaInfo=RequestMetaInfo(timestamp=$ts)), User(content=Test user message, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[]), Assistant(content=Test assistant response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null), User(content=Test LLM call prompt, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[])], tools: [])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (strategy: $strategyName, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node with tools`() = runTest {

        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metaInfo=RequestMetaInfo(timestamp=$ts)), User(content=Test user message, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[]), Assistant(content=Test assistant response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null), User(content=Test LLM call prompt, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[])], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (strategy: $strategyName, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler several nodes`() = runTest {

        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metaInfo=RequestMetaInfo(timestamp=$ts)), User(content=Test user message, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[]), Assistant(content=Test assistant response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null), User(content=Test LLM call prompt, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[])], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null))",
            "OnBeforeNode (node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metaInfo=RequestMetaInfo(timestamp=$ts)), User(content=Test user message, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[]), Assistant(content=Test assistant response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null), User(content=Test LLM call prompt, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[]), Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null), User(content=Test LLM call with tools prompt, metaInfo=RequestMetaInfo(timestamp=$ts), mediaContent=[])], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null), mediaContent=null, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (strategy: $strategyName, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler with multiple handlers`() = runTest {
        val collectedEvents = mutableListOf<String>()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = { },
            installFeatures = {
                install(EventHandler) {
                    onBeforeAgentStarted { strategy: AIAgentStrategy, agent: AIAgent ->
                        collectedEvents.add("OnBeforeAgentStarted first (strategy: ${strategy.name})")
                    }

                    onBeforeAgentStarted { strategy: AIAgentStrategy, agent: AIAgent ->
                        collectedEvents.add("OnBeforeAgentStarted second (strategy: ${strategy.name})")
                    }

                    onAgentFinished { strategyName: String, result: String? ->
                        collectedEvents.add("OnAgentFinished (strategy: $strategyName, result: $agentResult)")
                    }
                }
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted first (strategy: $strategyName)",
            "OnBeforeAgentStarted second (strategy: $strategyName)",
            "OnAgentFinished (strategy: $strategyName, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, collectedEvents.size)
        assertContentEquals(expectedEvents, collectedEvents)
    }
}
