package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WorkflowLogger {
    private volatile boolean running = true;
    private static final long POLL_INTERVAL_MS = 30_000;
    private final ApiDataRetriever api;
    private final Map<Long, Workflow> existingWorkflows = new HashMap<>();
    private final Set<Long> queuedWorkflowRuns = new HashSet<>();
    private final Set<Long> completedWorkflowRunIds = new HashSet<>();
    private final Map<WorkflowRun, Map<Long, WorkflowJob>> activeWorkflowRuns = new HashMap<>();

    private OffsetDateTime oldestNotCompletedJobTimestamp;
    private OffsetDateTime lastPollTimestamp;

    public WorkflowLogger(ApiDataRetriever api) {
        this.api = api;
        initWorkflows();
    }

    public void registerShutdownHook() {
        Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.running = false;
            mainThread.interrupt();
        }));
    }

    public void handleNewRepository() throws Exception {
        List<WorkflowRun> workflowRuns = api.getWorkflowRunsFrom(OffsetDateTime.now().minus(Duration.ofDays(3)).toString());
        updateLocalCache(workflowRuns);
        this.oldestNotCompletedJobTimestamp = getOldestActiveRunTimestamp(workflowRuns);
        updateTimestamp();
    }

    public void handleExistingRepository(String lastRetrieved, String lastNotCompletedWorkflowRunTimestamp) throws Exception {
        List<WorkflowRun> workflowsSinceLast = api.getWorkflowRunsFrom(lastRetrieved);

        this.oldestNotCompletedJobTimestamp = getOldestActiveRunTimestamp(workflowsSinceLast);

        workflowsSinceLast = filterWorkflowsUpdatedAfter(workflowsSinceLast, lastNotCompletedWorkflowRunTimestamp);

        for (WorkflowRun workflowRun : workflowsSinceLast) {
            List<WorkflowJob> jobs;
            switch (workflowRun.getStatus()) {
                case Status.COMPLETED:
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    printCompletedWorkflow(workflowRun, jobs);
                    this.completedWorkflowRunIds.add(workflowRun.getId());
                    break;
                case Status.IN_PROGRESS:
                    printWorkflowRun(workflowRun);
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    activeWorkflowRuns.put(workflowRun, new HashMap<>());

                    for (WorkflowJob job : jobs) {
                        activeWorkflowRuns.get(workflowRun).put(job.getId(), job);
                        printWorkflowJob(job, true, true);
                    }
                    break;
                case Status.QUEUED:
                    this.queuedWorkflowRuns.add(workflowRun.getId());
                    printWorkflowRun(workflowRun);
                    break;
                default:
                    break;
            }
        }
        updateTimestamp();
    }

    private void updateTimestamp() {
        this.lastPollTimestamp = OffsetDateTime.now();
        Repository.updateTimestamp(api.repo, api.owner, this.lastPollTimestamp, this.oldestNotCompletedJobTimestamp);
    }

    private List<WorkflowRun> filterWorkflowsUpdatedAfter(List<WorkflowRun> workflowsSinceLast, String shutdownInstant) {
        return workflowsSinceLast.stream().filter(r -> r.getUpdatedAt() != null && r.getUpdatedAt()
                .isAfter(OffsetDateTime.parse(shutdownInstant)))
                .sorted(Comparator.comparing(WorkflowRun::getUpdatedAt)).toList();
    }

    private OffsetDateTime getOldestActiveRunTimestamp(List<WorkflowRun> workflowRuns) {
        OffsetDateTime time = workflowRuns.stream()
                .filter(r -> r.getStatus() != Status.COMPLETED)
                .map(WorkflowRun::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(this.oldestNotCompletedJobTimestamp);
        return time == null ? OffsetDateTime.now() : time;
    }

    public void startPolling() throws Exception {
        pollingMode();
    }

    private void pollingMode() throws Exception {
        System.out.println("Started polling for changes (Press Ctrl+C to stop)...");
        while (running) {
            try {
                checkForChanges();
                updateTimestamp();
            } catch (Exception e) {
                System.err.println("Error during poll: " + e.getMessage());
                e.printStackTrace();
            }

            // Sleep to preserve rate limit
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void checkForChanges() throws Exception {
        List<WorkflowRun> workflowRuns = api.getWorkflowRunsFrom(this.oldestNotCompletedJobTimestamp.toString());
        this.oldestNotCompletedJobTimestamp = getOldestActiveRunTimestamp(workflowRuns);

        List<WorkflowRun> filteredWorkflowRuns = filterWorkflowsUpdatedAfter(workflowRuns, this.lastPollTimestamp.toString());

        for (WorkflowRun run : filteredWorkflowRuns) {
            switch (run.getStatus()) {
                case Status.COMPLETED:
                    if (!completedWorkflowRunIds.contains(run.getId())) {
                        printCompletedWorkflow(run, api.getJobsForWorkflowRun(run.getId()));
                    }
                    break;
                case Status.IN_PROGRESS:
                    if (activeWorkflowRuns.containsKey(run)) {
                    checkForJobUpdatesForWorkflowRun(run);
                } else {
                    // New Run Detected
                    printWorkflowRun(run);
                    activeWorkflowRuns.put(run, new HashMap<>());

                    // Initialize jobs for the new run
                    List<WorkflowJob> initialJobs = api.getJobsForWorkflowRun(run.getId());
                    for (WorkflowJob job : initialJobs) {
                        activeWorkflowRuns.get(run).put(job.getId(), job);
                        printWorkflowJob(job, true, true);
                    }
                }
                    break;
                case Status.QUEUED:
                    if (!queuedWorkflowRuns.contains(run.getId())) {
                        printWorkflowRun(run);
                    }
                default:
                    break;
            }
        }

        updateLocalCache(workflowRuns);
    }

    private void updateLocalCache(List<WorkflowRun> runs) {
        this.activeWorkflowRuns.keySet().retainAll(runs.stream()
                .filter(r -> r.getStatus() == Status.IN_PROGRESS)
                .collect(Collectors.toSet()));

        this.completedWorkflowRunIds.clear();
        this.completedWorkflowRunIds.addAll(runs.stream()
                .filter(r -> r.getStatus() == Status.COMPLETED)
                .map(WorkflowRun::getId).collect(Collectors.toSet()));

        this.queuedWorkflowRuns.clear();
        this.queuedWorkflowRuns.addAll(runs.stream()
                .filter(r -> r.getStatus() == Status.QUEUED)
                .map(WorkflowRun::getId).collect(Collectors.toSet()));
    }

    private void checkForJobUpdatesForWorkflowRun(WorkflowRun workflowRun) throws Exception {
        List<WorkflowJob> currentJobs = api.getJobsForWorkflowRun(workflowRun.getId());
        Map<Long, WorkflowJob> cachedJobs = this.activeWorkflowRuns.get(workflowRun);

        for (WorkflowJob currentJob : currentJobs) {
            WorkflowJob lastKnownJob = cachedJobs.get(currentJob.getId());

            if (lastKnownJob == null) {
                printWorkflowJob(currentJob, false, false);
                cachedJobs.put(currentJob.getId(), currentJob);
                continue;
            }

            if (currentJob.getStatus() != lastKnownJob.getStatus()) {
                printWorkflowJob(currentJob, false, false);
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        queuedStr.append("[Workflow] ").append(assosiatedWorkflow.getName()).append("\n");
        queuedStr.append(
                (workflowRun.getUpdatedAt() == null ? workflowRun.getCreatedAt() : workflowRun.getUpdatedAt())
                        .format(formatter)
                )
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
                        .forEach(job -> printWorkflowJob(job, true, false));
                break;
            case ACTION_REQUIRED:
                System.out.println("Run link: " + run.getHtmlUrl());
                break;
            default:
                break;
        }
    }

    private void printWorkflowJob(WorkflowJob job, boolean printSteps, boolean printSuccessfulSteps) {
        StringBuilder queuedStr = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        queuedStr.append((job.getCompletedAt() == null ?
                        (job.getStartedAt() == null ? job.getCreatedAt() : job.getStartedAt())
                        : job.getCompletedAt()).format(formatter))
                .append(" [Job ");
        if (job.getStatus() == Status.COMPLETED) {
            queuedStr.append(job.getConclusion());
        } else {
            queuedStr.append(job.getStatus());
        }
        queuedStr.append("] ")
        .append(job.getName());

        System.out.println(queuedStr);
        if (printSteps) {
            List<JobStep> steps = job.getSortedSteps();

            if (!printSuccessfulSteps) {
                steps = steps.stream().filter(s -> s.getConclusion() != null
                        && s.getConclusion() != Conclusion.SUCCESS).toList();
            }

            for (JobStep step : steps) {
                printWorkflowJobStep(step);
            }
        }
    }

    private void printWorkflowJobStep(JobStep step) {
        if (step.getStatus() != Status.COMPLETED && step.getStatus() != Status.IN_PROGRESS) {
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder queuedStr = new StringBuilder();
        queuedStr.append((step.getCompletedAt() == null ? step.getStartedAt() : step.getCompletedAt()).format(formatter))
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
