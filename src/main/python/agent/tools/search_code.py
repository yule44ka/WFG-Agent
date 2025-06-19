"""
Search Code tool for the YouTrack Workflow Generator Agent.

This module provides a SearchCodeTool class that allows the agent to search
for code snippets in the YouTrack scripting API.
"""

import os
import re
import json
from typing import Dict, List, Any, Optional


class SearchCodeTool:
    """
    Tool for searching code in the YouTrack scripting API.
    
    This tool allows the agent to search for code snippets, examples, and documentation
    in the YouTrack scripting API files.
    """
    
    def __init__(self, api_dir: Optional[str] = None):
        """
        Initialize the SearchCodeTool.
        
        Args:
            api_dir: Optional path to the YouTrack scripting API directory.
                    If not provided, it will look for the directory in the default location.
        """
        # Set the API directory
        if api_dir:
            self.api_dir = api_dir
        else:
            # Try to find the API directory in the default location
            current_dir = os.path.dirname(os.path.abspath(__file__))
            project_root = os.path.abspath(os.path.join(current_dir, "../../../.."))
            self.api_dir = os.path.join(project_root, "packages/youtrack-scripting-api/package")
        
        # Check if the API directory exists
        if not os.path.exists(self.api_dir):
            raise ValueError(f"YouTrack scripting API directory not found at {self.api_dir}")
        
        # Initialize the cache for file contents
        self.file_cache = {}
    
    def execute(self, query: str) -> List[Dict[str, Any]]:
        """
        Search for code snippets in the YouTrack scripting API.
        
        Args:
            query: The search query.
            
        Returns:
            A list of search results, each containing file path, line number, and code snippet.
        """
        # Normalize the query
        query = query.lower()
        
        # Get all JavaScript files in the API directory
        js_files = self._get_js_files()
        
        # Search for the query in each file
        results = []
        for file_path in js_files:
            file_results = self._search_file(file_path, query)
            results.extend(file_results)
        
        # Sort results by relevance (number of query terms matched)
        results.sort(key=lambda r: r.get("relevance", 0), reverse=True)
        
        return results
    
    def _get_js_files(self) -> List[str]:
        """
        Get all JavaScript files in the API directory.
        
        Returns:
            A list of file paths.
        """
        js_files = []
        
        for root, _, files in os.walk(self.api_dir):
            for file in files:
                if file.endswith(".js"):
                    js_files.append(os.path.join(root, file))
        
        return js_files
    
    def _search_file(self, file_path: str, query: str) -> List[Dict[str, Any]]:
        """
        Search for the query in a file.
        
        Args:
            file_path: The path to the file.
            query: The search query.
            
        Returns:
            A list of search results for the file.
        """
        # Get the file content from cache or read it
        if file_path in self.file_cache:
            content = self.file_cache[file_path]
        else:
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.file_cache[file_path] = content
        
        # Split the content into lines
        lines = content.split("\n")
        
        # Split the query into terms
        query_terms = query.lower().split()
        
        # Search for the query terms in the file
        results = []
        
        # Track the current code block
        current_block = []
        current_block_start = 0
        in_comment_block = False
        
        for i, line in enumerate(lines):
            line_lower = line.lower()
            
            # Check if this line starts or ends a comment block
            if "/**" in line:
                in_comment_block = True
                current_block = [line]
                current_block_start = i
            elif "*/" in line and in_comment_block:
                in_comment_block = False
                current_block.append(line)
                
                # Check if the comment block contains any query terms
                block_text = "\n".join(current_block).lower()
                relevance = sum(1 for term in query_terms if term in block_text)
                
                if relevance > 0:
                    # Add the comment block and the following code
                    code_block = current_block.copy()
                    
                    # Add the next few lines (likely the code that follows the comment)
                    for j in range(i + 1, min(i + 11, len(lines))):
                        code_block.append(lines[j])
                    
                    results.append({
                        "file": os.path.basename(file_path),
                        "file_path": file_path,
                        "line": current_block_start + 1,
                        "code": "\n".join(code_block),
                        "relevance": relevance,
                        "type": "comment_with_code"
                    })
                
                current_block = []
            elif in_comment_block:
                current_block.append(line)
            
            # Check if the line contains any query terms
            relevance = sum(1 for term in query_terms if term in line_lower)
            
            if relevance > 0 and not in_comment_block:
                # Get the context (a few lines before and after)
                start = max(0, i - 5)
                end = min(len(lines), i + 6)
                context = lines[start:end]
                
                results.append({
                    "file": os.path.basename(file_path),
                    "file_path": file_path,
                    "line": i + 1,
                    "code": "\n".join(context),
                    "relevance": relevance,
                    "type": "code"
                })
        
        return results
    
    def search_entity(self, entity_name: str) -> Dict[str, Any]:
        """
        Search for a specific entity in the YouTrack scripting API.
        
        Args:
            entity_name: The name of the entity to search for.
            
        Returns:
            Information about the entity, including its documentation and examples.
        """
        # Search for the entity
        results = self.execute(entity_name)
        
        # Filter results to find the entity definition
        entity_info = {
            "name": entity_name,
            "documentation": None,
            "examples": [],
            "methods": [],
            "properties": []
        }
        
        for result in results:
            code = result.get("code", "")
            
            # Check if this is the entity definition
            if f"class {entity_name}" in code or f"function {entity_name}" in code or f"const {entity_name}" in code:
                entity_info["documentation"] = code
            
            # Check if this is an example
            if "example" in code.lower() and entity_name.lower() in code.lower():
                entity_info["examples"].append(code)
            
            # Check if this is a method or property
            if f"{entity_name}." in code or f"{entity_name.lower()}." in code:
                # Try to extract the method or property name
                match = re.search(rf"{entity_name}\.(\w+)", code)
                if match:
                    method_name = match.group(1)
                    
                    # Check if it's a method or property
                    if f"{entity_name}.{method_name}(" in code or f"{entity_name}.{method_name} =" in code:
                        # It's a method
                        entity_info["methods"].append({
                            "name": method_name,
                            "code": code
                        })
                    else:
                        # It's a property
                        entity_info["properties"].append({
                            "name": method_name,
                            "code": code
                        })
        
        return entity_info