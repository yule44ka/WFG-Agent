"""
Main entry point for the YouTrack Workflow Generator Agent.

This module provides a command-line interface for interacting with the agent.
"""

import os
import sys
import argparse
from typing import Optional

from agent.agent import YouTrackWorkflowAgent
from agent.observations.get_feedback import GetFeedbackObservation


def main():
    """
    Main entry point for the YouTrack Workflow Generator Agent.
    """
    # Parse command-line arguments
    parser = argparse.ArgumentParser(description="YouTrack Workflow Generator Agent")
    parser.add_argument("--prompt", type=str, help="The prompt describing the desired workflow script")
    parser.add_argument("--api-key", type=str, help="API key for the LLM service")
    parser.add_argument("--output", type=str, help="Output file for the generated script")
    parser.add_argument("--interactive", action="store_true", help="Run in interactive mode")
    args = parser.parse_args()

    # Get API key from arguments or environment variable
    api_key = args.api_key or os.environ.get("GRAZIE_API_KEY")
    if not api_key:
        print("Error: API key must be provided via --api-key or GRAZIE_API_KEY environment variable")
        sys.exit(1)

    # Create the agent
    agent = YouTrackWorkflowAgent(api_key=api_key)

    # Get the prompt
    prompt = args.prompt

    # If no prompt is provided or interactive mode is enabled, enter interactive mode
    if not prompt or args.interactive:
        prompt = interactive_mode()

    # Generate the workflow script
    print("\nGenerating workflow script...\n")
    workflow_script = agent.generate_workflow_script(prompt)

    # Display the generated script
    print("\n=== Generated Workflow Script ===\n")
    print(workflow_script)
    print("\n=================================\n")

    # Save to output file if specified
    if args.output:
        with open(args.output, "w") as f:
            f.write(workflow_script)
        print(f"Workflow script saved to {args.output}")

    # Get feedback in interactive mode
    if args.interactive:
        feedback_observation = GetFeedbackObservation()
        feedback = feedback_observation.get_code_feedback(workflow_script)

        # If the user is not satisfied, regenerate the script
        if not feedback.get("feedback", {}).get("satisfaction", True):
            requested_changes = feedback.get("feedback", {}).get("requested_changes", "")

            if requested_changes:
                print("\nRegenerating workflow script based on your feedback...\n")
                updated_prompt = f"{prompt}\n\nAdditional requirements: {requested_changes}"
                workflow_script = agent.generate_workflow_script(updated_prompt)

                # Display the regenerated script
                print("\n=== Regenerated Workflow Script ===\n")
                print(workflow_script)
                print("\n====================================\n")

                # Save to output file if specified
                if args.output:
                    with open(args.output, "w") as f:
                        f.write(workflow_script)
                    print(f"Updated workflow script saved to {args.output}")


def interactive_mode() -> str:
    """
    Run the agent in interactive mode.

    Returns:
        The user's prompt.
    """
    print("=== YouTrack Workflow Generator Agent ===")
    print("Welcome to the YouTrack Workflow Generator Agent!")
    print("This agent will help you generate YouTrack workflow scripts based on your requirements.")
    print("Please describe the workflow script you want to generate.")
    print("For example: 'Create a workflow script that automatically assigns issues to the team lead when they are created with a Critical priority.'")
    print("\nEnter your prompt (type 'exit' to quit):")

    prompt = input("> ")

    if prompt.lower() == "exit":
        print("Exiting...")
        sys.exit(0)

    return prompt


if __name__ == "__main__":
    main()
