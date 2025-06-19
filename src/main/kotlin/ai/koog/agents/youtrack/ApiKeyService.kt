package ai.koog.agents.youtrack

/**
 * Service for retrieving API keys for LLM services.
 */
object ApiKeyService {
    /**
     * Gets the OpenAI API key from the environment variable.
     * If not set, returns a default value or throws an exception.
     */
    val openAIApiKey: String
        get() = System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY environment variable is not set")

    /**
     * Gets the Anthropic API key from the environment variable.
     * If not set, returns a default value or throws an exception.
     */
    val anthropicApiKey: String
        get() = System.getenv("ANTHROPIC_API_KEY") ?: throw IllegalArgumentException("ANTHROPIC_API_KEY environment variable is not set")

    /**
     * Gets the Google API key from the environment variable.
     * If not set, returns a default value or throws an exception.
     */
    val googleApiKey: String
        get() = System.getenv("GOOGLE_API_KEY") ?: throw IllegalArgumentException("GOOGLE_API_KEY environment variable is not set")

    /**
     * Gets the Grazie API key from the environment variable.
     * If not set, returns a default value or throws an exception.
     */
    val grazieApiKey: String
        get() = System.getenv("GRAZIE_API_KEY") ?: throw IllegalArgumentException("GRAZIE_API_KEY environment variable is not set")
}