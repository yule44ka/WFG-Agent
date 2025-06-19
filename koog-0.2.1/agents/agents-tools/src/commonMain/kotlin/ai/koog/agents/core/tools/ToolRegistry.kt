package ai.koog.agents.core.tools

/**
 * A registry that manages a collection of tools for use by agents.
 *
 * ToolRegistry serves as a central repository for all tools available to an agent.
 * It provides functionality to register tools and retrieve them by name or type.
 *
 * Key features:
 * - Maintains a unique collection of named tools
 * - Provides methods to retrieve tools by name or type
 * - Supports merging multiple registries
 *
 * Usage examples:
 * 1. Creating a registry:
 *    ```
 *    val registry = ToolRegistry {
 *        tool(MyCustomTool())
 *        tool(AnotherTool())
 *    }
 *    ```
 * 2. Merging registries:
 *    ```
 *    val combinedRegistry = registry1 + registry2
 *    ```
 *
 * @property tools The list of tools contained in this registry
 */
public class ToolRegistry private constructor(tools: List<Tool<*, *>> = emptyList()) {

    private val _tools: MutableList<Tool<*, *>> = tools.toMutableList()

    public val tools: List<Tool<*, *>>
        get() = _tools.toList()

    /**
     * Retrieves a tool by its name from the registry.
     *
     * This method searches for a tool with the specified name.
     *
     * @param toolName The name of the tool to retrieve
     * @return The tool with the specified name
     * @throws IllegalArgumentException if no tool with the specified name is found
     */
    public fun getTool(toolName: String): Tool<*, *> {
        return tools
            .firstOrNull { it.name == toolName }
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined")
    }

    /**
     * Retrieves a tool by its type from registry.
     *
     * This method searches for a tool of the specified type.
     *
     * @param T The type of tool to retrieve
     * @return The tool of the specified type
     * @throws IllegalArgumentException if no tool of the specified type is found
     */
    public inline fun <reified T : Tool<*, *>> getTool(): T {
        return tools
            .firstOrNull { it::class == T::class }
            ?.let { it as? T }
            ?: throw IllegalArgumentException("Tool with type ${T::class} is not defined")
    }

    public operator fun plus(toolRegistry: ToolRegistry): ToolRegistry {
        val mergedTools = (this.tools + toolRegistry.tools).distinctBy { it.name }
        return ToolRegistry(mergedTools)
    }

    public fun add(tool: Tool<*, *>) {
        if (_tools.contains(tool)) return
        _tools.add(tool)
    }

    public fun addAll(vararg tools: Tool<*, *>) {
        tools.forEach { tool -> add(tool) }
    }

    public class Builder internal constructor() {
        private val tools = mutableListOf<Tool<*, *>>()

        /**
         * Add a tool to the registry
         */
        public fun tool(tool: Tool<*, *>) {
            require(tool.name !in tools.map { it.name }) { "Tool \"${tool.name}\" is already defined" }
            tools.add(tool)
        }

        /**
         * Add multiple tools to the registry
         */
        public fun tools(toolsList: List<Tool<*, *>>) {
            toolsList.forEach { tool(it) }
        }

        internal fun build(): ToolRegistry {
            return ToolRegistry(tools)
        }
    }

    /**
     * Companion object providing factory methods and constants for ToolRegistry.
     */
    public companion object {
        /**
         * Creates a new ToolRegistry using the provided builder initialization block.
         *
         * @param init A lambda that configures the registry by adding tools
         * @return A new ToolRegistry instance configured according to the initialization block
         */
        public operator fun invoke(init: Builder.() -> Unit): ToolRegistry = Builder().apply(init).build()

        /**
         * A constant representing an empty registry with no tools.
         */
        public val EMPTY: ToolRegistry = ToolRegistry(emptyList())
    }
}