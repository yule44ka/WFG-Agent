package ai.koog.agents.youtrack.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tool set for generating and managing YouTrack workflow scripts.
 */
@LLMDescription("Tools for generating and managing YouTrack workflow scripts")
class CodeGenerationToolSet : ToolSet {

    private val scriptsDirectory = "generated-scripts"

    init {
        // Ensure the scripts directory exists
        val dir = File(scriptsDirectory)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    @Tool
    @LLMDescription("Generate a YouTrack workflow script based on a description")
    fun generateWorkflowScript(
        @LLMDescription("A detailed description of what the workflow script should do")
        description: String,
        
        @LLMDescription("The name of the script file to create (without extension)")
        scriptName: String
    ): String {
        // Create an LLM request tool to generate the code
        val llmTool = LLMRequestToolSet()
        
        // Get the workflow script template
        val apiTool = YouTrackScriptingAPIToolSet()
        val template = apiTool.getWorkflowScriptTemplate()
        
        // Generate the code using the LLM
        val prompt = """
            Generate a YouTrack workflow script based on the following description:
            
            $description
            
            Use the following template as a starting point:
            
            ```javascript
            $template
            ```
            
            Make sure to:
            1. Give the rule a descriptive title
            2. Implement appropriate guard conditions
            3. Implement the action logic
            4. Specify any required fields in the requirements section
            
            Return only the complete JavaScript code without explanations or markdown formatting.
        """.trimIndent()
        
        val generatedCode = llmTool.generateCode(prompt, "javascript")
        
        // Save the generated code to a file
        val fileName = "$scriptName.js"
        val filePath = "$scriptsDirectory/$fileName"
        
        try {
            File(filePath).writeText(generatedCode)
            return "Successfully generated workflow script at $filePath:\n\n$generatedCode"
        } catch (e: Exception) {
            return "Error saving generated script: ${e.message}\n\nGenerated code:\n$generatedCode"
        }
    }

    @Tool
    @LLMDescription("List all generated workflow scripts")
    fun listGeneratedScripts(): String {
        val dir = File(scriptsDirectory)
        if (!dir.exists() || !dir.isDirectory) {
            return "Error: Generated scripts directory not found at $scriptsDirectory"
        }

        val files = dir.listFiles()?.filter { it.isFile && it.extension == "js" } ?: emptyList()
        
        return if (files.isEmpty()) {
            "No generated workflow scripts found"
        } else {
            "Generated workflow scripts:\n" + files.joinToString("\n") { it.name }
        }
    }

    @Tool
    @LLMDescription("Get the content of a generated workflow script")
    fun getGeneratedScriptContent(
        @LLMDescription("The name of the script file to retrieve (with or without .js extension)")
        scriptName: String
    ): String {
        val fileName = if (scriptName.endsWith(".js")) scriptName else "$scriptName.js"
        val filePath = "$scriptsDirectory/$fileName"
        val file = File(filePath)
        
        if (!file.exists() || !file.isFile) {
            return "Error: Script not found at $filePath"
        }

        return try {
            file.readText()
        } catch (e: Exception) {
            "Error reading script: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Modify an existing workflow script")
    fun modifyWorkflowScript(
        @LLMDescription("The name of the script file to modify (with or without .js extension)")
        scriptName: String,
        
        @LLMDescription("A description of the modifications to make")
        modificationDescription: String
    ): String {
        val fileName = if (scriptName.endsWith(".js")) scriptName else "$scriptName.js"
        val filePath = "$scriptsDirectory/$fileName"
        val file = File(filePath)
        
        if (!file.exists() || !file.isFile) {
            return "Error: Script not found at $filePath"
        }

        try {
            val existingCode = file.readText()
            
            // Create an LLM request tool to generate the modified code
            val llmTool = LLMRequestToolSet()
            
            val prompt = """
                Modify the following YouTrack workflow script according to these requirements:
                
                $modificationDescription
                
                Here is the existing code:
                
                ```javascript
                $existingCode
                ```
                
                Return only the complete modified JavaScript code without explanations or markdown formatting.
            """.trimIndent()
            
            val modifiedCode = llmTool.generateCode(prompt, "javascript")
            
            // Save the modified code
            file.writeText(modifiedCode)
            
            return "Successfully modified workflow script at $filePath:\n\n$modifiedCode"
        } catch (e: Exception) {
            return "Error modifying script: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Validate a workflow script for common errors")
    fun validateWorkflowScript(
        @LLMDescription("The JavaScript code of the workflow script to validate")
        scriptCode: String
    ): String {
        // Create an LLM request tool to validate the code
        val llmTool = LLMRequestToolSet()
        
        val prompt = """
            Validate the following YouTrack workflow script for common errors and best practices:
            
            ```javascript
            $scriptCode
            ```
            
            Check for:
            1. Syntax errors
            2. Missing or incorrect imports
            3. Proper error handling
            4. Correct usage of YouTrack scripting API
            5. Potential performance issues
            
            Return a validation report with any issues found and suggestions for improvement.
            If no issues are found, state that the script appears valid.
        """.trimIndent()
        
        return llmTool.generateText(prompt)
    }

    @Tool
    @LLMDescription("Generate documentation for a workflow script")
    fun generateScriptDocumentation(
        @LLMDescription("The JavaScript code of the workflow script to document")
        scriptCode: String
    ): String {
        // Create an LLM request tool to generate documentation
        val llmTool = LLMRequestToolSet()
        
        val prompt = """
            Generate comprehensive documentation for the following YouTrack workflow script:
            
            ```javascript
            $scriptCode
            ```
            
            The documentation should include:
            1. A high-level overview of what the script does
            2. The trigger conditions (when the script runs)
            3. The actions performed by the script
            4. Required fields and their types
            5. Any potential side effects or limitations
            
            Format the documentation in Markdown.
        """.trimIndent()
        
        return llmTool.generateText(prompt)
    }
}