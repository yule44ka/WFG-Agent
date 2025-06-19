# Agent Overview
Goal: Code generation of youtrack workflow scripts by user prompt.

# Framework: Koog (https://github.com/JetBrains/koog)
Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin. It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

# YouTrack Scripting API
The library `youtrack-scripting-api` provides a set of APIs to interact with YouTrack's scripting capabilities, allowing users to automate tasks and extend YouTrack's functionality.

# Tools
1. `bash` - Execute Bash commands.
2. `llm request` - Interact with a large language model (LLM) for generating text or code.
3. `search code in youtrack-scripting-api library file` - Search for code snippets in the `youtrack-scripting-api` package.
4. `generate code` - Generate code based on the user's request.

# Actions
1.`ask clarification` - Ask the user for clarification on their request.

# Observations
1. `get user prompt` - Get user request for a desired workflow script.
2. `get feedback` - Get feedback from the user.

# Strategy
1. `cot` - Use chain-of-thought reasoning to break down the problem into smaller steps.
2. `plan` - Create a plan based on the user's prompt.

# Memory
We can use memory to store information about the user's requests, preferences, and previous interactions. This allows the agent to provide more personalized responses and improve its performance over time.

# Agent Workflow
Get user prompt -> CoT -> Ask questions ->
                                -> User -> 
                                        -> Plan -> Tools calling -> Return code

# Resources
1. https://huyenchip.com/2025/01/07/agents.html - A comprehensive guide on AI agents and their applications.
2. https://www.anthropic.com/engineering/building-effective-agents - A resource on building effective AI agents.
