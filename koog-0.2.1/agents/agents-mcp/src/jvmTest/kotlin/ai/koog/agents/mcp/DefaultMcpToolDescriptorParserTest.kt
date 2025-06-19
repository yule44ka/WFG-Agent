package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultMcpToolDescriptorParserTest {

    private val parser = DefaultMcpToolDescriptorParser

    @Test
    fun `test basic tool parsing with name and description`() {
        // Create a simple SDK Tool with just a name and description
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject { },
            required = emptyList()
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = emptyList()
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing required and optional parameters`() {
        // Test with both required and optional parameters
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("requiredParam") {
                    put("type", "string")
                    put("description", "Required parameter")
                }
                putJsonObject("optionalParam") {
                    put("type", "integer")
                    put("description", "Optional parameter")
                }
            },
            required = listOf("requiredParam") // Only requiredParam is required, optionalParam is optional
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "requiredParam",
                    description = "Required parameter",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "optionalParam",
                    description = "Optional parameter",
                    type = ToolParameterType.Integer
                )
            )
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing all parameter types`() {
        // Create an SDK Tool with all parameter types
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                // Primitive types
                putJsonObject("stringParam") {
                    put("type", "string")
                    put("description", "String parameter")
                }
                putJsonObject("integerParam") {
                    put("type", "integer")
                    put("description", "Integer parameter")
                }
                putJsonObject("numberParam") {
                    put("type", "number")
                    put("description", "Number parameter")
                }
                putJsonObject("booleanParam") {
                    put("type", "boolean")
                    put("description", "Boolean parameter")
                }

                // Array types
                putJsonObject("arrayParam") {
                    put("type", "array")
                    put("description", "Array parameter")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }

                // Object type
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter")
                    putJsonObject("properties") {
                        putJsonObject("nestedString") {
                            put("type", "string")
                            put("description", "Nested string parameter")
                        }
                        putJsonObject("nestedInteger") {
                            put("type", "integer")
                            put("description", "Nested integer parameter")
                        }
                    }
                }
            },
            required = emptyList()
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool",
            requiredParameters = emptyList(),
            optionalParameters = listOf(
                // Primitive types
                ToolParameterDescriptor(
                    name = "stringParam",
                    description = "String parameter",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "integerParam",
                    description = "Integer parameter",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "numberParam",
                    description = "Number parameter",
                    type = ToolParameterType.Float
                ),
                ToolParameterDescriptor(
                    name = "booleanParam",
                    description = "Boolean parameter",
                    type = ToolParameterType.Boolean
                ),

                // Array type
                ToolParameterDescriptor(
                    name = "arrayParam",
                    description = "Array parameter",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),

                // Object type
                ToolParameterDescriptor(
                    name = "objectParam",
                    description = "Object parameter",
                    type = ToolParameterType.Object(
                        listOf(
                            ToolParameterDescriptor(
                                name = "nestedString",
                                description = "Object parameter",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "nestedInteger",
                                description = "Object parameter",
                                type = ToolParameterType.Integer
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parsing enum parameter type`() {
        // Create an SDK Tool with an enum parameter
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool with enum parameter",
            properties = buildJsonObject {
                putJsonObject("enumParam") {
                    put("type", "enum")
                    put("description", "Enum parameter")
                    putJsonArray("enum") {
                        add("option1")
                        add("option2")
                        add("option3")
                    }
                }
            },
            required = listOf("enumParam")
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the basic properties
        assertEquals("test-tool", toolDescriptor.name)
        assertEquals("A test tool with enum parameter", toolDescriptor.description)
        assertEquals(1, toolDescriptor.requiredParameters.size)
        assertEquals(0, toolDescriptor.optionalParameters.size)

        // Verify the enum parameter
        val enumParam = toolDescriptor.requiredParameters.first()
        assertEquals("enumParam", enumParam.name)
        assertEquals("Enum parameter", enumParam.description)
        assertTrue(enumParam.type is ToolParameterType.Enum)

        // Verify the enum values
        val enumType = enumParam.type as ToolParameterType.Enum
        val expectedOptions = arrayOf("option1", "option2", "option3")
        assertEquals(expectedOptions.size, enumType.entries.size)
        expectedOptions.forEachIndexed { index, option ->
            assertEquals(option, enumType.entries[index])
        }
    }

    @Test
    fun `test parsing object parameter with additional properties`() {
        // Create an SDK Tool with an object parameter that has additional properties
        val sdkTool = createSdkTool(
            name = "test-tool",
            description = "A test tool with object parameter",
            properties = buildJsonObject {
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "Name property")
                        }
                        putJsonObject("age") {
                            put("type", "integer")
                            put("description", "Age property")
                        }
                    }
                    putJsonArray("required") {
                        add("name")
                    }
                    putJsonObject("additionalProperties") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("objectParam")
        )

        // Parse the tool
        val toolDescriptor = parser.parse(sdkTool)

        // Verify the result
        val expectedToolDescriptor = ToolDescriptor(
            name = "test-tool",
            description = "A test tool with object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "objectParam",
                    description = "Object parameter",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "name",
                                description = "Object parameter",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "age",
                                description = "Object parameter",
                                type = ToolParameterType.Integer
                            )
                        ),
                        requiredProperties = listOf("name"),
                        additionalPropertiesType = ToolParameterType.String,
                        additionalProperties = true
                    )
                )
            ),
            optionalParameters = emptyList()
        )
        assertEquals(expectedToolDescriptor, toolDescriptor)
    }

    @Test
    fun `test parameter type is missing`() {
        val missingTypeToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidParam") {
                    put("description", "Invalid parameter")
                    // Missing type property
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when parameter type is missing") {
            parser.parse(missingTypeToolSdk)
        }
    }

    @Test
    fun `test array items property is missing`() {
        val missingArrayItemsToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidArrayParam") {
                    put("type", "array")
                    put("description", "Invalid array parameter")
                    // Missing items property
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when array items property is missing") {
            parser.parse(missingArrayItemsToolSdk)
        }
    }

    @Test
    fun `test object without properties returns empty properties list`() {
        val missingObjectPropertiesToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("objectParam") {
                    put("type", "object")
                    put("description", "Object parameter without properties")
                    // Missing properties property
                }
            },
            required = emptyList()
        )

        val toolDescriptor = parser.parse(missingObjectPropertiesToolSdk)
        val objectParam = toolDescriptor.optionalParameters.first()
        val objectType = objectParam.type as ToolParameterType.Object
        assertEquals(emptyList(), objectType.properties, "Object without properties should have empty properties list")
    }

    @Test
    fun `test parameter type is unsupported`() {
        val unsupportedTypeToolSdk = createSdkTool(
            name = "test-tool",
            description = "A test tool",
            properties = buildJsonObject {
                putJsonObject("invalidTypeParam") {
                    put("type", "unsupported")
                    put("description", "Invalid type parameter")
                }
            },
            required = emptyList()
        )

        assertFailsWith<IllegalArgumentException>("Should fail when parameter type is unsupported") {
            parser.parse(unsupportedTypeToolSdk)
        }
    }

    // Helper function to create an SDK Tool for testing
    private fun createSdkTool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String>
    ): Tool {
        return Tool(
            name = name,
            description = description,
            inputSchema = Tool.Input(
                properties = properties,
                required = required
            )
        )
    }
}
