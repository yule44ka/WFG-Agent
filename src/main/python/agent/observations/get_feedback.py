"""
Get Feedback observation for the YouTrack Workflow Generator Agent.

This module provides a GetFeedbackObservation class that allows the agent to
get feedback from the user.
"""

import sys
from typing import Dict, Any, Optional


class GetFeedbackObservation:
    """
    Observation for getting feedback from the user.
    
    This observation allows the agent to get feedback from the user on
    clarification questions or generated code.
    """
    
    def __init__(self, input_func=None):
        """
        Initialize the GetFeedbackObservation.
        
        Args:
            input_func: Optional function to use for getting input from the user.
                       If not provided, the built-in input function will be used.
        """
        self.input_func = input_func or input
    
    def execute(self, questions: Optional[list] = None) -> Dict[str, Any]:
        """
        Get feedback from the user.
        
        Args:
            questions: Optional list of questions to ask the user.
                     If not provided, a general feedback request will be used.
            
        Returns:
            A dictionary containing the user's feedback.
        """
        feedback = {}
        
        print("\n=== Feedback Request ===")
        
        if questions:
            # Ask specific questions
            print("Please answer the following questions:")
            
            for i, question in enumerate(questions):
                print(f"\n{i+1}. {question}")
                answer = self.input_func("> ")
                feedback[f"question_{i+1}"] = {
                    "question": question,
                    "answer": answer
                }
        else:
            # Ask for general feedback
            print("Please provide feedback on the generated code or any other aspects:")
            feedback_text = self.input_func("> ")
            feedback["general_feedback"] = feedback_text
        
        print("=== Thank you for your feedback ===\n")
        
        return {
            "type": "user_feedback",
            "feedback": feedback,
            "timestamp": None  # In a real implementation, this would be a timestamp
        }
    
    def get_clarification(self, questions: list) -> Dict[str, Any]:
        """
        Get clarification from the user on specific questions.
        
        Args:
            questions: List of questions to ask the user.
            
        Returns:
            A dictionary containing the user's clarification responses.
        """
        print("\n=== Clarification Request ===")
        print("I need some clarification to better understand your requirements:")
        
        responses = {}
        
        for i, question in enumerate(questions):
            print(f"\n{i+1}. {question}")
            answer = self.input_func("> ")
            responses[f"question_{i+1}"] = {
                "question": question,
                "answer": answer
            }
        
        print("=== Thank you for your clarification ===\n")
        
        return {
            "type": "clarification",
            "responses": responses,
            "timestamp": None  # In a real implementation, this would be a timestamp
        }
    
    def get_code_feedback(self, code: str) -> Dict[str, Any]:
        """
        Get feedback from the user on generated code.
        
        Args:
            code: The generated code to get feedback on.
            
        Returns:
            A dictionary containing the user's feedback on the code.
        """
        print("\n=== Code Feedback Request ===")
        print("I've generated the following code based on your requirements:")
        print("\n```javascript")
        print(code)
        print("```\n")
        
        print("Is this code satisfactory? (yes/no)")
        satisfaction = self.input_func("> ").lower()
        
        feedback = {
            "satisfaction": satisfaction.startswith("y")
        }
        
        if not feedback["satisfaction"]:
            print("\nWhat changes would you like to see in the code?")
            changes = self.input_func("> ")
            feedback["requested_changes"] = changes
        
        print("=== Thank you for your feedback ===\n")
        
        return {
            "type": "code_feedback",
            "feedback": feedback,
            "timestamp": None  # In a real implementation, this would be a timestamp
        }