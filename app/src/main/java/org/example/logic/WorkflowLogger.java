package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WorkflowLogger {
    private final String owner;
    private final String repo;
    private final ApiDataRetriever api;
    private final Map<Long, Workflow> existingWorkflows = new HashMap<>();
    private final Set<WorkflowRun> queuedWorkflowRuns = new HashSet<>();
    private final Map<WorkflowRun, Map<Long, WorkflowJob>> workflowRunToJobMap = new HashMap<>();

    public WorkflowLogger(String repo, String owner, String token) {
        this.owner = owner;
        this.repo = repo;
        this.api = new ApiDataRetriever(repo, owner, token);

        initWorkflows();
    }

    public void handleNewRepository() throws Exception {
        queuedWorkflowRuns.addAll(api.getQueuedWorkflowRuns());
        List<WorkflowRun> active = api.getActiveWorkflowRuns();

        for (WorkflowRun activeRun : active) {
            List<WorkflowJob> jobs = api.getJobsForWorkflowRun(activeRun.getId());
            workflowRunToJobMap.put(activeRun, new HashMap<>());

            for (WorkflowJob job : jobs) {
                workflowRunToJobMap.get(activeRun).put(job.getId(), job);
            }
        }

        pollingMode();
    }

    public void handleExistingRepository(String lastRetrieved) throws Exception {
        List<WorkflowRun> workflowsSinceLast = api.getWorkflowRunsFrom(lastRetrieved);

        for (WorkflowRun workflowRun : workflowsSinceLast) {
            List<WorkflowJob> jobs;
            switch (workflowRun.getStatus()) {
                case Status.COMPLETED:
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    printCompletedWorkflow(workflowRun, jobs);
                    break;
                case Status.IN_PROGRESS:
                    printWorkflowRun(workflowRun);
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    workflowRunToJobMap.put(workflowRun, new HashMap<>());

                    for (WorkflowJob job : jobs) {
                        workflowRunToJobMap.get(workflowRun).put(job.getId(), job);

                        if (job.getStatus() == Status.IN_PROGRESS || job.getStatus() == Status.COMPLETED) {
                            printWorkflowJob(job);
                            for (JobStep step : job.getSortedSteps()) {
                                printWorkflowJobStep(step);
                            }
                        }
                    }
                    break;
                default:
                    printWorkflowRun(workflowRun);
                    break;
            }
        }
        Repository.updateTimestamp(this.repo, this.owner);

        pollingMode();
    }

    private void pollingMode() throws Exception {

    }

    private void checkForChanges() throws Exception {
        // 1. Check Queued Runs
        List<WorkflowRun> currentQueued = api.getQueuedWorkflowRuns();
        for (WorkflowRun run : currentQueued) {
            if (!this.queuedWorkflowRuns.contains(run)) {
                printWorkflowRun(run);
            }
        }
        this.queuedWorkflowRuns.clear();
        this.queuedWorkflowRuns.addAll(currentQueued);

        // 2. Check Active Runs
        List<WorkflowRun> activeWorkflows = api.getActiveWorkflowRuns();

        for (WorkflowRun activeRun : activeWorkflows) {
            if (workflowRunToJobMap.containsKey(activeRun)) {
                checkForJobUpdatesForWorkflowRun(activeRun);
            } else {
                // New Run Detected
                printWorkflowRun(activeRun);
                workflowRunToJobMap.put(activeRun, new HashMap<>());

                // Initialize jobs for the new run
                List<WorkflowJob> initialJobs = api.getJobsForWorkflowRun(activeRun.getId());
                for (WorkflowJob job : initialJobs) {
                    workflowRunToJobMap.get(activeRun).put(job.getId(), job);
                    printWorkflowJob(job);

                    if (job.getStatus() == Status.IN_PROGRESS) {
                        for (JobStep step : job.getSortedSteps()) {
                            printWorkflowJobStep(step);
                        }
                    }
                }
            }
        }

        // 3. Check Completed Runs
        Set<WorkflowRun> activeSet = new HashSet<>(activeWorkflows);
        Set<WorkflowRun> trackedRuns = new HashSet<>(workflowRunToJobMap.keySet());

        // trackedRuns - activeSet = Completed Runs
        trackedRuns.removeAll(activeSet);

        for (WorkflowRun completedRun : trackedRuns) {
//            // Fetch final state (needed for 'conclusion')
//            // Note: You might need to refresh the object if 'activeWorkflows' list didn't contain the completed version
//            // But usually, if it disappears from active, we treat it as done.
//            // Ideally, fetch the single run one last time to get the final 'conclusion' enum.
//            WorkflowRun finalStateRun = api.getWorkflowRunById(completedRun.getId());
//
//            if (finalStateRun != null) {
                printCompletedWorkflow(completedRun, new ArrayList<>(workflowRunToJobMap.get(completedRun).values()));
//            }
            workflowRunToJobMap.remove(completedRun);
        }
    }

    private void checkForJobUpdatesForWorkflowRun(WorkflowRun workflowRun) throws Exception {
        List<WorkflowJob> currentJobs = api.getJobsForWorkflowRun(workflowRun.getId());
        Map<Long, WorkflowJob> cachedJobs = this.workflowRunToJobMap.get(workflowRun);

        for (WorkflowJob currentJob : currentJobs) {
            WorkflowJob lastKnownJob = cachedJobs.get(currentJob.getId());

            // FIX 1: Handle New Jobs (Matrix expansion)
            // If the job wasn't in our map last time, it's new.
            // Original code crashed here because lastKnownJob was null.
            if (lastKnownJob == null) {
                printWorkflowJob(currentJob);
                cachedJobs.put(currentJob.getId(), currentJob);
                // Print steps if they exist already
                if (currentJob.getStatus() == Status.IN_PROGRESS) {
                    currentJob.getSortedSteps().forEach(this::printWorkflowJobStep);
                }
                continue; // Skip the comparison logic for this loop
            }

            // Check for Status Change
            if (currentJob.getStatus() != lastKnownJob.getStatus()) {
                printWorkflowJob(currentJob);
                cachedJobs.put(currentJob.getId(), currentJob);
            }

            // FIX 2: Handle Dynamic Steps
            // We map the OLD steps by their Number (ID) for easy lookup.
            Map<Integer, JobStep> oldStepMap = lastKnownJob.getSortedSteps().stream()
                    .collect(Collectors.toMap(JobStep::getNumber, Function.identity()));

            // We iterate the CURRENT steps. This ensures we catch new steps added by the runner.
            for (JobStep currentStep : currentJob.getSortedSteps()) {
                JobStep oldStep = oldStepMap.get(currentStep.getNumber());

                // Scenario A: Step is brand new (Composite action expanded)
                if (oldStep == null) {
                    printWorkflowJobStep(currentStep);
                }
                // Scenario B: Step existed, check if status changed
                else if (oldStep.getStatus() != currentStep.getStatus()) {
                    printWorkflowJobStep(currentStep);
                }
            }

            // Update the cache with the fresh job object (containing the new steps list)
            cachedJobs.put(currentJob.getId(), currentJob);
        }
    }

    private void printWorkflowRun(WorkflowRun workflowRun) {
        StringBuilder queuedStr = new StringBuilder();
        Workflow assosiatedWorkflow = existingWorkflows.get(workflowRun.getWorkflowId());
        queuedStr.append("[Workflow] ").append(assosiatedWorkflow.getName());
        queuedStr.append("\t")
                .append("[RUN ")
                .append(workflowRun.getStatus());
                queuedStr.append("] ")
                .append(workflowRun.getName())
                .append(" ")
                .append("Branch: ")
                .append(workflowRun.getHeadBranch())
                .append(" ")
                .append("Commit: ")
                .append(workflowRun.getHeadSha());
        System.out.println(queuedStr);
    }

    private void printCompletedWorkflow(WorkflowRun run, List<WorkflowJob> jobs) {
        printWorkflowRun(run);
        switch (run.getConclusion()) {
            case Conclusion.FAILURE:
            case Conclusion.TIMED_OUT:
                jobs.stream()
                        .filter(j -> j.getConclusion() == Conclusion.FAILURE
                        || j.getConclusion() == Conclusion.TIMED_OUT
                        || j.getConclusion() == Conclusion.STARTUP_FAILURE)
                        .forEach(this::printWorkflowJob);
                break;
            case ACTION_REQUIRED:
                System.out.println("Run link: " + run.getHtmlUrl());
                break;
            default:
                break;
        }
    }

    private void printWorkflowJob(WorkflowJob job) {
        StringBuilder queuedStr = new StringBuilder();

        queuedStr.append("\t\t")
                .append(job.getCompletedAt() == null ?
                        (job.getStartedAt() == null ? job.getCreatedAt() : job.getStartedAt())
                        : job.getCompletedAt())
                .append(" [Job ");
        if (job.getStatus() == Status.COMPLETED) {
            queuedStr.append(job.getConclusion());
        } else {
            queuedStr.append(job.getStatus());
        }
        queuedStr.append("] ")
        .append(job.getName())
        .append("\n");

        System.out.println(queuedStr);
    }

    private void printWorkflowJobStep(JobStep step) {
        if (step.getStatus() != Status.COMPLETED && step.getStatus() != Status.IN_PROGRESS) {
            return;
        }

        StringBuilder queuedStr = new StringBuilder();
        queuedStr.append("\t\t\t")
                .append(step.getCompletedAt() == null ? step.getStartedAt() : step.getCompletedAt())
                .append(" [Step ");
        if (step.getStatus() == Status.COMPLETED) {
            queuedStr.append(step.getConclusion());
        } else {
            queuedStr.append(step.getStatus());
        }
        queuedStr.append("] ")
        .append(step.getName())
        .append("\n");
        System.out.println(queuedStr);
    }

    private void initWorkflows() {
        try {
            List<Workflow> workflows = api.getWorkflows();
            for (Workflow workflow : workflows) {
                existingWorkflows.put(workflow.getId(), workflow);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
