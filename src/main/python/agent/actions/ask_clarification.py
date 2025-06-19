"""
Ask Clarification action for the YouTrack Workflow Generator Agent.

This module provides an AskClarificationAction class that allows the agent to
ask the user for clarification on their request.
"""

from typing import Dict, List, Any, Optional

from ..tools.llm_request import LLMRequestTool


class AskClarificationAction:
    """
    Action for asking clarification questions.
    
    This action allows the agent to generate clarification questions based on
    the chain-of-thought reasoning results.
    """
    
    def __init__(self, llm_tool: LLMRequestTool):
        """
        Initialize the AskClarificationAction.
        
        Args:
            llm_tool: The LLMRequestTool to use for generating clarification questions.
        """
        self.llm_tool = llm_tool
    
    def execute(self, cot_result: Dict[str, Any]) -> List[str]:
        """
        Generate clarification questions based on the chain-of-thought reasoning results.
        
        Args:
            cot_result: The result of the chain-of-thought reasoning.
            
        Returns:
            A list of clarification questions.
        """
        # Extract information from the chain-of-thought result
        reasoning = cot_result.get("reasoning", "")
        missing_info = cot_result.get("missing_information", [])
        ambiguities = cot_result.get("ambiguities", [])
        
        # Create a prompt for generating clarification questions
        prompt = self._create_clarification_prompt(reasoning, missing_info, ambiguities)
        
        # Generate clarification questions
        result = self.llm_tool.execute(prompt)
        
        if result.get("success", False):
            # Parse the questions from the content
            content = result.get("content", "")
            questions = self._parse_questions(content)
            return questions
        else:
            # If generation failed, return a default question
            return ["Could you please provide more details about your requirements?"]
    
    def _create_clarification_prompt(
        self,
        reasoning: str,
        missing_info: List[str],
        ambiguities: List[str]
    ) -> str:
        """
        Create a prompt for generating clarification questions.
        
        Args:
            reasoning: The reasoning from the chain-of-thought result.
            missing_info: A list of missing information items.
            ambiguities: A list of ambiguities.
            
        Returns:
            A prompt for generating clarification questions.
        """
        prompt = "Based on the user's request for a YouTrack workflow script, I need to ask some clarification questions.\n\n"
        
        if reasoning:
            prompt += f"Here's my reasoning about the request:\n{reasoning}\n\n"
        
        if missing_info:
            prompt += "Missing information:\n"
            for item in missing_info:
                prompt += f"- {item}\n"
            prompt += "\n"
        
        if ambiguities:
            prompt += "Ambiguities:\n"
            for item in ambiguities:
                prompt += f"- {item}\n"
            prompt += "\n"
        
        prompt += """
Please generate 2-5 clear, concise clarification questions that would help me understand the user's requirements better.
The questions should:
1. Address the missing information and ambiguities
2. Be specific to YouTrack workflow scripts
3. Help gather details about fields, conditions, actions, and requirements
4. Be numbered (1., 2., etc.)
"""
        
        return prompt
    
    def _parse_questions(self, content: str) -> List[str]:
        """
        Parse questions from the LLM response content.
        
        Args:
            content: The content from the LLM response.
            
        Returns:
            A list of questions.
        """
        # Split the content by lines
        lines = content.strip().split("\n")
        
        # Extract lines that look like questions (numbered or with question marks)
        questions = []
        
        for line in lines:
            line = line.strip()
            
            # Skip empty lines
            if not line:
                continue
            
            # Check if the line is a numbered question
            if line.startswith(("1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.")):
                # Remove the number and trim
                question = line[2:].strip()
                questions.append(question)
            # Check if the line ends with a question mark
            elif line.endswith("?"):
                questions.append(line)
        
        # If no questions were found, return the whole content as one question
        if not questions and content.strip():
            questions = [content.strip()]
        
        return questions