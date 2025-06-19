package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * An object representing the exit tool, primarily intended for ending conversations upon user request
 * or based on agent decision. This tool finalizes interactions with a provided message.
 *
 * The tool utilizes a structured set of arguments, which includes the final message of the agent
 * to provide closure to the conversation. It returns the result as a standardized string, signaling
 * the execution has been completed.
 *
 * The descriptor defines the tool's metadata including its name, description, and required parameters.
 */
public object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    public data class Args(val message: String) : Tool.Args

    override suspend fun doExecute(args: Args): String {
        return "DONE"
    }

    override val argsSerializer: KSerializer<Args>
        get() = Args.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = "__exit__",
            description = "Service tool, used by the agent to end conversation on user request or agent decision",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "message", description = "Final message of the agent", type = ToolParameterType.String
                )
            )
        )
}
