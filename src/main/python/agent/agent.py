"""
YouTrack Workflow Generator Agent

This module implements an agent that generates YouTrack workflow scripts based on user prompts.
The agent uses a combination of tools, actions, observations, and strategies to understand the
user's requirements and generate appropriate workflow scripts.
"""

import os
import json
from typing import Dict, List, Optional, Any

# Import tools
from .tools.bash import BashTool
from .tools.llm_request import LLMRequestTool
from .tools.search_code import SearchCodeTool
from .tools.retrieve_code_shots import RetrieveCodeShotsTool
from .tools.generate_code import GenerateCodeTool

# Import actions
from .actions.ask_clarification import AskClarificationAction
from .actions.test_code import TestCodeAction

# Import observations
from .observations.get_feedback import GetFeedbackObservation
from .observations.get_test_results import GetTestResultsObservation

# Import strategies
from .strategies.cot import ChainOfThoughtStrategy
from .strategies.plan import PlanStrategy

# Import memory
from .memory import Memory


class YouTrackWorkflowAgent:
    """
    Agent for generating YouTrack workflow scripts based on user prompts.

    This agent implements the workflow described in agent.md, using various tools,
    actions, observations, and strategies to understand the user's requirements
    and generate appropriate workflow scripts.
    """

    def __init__(self, api_key: Optional[str] = None):
        """
        Initialize the YouTrack Workflow Generator Agent.

        Args:
            api_key: Optional API key for the LLM service. If not provided,
                     it will be read from the OPENAI_API_KEY environment variable.
        """
        # Initialize API key
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY")
        if not self.api_key:
            raise ValueError("API key must be provided or set as OPENAI_API_KEY environment variable")

        # Initialize memory
        self.memory = Memory()

        # Initialize tools
        self.bash_tool = BashTool()
        self.llm_request_tool = LLMRequestTool(api_key=self.api_key)
        self.search_code_tool = SearchCodeTool()
        self.retrieve_code_shots_tool = RetrieveCodeShotsTool()
        self.generate_code_tool = GenerateCodeTool(api_key=self.api_key)

        # Initialize actions
        self.ask_clarification_action = AskClarificationAction(llm_tool=self.llm_request_tool)
        self.test_code_action = TestCodeAction(bash_tool=self.bash_tool)

        # Initialize observations
        self.get_feedback_observation = GetFeedbackObservation()
        self.get_test_results_observation = GetTestResultsObservation()

        # Initialize strategies
        self.cot_strategy = ChainOfThoughtStrategy(llm_tool=self.llm_request_tool)
        self.plan_strategy = PlanStrategy(llm_tool=self.llm_request_tool)

    def generate_workflow_script(self, prompt: str) -> str:
        """
        Generate a YouTrack workflow script based on the user's prompt.

        Args:
            prompt: The user's prompt describing the desired workflow script.

        Returns:
            The generated workflow script as a string.
        """
        # Store the prompt in memory
        self.memory.add_user_prompt(prompt)

        # Use chain-of-thought reasoning to understand the prompt
        cot_result = self.cot_strategy.execute(prompt)
        self.memory.add_cot_result(cot_result)

        # Ask clarification questions if needed
        clarification_needed = self._needs_clarification(cot_result)
        if clarification_needed:
            clarification_questions = self.ask_clarification_action.execute(cot_result)
            self.memory.add_clarification_questions(clarification_questions)

            # Ask clarification questions to the user
            user_feedback = self.get_feedback_observation.get_clarification(clarification_questions)
            self.memory.add_user_feedback(user_feedback)

            # Update the prompt with the clarification
            prompt = self._update_prompt_with_clarification(prompt, user_feedback)
            self.memory.add_updated_prompt(prompt)

        # Create a plan for generating the workflow script
        plan = self.plan_strategy.execute(prompt)
        self.memory.add_plan(plan)

        # Search for relevant code in the YouTrack scripting API
        search_results = self.search_code_tool.execute(prompt)
        self.memory.add_search_results(search_results)

        # Retrieve relevant code shots from the database
        code_shots = self.retrieve_code_shots_tool.execute(prompt)
        self.memory.add_code_shots(code_shots)

        # Generate the workflow script
        generated_code = self.generate_code_tool.execute(
            prompt=prompt,
            search_results=search_results,
            code_shots=code_shots,
            plan=plan
        )
        self.memory.add_generated_code(generated_code)

        # Test the generated code
        test_result = self.test_code_action.execute(generated_code)
        self.memory.add_test_result(test_result)

        # If the test failed, regenerate the code
        if not test_result.get("success", False):
            # Regenerate with the test result information
            generated_code = self.generate_code_tool.execute(
                prompt=prompt,
                search_results=search_results,
                code_shots=code_shots,
                plan=plan,
                test_result=test_result
            )
            self.memory.add_generated_code(generated_code, is_regenerated=True)

            # Test again
            test_result = self.test_code_action.execute(generated_code)
            self.memory.add_test_result(test_result)

        return generated_code

    def _needs_clarification(self, cot_result: Dict[str, Any]) -> bool:
        """
        Determine if clarification is needed based on the chain-of-thought result.

        Args:
            cot_result: The result of the chain-of-thought reasoning.

        Returns:
            True if clarification is needed, False otherwise.
        """
        # Always ask clarifying questions before generating code
        return True

    def _update_prompt_with_clarification(self, prompt: str, clarification: Dict[str, Any]) -> str:
        """
        Update the prompt with the clarification provided by the user.

        Args:
            prompt: The original prompt.
            clarification: The clarification provided by the user.

        Returns:
            The updated prompt.
        """
        # Extract the responses from the clarification
        responses = clarification.get("responses", {})

        # Format the clarification responses
        clarification_text = "\n\nClarification:\n"
        has_substantive_response = False

        for key, value in responses.items():
            question = value.get("question", "")
            answer = value.get("answer", "")

            # Check if the answer is substantive (more than just "no", "yes", etc.)
            if len(answer.strip()) > 3 and not answer.lower() in ["yes", "no", "none", "n/a"]:
                has_substantive_response = True

            clarification_text += f"Q: {question}\nA: {answer}\n\n"

        # If there are no substantive responses, add a note to help the LLM
        if not has_substantive_response:
            clarification_text += "Note: The user provided minimal responses to the clarification questions. Please generate a workflow script based on the original prompt and make reasonable assumptions where information is missing.\n\n"

        # Update the prompt with the clarification
        return f"{prompt}{clarification_text}"


if __name__ == "__main__":
    # Example usage
    agent = YouTrackWorkflowAgent()

    # Example prompt
    prompt = """
    Create a workflow script that automatically assigns issues to the team lead
    when they are created with a 'Critical' priority.
    """

    # Generate the workflow script
    workflow_script = agent.generate_workflow_script(prompt)

    # Print the generated script
    print(workflow_script)
