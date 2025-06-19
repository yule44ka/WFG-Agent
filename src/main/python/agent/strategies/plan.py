"""
Plan strategy for the YouTrack Workflow Generator Agent.

This module provides a PlanStrategy class that allows the agent to create
a plan based on the user's prompt.
"""

from typing import Dict, List, Any, Optional

from ..tools.llm_request import LLMRequestTool


class PlanStrategy:
    """
    Strategy for creating a plan.
    
    This strategy allows the agent to create a detailed plan for generating
    a YouTrack workflow script based on the user's prompt.
    """
    
    def __init__(self, llm_tool: LLMRequestTool):
        """
        Initialize the PlanStrategy.
        
        Args:
            llm_tool: The LLMRequestTool to use for generating the plan.
        """
        self.llm_tool = llm_tool
    
    def execute(self, prompt: str, cot_result: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Execute the plan strategy on the given prompt.
        
        Args:
            prompt: The user's prompt describing the desired workflow script.
            cot_result: Optional result from the chain-of-thought strategy.
            
        Returns:
            A dictionary containing the plan.
        """
        # Create a prompt for generating the plan
        plan_prompt = self._create_plan_prompt(prompt, cot_result)
        
        # Generate the plan
        result = self.llm_tool.execute(plan_prompt)
        
        if result.get("success", False):
            # Parse the plan
            content = result.get("content", "")
            parsed_result = self._parse_plan_result(content)
            
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
                "plan": "",
                "components": [],
                "requirements": []
            }
    
    def _create_plan_prompt(self, prompt: str, cot_result: Optional[Dict[str, Any]] = None) -> str:
        """
        Create a prompt for generating the plan.
        
        Args:
            prompt: The user's prompt describing the desired workflow script.
            cot_result: Optional result from the chain-of-thought strategy.
            
        Returns:
            A prompt for generating the plan.
        """
        plan_prompt = f"""
I need to create a detailed plan for generating a YouTrack workflow script based on the following request:

"{prompt}"
"""
        
        # Add chain-of-thought reasoning if available
        if cot_result:
            reasoning = cot_result.get("reasoning", "")
            steps = cot_result.get("steps", [])
            
            if reasoning:
                plan_prompt += f"\n\nHere's my reasoning about the request:\n{reasoning}\n"
            
            if steps:
                plan_prompt += "\n\nI've broken down the problem into these steps:\n"
                for i, step in enumerate(steps):
                    plan_prompt += f"{i+1}. {step}\n"
        
        plan_prompt += """
Now, I need a detailed plan for implementing this workflow script. Please help me create a plan that includes:

1. The overall approach to implementing the workflow script
2. The key components that need to be implemented (guard conditions, actions, etc.)
3. The specific requirements (fields, users, etc.) that need to be defined
4. Any edge cases or special considerations to handle

Format your response with these sections:
- Plan: A detailed, step-by-step plan for implementing the workflow script
- Components: A list of the key components that need to be implemented
- Requirements: A list of the specific requirements that need to be defined
"""
        
        return plan_prompt
    
    def _parse_plan_result(self, content: str) -> Dict[str, Any]:
        """
        Parse the plan result.
        
        Args:
            content: The content from the LLM response.
            
        Returns:
            A dictionary containing the parsed plan.
        """
        # Initialize the result
        result = {
            "success": True,
            "plan": "",
            "components": [],
            "requirements": []
        }
        
        # Process each section
        current_section = None
        
        for line in content.split("\n"):
            line = line.strip()
            
            # Skip empty lines
            if not line:
                continue
            
            # Check for section headers
            if line.lower().startswith("plan:"):
                current_section = "plan"
                result["plan"] = line[len("plan:"):].strip()
            elif line.lower().startswith("components:"):
                current_section = "components"
            elif line.lower().startswith("requirements:"):
                current_section = "requirements"
            elif current_section:
                # Process content based on the current section
                if current_section == "plan":
                    result["plan"] += "\n" + line
                elif current_section == "components" and line.strip():
                    # Check if the line starts with a bullet point or number
                    if line.startswith(("- ", "* ", "• ")):
                        component = line[2:].strip()
                        result["components"].append(component)
                    elif line[0].isdigit() and ". " in line:
                        component = line.split(". ", 1)[1]
                        result["components"].append(component)
                    else:
                        # Add as a new component
                        result["components"].append(line)
                elif current_section == "requirements" and line.strip():
                    # Check if the line starts with a bullet point or number
                    if line.startswith(("- ", "* ", "• ")):
                        requirement = line[2:].strip()
                        result["requirements"].append(requirement)
                    elif line[0].isdigit() and ". " in line:
                        requirement = line.split(". ", 1)[1]
                        result["requirements"].append(requirement)
                    else:
                        # Add as a new requirement
                        result["requirements"].append(line)
        
        return result