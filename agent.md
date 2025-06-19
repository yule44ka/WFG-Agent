# Tools
1. `bash` - Execute Bash commands.
2. `llm chat` - Interact with a large language model (LLM) for generating text or code.
3. `search code in youtrack-scripting-api library file` - Search for code snippets in the `youtrack-scripting-api` package.
4. `retrieve code shots from database` - Retrieve code shots from the database.
5. `generate code` - Generate code based on the user's request.

# Actions
1.`ask clarification` - Ask the user for clarification on their request.
2.`test code` - Test the generated code to ensure it works as expected.

# Observations
1. `get feedback` - Get feedback from the user.
2. `get test results` - Retrieve the results of the code tests to verify correctness.

# Strategy
1. `cot` - Use chain-of-thought reasoning to break down the problem into smaller steps.
2. `plan` - Create a plan based on the user's prompt.

# Workflow
User prompt -> CoT -> Ask questions ->
                                -> User -> 
                                        -> Plan -> Tools calling -> 
                                                        -> Testing subgraph -> Return