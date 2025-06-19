"""
LLM Request tool for the YouTrack Workflow Generator Agent.

This module provides a LLMRequestTool class that allows the agent to interact
with a large language model (LLM) for generating text or code.
"""

import os
import json
import requests
from typing import Dict, List, Any, Optional


class LLMRequestTool:
    """
    Tool for interacting with a large language model (LLM).

    This tool allows the agent to send requests to an LLM API and get responses.
    It currently supports JetBrains Grazie API.
    """

    def __init__(self, api_key: Optional[str] = None, model: str = "grazie-xl"):
        """
        Initialize the LLMRequestTool.

        Args:
            api_key: Optional API key for the LLM service. If not provided,
                     it will be read from the GRAZIE_API_KEY environment variable.
            model: The model to use for generating responses. Default is "grazie-xl".
        """
        self.api_key = api_key or os.environ.get("GRAZIE_API_KEY")
        if not self.api_key:
            raise ValueError("API key must be provided or set as GRAZIE_API_KEY environment variable")

        self.model = model
        self.api_url = "https://api.grazie.jetbrains.com/v1/chat/completions"

    def execute(self, prompt: str, system_prompt: Optional[str] = None, temperature: float = 0.7) -> Dict[str, Any]:
        """
        Send a request to the LLM API and get a response.

        Args:
            prompt: The prompt to send to the LLM.
            system_prompt: Optional system prompt to set the context for the LLM.
                          If not provided, a default system prompt will be used.
            temperature: The temperature parameter for the LLM. Higher values make the output
                        more random, while lower values make it more deterministic.

        Returns:
            A dictionary containing the LLM response and metadata.
        """
        try:
            # Prepare the messages
            messages = []

            # Add system prompt if provided
            if system_prompt:
                messages.append({"role": "system", "content": system_prompt})
            else:
                # Default system prompt for YouTrack workflow script generation
                messages.append({
                    "role": "system",
                    "content": (
                        "You are an expert in YouTrack workflow scripting. "
                        "Your task is to help generate high-quality workflow scripts "
                        "for YouTrack based on user requirements. "
                        "Provide detailed, well-structured, and efficient code that follows best practices."
                    )
                })

            # Add user prompt
            messages.append({"role": "user", "content": prompt})

            # Prepare the request payload
            payload = {
                "model": self.model,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": 4000
            }

            # Set up headers
            headers = {
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}"
            }

            # Send the request
            response = requests.post(
                self.api_url,
                headers=headers,
                data=json.dumps(payload)
            )

            # Parse the response
            response_data = response.json()

            # Extract the content from the response
            if "choices" in response_data and len(response_data["choices"]) > 0:
                content = response_data["choices"][0]["message"]["content"]

                # Return the result
                return {
                    "success": True,
                    "content": content,
                    "model": self.model,
                    "prompt": prompt,
                    "system_prompt": system_prompt,
                    "temperature": temperature,
                    "response_data": response_data
                }
            else:
                # Return error information
                return {
                    "success": False,
                    "error": "No content in response",
                    "prompt": prompt,
                    "system_prompt": system_prompt,
                    "temperature": temperature,
                    "response_data": response_data
                }
        except Exception as e:
            # Return error information
            return {
                "success": False,
                "error": str(e),
                "prompt": prompt,
                "system_prompt": system_prompt,
                "temperature": temperature
            }

    def generate_code(self, prompt: str, language: str = "javascript") -> Dict[str, Any]:
        """
        Generate code using the LLM.

        Args:
            prompt: The prompt describing the code to generate.
            language: The programming language for the code. Default is "javascript".

        Returns:
            A dictionary containing the generated code and metadata.
        """
        # Create a system prompt for code generation
        system_prompt = (
            f"You are an expert in {language} programming, specializing in YouTrack workflow scripts. "
            f"Your task is to generate high-quality, efficient, and well-documented {language} code "
            f"for YouTrack workflow scripts based on the user's requirements. "
            f"Provide only the code without any additional explanations or markdown formatting."
        )

        # Set a lower temperature for more deterministic code generation
        temperature = 0.2

        # Execute the request
        result = self.execute(prompt, system_prompt, temperature)

        # If successful, extract the code from the content
        if result.get("success", False):
            content = result["content"]

            # Try to extract code blocks if present
            code = content
            if "```" in content:
                # Extract code from markdown code blocks
                code_blocks = []
                lines = content.split("\n")
                in_code_block = False
                current_block = []

                for line in lines:
                    if line.startswith("```"):
                        if in_code_block:
                            # End of code block
                            code_blocks.append("\n".join(current_block))
                            current_block = []
                        in_code_block = not in_code_block
                    elif in_code_block:
                        current_block.append(line)

                if code_blocks:
                    code = "\n\n".join(code_blocks)

            # Update the result with the extracted code
            result["code"] = code

        return result
