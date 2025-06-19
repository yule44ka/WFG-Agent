# Project Guidelines

# Framework: Koog
Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin. It lets you create agents that can interact with tools, handle complex workflows, and communicate with users.

# Project Structure
In the directory `packages/youtrack-scripring-api`, you will find files of youtrack-scripting-api library.
Use this library to create scripts that can be executed in YouTrack.
`agent.md` is the main file that contains the agent's logic and tools.

# YouTrack Scripting API
The library `youtrack-scripting-api` provides a set of APIs to interact with YouTrack's scripting capabilities, allowing users to automate tasks and extend YouTrack's functionality.

# Workflow script template
```javascript
/**
 * This is a template for an on-change rule. This rule defines what
 * happens when a change is applied to an issue.
 *
 * For details, read the Quick Start Guide:
 * https://www.jetbrains.com/help/youtrack/devportal/Quick-Start-Guide-Workflows-JS.html
 */

const entities = require('@jetbrains/youtrack-scripting-api/entities');

exports.rule = entities.Issue.onChange({
  // TODO: give the rule a human-readable title
  title: 'Date',
  guard: (ctx) => {
    // TODO specify the conditions for executing the rule
    return true;
  },
  action: (ctx) => {
    const issue = ctx.issue;
    // TODO: specify what to do when a change is applied to an issue
  },
  requirements: {
    // TODO: add requirements
  }
});
```
