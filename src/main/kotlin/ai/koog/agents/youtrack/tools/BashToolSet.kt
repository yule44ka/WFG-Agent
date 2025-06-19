package ai.koog.agents.youtrack.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Tool set for executing bash commands.
 */
@LLMDescription("Tools for executing bash commands")
class BashToolSet : ToolSet {

    @Tool
    @LLMDescription("Execute a bash command and return the output")
    fun executeBashCommand(
        @LLMDescription("The bash command to execute")
        command: String,
        
        @LLMDescription("Timeout in seconds (default: 30)")
        timeoutSeconds: Int = 30
    ): String {
        return try {
            val process = ProcessBuilder("/bin/bash", "-c", command)
                .redirectErrorStream(true)
                .start()
            
            val completed = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return "Command execution timed out after $timeoutSeconds seconds"
            }
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            if (process.exitValue() != 0) {
                "Command execution failed with exit code ${process.exitValue()}\n$output"
            } else {
                output.toString()
            }
        } catch (e: Exception) {
            "Error executing command: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("List files in a directory")
    fun listFiles(
        @LLMDescription("The directory path to list files from (default: current directory)")
        directory: String = "."
    ): String {
        return executeBashCommand("ls -la $directory")
    }

    @Tool
    @LLMDescription("Read the content of a file")
    fun readFile(
        @LLMDescription("The path to the file to read")
        filePath: String
    ): String {
        return executeBashCommand("cat $filePath")
    }

    @Tool
    @LLMDescription("Write content to a file")
    fun writeFile(
        @LLMDescription("The path to the file to write")
        filePath: String,
        
        @LLMDescription("The content to write to the file")
        content: String
    ): String {
        // Create a temporary file with the content
        val tempFile = "temp_${System.currentTimeMillis()}.txt"
        executeBashCommand("echo '$content' > $tempFile")
        
        // Move the temporary file to the target location
        val result = executeBashCommand("mv $tempFile $filePath")
        
        return if (result.isBlank()) {
            "Successfully wrote content to $filePath"
        } else {
            "Error writing to file: $result"
        }
    }
}