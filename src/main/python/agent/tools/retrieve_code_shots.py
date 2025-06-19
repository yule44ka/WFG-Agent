"""
Retrieve Code Shots tool for the YouTrack Workflow Generator Agent.

This module provides a RetrieveCodeShotsTool class that allows the agent to
retrieve code shots from a database of example YouTrack workflow scripts.
"""

import os
import json
import re
from typing import Dict, List, Any, Optional


class RetrieveCodeShotsTool:
    """
    Tool for retrieving code shots from a database.
    
    This tool allows the agent to retrieve example code shots for common
    YouTrack workflow scenarios.
    """
    
    def __init__(self, database_path: Optional[str] = None):
        """
        Initialize the RetrieveCodeShotsTool.
        
        Args:
            database_path: Optional path to the code shots database file.
                          If not provided, a default database will be used.
        """
        self.database_path = database_path
        
        # Initialize the code shots database
        self.code_shots = self._initialize_database()
    
    def execute(self, query: str) -> List[Dict[str, Any]]:
        """
        Retrieve code shots based on the query.
        
        Args:
            query: The search query.
            
        Returns:
            A list of relevant code shots.
        """
        # Normalize the query
        query = query.lower()
        
        # Split the query into terms
        query_terms = query.lower().split()
        
        # Search for relevant code shots
        results = []
        
        for code_shot in self.code_shots:
            # Calculate relevance based on matching terms in title and description
            title = code_shot.get("title", "").lower()
            description = code_shot.get("description", "").lower()
            tags = code_shot.get("tags", [])
            
            # Calculate relevance score
            relevance = 0
            
            # Check title
            for term in query_terms:
                if term in title:
                    relevance += 3  # Higher weight for title matches
            
            # Check description
            for term in query_terms:
                if term in description:
                    relevance += 1
            
            # Check tags
            for tag in tags:
                if tag.lower() in query:
                    relevance += 2  # Higher weight for tag matches
            
            # Add to results if relevant
            if relevance > 0:
                result = code_shot.copy()
                result["relevance"] = relevance
                results.append(result)
        
        # Sort results by relevance
        results.sort(key=lambda r: r.get("relevance", 0), reverse=True)
        
        return results
    
    def _initialize_database(self) -> List[Dict[str, Any]]:
        """
        Initialize the code shots database.
        
        If a database file is provided, load it. Otherwise, use a default set of code shots.
        
        Returns:
            A list of code shots.
        """
        if self.database_path and os.path.exists(self.database_path):
            # Load the database from file
            try:
                with open(self.database_path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                # Fall back to default database
                return self._get_default_database()
        else:
            # Use default database
            return self._get_default_database()
    
    def _get_default_database(self) -> List[Dict[str, Any]]:
        """
        Get the default code shots database.
        
        Returns:
            A list of default code shots.
        """
        return [
            {
                "id": "auto_assign_critical",
                "title": "Auto-assign critical issues to team lead",
                "description": "Automatically assigns issues with Critical priority to the team lead",
                "tags": ["auto-assign", "priority", "critical", "team lead"],
                "code": """
const entities = require('@jetbrains/youtrack-scripting-api/entities');

exports.rule = entities.Issue.onChange({
  title: 'Auto-assign critical issues to team lead',
  guard: (ctx) => {
    const issue = ctx.issue;
    return issue.fields.Priority && 
           issue.fields.Priority.name === 'Critical' && 
           !issue.fields.Assignee;
  },
  action: (ctx) => {
    const issue = ctx.issue;
    issue.fields.Assignee = ctx.TeamLead;
  },
  requirements: {
    TeamLead: {
      type: entities.User,
      name: 'Team Lead'
    },
    Priority: {
      type: entities.EnumField.fieldType,
      name: 'Priority',
      Critical: {}
    }
  }
});
"""
            },
            {
                "id": "auto_set_due_date",
                "title": "Auto-set due date based on priority",
                "description": "Automatically sets a due date based on the issue's priority",
                "tags": ["due date", "priority", "deadline"],
                "code": """
const entities = require('@jetbrains/youtrack-scripting-api/entities');
const workflow = require('@jetbrains/youtrack-scripting-api/workflow');
const dateTime = require('@jetbrains/youtrack-scripting-api/date-time');

exports.rule = entities.Issue.onChange({
  title: 'Auto-set due date based on priority',
  guard: (ctx) => {
    const issue = ctx.issue;
    return issue.fields.Priority && 
           !issue.fields.DueDate && 
           issue.isReported; // Only for newly reported issues
  },
  action: (ctx) => {
    const issue = ctx.issue;
    const priority = issue.fields.Priority.name;
    
    let daysToAdd = 14; // Default: 2 weeks
    
    if (priority === 'Critical') {
      daysToAdd = 2; // 2 days for Critical
    } else if (priority === 'Major') {
      daysToAdd = 5; // 5 days for Major
    } else if (priority === 'Normal') {
      daysToAdd = 10; // 10 days for Normal
    }
    
    const now = new Date();
    issue.fields.DueDate = dateTime.addDays(now, daysToAdd);
  },
  requirements: {
    Priority: {
      type: entities.EnumField.fieldType,
      name: 'Priority'
    },
    DueDate: {
      type: entities.Field.dateType,
      name: 'Due Date'
    }
  }
});
"""
            },
            {
                "id": "notify_on_comment",
                "title": "Notify mentioned users in comments",
                "description": "Sends notifications to users mentioned in comments using @username syntax",
                "tags": ["notification", "comment", "mention", "@mention"],
                "code": """
const entities = require('@jetbrains/youtrack-scripting-api/entities');
const workflow = require('@jetbrains/youtrack-scripting-api/workflow');
const notifications = require('@jetbrains/youtrack-scripting-api/notifications');

exports.rule = entities.Issue.onChange({
  title: 'Notify mentioned users in comments',
  guard: (ctx) => {
    return ctx.issue.comments.added.isNotEmpty();
  },
  action: (ctx) => {
    const issue = ctx.issue;
    const newComments = issue.comments.added;
    
    newComments.forEach(comment => {
      const text = comment.text;
      const mentionRegex = /@(\\w+)/g;
      const mentions = text.match(mentionRegex);
      
      if (mentions) {
        const uniqueMentions = [...new Set(mentions)];
        
        uniqueMentions.forEach(mention => {
          const username = mention.substring(1); // Remove the @ symbol
          
          // Find the user
          const user = ctx.UserMentioned.login === username ? ctx.UserMentioned : null;
          
          if (user) {
            // Send notification
            notifications.notifyUser(user, {
              issue: issue,
              subject: `You were mentioned in a comment on ${issue.id}`,
              body: `${comment.author.fullName} mentioned you in a comment on ${issue.id}:\\n\\n${text}`
            });
          }
        });
      }
    });
  },
  requirements: {
    UserMentioned: {
      type: entities.User
    }
  }
});
"""
            },
            {
                "id": "auto_add_tag",
                "title": "Auto-add tags based on description",
                "description": "Automatically adds tags to issues based on keywords in the description",
                "tags": ["tag", "auto-tag", "keyword", "description"],
                "code": """
const entities = require('@jetbrains/youtrack-scripting-api/entities');

exports.rule = entities.Issue.onChange({
  title: 'Auto-add tags based on description',
  guard: (ctx) => {
    const issue = ctx.issue;
    return issue.description && 
           (issue.isReported || issue.fields.description.isChanged);
  },
  action: (ctx) => {
    const issue = ctx.issue;
    const description = issue.description.toLowerCase();
    
    // Define keyword to tag mappings
    const keywordTagMappings = [
      { keywords: ['ui', 'interface', 'button', 'dialog', 'screen'], tag: ctx.UITag },
      { keywords: ['performance', 'slow', 'speed', 'optimization'], tag: ctx.PerformanceTag },
      { keywords: ['crash', 'exception', 'error', 'bug'], tag: ctx.BugTag },
      { keywords: ['documentation', 'docs', 'help'], tag: ctx.DocumentationTag }
    ];
    
    // Check for keywords and add corresponding tags
    keywordTagMappings.forEach(mapping => {
      const hasKeyword = mapping.keywords.some(keyword => description.includes(keyword));
      
      if (hasKeyword && !issue.tags.contains(mapping.tag)) {
        issue.tags.add(mapping.tag);
      }
    });
  },
  requirements: {
    UITag: {
      type: entities.IssueTag,
      name: 'UI'
    },
    PerformanceTag: {
      type: entities.IssueTag,
      name: 'Performance'
    },
    BugTag: {
      type: entities.IssueTag,
      name: 'Bug'
    },
    DocumentationTag: {
      type: entities.IssueTag,
      name: 'Documentation'
    }
  }
});
"""
            },
            {
                "id": "state_transition",
                "title": "State transition workflow",
                "description": "Implements a state machine for issue states with validation rules",
                "tags": ["state", "transition", "workflow", "validation"],
                "code": """
const entities = require('@jetbrains/youtrack-scripting-api/entities');
const workflow = require('@jetbrains/youtrack-scripting-api/workflow');

exports.rule = entities.Issue.onChange({
  title: 'State transition workflow',
  guard: (ctx) => {
    const issue = ctx.issue;
    return issue.fields.State && issue.fields.State.isChanged;
  },
  action: (ctx) => {
    const issue = ctx.issue;
    const oldState = issue.fields.State.oldValue;
    const newState = issue.fields.State;
    
    // Define valid transitions
    const validTransitions = {
      'Open': ['In Progress', 'Postponed', 'Closed'],
      'In Progress': ['Open', 'Fixed', 'Won\'t Fix'],
      'Fixed': ['Open', 'Verified', 'Closed'],
      'Verified': ['Open', 'Closed'],
      'Won\\'t Fix': ['Open', 'Closed'],
      'Postponed': ['Open', 'Closed'],
      'Closed': ['Open']
    };
    
    // Check if the transition is valid
    if (oldState && validTransitions[oldState.name]) {
      const allowedNewStates = validTransitions[oldState.name];
      
      if (!allowedNewStates.includes(newState.name)) {
        workflow.check(false, `Cannot transition from '${oldState.name}' to '${newState.name}'. ` +
                             `Allowed transitions: ${allowedNewStates.join(', ')}`);
      }
    }
    
    // Additional validation rules
    if (newState.name === 'Fixed' && !issue.fields.Assignee) {
      workflow.check(false, 'Cannot set state to Fixed without an assignee');
    }
    
    if (newState.name === 'Verified' && ctx.currentUser.login === issue.fields.Assignee.login) {
      workflow.check(false, 'The assignee cannot verify their own fix');
    }
    
    if (newState.name === 'Closed') {
      // Auto-set resolution if not set
      if (!issue.fields.Resolution) {
        if (oldState && oldState.name === 'Fixed') {
          issue.fields.Resolution = ctx.FixedResolution;
        } else if (oldState && oldState.name === 'Won\\'t Fix') {
          issue.fields.Resolution = ctx.WontFixResolution;
        }
      }
    }
  },
  requirements: {
    State: {
      type: entities.State.fieldType,
      name: 'State',
      Open: {},
      'In Progress': {},
      Fixed: {},
      Verified: {},
      'Won\\'t Fix': {},
      Postponed: {},
      Closed: {}
    },
    Resolution: {
      type: entities.EnumField.fieldType,
      name: 'Resolution'
    },
    FixedResolution: {
      type: entities.EnumField.fieldType,
      name: 'Resolution',
      Fixed: {}
    },
    WontFixResolution: {
      type: entities.EnumField.fieldType,
      name: 'Resolution',
      'Won\\'t Fix': {}
    }
  }
});
"""
            }
        ]