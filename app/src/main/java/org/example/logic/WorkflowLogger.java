package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WorkflowLogger {
    private volatile boolean running = true;
    private static final long POLL_INTERVAL_MS = 30_000;
    private final ApiDataRetriever api;
    private final Map<Long, Workflow> existingWorkflows = new HashMap<>();
    private final Set<WorkflowRun> queuedWorkflowRuns = new HashSet<>();
    private final Map<WorkflowRun, Map<Long, WorkflowJob>> workflowRunToJobMap = new HashMap<>();

    public WorkflowLogger(ApiDataRetriever api) {
        this.api = api;
        initWorkflows();
    }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nStopping gracefully... please wait.");
            this.running = false;
        }));
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
        Repository.updateTimestamp(api.repo, api.owner);


    }

    public void startPolling() throws Exception {
        pollingMode();
    }

    private void pollingMode() throws Exception {
        System.out.println("Started polling for changes (Press Ctrl+C to stop)...");
        while (running) {
            try {
                checkForChanges();
                Repository.updateTimestamp(api.repo, api.owner);
            } catch (Exception e) {
                System.err.println("Error during poll: " + e.getMessage());
                e.printStackTrace();
            }

            // Sleep to preserve rate limit
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    protected void checkForChanges() throws Exception {
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
            WorkflowRun finalStateRun = api.getWorkflowRunById(completedRun.getId());

            if (finalStateRun != null) {
                printCompletedWorkflow(finalStateRun, new ArrayList<>(workflowRunToJobMap.get(completedRun).values()));
            }
            workflowRunToJobMap.remove(completedRun);
        }
    }

    private void checkForJobUpdatesForWorkflowRun(WorkflowRun workflowRun) throws Exception {
        List<WorkflowJob> currentJobs = api.getJobsForWorkflowRun(workflowRun.getId());
        Map<Long, WorkflowJob> cachedJobs = this.workflowRunToJobMap.get(workflowRun);

        for (WorkflowJob currentJob : currentJobs) {
            WorkflowJob lastKnownJob = cachedJobs.get(currentJob.getId());

            if (lastKnownJob == null) {
                printWorkflowJob(currentJob);
                cachedJobs.put(currentJob.getId(), currentJob);
                // Print steps if they exist already
                if (currentJob.getStatus() == Status.IN_PROGRESS) {
                    currentJob.getSortedSteps().forEach(this::printWorkflowJobStep);
                }
                continue;
            }

            if (currentJob.getStatus() != lastKnownJob.getStatus()) {
                printWorkflowJob(currentJob);
                cachedJobs.put(currentJob.getId(), currentJob);
            }

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

            // Update the cache with the fresh job object
            cachedJobs.put(currentJob.getId(), currentJob);
        }
    }

    private void printWorkflowRun(WorkflowRun workflowRun) {
        StringBuilder queuedStr = new StringBuilder();
        Workflow assosiatedWorkflow = existingWorkflows.get(workflowRun.getWorkflowId());
        queuedStr.append("[Workflow] ").append(assosiatedWorkflow.getName()).append("\n");
        queuedStr.append(workflowRun.getUpdatedAt() == null ? workflowRun.getCreatedAt() : workflowRun.getUpdatedAt())
                .append(" [RUN ");
        if (workflowRun.getStatus() == Status.COMPLETED) {
            queuedStr.append(workflowRun.getConclusion());
        } else {
            queuedStr.append(workflowRun.getStatus());
        }
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

        queuedStr.append(job.getCompletedAt() == null ?
                        (job.getStartedAt() == null ? job.getCreatedAt() : job.getStartedAt())
                        : job.getCompletedAt())
                .append(" [Job ");
        if (job.getStatus() == Status.COMPLETED) {
            queuedStr.append(job.getConclusion());
        } else {
            queuedStr.append(job.getStatus());
        }
        queuedStr.append("] ")
        .append(job.getName());

        System.out.println(queuedStr);
    }

    private void printWorkflowJobStep(JobStep step) {
        if (step.getStatus() != Status.COMPLETED && step.getStatus() != Status.IN_PROGRESS) {
            return;
        }

        StringBuilder queuedStr = new StringBuilder();
        queuedStr.append(step.getCompletedAt() == null ? step.getStartedAt() : step.getCompletedAt())
                .append(" [Step ");
        if (step.getStatus() == Status.COMPLETED) {
            queuedStr.append(step.getConclusion());
        } else {
            queuedStr.append(step.getStatus());
        }
        queuedStr.append("] ")
        .append(step.getName());
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
