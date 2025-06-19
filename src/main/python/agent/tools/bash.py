"""
Bash tool for the YouTrack Workflow Generator Agent.

This module provides a BashTool class that allows the agent to execute Bash commands.
"""

import subprocess
from typing import Dict, Any, Optional


class BashTool:
    """
    Tool for executing Bash commands.
    
    This tool allows the agent to execute Bash commands and get their output.
    """
    
    def __init__(self, working_dir: Optional[str] = None):
        """
        Initialize the BashTool.
        
        Args:
            working_dir: Optional working directory for executing commands.
                        If not provided, the current working directory will be used.
        """
        self.working_dir = working_dir
    
    def execute(self, command: str) -> Dict[str, Any]:
        """
        Execute a Bash command.
        
        Args:
            command: The Bash command to execute.
            
        Returns:
            A dictionary containing the command output and status.
        """
        try:
            # Execute the command and capture output
            process = subprocess.Popen(
                command,
                shell=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                cwd=self.working_dir
            )
            
            # Get stdout and stderr
            stdout, stderr = process.communicate()
            
            # Get return code
            return_code = process.returncode
            
            # Return the result
            return {
                "success": return_code == 0,
                "return_code": return_code,
                "stdout": stdout,
                "stderr": stderr,
                "command": command
            }
        except Exception as e:
            # Return error information
            return {
                "success": False,
                "error": str(e),
                "command": command
            }