# GitHub Workflow logger
This project logs latest changes in GitHub Actions workflow by constantly polling GitHub API.
# How to Run
In Github releases you can find pre-built .jar and a run.sh script, which you can run directly by running
`run.sh <LINK_TO_REPOSITORY> <GITHUB_TOKEN>`, or build/run it yourself with gradle.
# How it works
 - First Start: Application remembers the repository by writing it to SQLite database and starts polling directly.
 - X Start: Retrieve last start from the database and start polling from there.
 - Polling happens every 30 seconds.
 - If there are any changes, it logs them to console
 - For failed runs, it logs all jobs and steps that failed.

