# Project Guidelines

# Project Structure
In the directory `packages/youtrack-scripring-api`, you will find files of youtrack-scripting-api library.

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
