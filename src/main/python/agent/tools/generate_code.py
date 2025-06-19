"""
Generate Code tool for the YouTrack Workflow Generator Agent.

This module provides a GenerateCodeTool class that allows the agent to generate
YouTrack workflow scripts based on the user's request.
"""

import os
import json
from typing import Dict, List, Any, Optional

from .llm_request import LLMRequestTool


class GenerateCodeTool:
    """
    Tool for generating YouTrack workflow scripts.

    This tool allows the agent to generate workflow scripts based on the user's request,
    incorporating information from search results and code shots to improve the quality
    of the generated code.
    """

    def __init__(self, api_key: Optional[str] = None):
        """
        Initialize the GenerateCodeTool.

        Args:
            api_key: Optional API key for the LLM service. If not provided,
                     it will be read from the OPENAI_API_KEY environment variable.
        """
        self.llm_tool = LLMRequestTool(api_key=api_key)

    def execute(
        self,
        prompt: str,
        search_results: Optional[List[Dict[str, Any]]] = None,
        code_shots: Optional[List[Dict[str, Any]]] = None,
        plan: Optional[Dict[str, Any]] = None,
        test_result: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Generate a YouTrack workflow script based on the user's request.

        Args:
            prompt: The user's prompt describing the desired workflow script.
            search_results: Optional search results from the SearchCodeTool.
            code_shots: Optional code shots from the RetrieveCodeShotsTool.
            plan: Optional plan from the PlanStrategy.
            test_result: Optional test result from a previous generation attempt.

        Returns:
            The generated workflow script as a string.
        """
        # Create a detailed prompt for the LLM
        detailed_prompt = self._create_detailed_prompt(
            prompt=prompt,
            search_results=search_results,
            code_shots=code_shots,
            plan=plan,
            test_result=test_result
        )

        # Generate the code
        result = self.llm_tool.generate_code(detailed_prompt)

        if result.get("success", False):
            return result.get("code", "")
        else:
            # If generation failed, return a default script with an error message
            error = result.get("error", "Unknown error")

            # Create a basic workflow script that can be used as a starting point
            default_script = f"""
// Error generating code: {error}
// This is a basic template that you can modify to meet your requirements.

const entities = require('@jetbrains/youtrack-scripting-api/entities');

exports.rule = entities.Issue.onChange({{
  title: 'Basic Workflow Script',
  guard: (ctx) => {{
    const issue = ctx.issue;
    // TODO: Add your guard conditions here
    return true;
  }},
  action: (ctx) => {{
    const issue = ctx.issue;
    // TODO: Add your action logic here

    // Example: Log a message
    console.log('Workflow script executed for issue: ' + issue.id);
  }},
  requirements: {{
    // TODO: Add your requirements here
  }}
}});
"""

            return default_script

    def _create_detailed_prompt(
        self,
        prompt: str,
        search_results: Optional[List[Dict[str, Any]]] = None,
        code_shots: Optional[List[Dict[str, Any]]] = None,
        plan: Optional[Dict[str, Any]] = None,
        test_result: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Create a detailed prompt for the LLM.

        Args:
            prompt: The user's prompt describing the desired workflow script.
            search_results: Optional search results from the SearchCodeTool.
            code_shots: Optional code shots from the RetrieveCodeShotsTool.
            plan: Optional plan from the PlanStrategy.
            test_result: Optional test result from a previous generation attempt.

        Returns:
            A detailed prompt for the LLM.
        """
        detailed_prompt = f"Generate a YouTrack workflow script based on the following request:\n\n{prompt}\n\n"

        # Add plan if available
        if plan:
            plan_text = plan.get("plan", "")
            if plan_text:
                detailed_prompt += f"Plan:\n{plan_text}\n\n"

        # Add relevant code shots if available
        if code_shots and len(code_shots) > 0:
            detailed_prompt += "Here are some relevant examples of YouTrack workflow scripts:\n\n"

            # Add up to 3 most relevant code shots
            for i, code_shot in enumerate(code_shots[:3]):
                title = code_shot.get("title", "")
                description = code_shot.get("description", "")
                code = code_shot.get("code", "")

                detailed_prompt += f"Example {i+1}: {title}\n"
                detailed_prompt += f"Description: {description}\n"
                detailed_prompt += f"Code:\n{code}\n\n"

        # Add relevant search results if available
        if search_results and len(search_results) > 0:
            detailed_prompt += "Here are some relevant code snippets from the YouTrack scripting API:\n\n"

            # Add up to 5 most relevant search results
            for i, result in enumerate(search_results[:5]):
                file = result.get("file", "")
                code = result.get("code", "")

                detailed_prompt += f"Snippet {i+1} from {file}:\n{code}\n\n"

        # Add test result if available
        if test_result:
            success = test_result.get("success", False)
            if not success:
                error = test_result.get("error", "")
                stderr = test_result.get("stderr", "")

                detailed_prompt += "The previous code generation had errors:\n"
                if error:
                    detailed_prompt += f"Error: {error}\n"
                if stderr:
                    detailed_prompt += f"Details: {stderr}\n"
                detailed_prompt += "Please fix these issues in the new code.\n\n"

        # Add instructions for the code format
        detailed_prompt += """
Please generate a complete, working YouTrack workflow script that follows these guidelines:
1. Use the standard YouTrack workflow script format with exports.rule
2. Include appropriate guard conditions to ensure the rule only runs when needed
3. Include all necessary requirements
4. Add comments to explain complex logic
5. Handle edge cases appropriately
6. Use the YouTrack scripting API correctly (entities, workflow, etc.)
7. Return only the code without any additional explanations or markdown formatting
"""

        return detailed_prompt
