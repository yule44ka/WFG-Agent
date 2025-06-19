"""
Memory module for the YouTrack Workflow Generator Agent.

This module provides a Memory class that stores information about the user's
requests, preferences, and previous interactions. This allows the agent to
provide more personalized responses and improve its performance over time.
"""

from typing import Dict, List, Any, Optional
import json
import os
from datetime import datetime


class Memory:
    """
    Memory class for storing information about user interactions.
    
    This class provides methods for storing and retrieving information about
    the user's requests, preferences, and previous interactions.
    """
    
    def __init__(self, storage_path: Optional[str] = None):
        """
        Initialize the Memory.
        
        Args:
            storage_path: Optional path to the storage directory. If not provided,
                         a default path will be used.
        """
        self.storage_path = storage_path or os.path.join(os.path.expanduser("~"), ".youtrack_workflow_agent")
        os.makedirs(self.storage_path, exist_ok=True)
        
        # Initialize memory storage
        self.current_session = {
            "id": datetime.now().strftime("%Y%m%d%H%M%S"),
            "timestamp": datetime.now().isoformat(),
            "user_prompt": None,
            "cot_result": None,
            "clarification_questions": None,
            "user_feedback": None,
            "updated_prompt": None,
            "plan": None,
            "search_results": None,
            "code_shots": None,
            "generated_code": None,
            "test_result": None,
            "regenerated_code": None,
            "final_test_result": None
        }
        
        # Load previous sessions
        self.previous_sessions = self._load_previous_sessions()
    
    def add_user_prompt(self, prompt: str) -> None:
        """
        Add a user prompt to the memory.
        
        Args:
            prompt: The user's prompt.
        """
        self.current_session["user_prompt"] = prompt
        self._save_current_session()
    
    def add_cot_result(self, cot_result: Dict[str, Any]) -> None:
        """
        Add a chain-of-thought result to the memory.
        
        Args:
            cot_result: The result of the chain-of-thought reasoning.
        """
        self.current_session["cot_result"] = cot_result
        self._save_current_session()
    
    def add_clarification_questions(self, questions: List[str]) -> None:
        """
        Add clarification questions to the memory.
        
        Args:
            questions: The clarification questions.
        """
        self.current_session["clarification_questions"] = questions
        self._save_current_session()
    
    def add_user_feedback(self, feedback: Dict[str, Any]) -> None:
        """
        Add user feedback to the memory.
        
        Args:
            feedback: The user's feedback.
        """
        self.current_session["user_feedback"] = feedback
        self._save_current_session()
    
    def add_updated_prompt(self, prompt: str) -> None:
        """
        Add an updated prompt to the memory.
        
        Args:
            prompt: The updated prompt.
        """
        self.current_session["updated_prompt"] = prompt
        self._save_current_session()
    
    def add_plan(self, plan: Dict[str, Any]) -> None:
        """
        Add a plan to the memory.
        
        Args:
            plan: The plan for generating the workflow script.
        """
        self.current_session["plan"] = plan
        self._save_current_session()
    
    def add_search_results(self, results: List[Dict[str, Any]]) -> None:
        """
        Add search results to the memory.
        
        Args:
            results: The search results.
        """
        self.current_session["search_results"] = results
        self._save_current_session()
    
    def add_code_shots(self, code_shots: List[Dict[str, Any]]) -> None:
        """
        Add code shots to the memory.
        
        Args:
            code_shots: The code shots.
        """
        self.current_session["code_shots"] = code_shots
        self._save_current_session()
    
    def add_generated_code(self, code: str, is_regenerated: bool = False) -> None:
        """
        Add generated code to the memory.
        
        Args:
            code: The generated code.
            is_regenerated: Whether the code is regenerated after a failed test.
        """
        if is_regenerated:
            self.current_session["regenerated_code"] = code
        else:
            self.current_session["generated_code"] = code
        self._save_current_session()
    
    def add_test_result(self, result: Dict[str, Any]) -> None:
        """
        Add a test result to the memory.
        
        Args:
            result: The test result.
        """
        if self.current_session["test_result"] is None:
            self.current_session["test_result"] = result
        else:
            self.current_session["final_test_result"] = result
        self._save_current_session()
    
    def get_similar_prompts(self, prompt: str, limit: int = 5) -> List[Dict[str, Any]]:
        """
        Get similar prompts from previous sessions.
        
        Args:
            prompt: The current prompt.
            limit: The maximum number of similar prompts to return.
            
        Returns:
            A list of similar prompts from previous sessions.
        """
        # This is a simple implementation; in a real application, this would use
        # more sophisticated similarity measures
        similar_prompts = []
        
        for session in self.previous_sessions:
            session_prompt = session.get("user_prompt")
            if session_prompt and any(word in session_prompt.lower() for word in prompt.lower().split()):
                similar_prompts.append({
                    "prompt": session_prompt,
                    "generated_code": session.get("generated_code") or session.get("regenerated_code"),
                    "timestamp": session.get("timestamp")
                })
                
                if len(similar_prompts) >= limit:
                    break
        
        return similar_prompts
    
    def _save_current_session(self) -> None:
        """
        Save the current session to disk.
        """
        session_file = os.path.join(self.storage_path, f"session_{self.current_session['id']}.json")
        with open(session_file, "w") as f:
            json.dump(self.current_session, f, indent=2)
    
    def _load_previous_sessions(self) -> List[Dict[str, Any]]:
        """
        Load previous sessions from disk.
        
        Returns:
            A list of previous sessions.
        """
        sessions = []
        
        if os.path.exists(self.storage_path):
            for filename in os.listdir(self.storage_path):
                if filename.startswith("session_") and filename.endswith(".json"):
                    try:
                        with open(os.path.join(self.storage_path, filename), "r") as f:
                            session = json.load(f)
                            sessions.append(session)
                    except (json.JSONDecodeError, IOError):
                        # Skip invalid files
                        pass
        
        # Sort sessions by timestamp (newest first)
        sessions.sort(key=lambda s: s.get("timestamp", ""), reverse=True)
        
        return sessions