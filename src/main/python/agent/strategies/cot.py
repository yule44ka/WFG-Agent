"""
Chain of Thought strategy for the YouTrack Workflow Generator Agent.

This module provides a ChainOfThoughtStrategy class that allows the agent to
use chain-of-thought reasoning to break down the problem into smaller steps.
"""

from typing import Dict, List, Any, Optional

from ..tools.llm_request import LLMRequestTool


class ChainOfThoughtStrategy:
    """
    Strategy for using chain-of-thought reasoning.
    
    This strategy allows the agent to break down the problem into smaller steps
    and reason through the solution process.
    """
    
    def __init__(self, llm_tool: LLMRequestTool):
        """
        Initialize the ChainOfThoughtStrategy.
        
        Args:
            llm_tool: The LLMRequestTool to use for generating chain-of-thought reasoning.
        """
        self.llm_tool = llm_tool
    
    def execute(self, prompt: str) -> Dict[str, Any]:
        """
        Execute the chain-of-thought strategy on the given prompt.
        
        Args:
            prompt: The user's prompt describing the desired workflow script.
            
        Returns:
            A dictionary containing the chain-of-thought reasoning results.
        """
        # Create a prompt for chain-of-thought reasoning
        cot_prompt = self._create_cot_prompt(prompt)
        
        # Generate chain-of-thought reasoning
        result = self.llm_tool.execute(cot_prompt)
        
        if result.get("success", False):
            # Parse the chain-of-thought reasoning
            content = result.get("content", "")
            parsed_result = self._parse_cot_result(content)
            
            # Add the original prompt and raw content
            parsed_result["prompt"] = prompt
            parsed_result["raw_content"] = content
            
            return parsed_result
        else:
            # If generation failed, return a basic result
            return {
                "success": False,
                "error": result.get("error", "Unknown error"),
                "prompt": prompt,
                "reasoning": "",
                "steps": [],
                "missing_information": [],
                "ambiguities": [],
                "needs_clarification": True
            }
    
    def _create_cot_prompt(self, prompt: str) -> str:
        """
        Create a prompt for chain-of-thought reasoning.
        
        Args:
            prompt: The user's prompt describing the desired workflow script.
            
        Returns:
            A prompt for generating chain-of-thought reasoning.
        """
        return f"""
I need to generate a YouTrack workflow script based on the following request:

"{prompt}"

Before I start coding, I want to think through this problem step by step to make sure I understand the requirements and identify any missing information or ambiguities.

Please help me with a chain-of-thought reasoning process by:

1. Analyzing what the user is asking for in terms of a YouTrack workflow script
2. Breaking down the problem into smaller steps
3. Identifying the key components needed (fields, conditions, actions, etc.)
4. Noting any missing information that I would need to ask the user about
5. Highlighting any ambiguities in the request
6. Determining if clarification is needed before proceeding

Format your response with these sections:
- Reasoning: Your step-by-step analysis of the request
- Steps: A numbered list of steps to implement the workflow script
- Missing Information: A list of any information that's missing from the request
- Ambiguities: A list of any ambiguous aspects of the request
- Needs Clarification: Yes/No - whether I should ask for clarification before proceeding
"""
    
    def _parse_cot_result(self, content: str) -> Dict[str, Any]:
        """
        Parse the chain-of-thought reasoning result.
        
        Args:
            content: The content from the LLM response.
            
        Returns:
            A dictionary containing the parsed chain-of-thought reasoning.
        """
        # Initialize the result
        result = {
            "success": True,
            "reasoning": "",
            "steps": [],
            "missing_information": [],
            "ambiguities": [],
            "needs_clarification": False
        }
        
        # Split the content by sections
        sections = content.split("\n-")
        
        # Process each section
        current_section = None
        
        for line in content.split("\n"):
            line = line.strip()
            
            # Skip empty lines
            if not line:
                continue
            
            # Check for section headers
            if line.lower().startswith("reasoning:"):
                current_section = "reasoning"
                result["reasoning"] = line[len("reasoning:"):].strip()
            elif line.lower().startswith("steps:"):
                current_section = "steps"
            elif line.lower().startswith("missing information:"):
                current_section = "missing_information"
            elif line.lower().startswith("ambiguities:"):
                current_section = "ambiguities"
            elif line.lower().startswith("needs clarification:"):
                value = line[len("needs clarification:"):].strip().lower()
                result["needs_clarification"] = value.startswith("yes")
                current_section = None
            elif current_section:
                # Process content based on the current section
                if current_section == "reasoning":
                    result["reasoning"] += "\n" + line
                elif current_section == "steps" and line.strip():
                    # Check if the line starts with a number
                    if line[0].isdigit() and ". " in line:
                        step = line.split(". ", 1)[1]
                        result["steps"].append(step)
                    else:
                        # Append to the last step if it exists
                        if result["steps"]:
                            result["steps"][-1] += " " + line
                elif current_section == "missing_information" and line.strip():
                    # Check if the line starts with a bullet point or number
                    if line.startswith(("- ", "* ", "• ")):
                        item = line[2:].strip()
                        result["missing_information"].append(item)
                    elif line[0].isdigit() and ". " in line:
                        item = line.split(". ", 1)[1]
                        result["missing_information"].append(item)
                    else:
                        # Add as a new item
                        result["missing_information"].append(line)
                elif current_section == "ambiguities" and line.strip():
                    # Check if the line starts with a bullet point or number
                    if line.startswith(("- ", "* ", "• ")):
                        item = line[2:].strip()
                        result["ambiguities"].append(item)
                    elif line[0].isdigit() and ". " in line:
                        item = line.split(". ", 1)[1]
                        result["ambiguities"].append(item)
                    else:
                        # Add as a new item
                        result["ambiguities"].append(line)
        
        # If there are missing information or ambiguities, set needs_clarification to True
        if result["missing_information"] or result["ambiguities"]:
            result["needs_clarification"] = True
        
        return result