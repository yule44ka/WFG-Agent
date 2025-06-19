/**
 * This rule ensures that a due date is set when creating an issue.
 *
 * For details, read the Quick Start Guide:
 * https://www.jetbrains.com/help/youtrack/devportal/Quick-Start-Guide-Workflows-JS.html
 */

const entities = require('@jetbrains/youtrack-scripting-api/entities');

exports.rule = entities.Issue.onChange({
  title: 'Due Date Required',
  guard: (ctx) => {
    const issue = ctx.issue;
    // Only apply this rule when an issue is being created
    return issue.becomesReported;
  },
  action: (ctx) => {
    const issue = ctx.issue;
    // Check if Due Date is not set
    if (!issue.fields['Due Date']) {
      throw 'Due Date is required when creating an issue';
    }
  },
  requirements: {
    // Require the Due Date field
    DueDate: {
      type: entities.Field.dateType,
      name: 'Due Date'
    }
  }
});
