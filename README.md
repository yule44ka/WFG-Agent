# YouTrack Workflow Generator Agent

This project implements an AI agent that generates YouTrack workflow scripts based on user prompts. The agent uses the Koog framework for implementation and leverages large language models to generate code.

## Project Structure

- `src/main/kotlin/ai/koog/agents/youtrack/`: Contains the agent implementation
  - `YouTrackWorkflowGeneratorAgent.kt`: Main agent implementation
  - `tools/`: Contains tool implementations
    - `BashToolSet.kt`: Tools for executing bash commands
    - `LLMRequestToolSet.kt`: Tools for interacting with large language models
    - `YouTrackScriptingAPIToolSet.kt`: Tools for accessing the YouTrack scripting API
    - `CodeGenerationToolSet.kt`: Tools for generating and managing workflow scripts
- `packages/youtrack-scripting-api/`: Contains the YouTrack scripting API
  - `entities.js`: Provides access to YouTrack entities
  - `fields.js`: Provides access to issue fields
  - `workflow.js`: Provides workflow-specific functionality
  - `index.js`: Main entry point for the API
- `generated-scripts/`: Directory where generated workflow scripts are stored

## Agent Workflow

1. **Get User Prompt**: The agent receives a prompt from the user describing the desired workflow script.
2. **Chain of Thought**: The agent breaks down the problem into smaller steps.
3. **Ask Clarification**: If needed, the agent asks the user for clarification.
4. **Plan Generation**: The agent creates a plan for generating the script.
5. **Code Generation**: The agent generates the workflow script based on the plan.
6. **Return Code**: The agent returns the generated script to the user.

## Memory

The agent uses memory to store information about:
- User preferences and previous interactions
- Generated workflow scripts and their descriptions

This allows the agent to provide more personalized responses and improve its performance over time.

## Tools

The agent has access to the following tools:

1. **Bash Tools**:
   - Execute bash commands
   - List files
   - Read file content
   - Write to files

2. **LLM Request Tools**:
   - Generate text
   - Generate code
   - Explain code

3. **YouTrack Scripting API Tools**:
   - List API files
   - Get API file content
   - Search in API files
   - Get workflow script template
   - Get common workflow patterns

4. **Code Generation Tools**:
   - Generate workflow scripts
   - List generated scripts
   - Get script content
   - Modify scripts
   - Validate scripts
   - Generate documentation

## Running the Agent

The agent runs as an interactive console application that allows you to enter prompts and receive generated workflow scripts.

To start the console application:

```bash
# From the project root directory
./gradlew run
```

This will launch the interactive console where you can enter your prompts.

### Console Application Usage

Once the application is running, you'll see a welcome message and a prompt to enter your request:

```
================================================================================
YouTrack Workflow Generator Agent - Console Application
================================================================================
Using LLM service: anthropic
Type 'exit', 'quit', or 'q' to exit the application
================================================================================

Enter your prompt (or 'exit' to quit): 
```

1. Enter your prompt describing the workflow script you want to generate
2. The agent will process your request and display the result
3. You can then enter another prompt or exit the application
4. To exit, type `exit`, `quit`, or `q` at the prompt

Example interaction:

```
Enter your prompt (or 'exit' to quit): Create a workflow that adds a tag "needs-review" when priority is set to Critical

Processing your request. This may take a moment...

--------------------------------------------------------------------------------
RESULT:
--------------------------------------------------------------------------------
[Generated workflow script will appear here]
--------------------------------------------------------------------------------

Enter your prompt (or 'exit' to quit): exit
Exiting application. Goodbye!
```

## Example Usage

```kotlin
// Create memory provider
val memoryProvider = LocalFileMemoryProvider(
    config = LocalMemoryConfig("youtrack-workflow-generator-memory"),
    storage = EncryptedStorage(
        fs = JVMFileSystemProvider.ReadWrite,
        encryption = Aes256GCMEncryptor("your-secret-key-here")
    ),
    fs = JVMFileSystemProvider.ReadWrite,
    root = Path("memory")
)

// Create tool sets
val bashToolSet = BashToolSet()
val llmRequestToolSet = LLMRequestToolSet()
val youTrackScriptingAPIToolSet = YouTrackScriptingAPIToolSet()
val codeGenerationToolSet = CodeGenerationToolSet()

// Create and run the agent
val agent = createYouTrackWorkflowGeneratorAgent(
    bashToolSet = bashToolSet,
    llmRequestToolSet = llmRequestToolSet,
    youTrackScriptingAPIToolSet = youTrackScriptingAPIToolSet,
    codeGenerationToolSet = codeGenerationToolSet,
    memoryProvider = memoryProvider,
    promptExecutor = createPromptExecutor()
)

// Run the agent with a prompt
val result = agent.run("Generate a YouTrack workflow script that automatically sets the due date to 7 days from now when an issue is created.")
println("Generated script: $result")
```

## Setting Up API Keys

To use the agent with real LLM services, you need to set up the following environment variables:

1. For Anthropic Claude:
   ```bash
   export ANTHROPIC_API_KEY=your_anthropic_api_key_here
   ```

2. For OpenAI:
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   ```

3. For Grazie:
   ```bash
   export GRAZIE_API_KEY=your_grazie_api_key_here
   ```

4. For memory encryption (optional, but recommended for production):
   ```bash
   export ENCRYPTION_KEY=your_secure_encryption_key_here
   ```

## Running with Different LLM Services

You can specify which LLM service to use when running the agent:

```bash
# Run with Anthropic Claude (default)
./gradlew run

# Run with OpenAI
./gradlew run --args="openai"

# Run with Grazie
./gradlew run --args="grazie"
```

## Notes

- The agent uses Claude 3 Opus as the default model, but you can configure it to use other models.
- The memory is stored in an encrypted format for security.
- If environment variables are not set, the agent will throw an exception with instructions on what needs to be set.
