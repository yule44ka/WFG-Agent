package ai.koog.agents.youtrack.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.youtrack.ApiKeyService
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message

/**
 * Tool set for interacting with a large language model (LLM).
 */
@LLMDescription("Tools for interacting with a large language model")
class LLMRequestToolSet : ToolSet {

    @Tool
    @LLMDescription("Generate text using a large language model")
    fun generateText(
        @LLMDescription("The prompt to send to the LLM")
        prompt: String,

        @LLMDescription("System instructions for the LLM (optional)")
        systemInstructions: String = "You are a helpful assistant that specializes in YouTrack workflow scripting.",

        @LLMDescription("The LLM service to use (anthropic, openai, grazie)")
        llmService: String = "anthropic"
    ): String {
        return try {
            // Use the appropriate API key based on the selected LLM service
            val executor = when (llmService.lowercase()) {
                "anthropic" -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
                "openai" -> simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
                "grazie" -> simpleAnthropicExecutor(ApiKeyService.grazieApiKey) // Using Anthropic executor for Grazie
                else -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey) // Default to Anthropic
            }

            val response = executor.execute(
                messages = listOf(
                    Message.System(systemInstructions),
                    Message.User(prompt)
                ),
                model = AnthropicModels.Claude_3_Sonnet
            )

            when (response) {
                is Message.Assistant -> response.content
                else -> "Error: Unexpected response type: ${response::class.simpleName}"
            }
        } catch (e: Exception) {
            "Error generating text: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Generate code using a large language model")
    fun generateCode(
        @LLMDescription("The prompt describing the code to generate")
        prompt: String,

        @LLMDescription("The programming language to generate code in")
        language: String = "javascript",

        @LLMDescription("System instructions for the LLM (optional)")
        systemInstructions: String = "You are a skilled programmer specializing in YouTrack workflow scripting. Generate only code without explanations.",

        @LLMDescription("The LLM service to use (anthropic, openai, grazie)")
        llmService: String = "anthropic"
    ): String {
        return try {
            // Use the appropriate API key based on the selected LLM service
            val executor = when (llmService.lowercase()) {
                "anthropic" -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
                "openai" -> simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
                "grazie" -> simpleAnthropicExecutor(ApiKeyService.grazieApiKey) // Using Anthropic executor for Grazie
                else -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey) // Default to Anthropic
            }

            val fullPrompt = """
                Generate code in $language based on the following requirements:

                $prompt

                Return only the code without explanations or markdown formatting.
            """.trimIndent()

            val response = executor.execute(
                messages = listOf(
                    Message.System(systemInstructions),
                    Message.User(fullPrompt)
                ),
                model = AnthropicModels.Claude_3_Sonnet
            )

            when (response) {
                is Message.Assistant -> response.content
                else -> "Error: Unexpected response type: ${response::class.simpleName}"
            }
        } catch (e: Exception) {
            "Error generating code: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Explain code using a large language model")
    fun explainCode(
        @LLMDescription("The code to explain")
        code: String,

        @LLMDescription("The level of detail for the explanation (brief, moderate, detailed)")
        detailLevel: String = "moderate",

        @LLMDescription("The LLM service to use (anthropic, openai, grazie)")
        llmService: String = "anthropic"
    ): String {
        return try {
            // Use the appropriate API key based on the selected LLM service
            val executor = when (llmService.lowercase()) {
                "anthropic" -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey)
                "openai" -> simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
                "grazie" -> simpleAnthropicExecutor(ApiKeyService.grazieApiKey) // Using Anthropic executor for Grazie
                else -> simpleAnthropicExecutor(ApiKeyService.anthropicApiKey) // Default to Anthropic
            }

            val fullPrompt = """
                Explain the following code with a $detailLevel level of detail:

                ```
                $code
                ```
            """.trimIndent()

            val response = executor.execute(
                messages = listOf(
                    Message.System("You are a skilled programmer who explains code clearly and accurately."),
                    Message.User(fullPrompt)
                ),
                model = AnthropicModels.Claude_3_Sonnet
            )

            when (response) {
                is Message.Assistant -> response.content
                else -> "Error: Unexpected response type: ${response::class.simpleName}"
            }
        } catch (e: Exception) {
            "Error explaining code: ${e.message}"
        }
    }
}
