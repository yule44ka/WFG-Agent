package ai.koog.agents.youtrack

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.StringSubgraphResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.nodes.nodeLoadFromMemory
import ai.koog.agents.memory.feature.nodes.nodeSaveToMemory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.JVMFileSystemProvider
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.Aes256GCMEncryptor
import ai.koog.agents.memory.storage.EncryptedStorage
import ai.koog.agents.youtrack.tools.BashToolSet
import ai.koog.agents.youtrack.tools.CodeGenerationToolSet
import ai.koog.agents.youtrack.tools.LLMRequestToolSet
import ai.koog.agents.youtrack.tools.YouTrackScriptingAPIToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.io.path.Path

/**
 * Memory subjects for the YouTrack workflow generator agent
 */
private object MemorySubjects {
    /**
     * Information about user preferences and previous interactions
     */
    @Serializable
    data object User : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String = "User preferences and previous interactions"
        override val priorityLevel: Int = 1
    }

    /**
     * Information about generated workflow scripts
     */
    @Serializable
    data object WorkflowScripts : MemorySubject() {
        override val name: String = "workflow-scripts"
        override val promptDescription: String = "Generated workflow scripts and their descriptions"
        override val priorityLevel: Int = 2
    }
}

/**
 * Creates and configures a YouTrack workflow generator agent.
 * This agent generates YouTrack workflow scripts based on user prompts.
 */
fun createYouTrackWorkflowGeneratorAgent(
    bashToolSet: BashToolSet,
    llmRequestToolSet: LLMRequestToolSet,
    youTrackScriptingAPIToolSet: YouTrackScriptingAPIToolSet,
    codeGenerationToolSet: CodeGenerationToolSet,
    memoryProvider: AgentMemoryProvider,
    promptExecutor: PromptExecutor,
    maxAgentIterations: Int = 50
): AIAgent {
    // Memory concepts
    val userPreferencesConcept = Concept(
        keyword = "user-preferences",
        description = """
            Information about the user's preferences including:
            - Preferred script complexity
            - Preferred coding style
            - Common use cases
            - Previous script requests
            This information helps in personalizing the generated scripts.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    val generatedScriptsConcept = Concept(
        keyword = "generated-scripts",
        description = """
            Information about previously generated workflow scripts including:
            - Script descriptions
            - Script purposes
            - Script structures
            - User feedback
            This information helps in improving future script generation.
        """.trimIndent(),
        factType = FactType.MULTIPLE
    )

    // Agent configuration
    val agentConfig = AIAgentConfig(
        prompt = prompt("youtrack-workflow-generator") {},
        model = AnthropicModels.Claude_3_Opus,
        maxAgentIterations = maxAgentIterations
    )

    // Create agent strategy
    val strategy = strategy("youtrack-workflow-generator", toolSelectionStrategy = ToolSelectionStrategy.NONE) {
        val loadMemory by subgraph<String, String>(tools = emptyList()) {
            val nodeLoadUserPreferences by nodeLoadFromMemory<String>(
                concept = userPreferencesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val nodeLoadGeneratedScripts by nodeLoadFromMemory<String>(
                concept = generatedScriptsConcept,
                subject = MemorySubjects.WorkflowScripts,
                scope = MemoryScopeType.PRODUCT
            )

            nodeStart then nodeLoadUserPreferences then nodeLoadGeneratedScripts then nodeFinish
        }

        val chainOfThought by subgraphWithTask<String>(
            tools = emptyList()
        ) { userInput ->
            markdown {
                h2("Chain of Thought Reasoning")
                text("Given the user's request, let's break down the problem into smaller steps:")
                br()
                text("1. Understand what the user is asking for")
                text("2. Identify the key components needed in the workflow script")
                text("3. Determine if we need to ask for clarification")
                text("4. Create a plan for generating the script")
                br()
                h3("User's request:")
                text(userInput)
            }
        }

        val askClarification by subgraphWithTask<String>(
            tools = emptyList()
        ) { reasoning ->
            markdown {
                h2("Ask for Clarification")
                text("Based on the chain of thought reasoning, determine if you need to ask the user for clarification.")
                text("If clarification is needed, formulate specific questions to ask the user.")
                br()
                h3("Chain of thought reasoning:")
                text(reasoning)
            }
        }

        val planGeneration by subgraphWithTask<String>(
            tools = emptyList()
        ) { input ->
            markdown {
                h2("Plan Generation")
                text("Create a detailed plan for generating the YouTrack workflow script based on the user's request and any clarifications.")
                text("The plan should include:")
                br()
                text("1. The purpose of the workflow script")
                text("2. The trigger conditions")
                text("3. The actions to be performed")
                text("4. Any requirements or dependencies")
                br()
                h3("Input:")
                text(input)
            }
        }

        val generateCode by subgraphWithTask<String>(
            tools = bashToolSet.asTools() + 
                   llmRequestToolSet.asTools() + 
                   youTrackScriptingAPIToolSet.asTools() + 
                   codeGenerationToolSet.asTools()
        ) { plan ->
            markdown {
                h2("Generate YouTrack Workflow Script")
                text("Based on the plan, generate a YouTrack workflow script that meets the user's requirements.")
                text("Use the available tools to search for relevant code snippets, generate code, and test the script.")
                br()
                h3("Plan:")
                text(plan)
            }
        }

        val saveToMemory by subgraph<String, String>(tools = emptyList()) {
            val saveUserPreferences by nodeSaveToMemory<String>(
                concept = userPreferencesConcept,
                subject = MemorySubjects.User,
                scope = MemoryScopeType.PRODUCT
            )

            val saveGeneratedScripts by nodeSaveToMemory<String>(
                concept = generatedScriptsConcept,
                subject = MemorySubjects.WorkflowScripts,
                scope = MemoryScopeType.PRODUCT
            )

            nodeStart then saveUserPreferences then saveGeneratedScripts then nodeFinish
        }

        val retrieveResult by node<StringSubgraphResult, String> { it.result }

        // Define the main workflow
        nodeStart then loadMemory then chainOfThought then askClarification then planGeneration then generateCode then retrieveResult then saveToMemory then nodeFinish
    }

    // Create and configure the agent
    return AIAgent(
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry {
            tools(bashToolSet.asTools())
            tools(llmRequestToolSet.asTools())
            tools(youTrackScriptingAPIToolSet.asTools())
            tools(codeGenerationToolSet.asTools())
        }
    ) {
        install(AgentMemory) {
            this.memoryProvider = memoryProvider
            this.featureName = "youtrack-workflow-generator"
            this.productName = "youtrack"
            this.organizationName = "jetbrains"
        }
    }
}

/**
 * Main entry point for running the YouTrack workflow generator agent as a console application.
 * 
 * @param llmService The LLM service to use (anthropic, openai, grazie)
 */
fun main(args: Array<String>) = runBlocking {
    // Determine which LLM service to use from command line arguments or default to "anthropic"
    val llmService = args.firstOrNull() ?: "anthropic"

    // Print welcome message
    println("=".repeat(80))
    println("YouTrack Workflow Generator Agent - Console Application")
    println("=".repeat(80))
    println("Using LLM service: $llmService")
    println("Type 'exit', 'quit', or 'q' to exit the application")
    println("=".repeat(80))

    try {
        // Create memory provider with a secure encryption key
        val encryptionKey = System.getenv("ENCRYPTION_KEY") ?: "default-encryption-key-please-change-in-production"
        val memoryProvider = LocalFileMemoryProvider(
            config = LocalMemoryConfig("youtrack-workflow-generator-memory"),
            storage = EncryptedStorage(
                fs = JVMFileSystemProvider.ReadWrite,
                encryption = Aes256GCMEncryptor(encryptionKey)
            ),
            fs = JVMFileSystemProvider.ReadWrite,
            root = Path("memory")
        )

        // Create tool sets
        val bashToolSet = BashToolSet()
        val llmRequestToolSet = LLMRequestToolSet()
        val youTrackScriptingAPIToolSet = YouTrackScriptingAPIToolSet()
        val codeGenerationToolSet = CodeGenerationToolSet()

        // Create the agent
        val agent = createYouTrackWorkflowGeneratorAgent(
            bashToolSet = bashToolSet,
            llmRequestToolSet = llmRequestToolSet,
            youTrackScriptingAPIToolSet = youTrackScriptingAPIToolSet,
            codeGenerationToolSet = codeGenerationToolSet,
            memoryProvider = memoryProvider,
            promptExecutor = createPromptExecutor(llmService)
        )

        // Main interaction loop
        while (true) {
            print("\nEnter your prompt (or 'exit' to quit): ")
            val userInput = readLine() ?: ""

            // Check if user wants to exit
            if (userInput.lowercase() in listOf("exit", "quit", "q")) {
                println("Exiting application. Goodbye!")
                break
            }

            // Skip empty inputs
            if (userInput.isBlank()) {
                println("Please enter a prompt or type 'exit' to quit.")
                continue
            }

            try {
                println("\nProcessing your request. This may take a moment...\n")

                // Run the agent with the user's input
                val result = agent.run(userInput)

                // Display the result
                println("-".repeat(80))
                println("RESULT:")
                println("-".repeat(80))
                println(result)
                println("-".repeat(80))
            } catch (e: Exception) {
                println("Error processing your request: ${e.message}")
                println("Please try again with a different prompt.")
            }
        }
    } catch (e: Exception) {
        println("Error initializing the agent: ${e.message}")
        println("Please check your environment variables and try again.")
    }
}

/**
 * Creates a prompt executor for the agent.
 * 
 * @param llmService The LLM service to use (anthropic, openai, grazie)
 * @return A configured prompt executor
 */
private fun createPromptExecutor(llmService: String = "anthropic"): PromptExecutor {
    return when (llmService.lowercase()) {
        "anthropic" -> ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
        "openai" -> ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
        "grazie" -> ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor(ApiKeyService.grazieApiKey) // Using Anthropic executor for Grazie
        else -> ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor(ApiKeyService.anthropicApiKey) // Default to Anthropic
    }
}
