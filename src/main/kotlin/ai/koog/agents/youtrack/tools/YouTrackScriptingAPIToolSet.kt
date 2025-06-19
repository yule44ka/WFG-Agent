package ai.koog.agents.youtrack.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tool set for searching and accessing the YouTrack scripting API.
 */
@LLMDescription("Tools for searching and accessing the YouTrack scripting API")
class YouTrackScriptingAPIToolSet : ToolSet {

    private val apiBasePath = "packages/youtrack-scripting-api"

    @Tool
    @LLMDescription("List all files in the YouTrack scripting API package")
    fun listAPIFiles(): String {
        val apiDir = File(apiBasePath)
        if (!apiDir.exists() || !apiDir.isDirectory) {
            return "Error: YouTrack scripting API directory not found at $apiBasePath"
        }

        val files = apiDir.listFiles()
        if (files.isNullOrEmpty()) {
            return "No files found in the YouTrack scripting API directory"
        }

        return files.joinToString("\n") { it.name }
    }

    @Tool
    @LLMDescription("Get the content of a specific file in the YouTrack scripting API package")
    fun getAPIFileContent(
        @LLMDescription("The name of the file to retrieve (e.g., 'entities.js')")
        fileName: String
    ): String {
        val filePath = "$apiBasePath/$fileName"
        val file = File(filePath)
        
        if (!file.exists() || !file.isFile) {
            return "Error: File not found at $filePath"
        }

        return try {
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Search for code snippets in the YouTrack scripting API files")
    fun searchInAPIFiles(
        @LLMDescription("The search term to look for in the API files")
        searchTerm: String
    ): String {
        val apiDir = File(apiBasePath)
        if (!apiDir.exists() || !apiDir.isDirectory) {
            return "Error: YouTrack scripting API directory not found at $apiBasePath"
        }

        val results = StringBuilder()
        val files = apiDir.listFiles()?.filter { it.isFile && it.extension == "js" } ?: emptyList()

        if (files.isEmpty()) {
            return "No JavaScript files found in the YouTrack scripting API directory"
        }

        var foundAny = false
        for (file in files) {
            try {
                val content = file.readText()
                val lines = content.lines()
                
                for ((index, line) in lines.withIndex()) {
                    if (line.contains(searchTerm, ignoreCase = true)) {
                        if (!foundAny) {
                            results.append("Search results for '$searchTerm':\n\n")
                            foundAny = true
                        }
                        
                        // Add file name and line number
                        results.append("File: ${file.name}, Line ${index + 1}:\n")
                        
                        // Add context (3 lines before and after)
                        val startLine = maxOf(0, index - 3)
                        val endLine = minOf(lines.size - 1, index + 3)
                        
                        for (contextLine in startLine..endLine) {
                            val prefix = if (contextLine == index) ">> " else "   "
                            results.append("$prefix${lines[contextLine]}\n")
                        }
                        
                        results.append("\n")
                    }
                }
            } catch (e: Exception) {
                results.append("Error searching in ${file.name}: ${e.message}\n")
            }
        }

        return if (foundAny) results.toString() else "No matches found for '$searchTerm' in the YouTrack scripting API files"
    }

    @Tool
    @LLMDescription("Get a workflow script template")
    fun getWorkflowScriptTemplate(): String {
        return """
            /**
             * This is a template for an on-change rule. This rule defines what
             * happens when a change is applied to an issue.
             *
             * For details, read the Quick Start Guide:
             * https://www.jetbrains.com/help/youtrack/devportal/Quick-Start-Guide-Workflows-JS.html
             */

            const entities = require('@jetbrains/youtrack-scripting-api/entities');

            exports.rule = entities.Issue.onChange({
              // TODO: give the rule a human-readable title
              title: 'My Rule',
              guard: (ctx) => {
                // TODO specify the conditions for executing the rule
                return true;
              },
              action: (ctx) => {
                const issue = ctx.issue;
                // TODO: specify what to do when a change is applied to an issue
              },
              requirements: {
                // TODO: add requirements
              }
            });
        """.trimIndent()
    }

    @Tool
    @LLMDescription("Get examples of common YouTrack workflow patterns")
    fun getCommonWorkflowPatterns(
        @LLMDescription("The type of pattern to retrieve (e.g., 'field update', 'notification', 'state transition')")
        patternType: String = "all"
    ): String {
        val patterns = mapOf(
            "field update" to """
                // Example: Update a field when another field changes
                exports.rule = entities.Issue.onChange({
                  title: 'Update Due Date when Priority Changes',
                  guard: (ctx) => {
                    return ctx.issue.fields.Priority && ctx.issue.fields.Priority.changed;
                  },
                  action: (ctx) => {
                    const issue = ctx.issue;
                    const priority = issue.fields.Priority;
                    
                    if (priority.name === 'Critical') {
                      // Set due date to tomorrow for critical issues
                      const tomorrow = new Date();
                      tomorrow.setDate(tomorrow.getDate() + 1);
                      issue.fields.DueDate = tomorrow;
                    }
                  },
                  requirements: {
                    Priority: { type: entities.Field.enum },
                    DueDate: { type: entities.Field.date }
                  }
                });
            """.trimIndent(),
            
            "notification" to """
                // Example: Send notification when an issue is assigned
                const workflow = require('@jetbrains/youtrack-scripting-api/workflow');
                
                exports.rule = entities.Issue.onChange({
                  title: 'Notify on Assignment',
                  guard: (ctx) => {
                    return ctx.issue.fields.Assignee && ctx.issue.fields.Assignee.changed;
                  },
                  action: (ctx) => {
                    const issue = ctx.issue;
                    const assignee = issue.fields.Assignee;
                    
                    if (assignee) {
                      workflow.WorkflowActions.sendNotification({
                        to: assignee.email,
                        subject: `You've been assigned to issue ${issue.id}`,
                        body: `You've been assigned to issue ${issue.id}: ${issue.summary}`
                      });
                    }
                  },
                  requirements: {
                    Assignee: { type: entities.Field.user }
                  }
                });
            """.trimIndent(),
            
            "state transition" to """
                // Example: Automatically transition issue state based on conditions
                exports.rule = entities.Issue.onChange({
                  title: 'Auto-transition to In Progress',
                  guard: (ctx) => {
                    return ctx.issue.fields.Assignee && 
                           ctx.issue.fields.Assignee.changed &&
                           ctx.issue.fields.State;
                  },
                  action: (ctx) => {
                    const issue = ctx.issue;
                    const state = issue.fields.State;
                    
                    // If issue is assigned and in Open state, move to In Progress
                    if (state.name === 'Open' && issue.fields.Assignee) {
                      issue.fields.State = issue.project.findFieldValue('State', 'In Progress');
                    }
                  },
                  requirements: {
                    Assignee: { type: entities.Field.user },
                    State: { type: entities.Field.state }
                  }
                });
            """.trimIndent()
        )

        return if (patternType.lowercase() == "all") {
            patterns.entries.joinToString("\n\n") { (type, code) ->
                "=== $type ===\n$code"
            }
        } else {
            patterns[patternType.lowercase()] ?: "Pattern type '$patternType' not found. Available types: ${patterns.keys.joinToString(", ")}"
        }
    }
}