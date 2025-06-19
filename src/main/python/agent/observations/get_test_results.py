"""
Get Test Results observation for the YouTrack Workflow Generator Agent.

This module provides a GetTestResultsObservation class that allows the agent to
retrieve the results of code tests to verify correctness.
"""

from typing import Dict, Any, Optional

from ..actions.test_code import TestCodeAction


class GetTestResultsObservation:
    """
    Observation for getting test results.
    
    This observation allows the agent to retrieve the results of code tests
    to verify the correctness of generated workflow scripts.
    """
    
    def __init__(self, test_code_action: Optional[TestCodeAction] = None):
        """
        Initialize the GetTestResultsObservation.
        
        Args:
            test_code_action: Optional TestCodeAction to use for testing code.
                             If not provided, the observation will only process existing results.
        """
        self.test_code_action = test_code_action
    
    def execute(self, code: Optional[str] = None, test_result: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Get test results for the generated code.
        
        Args:
            code: Optional code to test. If provided and test_code_action is available,
                 the code will be tested.
            test_result: Optional existing test result to process.
            
        Returns:
            A dictionary containing the test results and analysis.
        """
        # If code is provided and test_code_action is available, test the code
        if code and self.test_code_action:
            result = self.test_code_action.execute(code)
        elif test_result:
            # Use the provided test result
            result = test_result
        else:
            # No code or test result provided
            return {
                "success": False,
                "message": "No code or test result provided",
                "analysis": "Cannot analyze test results without code or test result"
            }
        
        # Analyze the test result
        analysis = self._analyze_test_result(result)
        
        # Return the result with analysis
        return {
            "success": result.get("success", False),
            "test_result": result,
            "analysis": analysis
        }
    
    def _analyze_test_result(self, result: Dict[str, Any]) -> str:
        """
        Analyze the test result to provide a human-readable summary.
        
        Args:
            result: The test result to analyze.
            
        Returns:
            A human-readable analysis of the test result.
        """
        success = result.get("success", False)
        
        if success:
            return "The code passed all tests and validations."
        
        # Collect issues from the test result
        issues = []
        
        # Check syntax issues
        syntax_check = result.get("syntax_check", {})
        if not syntax_check.get("success", False):
            error = syntax_check.get("error", "Unknown syntax error")
            issues.append(f"Syntax error: {error}")
        
        # Check validation issues
        validation = result.get("validation", {})
        if not validation.get("success", False):
            validation_issues = validation.get("issues", [])
            for issue in validation_issues:
                issues.append(f"Validation issue: {issue}")
        
        # If no specific issues were found but the test failed, add a generic message
        if not issues:
            issues.append("The code failed testing for unknown reasons.")
        
        # Create a summary
        if len(issues) == 1:
            return issues[0]
        else:
            summary = "The code has the following issues:\n"
            for i, issue in enumerate(issues):
                summary += f"{i+1}. {issue}\n"
            return summary
    
    def format_for_display(self, result: Dict[str, Any]) -> str:
        """
        Format the test result for display to the user.
        
        Args:
            result: The test result to format.
            
        Returns:
            A formatted string representation of the test result.
        """
        success = result.get("success", False)
        analysis = result.get("analysis", "")
        
        if success:
            return f"✅ Test passed: {analysis}"
        else:
            return f"❌ Test failed: {analysis}"