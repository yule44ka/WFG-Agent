@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.testing.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.*
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.BaseBuilder
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.datetime.Clock
import org.jetbrains.annotations.TestOnly
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class DummyAgentContext(
    private val builder: AIAgentContextMockBuilder,
) : AIAgentContextBase {
    public val isLLMDefined: Boolean = builder.llm != null
    public val isEnvironmentDefined: Boolean = builder.environment != null

    override val environment: AIAgentEnvironment
        get() = builder.environment
            ?: throw NotImplementedError("Environment is not mocked")

    override val agentInput: String
        get() = builder.agentInput
            ?: throw NotImplementedError("Config is not mocked")

    override val config: AIAgentConfigBase
        get() = builder.config
            ?: throw NotImplementedError("Config is not mocked")

    override val llm: AIAgentLLMContext
        get() = builder.llm
            ?: throw NotImplementedError("LLM is not mocked")

    override val stateManager: AIAgentStateManager
        get() = builder.stateManager
            ?: throw NotImplementedError("State manager is not mocked")

    override val storage: AIAgentStorage
        get() = builder.storage
            ?: throw NotImplementedError("Storage is not mocked")

    override val sessionUuid: Uuid
        get() = builder.sessionUuid
            ?: throw NotImplementedError("Session UUID is not mocked")

    override val strategyId: String
        get() = builder.strategyId
            ?: throw NotImplementedError("Strategy ID is not mocked")

    /**
     * @suppress
     */
    @InternalAgentsApi
    override val pipeline: AIAgentPipeline = AIAgentPipeline()

    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? =
        throw NotImplementedError("feature() getting in runtime is not supported for mock")

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? =
        throw NotImplementedError("feature()  getting in runtime is not supported for mock")

    override fun copy(
        environment: AIAgentEnvironment?,
        agentInput: String?,
        config: AIAgentConfigBase?,
        llm: AIAgentLLMContext?,
        stateManager: AIAgentStateManager?,
        storage: AIAgentStorage?,
        sessionUuid: Uuid?,
        strategyId: String?,
        pipeline: AIAgentPipeline?
    ): AIAgentContextBase = DummyAgentContext(
        builder.copy().apply {
            environment?.let { this.environment = it }
            agentInput?.let { this.agentInput = it }
            config?.let { this.config = it }
            llm?.let { this.llm = it }
            stateManager?.let { this.stateManager = it }
            storage?.let { this.storage = it }
            sessionUuid?.let { this.sessionUuid = it }
            strategyId?.let { this.strategyId = it }
        }
    )
}

@TestOnly
public interface AIAgentContextMockBuilderBase : BaseBuilder<AIAgentContextBase> {
    public var environment: AIAgentEnvironment?
    public var agentInput: String?
    public var config: AIAgentConfigBase?
    public var llm: AIAgentLLMContext?
    public var stateManager: AIAgentStateManager?
    public var storage: AIAgentStorage?
    public var sessionUuid: Uuid?
    public var strategyId: String?

    public fun copy(): AIAgentContextMockBuilderBase

    override fun build(): AIAgentContextBase
}

@TestOnly
public class AIAgentContextMockBuilder() : AIAgentContextMockBuilderBase {
    override var environment: AIAgentEnvironment? = null
    override var agentInput: String? = null
    override var config: AIAgentConfigBase? = null
    override var llm: AIAgentLLMContext? = null
    override var stateManager: AIAgentStateManager? = null
    override var storage: AIAgentStorage? = null
    override var sessionUuid: Uuid? = null
    override var strategyId: String? = null

    override fun copy(): AIAgentContextMockBuilder {
        return AIAgentContextMockBuilder().also {
            it.environment = environment
            it.agentInput = agentInput
            it.config = config
            it.llm = llm
            it.stateManager = stateManager
            it.storage = storage
            it.sessionUuid = sessionUuid
            it.strategyId = strategyId
        }
    }

    override fun build(): DummyAgentContext {
        return DummyAgentContext(this.copy())
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T : Any> createDummyProxy(name: String): T {
            return ProxyHandler<T>(name).createProxy()
        }
    }

    public class ProxyHandler<T : Any>(private val name: String) {
        @Suppress("UNCHECKED_CAST")
        public fun createProxy(): T {
            return object : Any() {
                override fun toString() = "DummyProxy<${name}>"

                override fun equals(other: Any?): Boolean {
                    return this === other
                }

                @Suppress("UNUSED_PARAMETER")
                operator fun get(propertyName: String): Any? {
                    error("Unimplemented property access: $name.$propertyName")
                }

                @Suppress("UNUSED_PARAMETER")
                fun invoke(methodName: String, vararg args: Any?): Any? {
                    error("Unimplemented method call: $name.$methodName(${args.joinToString()})")
                }
            } as T
        }
    }
}


@TestOnly
public sealed class NodeReference<Input, Output> {
    public abstract fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Output>

    public class Start<Input> : NodeReference<Input, Input>() {
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Input> =
            subgraph.start as AIAgentNodeBase<Input, Input>
    }

    public class Finish<Output> : NodeReference<Output, Output>() {
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Output, Output> =
            subgraph.finish as AIAgentNodeBase<Output, Output>
    }

    public open class NamedNode<Input, Output>(public val name: String) : NodeReference<Input, Output>() {
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Output> {
            val visited = mutableSetOf<String>()
            fun visit(node: AIAgentNodeBase<*, *>): AIAgentNodeBase<Input, Output>? {
                if (node is FinishAIAgentNodeBase) return null
                if (visited.contains(node.name)) return null
                visited.add(node.name)
                if (node.name == name) return node as? AIAgentNodeBase<Input, Output>
                return node.edges.firstNotNullOfOrNull { visit(it.toNode) }
            }

            val result = visit(subgraph.start).also {
                println("Visited nodes: ${visited.joinToString()} [${visited.size}]")
            }
                ?: throw IllegalArgumentException("Node with name '$name' not found")

            return result
        }
    }

    public open class SubgraphNode<Input, Output>(name: String) : NamedNode<Input, Output>(name) {
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentSubgraph<Input, Output> {
            val result = super.resolve(subgraph)

            if (result !is AIAgentSubgraph<Input, Output>) {
                throw IllegalArgumentException("Node with name '$name' is not a subgraph")
            }

            return result
        }
    }

    public class Strategy(name: String) : SubgraphNode<String, String>(name) {
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentStrategy {
            if (subgraph.name != name) {
                throw IllegalArgumentException("Strategy with name '$name' was expected")
            }

            if (subgraph !is AIAgentStrategy) {
                throw IllegalStateException("Resolving a strategy is not possible from a subgraph")
            }

            return subgraph
        }
    }
}

@TestOnly
public data class GraphAssertions(
    val name: String,
    val start: NodeReference.Start<*>,
    val finish: NodeReference.Finish<*>,
    val nodes: Map<String, NodeReference<*, *>>,
    val nodeOutputAssertions: List<NodeOutputAssertion<*, *>>,
    val edgeAssertions: List<EdgeAssertion<*, *>>,
    val unconditionalEdgeAssertions: List<UnconditionalEdgeAssertion>,
    val reachabilityAssertions: List<ReachabilityAssertion>,
    val subgraphAssertions: MutableList<SubGraphAssertions>
)

@TestOnly
public data class NodeOutputAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: DummyAgentContext,
    val input: Input,
    val expectedOutput: Output
)

@TestOnly
public data class EdgeAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: AIAgentContextBase,
    val output: Output,
    val expectedNode: NodeReference<*, *>
)

@TestOnly
public data class UnconditionalEdgeAssertion(
    val node: NodeReference<*, *>,
    val expectedNode: NodeReference<*, *>
)

@TestOnly
public data class ReachabilityAssertion(val from: NodeReference<*, *>, val to: NodeReference<*, *>)

@TestOnly
public data class SubGraphAssertions(
    val subgraphRef: NodeReference.SubgraphNode<*, *>,
    val graphAssertions: GraphAssertions
)

@TestOnly
public sealed interface AssertionResult {
    public class NotEqual(public val expected: Any?, public val actual: Any?, public val message: String) :
        AssertionResult

    public class False(public val message: String) : AssertionResult
}

/**
 * Provides functionality for testing graph-related stages in an AI agent pipeline.
 *
 * This feature allows you to configure and validate the relationships between nodes,
 * their outputs, and the overall graph structure within different stages of an agent.
 * It can validate:
 * - Stage order
 * - Node existence and reachability
 * - Node input/output behavior
 * - Edge connections between nodes
 *
 * The Testing feature is designed to be used with the [testGraph] function, which provides
 * a clean API for defining and executing graph tests.
 *
 * Example usage:
 * ```kotlin
 * AIAgent(
 *     // constructor arguments
 * ) {
 *     testGraph {
 *         // Assert the order of stages
 *         assertStagesOrder("first", "second")
 *
 *         // Test the first stage
 *         stage("first") {
 *             val start = startNode()
 *             val finish = finishNode()
 *
 *             // Assert nodes by name
 *             val askLLM = assertNodeByName<String, Message.Response>("callLLM")
 *             val callTool = assertNodeByName<ToolCall.Signature, ToolCall.Result>("executeTool")
 *
 *             // Assert node reachability
 *             assertReachable(start, askLLM)
 *             assertReachable(askLLM, callTool)
 *
 *             // Test node behavior
 *             assertNodes {
 *                 askLLM withInput "Hello" outputs Message.Assistant("Hello!")
 *             }
 *
 *             // Test edge connections
 *             assertEdges {
 *                 askLLM withOutput Message.Assistant("Hello!") goesTo giveFeedback
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @see testGraph
 * @see Testing.Config
 */
@TestOnly
public class Testing {
    /**
     * A mutable list storing `GraphAssertions` objects, which define validation
     * criteria for the graphs.
     *
     */
    private val graphAssertions = mutableListOf<GraphAssertions>()

    /**
     * Represents a configuration class responsible for managing assertion handlers and stage configurations.
     * It includes methods for registering custom assertion handling, managing stages and their order,
     * and defining stage-specific assertions.
     */
    public class Config : FeatureConfig() {
        /**
         * A clock instance used for managing timestamps within the configuration,
         * primarily for mock message timestamping purposes.
         *
         * This enables test scenarios that require precise control over time
         * by allowing the use of custom clock instances, such as mock or fixed clocks.
         */
        public var clock: Clock = Clock.System

        /**
         * Defines the tokenizer to be used for estimating token counts in text strings.
         *
         * Tokenizers are critical for features or applications requiring token-level control
         * or analysis, such as evaluating text input size relative to limits or optimizing
         * messages for LLM prompts. By default, this is set to `null`, which disables
         * token counting, but it can be replaced with a custom implementation of the `Tokenizer`
         * interface.
         *
         * Assigning a different tokenizer allows for customizable token estimation strategies
         * with varying levels of accuracy and performance depending on the use case.
         */
        public var tokenizer: Tokenizer? = null

        /**
         * A configuration flag that determines whether graph-related testing features are enabled.
         *
         * When set to `true`, additional testing mechanisms related to graph validation,
         * assertions, or structure evaluation may be activated within the system.
         * Default value is `false`, which disables graph testing functionalities.
         */
        public var enableGraphTesting: Boolean = false

        /**
         * A nullable variable that defines the handler invoked when an assertion result is generated.
         * It is a higher-order function that accepts an `AssertionResult` as its parameter.
         * This handler is used internally in assertion methods to process and handle assertion outcomes such as
         * mismatches between expected and actual values or boolean evaluation failures.
         */
        private var assertionHandler: ((AssertionResult) -> Unit)? = null

        /**
         * A mutable list of `StrategyAssertions` objects used to collect and manage graph-related assertions.
         *
         * Each element in the list represents a specific (sub)graph with its associated assertions, such as node outputs,
         * edge connections, and reachability between nodes.
         */
        private var assertions: GraphAssertions? = null

        /**
         * Retrieves the list of graph assertions defined in the configuration.
         *
         * @return A list of `GraphAssertions` representing the assertions to be applied for graph testing.
         */
        internal fun getAssertions(): GraphAssertions = assertions!!

        /**
         * Asserts that two values are equal. If the values are not equal, the specified message will be passed
         * to the assertion handler with details about the expected and actual values.
         *
         * @param expected The expected value to compare against.
         * @param actual The actual value to be compared.
         * @param message A custom message to include when the assertion fails.
         */
        internal fun assertEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                assertionHandler?.invoke(AssertionResult.NotEqual(expected, actual, message))
            }
        }

        /**
         * Asserts the truthfulness of the provided boolean value.
         * If the assertion fails (i.e., the value is false), a custom handler is invoked
         * with an `AssertionResult.False` containing the provided message.
         *
         * @param value the boolean value to evaluate. If false, the assertion fails.
         * @param message the message to include in the assertion result if the value is false.
         */
        internal fun assert(value: Boolean, message: String) {
            if (!value) {
                assertionHandler?.invoke(AssertionResult.False(message))
            }
        }

        /**
         * Sets a custom handler for processing assertion results.
         *
         * @param block A lambda which takes an `AssertionResult` as input and processes it.
         *              This allows customization of how assertion results are handled,
         *              such as logging or throwing custom exceptions.
         */
        public fun handleAssertion(block: (AssertionResult) -> Unit) {
            assertionHandler = block
        }

        /**
         * Configures and adds a verification strategy using the provided name and assertions.
         *
         * @param name The name of the strategy to be verified.
         * @param buildAssertions A lambda defining the assertions to be built for the strategy.
         */
        public fun verifyStrategy(name: String, buildAssertions: SubgraphAssertionsBuilder<String, String>.() -> Unit) {
            assertions =
                SubgraphAssertionsBuilder(NodeReference.Strategy(name), clock, tokenizer).apply(buildAssertions).build()
        }

        /**
         * Builder class for constructing subgraph-level assertions.
         * This includes setup for nodes, edges, reachability assertions, and context-related mock setups.
         *
         * @param subgraphRef: A [NodeReference.SubgraphNode] reference to a subgraph of the agent's graph strategy
         * @param clock: A clock that is used for mock message timestamps
         * @param tokenizer: Tokenizer that will be used to estimate token counts in mock messages
         */
        public class SubgraphAssertionsBuilder<Input, Output>(
            private val subgraphRef: NodeReference.SubgraphNode<Input, Output>,
            internal val clock: Clock,
            internal val tokenizer: Tokenizer?,
        ) {

            private val start: NodeReference.Start<Input> = NodeReference.Start<Input>()

            private val finish: NodeReference.Finish<Output> = NodeReference.Finish<Output>()


            /**
             * Stores a mapping of node names to their corresponding references.
             *
             * This property serves as the central repository of named nodes within the
             * StageAssertionsBuilder. Nodes are registered within this map when they are
             * asserted by name using functions like `assertNodeByName`. The keys in the map
             * represent the names of the nodes, while the values represent their references.
             *
             * The `nodes` map is later used during the `build()` process to construct
             * `StageAssertions`, ensuring all nodes are properly accounted for and linked
             * according to their usage in the assertions.
             */
            private val nodes = mutableMapOf<String, NodeReference<*, *>>()

            /**
             * A mutable list that collects NodeOutputAssertion instances. These assertions are used to validate
             * the outputs of specific nodes within a stage of the system's execution.
             *
             * Each NodeOutputAssertion in this list represents an expectation on the output behavior of a
             * particular node when provided with a certain input in a predefined context.
             *
             * This property is populated indirectly through the `assertNodes` function within the
             * `StageAssertionsBuilder` context, allowing users to define node-specific assertions
             * and add them to this collection.
             *
             * The collected assertions are later utilized during the construction of the
             * `StageAssertions` object to verify node output behavior.
             */
            private val nodeOutputs = mutableListOf<NodeOutputAssertion<*, *>>()

            /**
             * A mutable list storing all edge-related assertions for a stage.
             *
             * Each edge assertion represents an expected edge between two nodes in the
             * stage, including the originating node, its output, the target node, and the
             * execution context in which the assertion was made.
             *
             * This property is utilized during the edge validation process to ensure the
             * stage conforms to its designed behavior regarding node-to-node transitions.
             * The assertions are collected through the `EdgeAssertionsBuilder` and
             * integrated into the final `StageAssertions` object for the stage.
             */
            private val edgeAssertions = mutableListOf<EdgeAssertion<*, *>>()

            /**
             * Stores a collection of assertions that represent unconditional and direct relationships
             * between nodes in the graph. Each assertion describes an expected edge connection
             * without conditions or additional constraints.
             *
             * This property is typically populated during the process of asserting edge connections
             * within a graph, specifically using the `assertEdges` method or similar graph
             * assertion configurations.
             *
             * It is used internally to track and validate edge connections as part of the
             * graph testing framework to ensure the graph structure behaves as expected.
             */
            private val unconditionalEdgeAssertions = mutableListOf<UnconditionalEdgeAssertion>()

            /**
             * A mutable list of reachability assertions that define expected direct connections between nodes
             * in a stage. Each assertion represents a relationship where one node is reachable from another.
             *
             * This field is used to accumulate `ReachabilityAssertion` objects which are added via the
             * `assertReachable` method in the `StageAssertionsBuilder` class. These assertions are validated
             * when the stage's configuration is built using the `build` method.
             */
            private val reachabilityAssertions = mutableListOf<ReachabilityAssertion>()

            private val subgraphAssertions = mutableListOf<SubGraphAssertions>()

            /**
             * Provides a mock builder for the local agent stage context used within test environments.
             * This mock can be leveraged to construct or replicate contexts for validating various stage behaviors
             * including node outputs, edge assertions, and reachability assertions.
             * It acts as a centralized resource for contextual test data and stage-related configurations.
             */
            private val context = AIAgentContextMockBuilder()

            /**
             * Retrieves the starting node reference of the stage.
             *
             * @return The starting node reference of type NodeReference.Start, representing the entry point of the stage.
             */
            public fun startNode(): NodeReference.Start<Input> {
                return start
            }

            /**
             * Retrieves a reference to the finish node of the current stage in the graph.
             *
             * @return a [NodeReference.Finish] representing the terminal node of the stage.
             */
            public fun finishNode(): NodeReference.Finish<Output> {
                return finish
            }

            /**
             * Asserts the existence of a node by its name within the stage structure and returns a reference to it.
             * If the node with the given name has not been asserted previously, it creates a new `NamedNode` reference
             * and records it in the `nodes` map for tracking.
             *
             * @param name the name of the node to assert or retrieve.
             * @return a `NodeReference` for the node identified by the given name.
             */
            public fun <I, O> assertNodeByName(name: String): NodeReference.NamedNode<I, O> {
                val nodeRef = NodeReference.NamedNode<I, O>(name)
                nodes[name] = nodeRef
                return nodeRef
            }

            /**
             * Asserts the existence of a subgraph by its name*/
            public fun <I, O> assertSubgraphByName(
                name: String
            ): NodeReference.SubgraphNode<I, O> {
                val nodeRef = NodeReference.SubgraphNode<I, O>(name)
                nodes[name] = nodeRef
                return nodeRef
            }

            public fun <I, O> verifySubgraph(
                subgraph: NodeReference.SubgraphNode<I, O>,
                checkSubgraph: SubgraphAssertionsBuilder<I, O>.() -> Unit = {}
            ) {
                val assertions = SubgraphAssertionsBuilder(subgraph, clock, tokenizer).apply(checkSubgraph).build()
                subgraphAssertions.add(SubGraphAssertions(subgraph, assertions))
            }

            /**
             * Asserts that there is a reachable path between two specified nodes within a stage.
             *
             * @param from The starting node reference from where the reachability is checked.
             * @param to The target node reference to which the reachability is checked.
             */
            public fun assertReachable(from: NodeReference<*, *>, to: NodeReference<*, *>) {
                reachabilityAssertions.add(ReachabilityAssertion(from, to))
            }

            /**
             * Asserts the state of nodes in the stage using a provided block to define the desired assertions.
             * The block operates on a `NodeOutputAssertionsBuilder` to specify expectations for node inputs and outputs.
             *
             * @param block A lambda receiver operating on a `NodeOutputAssertionsBuilder` that defines the assertions
             *              to be applied to the nodes in the stage.
             */
            public fun assertNodes(block: NodeOutputAssertionsBuilder.() -> Unit) {
                val builder = NodeOutputAssertionsBuilder(this)
                builder.block()
                nodeOutputs.addAll(builder.assertions)
            }

            /**
             * Asserts the edges in the context of a graph by applying a set of edge assertions built using the provided block.
             *
             * @param block A lambda function that operates on an instance of `EdgeAssertionsBuilder` to define specific edge assertions.
             */
            public fun assertEdges(block: EdgeAssertionsBuilder.() -> Unit) {
                val builder = EdgeAssertionsBuilder(this)
                builder.block()
                edgeAssertions.addAll(builder.assertions)
                unconditionalEdgeAssertions.addAll(builder.unconditionalEdgeAssertions)
            }

            /**
             * Builds and returns a `StageAssertions` object based on the current state of the `StageAssertionsBuilder`.
             *
             * @return A `StageAssertions` instance containing the name, start and finish node references,
             *         map of nodes, node output assertions, edge assertions, and reachability assertions.
             */
            internal fun build(): GraphAssertions {
                return GraphAssertions(
                    subgraphRef.name,
                    start,
                    finish,
                    nodes,
                    nodeOutputs,
                    edgeAssertions,
                    unconditionalEdgeAssertions,
                    reachabilityAssertions,
                    subgraphAssertions
                )
            }

            /**
             * A builder class for constructing and managing assertions related to the outputs of nodes within a stage.
             * This class provides functionality to define and evaluate assertions for node outputs in a local agent's stage.
             *
             * @property stageBuilder A reference to the parent StageAssertionsBuilder, which serves as the context for assertions.
             * @property context A mock builder for the local agent stage context, used to manage and copy state during the assertion process.
             */
            public class NodeOutputAssertionsBuilder(
                private val stageBuilder: SubgraphAssertionsBuilder<*, *>,
                private val context: AIAgentContextMockBuilder = stageBuilder.context.copy()
            ) : AIAgentContextMockBuilderBase by context {

                /**
                 * Creates and returns a new copy of the NodeOutputAssertionsBuilder instance.
                 *
                 * @return a new NodeOutputAssertionsBuilder that contains a copy of the current stageBuilder
                 * and a copied context.
                 */
                override fun copy(): NodeOutputAssertionsBuilder =
                    NodeOutputAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * A mutable list that stores assertions representing the expected behavior and output of nodes
                 * in the context of a specific staging environment for testing purposes.
                 *
                 * Each assertion is an instance of [NodeOutputAssertion], which encapsulates information
                 * such as the node reference, the input provided, the expected output, and the context
                 * in which the node operates.
                 *
                 * These assertions are used to verify the correctness of node operations within the
                 * local agent stage context during testing.
                 */
                public val assertions: MutableList<NodeOutputAssertion<*, *>> = mutableListOf()

                /**
                 * Executes the specified block of code within a duplicate context of the current `NodeOutputAssertionsBuilder`.
                 *
                 * @param block The block of code to be executed within the duplicated context of `NodeOutputAssertionsBuilder`.
                 */
                public fun withContext(block: NodeOutputAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the provided input with the current node reference, creating a pair that links the node
                 * to its corresponding input.
                 *
                 * @param input The input value to associate with the node reference.
                 * @return A `NodeOutputPair` containing the node reference and the provided input.
                 */
                public infix fun <I, O> NodeReference<I, O>.withInput(input: I): NodeOutputPair<I, O> {
                    return NodeOutputPair(this, input)
                }

                /**
                 * Represents a pairing of a specific node reference and its corresponding input.
                 * This is used to define the expected output for a given input within the context of a specific node.
                 *
                 * @param I The type of the input for the node.
                 * @param O The type of the output for the node.
                 * @property node The reference to the specific node.
                 * @property input The input associated with the node.
                 */
                public inner class NodeOutputPair<I, O>(public val node: NodeReference<I, O>, public val input: I) {
                    /**
                     * Asserts that the output of the current node given the specified input matches the expected output.
                     *
                     * @param output The expected output to validate against the current node's actual output.
                     */
                    public infix fun outputs(output: O) {
                        assertions.add(NodeOutputAssertion(node, context.build(), input, output))
                    }
                }
            }

            /**
             * A builder class used to facilitate the creation and management of edge assertions in a stage context.
             * Delegates functionality to a local agent stage context mock builder for shared behaviors.
             *
             * @property stageBuilder The parent builder for the stage, used to initialize context and other related components.
             * @property context A local agent stage context mock builder, initialized as a copy of the stage builder's context.
             */
            public class EdgeAssertionsBuilder(
                private val stageBuilder: SubgraphAssertionsBuilder<*, *>,
                private val context: AIAgentContextMockBuilder = stageBuilder.context.copy()
            ) : AIAgentContextMockBuilderBase by context {

                /**
                 * A mutable list that holds all the defined `EdgeAssertion` instances for the current context.
                 *
                 * `EdgeAssertion` represents the relationship between nodes in a graph-like structure, detailing
                 * the output of a source node and its expected connection to a target node. These assertions are
                 * used to validate the behavior and flow of nodes within an agent's stage context.
                 *
                 * This list is populated during the execution of the `EdgeAssertionsBuilder` block in methods
                 * that build or define edge assertions. Each assertion is added via the respective fluent APIs
                 * provided within the builder.
                 */
                public val assertions: MutableList<EdgeAssertion<*, *>> = mutableListOf()

                /**
                 * A collection that stores assertions ensuring an unconditional connection
                 * between nodes in a graph testing context. Each assertion represents
                 * a defined relationship where a node always leads to a specified target node.
                 *
                 * This list is populated by adding instances of [UnconditionalEdgeAssertion] through relevant methods,
                 * such as when defining relationships or validating graph behavior.
                 */
                public val unconditionalEdgeAssertions: MutableList<UnconditionalEdgeAssertion> =
                    mutableListOf<UnconditionalEdgeAssertion>()

                /**
                 * Creates a deep copy of the current EdgeAssertionsBuilder instance, duplicating its state and context.
                 *
                 * @return A new EdgeAssertionsBuilder instance with the same stageBuilder and a copied context.
                 */
                override fun copy(): EdgeAssertionsBuilder = EdgeAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * Executes a given block of logic within the context of a copied instance of the current `EdgeAssertionsBuilder`.
                 *
                 * @param block The block of code to execute within the context of the copied `EdgeAssertionsBuilder` instance.
                 */
                public fun withContext(block: EdgeAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the given output with the current node reference, creating a pair that represents
                 * the node and its corresponding output.
                 *
                 * @param output the output value to associate with the current node reference
                 * @return an instance of EdgeOutputPair containing the current node reference and the associated output
                 */
                public infix fun <I, O> NodeReference<I, O>.withOutput(output: O): EdgeOutputPair<I, O> {
                    return EdgeOutputPair(this, output)
                }

                /**
                 * Creates an assertion to verify that the current node always leads to the given target node.
                 *
                 * @param targetNode The target node that the current node output is expected to connect to.
                 */
                public infix fun NodeReference<*, *>.alwaysGoesTo(targetNode: NodeReference<*, *>) {
                    unconditionalEdgeAssertions.add(UnconditionalEdgeAssertion(this, targetNode))
                }

                /**
                 * Represents a pair consisting of a node and its corresponding output within an edge assertion context.
                 * This is used to define expected edge behavior in a graph of nodes.
                 *
                 * @param I the type of the input expected by the node.
                 * @param O the type of the output produced by the node.
                 * @property node the reference to the node associated with this edge output.
                 * @property output the output value associated with the node.
                 */
                public inner class EdgeOutputPair<I, O>(public val node: NodeReference<I, O>, public val output: O) {
                    /**
                     * Creates an assertion to verify that a specific output from the current node leads to the given target node.
                     *
                     * @param targetNode The target node that the current node output is expected to connect to.
                     */
                    public infix fun goesTo(targetNode: NodeReference<*, *>) {
                        assertions.add(EdgeAssertion(node, context.build(), output, targetNode))
                    }
                }
            }
        }
    }

    /**
     * Companion object that defines the `Testing` feature as a `AIAgentFeature`.
     * This feature provides testing capabilities for validating graph-based stages, nodes,
     * reachability, outputs, and edges within an AI agent pipeline.
     */
    @TestOnly
    public companion object Feature : AIAgentFeature<Config, Testing> {
        /**
         * A storage key uniquely identifying the `Testing` feature within the local agent's storage.
         * The key is generated using the `createStorageKey` function and associates the
         * `Testing` feature type with its specific storage context.
         */
        override val key: AIAgentStorageKey<Testing> = createStorageKey("graph-testing-feature")

        /**
         * Creates the initial configuration for the graph testing feature.
         *
         * @return an instance of [Config] containing the initial setup for assertions and stage configuration.
         */
        override fun createInitialConfig(): Config = Config()

        /**
         * Installs the `Testing` feature into the specified `AIAgentPipeline` with the provided configuration.
         * The feature primarily validates stages, nodes, and connectivity of the AI agent pipeline.
         *
         * @param config The `Config` object containing setup and assertions for testing the pipeline.
         * @param pipeline The `AIAgentPipeline` instance to install the feature into.
         */
        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val feature = Testing()

            pipeline.interceptEnvironmentCreated(this, feature) { agentEnvironment ->
                MockEnvironment(agent.toolRegistry, agent.promptExecutor, agentEnvironment)
            }

            if (config.enableGraphTesting) {
                feature.graphAssertions.add(config.getAssertions())

                pipeline.interceptBeforeAgentStarted(this, feature) {
                    readStrategy { strategyGraph ->
                        val strategyAssertions = feature.graphAssertions.find { it.name == strategyGraph.name }
                        config.assert(
                            strategyAssertions != null,
                            "Assertions for strategyGraph with name `${strategyGraph.name}` not found in configuration."
                        )
                        strategyAssertions!!
                        verifyGraph(agent, strategyAssertions, strategyGraph, pipeline, config)
                    }
                }
            }
        }

        private suspend fun verifyGraph(
            agent: AIAgent,
            graphAssertions: GraphAssertions,
            graph: AIAgentSubgraph<*, *>,
            pipeline: AIAgentPipeline,
            config: Config
        ) {
            // Verify nodes exist
            for ((nodeName, nodeRef) in graphAssertions.nodes) {
                val node = nodeRef.resolve(graph)
                assertNotNull(
                    node,
                    "Node with name '$nodeName' not found in graph '${graph.name}'"
                )
            }

            // Verify reachability
            for (assertion in graphAssertions.reachabilityAssertions) {
                assertReachable(
                    assertion.from.resolve(graph),
                    assertion.to.resolve(graph),
                    "Node ${assertion.to.resolve(graph).name} is not reachable from ${
                        assertion.from.resolve(
                            graph
                        ).name
                    } in graph '${graph.name}'"
                )
            }

            // Verify node outputs using DFS
            for (assertion in graphAssertions.nodeOutputAssertions) {
                val fromNode = assertion.node.resolve(graph)

                val environment = if (assertion.context.isEnvironmentDefined)
                    assertion.context.environment
                else
                    MockEnvironment(agent.toolRegistry, agent.promptExecutor)

                val llm = if (assertion.context.isLLMDefined) {
                    assertion.context.llm
                } else {
                    AIAgentLLMContext(
                        tools = agent.toolRegistry.tools.map { it.descriptor },
                        prompt = agent.agentConfig.prompt,
                        model = agent.agentConfig.model,
                        promptExecutor = PromptExecutorProxy(
                            agent.promptExecutor,
                            pipeline,
                            assertion.context.sessionUuid,
                        ),
                        environment = environment,
                        config = agent.agentConfig,
                        clock = agent.clock
                    )
                }

                @OptIn(InternalAgentsApi::class)
                config.assertEquals(
                    assertion.expectedOutput,
                    fromNode.executeUnsafe(
                        assertion.context.copy(llm = llm, environment = environment),
                        assertion.input
                    ),
                    "Unexpected output for node ${fromNode.name} with input ${assertion.input} " +
                            "in graph '${graph.name}'"
                )
            }

            @OptIn(InternalAgentsApi::class)
            // Verify edges using DFS
            for (assertion in graphAssertions.edgeAssertions) {
                val fromNode = assertion.node.resolve(graph)
                val toNode = assertion.expectedNode.resolve(graph)

                val resolvedEdge = fromNode.resolveEdgeUnsafe(assertion.context, assertion.output)
                assertNotNull(
                    resolvedEdge,
                    "Expected to have at least one matching edge from node ${fromNode.name} with output ${assertion.output} "
                )

                config.assertEquals(
                    toNode,
                    resolvedEdge!!.edge.toNode,
                    "Expected `${fromNode.name}` with output ${assertion.output} to go to `${toNode.name}`, " +
                            "but it goes to ${resolvedEdge.edge.toNode.name} instead"
                )
            }

            @OptIn(InternalAgentsApi::class)
            // Verify edges using DFS
            for (assertion in graphAssertions.unconditionalEdgeAssertions) {
                val fromNode = assertion.node.resolve(graph)
                val toNode = assertion.expectedNode.resolve(graph)

                config.assertEquals(
                    1, fromNode.edges.size,
                    "Expected node ${fromNode.name} to have exactly one edge, " +
                            "but it has ${fromNode.edges.size} edges instead"
                )

                val actualToNode = fromNode.edges.single().toNode

                config.assertEquals(
                    toNode, actualToNode,
                    "Expected that from node ${fromNode.name} the only edge is going to ${toNode}, " +
                            "but it goes to ${actualToNode} instead"
                )
            }

            for (assertion in graphAssertions.subgraphAssertions) {
                verifyGraph(agent, assertion.graphAssertions, assertion.subgraphRef.resolve(graph), pipeline, config)
            }
        }

        /**
         * Verifies whether there is a path from one node to another within a graph. If the target node
         * is not reachable from the starting node, an assertion error is thrown with the provided message.
         *
         * @param from The starting node in the graph from which to check reachability.
         * @param to The target node to verify reachability to.
         * @param message The error message to include in the assertion error if the target node is not reachable.
         */
        private fun assertReachable(from: AIAgentNodeBase<*, *>, to: AIAgentNodeBase<*, *>, message: String) {
            val visited = mutableSetOf<String>()

            fun dfs(node: AIAgentNodeBase<*, *>): Boolean {
                if (node == to) return true
                if (visited.contains(node.name)) return false

                visited.add(node.name)

                for (edge in node.edges) {
                    if (dfs(edge.toNode)) return true
                }

                return false
            }

            if (!dfs(from)) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the provided value is not null. If the value is null, an AssertionError is thrown with the specified message.
         *
         * @param value The value to check for nullity.
         * @param message The error message to include in the exception if the value is null.
         */
        private fun assertNotNull(value: Any?, message: String) {
            if (value == null) {
                throw AssertionError(message)
            }
        }

        /**
         * Compares two lists and throws an AssertionError if they are not equal, with a specified error message.
         *
         * @param expected The expected list of elements.
         * @param actual The actual list of elements to compare against the expected list.
         * @param message The message to include in the assertion error if the lists are not equal.
         */
        private fun assertListEquals(expected: List<*>, actual: List<*>, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the given two values are equal. If they are not equal, it throws an AssertionError
         * with the provided message.
         *
         * @param expected the expected value to compare
         * @param actual the actual value to compare against the expected value
         * @param message the assertion failure message to include in the exception if the values are not equal
         */
        private fun assertValueEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }
    }
}

/**
 * Creates a `Message.Tool.Call` instance for the given tool and its arguments.
 *
 * This utility function simplifies the creation of tool call messages for testing purposes.
 * It automatically handles the encoding of arguments into the appropriate string format.
 *
 * @param tool The tool to be represented in the call message. The `Tool` instance contains metadata
 *             such as the tool's name and utility methods for encoding/decoding arguments.
 * @param args The arguments to be passed to the tool. Must match the type `Args` defined by the tool.
 * @return A `Message.Tool.Call` object representing a call to the specified tool with the encoded arguments.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool call message for testing
 * val message = toolCallMessage(CreateTool, CreateTool.Args("solve"))
 *
 * // Use in node output assertions
 * assertNodes {
 *     askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
 * }
 * ```
 */
public fun <Args : Tool.Args> Testing.Config.SubgraphAssertionsBuilder<*, *>.toolCallMessage(
    tool: Tool<Args, *>,
    args: Args
): Message.Tool.Call {
    val toolContent = tool.encodeArgsToString(args)
    val tokenCount = tokenizer?.countTokens(toolContent)

    return Message.Tool.Call(
        id = null,
        tool = tool.name,
        content = toolContent,
        metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenCount)
    )
}

/**
 * Creates an assistant message with the provided text and finish reason.
 *
 * @param text The content of the assistant message.
 * @param finishReason The reason indicating why the message was concluded. Defaults to null.
 * @return A new instance of Message.Assistant containing the provided content, finish reason, and associated metadata.
 */
public fun Testing.Config.SubgraphAssertionsBuilder<*, *>.assistantMessage(
    text: String,
    finishReason: String? = null
): Message.Assistant {
    val tokenCount = tokenizer?.countTokens(text)

    return Message.Assistant(
        content = text,
        finishReason = finishReason,
        metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenCount)
    )
}

/**
 * Converts a tool and its corresponding result into a `ReceivedToolResult` object.
 *
 * This utility function simplifies the creation of tool results for testing purposes.
 * It automatically handles the encoding of the result into the appropriate string format.
 *
 * @param tool The tool whose result is being processed. The tool provides context for the result.
 * @param result The result produced by the tool, which will be encoded into a string representation.
 * @return A `ReceivedToolResult` instance containing the tool's name and the encoded string representation of the result.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool result for testing
 * val result = toolResult(AnalyzeTool, AnalyzeTool.Result("Detailed analysis", 0.95))
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(AnalyzeTool, AnalyzeTool.Args("analyze")) outputs result
 * }
 * ```
 */
public fun <Result : ToolResult> toolResult(tool: Tool<*, Result>, result: Result): ReceivedToolResult =
    ReceivedToolResult(null, tool.name, tool.encodeResultToString(result), result)

/**
 * Constructs a `ReceivedToolResult` object using the provided tool and result string.
 *
 * This is a convenience function for simple tools that return text results.
 * It wraps the string result in a `ToolResult.Text` object.
 *
 * @param tool The tool for which the result is being created, of type `SimpleTool`.
 * @param result The result content generated by the tool execution as a string.
 * @return An instance of `ReceivedToolResult` containing the tool's name and the result string.
 *
 * Example usage:
 * ```kotlin
 * // Create a simple text tool result for testing
 * val result = toolResult(SolveTool, "solved")
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(SolveTool, SolveTool.Args("solve")) outputs result
 * }
 * ```
 */
public fun toolResult(tool: SimpleTool<*>, result: String): ReceivedToolResult =
    toolResult(tool, ToolResult.Text(result))

/**
 * Enables and configures the Testing feature for a Kotlin AI Agent instance.
 *
 * This function installs the Testing feature with the specified configuration.
 * It's typically used within the agent constructor block to enable testing capabilities.
 *
 * @param config A lambda function to configure the Testing feature. The default is an empty configuration.
 *
 * Example usage:
 * ```kotlin
 * // Create an agent with testing enabled
 * AIAgent(
 *     promptExecutor = mockLLMApi,
 *     toolRegistry = toolRegistry,
 *     strategy = strategy,
 *     eventHandler = eventHandler,
 *     agentConfig = agentConfig,
 * ) {
 *     // Enable testing with custom configuration
 *     withTesting {
 *         enableGraphTesting = true
 *         handleAssertion { assertionResult ->
 *             // Custom assertion handling
 *         }
 *     }
 * }
 * ```
 *
 * @see Testing
 * @see Testing.Config
 */
public fun FeatureContext.withTesting(config: Testing.Config.() -> Unit = {}) {
    install(Testing) {
        config()
    }
}
