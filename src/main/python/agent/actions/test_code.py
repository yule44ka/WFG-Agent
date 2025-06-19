"""
Test Code action for the YouTrack Workflow Generator Agent.

This module provides a TestCodeAction class that allows the agent to test
the generated code to ensure it works as expected.
"""

import os
import tempfile
import re
from typing import Dict, Any, Optional

from ..tools.bash import BashTool


class TestCodeAction:
    """
    Action for testing generated code.
    
    This action allows the agent to test the generated code to ensure it works as expected.
    Since we can't actually run the YouTrack workflow scripts directly, this action
    implements a simple syntax checker and validator for the generated code.
    """
    
    def __init__(self, bash_tool: BashTool):
        """
        Initialize the TestCodeAction.
        
        Args:
            bash_tool: The BashTool to use for executing commands.
        """
        self.bash_tool = bash_tool
    
    def execute(self, code: str) -> Dict[str, Any]:
        """
        Test the generated code.
        
        Args:
            code: The generated code to test.
            
        Returns:
            A dictionary containing the test results.
        """
        # Create a temporary file with the code
        with tempfile.NamedTemporaryFile(suffix=".js", delete=False) as temp_file:
            temp_file_path = temp_file.name
            temp_file.write(code.encode("utf-8"))
        
        try:
            # Run syntax check using Node.js
            syntax_result = self._check_syntax(temp_file_path)
            
            # If syntax check passed, run validation
            if syntax_result.get("success", False):
                validation_result = self._validate_code(code)
                
                # Combine results
                return {
                    "success": validation_result.get("success", False),
                    "syntax_check": syntax_result,
                    "validation": validation_result
                }
            else:
                # Return syntax check result
                return {
                    "success": False,
                    "syntax_check": syntax_result,
                    "validation": {"success": False, "message": "Syntax check failed, skipping validation"}
                }
        finally:
            # Clean up the temporary file
            os.unlink(temp_file_path)
    
    def _check_syntax(self, file_path: str) -> Dict[str, Any]:
        """
        Check the syntax of the generated code using Node.js.
        
        Args:
            file_path: The path to the file containing the code.
            
        Returns:
            A dictionary containing the syntax check results.
        """
        # Run Node.js to check syntax
        result = self.bash_tool.execute(f"node --check {file_path}")
        
        # If the command succeeded, the syntax is valid
        if result.get("success", False):
            return {
                "success": True,
                "message": "Syntax check passed"
            }
        else:
            # Extract error message
            stderr = result.get("stderr", "")
            
            return {
                "success": False,
                "message": "Syntax check failed",
                "error": stderr
            }
    
    def _validate_code(self, code: str) -> Dict[str, Any]:
        """
        Validate the generated code for common issues.
        
        Args:
            code: The generated code to validate.
            
        Returns:
            A dictionary containing the validation results.
        """
        issues = []
        
        # Check if the code contains the required imports
        if "require('@jetbrains/youtrack-scripting-api/entities')" not in code:
            issues.append("Missing required import: entities")
        
        # Check if the code exports a rule
        if "exports.rule" not in code:
            issues.append("Missing exports.rule")
        
        # Check if the code contains a title
        if "title:" not in code:
            issues.append("Missing rule title")
        
        # Check if the code contains a guard function
        if "guard:" not in code:
            issues.append("Missing guard function")
        
        # Check if the code contains an action function
        if "action:" not in code:
            issues.append("Missing action function")
        
        # Check for common syntax errors
        if re.search(r"ctx\.issue\s*\.\s*fields\s*\.\s*\w+\s*=", code) and "requirements:" not in code:
            issues.append("Setting field values but missing requirements section")
        
        # Check for potential infinite loops
        if "while" in code and "break" not in code:
            issues.append("Potential infinite loop: while loop without break statement")
        
        # Check for proper error handling
        if "try" in code and "catch" not in code:
            issues.append("Incomplete error handling: try without catch")
        
        # Return validation result
        if issues:
            return {
                "success": False,
                "message": "Validation failed",
                "issues": issues
            }
        else:
            return {
                "success": True,
                "message": "Validation passed"
            }