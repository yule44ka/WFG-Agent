# YouTrack Workflow Generator Agent

A Python agent that generates YouTrack workflow scripts based on user prompts.

## Overview

The YouTrack Workflow Generator Agent is a tool that helps users create workflow scripts for YouTrack by describing their requirements in natural language. The agent uses a combination of tools, actions, observations, and strategies to understand the user's requirements and generate appropriate workflow scripts.

## Features

- Generate YouTrack workflow scripts from natural language descriptions
- Interactive mode for refining requirements and getting feedback
- AI-generated clarification questions to better understand user requirements, with graceful handling of minimal responses
- Chain-of-thought reasoning to break down complex problems
- Detailed planning for script implementation
- Code validation and testing
- Memory for storing and retrieving previous interactions

## Installation

### Prerequisites

- Python 3.8 or higher
- Node.js (for JavaScript syntax checking)
- JetBrains Grazie API key

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/youtrack-workflow-generator.git
   cd youtrack-workflow-generator
   ```

2. Install the required dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. Set up your Grazie API key:
   ```bash
   export GRAZIE_API_KEY=your_grazie_api_key_here
   ```

## Usage

### Command Line Interface

The agent can be used from the command line with various options:

```bash
python src/main/python/main.py --prompt "Create a workflow script that require setting due to date when creating issue" --output workflow.js
```

#### Options

- `--prompt`: The prompt describing the desired workflow script
- `--api-key`: Grazie API key for the LLM service (if not set as an environment variable)
- `--output`: Output file for the generated script
- `--interactive`: Run in interactive mode

### Interactive Mode

You can also run the agent in interactive mode:

```bash
python src/main/python/main.py --interactive
```

In interactive mode, the agent will:
1. Ask for your requirements
2. Generate a workflow script
3. Show you the generated script
4. Ask for feedback
5. Regenerate the script if needed

## Architecture

The agent is built with a modular architecture that includes:

### Tools

1. `BashTool`: Execute Bash commands
2. `LLMRequestTool`: Interact with a large language model (LLM) for generating text or code
3. `SearchCodeTool`: Search for code snippets in the YouTrack scripting API
4. `RetrieveCodeShotsTool`: Retrieve code shots from a database
5. `GenerateCodeTool`: Generate code based on the user's request

### Actions

1. `AskClarificationAction`: Generate and ask AI-powered clarification questions based on the user's request
2. `TestCodeAction`: Test the generated code to ensure it works as expected

### Observations

1. `GetFeedbackObservation`: Get feedback from the user
2. `GetTestResultsObservation`: Retrieve the results of code tests to verify correctness

### Strategies

1. `ChainOfThoughtStrategy`: Use chain-of-thought reasoning to break down the problem into smaller steps
2. `PlanStrategy`: Create a plan based on the user's prompt

### Memory

The agent uses a memory system to store information about the user's requests, preferences, and previous interactions. This allows the agent to provide more personalized responses and improve its performance over time.

## Examples

### Example 1: Auto-assign critical issues

```bash
python src/main/python/main.py --prompt "Create a workflow script that automatically assigns issues to the team lead when they are created with a Critical priority" --output auto_assign.js
```

### Example 2: Auto-set due date based on priority

```bash
python src/main/python/main.py --prompt "Create a workflow script that automatically sets a due date based on the issue's priority" --output auto_due_date.js
```

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Acknowledgements

- YouTrack Scripting API documentation
- JetBrains for providing the Grazie language model API
