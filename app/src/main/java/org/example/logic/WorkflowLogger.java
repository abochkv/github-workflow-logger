package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;

import java.io.PrintStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private final Repository repo;
    private final PrintStream out;

    private OffsetDateTime oldestNotCompletedJobTimestamp;
    private OffsetDateTime lastLoggedTimestamp;
    private final DateTimeFormatter logTimeFormat =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ZoneId zoneId = ZoneId.systemDefault();

    public WorkflowLogger(ApiDataRetriever api, Repository repo, PrintStream out) {
        this.api = api;
        this.repo = repo;
        this.out = out;
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
        updateTimestamp(OffsetDateTime.MIN);
    }

    public void handleExistingRepository(String oldestNotCompletedJobTimestamp, String lastLoggedItem) throws Exception {
        List<WorkflowRun> workflowsSinceLast = api.getWorkflowRunsFrom(oldestNotCompletedJobTimestamp);
        this.lastLoggedTimestamp = OffsetDateTime.parse(lastLoggedItem);
        this.oldestNotCompletedJobTimestamp = getOldestActiveRunTimestamp(workflowsSinceLast);
        OffsetDateTime lastLoggedTimestamp = this.lastLoggedTimestamp;
        for (WorkflowRun workflowRun : workflowsSinceLast) {
            List<WorkflowJob> jobs;
            switch (workflowRun.getStatus()) {
                case Status.COMPLETED:
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    logWorkflowRunIfNeeded(workflowRun, lastLoggedTimestamp, jobs);
                    updateLastLoggedTimestampWorkflowRun(workflowRun);
                    this.completedWorkflowRunIds.add(workflowRun.getId());
                    break;
                case Status.IN_PROGRESS:
                    logWorkflowRunIfNeeded(workflowRun, lastLoggedTimestamp, null);
                    updateLastLoggedTimestampWorkflowRun(workflowRun);

                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());
                    activeWorkflowRuns.put(workflowRun, new HashMap<>());

                    for (WorkflowJob job : jobs) {
                        activeWorkflowRuns.get(workflowRun).put(job.getId(), job);
                        logJobIfNeeded(job, lastLoggedTimestamp);

                        updateLastLoggedTimestampJob(job);
                    }
                    break;
                case Status.QUEUED:
                    this.queuedWorkflowRuns.add(workflowRun.getId());
                    updateLastLoggedTimestampWorkflowRun(workflowRun);
                    logWorkflowRunIfNeeded(workflowRun, lastLoggedTimestamp, null);
                    break;
                default:
                    break;
            }
        }
        updateTimestamp(lastLoggedTimestamp);
    }

    public void startPolling() throws Exception {
        pollingMode();
    }

    private void pollingMode() throws Exception {
        out.println("Started polling for changes (Press Ctrl+C to stop)...");
        while (running) {
            try {
                checkForChanges();
            } catch (Exception e) {
                out.println("Error during poll: " + e.getMessage());
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
        OffsetDateTime oldest = this.oldestNotCompletedJobTimestamp;
        List<WorkflowRun> workflowRuns = api.getWorkflowRunsFrom(oldest.toString());
        this.oldestNotCompletedJobTimestamp = getOldestActiveRunTimestamp(workflowRuns);

        for (WorkflowRun run : workflowRuns) {
            switch (run.getStatus()) {
                case Status.COMPLETED:
                    if (!completedWorkflowRunIds.contains(run.getId())) {
                        List<WorkflowJob> jobs = api.getJobsForWorkflowRun(run.getId());
                        checkForJobUpdatesForWorkflowRun(run, jobs);
                        printCompletedWorkflow(run, jobs);
                    }
                    break;
                case Status.IN_PROGRESS:
                    if (activeWorkflowRuns.containsKey(run)) {
                    checkForJobUpdatesForWorkflowRun(run, null);
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
        updateTimestamp(this.lastLoggedTimestamp);
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

    private void checkForJobUpdatesForWorkflowRun(WorkflowRun workflowRun, List<WorkflowJob> currentJobs) throws Exception {
        if (currentJobs == null)
            currentJobs = api.getJobsForWorkflowRun(workflowRun.getId());
        Map<Long, WorkflowJob> cachedJobs = this.activeWorkflowRuns.getOrDefault(workflowRun, new HashMap<>());
        boolean runWasPrinted = false;

        for (WorkflowJob currentJob : currentJobs) {
            WorkflowJob lastKnownJob = cachedJobs.get(currentJob.getId());
            boolean jobWasPrinted = false;

            if (lastKnownJob == null) {
                if (!runWasPrinted) {
                    printWorkflowRun(workflowRun);
                    runWasPrinted = true;
                }
                printWorkflowJob(currentJob, false, false);
                cachedJobs.put(currentJob.getId(), currentJob);
                continue;
            }

            if (currentJob.getStatus() != lastKnownJob.getStatus()) {
                if (!runWasPrinted) {
                    printWorkflowRun(workflowRun);
                    runWasPrinted = true;
                }
                printWorkflowJob(currentJob, false, false);
                jobWasPrinted = true;
                cachedJobs.put(currentJob.getId(), currentJob);
            }

            Map<Integer, JobStep> oldStepMap = lastKnownJob.getSortedSteps().stream()
                    .collect(Collectors.toMap(JobStep::getNumber, Function.identity()));

            // We iterate the CURRENT steps. This ensures we catch new steps added by the runner.
            for (JobStep currentStep : currentJob.getSortedSteps()) {
                JobStep oldStep = oldStepMap.get(currentStep.getNumber());

                // Scenario A: Step is brand new (Composite action expanded)
                if (oldStep == null) {
                    if (!jobWasPrinted) {
                        if (!runWasPrinted) {
                            printWorkflowRun(workflowRun);
                            runWasPrinted = true;
                        }
                        printWorkflowJob(currentJob, false, false);
                        jobWasPrinted = true;
                    }
                    this.lastLoggedTimestamp = updateLastLoggedTimestampStep(currentStep);
                    printWorkflowJobStep(currentStep);
                }
                // Scenario B: Step existed, check if status changed
                else if (oldStep.getStatus() != currentStep.getStatus()) {
                    if (!jobWasPrinted) {
                        if (!runWasPrinted) {
                            printWorkflowRun(workflowRun);
                            runWasPrinted = true;
                        }
                        printWorkflowJob(currentJob, false, false);
                        jobWasPrinted = true;
                    }
                    this.lastLoggedTimestamp = updateLastLoggedTimestampStep(currentStep);
                    printWorkflowJobStep(currentStep);
                }
            }

            // Update the cache with the fresh job object
            cachedJobs.put(currentJob.getId(), currentJob);
        }
    }

    private OffsetDateTime updateLastLoggedTimestampStep(JobStep currentStep) {
        if (currentStep.getCompletedAt() != null && currentStep.getCompletedAt().isAfter(this.lastLoggedTimestamp))
            return currentStep.getCompletedAt();
        else if (currentStep.getStartedAt() != null && currentStep.getStartedAt().isAfter(this.lastLoggedTimestamp))
            return currentStep.getStartedAt();
        return this.lastLoggedTimestamp;
    }

    private void printWorkflowRun(WorkflowRun workflowRun) {
        Workflow associatedWorkflow = existingWorkflows.get(workflowRun.getWorkflowId());
        updateLastLoggedTimestampWorkflowRun(workflowRun);

        // 1. Determine the relevant timestamp
        OffsetDateTime timestamp = (workflowRun.getUpdatedAt() != null)
                ? workflowRun.getUpdatedAt()
                : workflowRun.getCreatedAt();

        // 2. Determine the status/conclusion display string
        String statusDisplay = (workflowRun.getStatus() == Status.COMPLETED)
                ? workflowRun.getConclusion().toString()
                : workflowRun.getStatus().toString();

        // 3. Use String.format for a clean, scannable template
        String header = String.format("[Workflow] %s", associatedWorkflow.getName());

        String details = String.format(
                "%s [RUN %s] %s | Branch: %s | Commit: %s",
                timestamp.atZoneSameInstant(zoneId).format(logTimeFormat),
                statusDisplay.toUpperCase(),
                workflowRun.getName(),
                workflowRun.getHeadBranch(),
                workflowRun.getHeadSha()
        );

        out.println(header);
        out.println(details);
    }

    private void printCompletedWorkflow(WorkflowRun run, List<WorkflowJob> jobs) {
        printWorkflowRun(run);

        Conclusion conclusion = run.getConclusion();
        if (Conclusion.FAILURE.equals(conclusion) || Conclusion.TIMED_OUT.equals(conclusion)) {
            out.println("   FAILURE SUMMARY:");
            jobs.stream()
                    .filter(j -> List.of(Conclusion.FAILURE, Conclusion.TIMED_OUT, Conclusion.STARTUP_FAILURE)
                            .contains(j.getConclusion()))
                    .forEach(job -> printWorkflowJob(job, true, false));
        } else if (Conclusion.ACTION_REQUIRED.equals(conclusion)) {
            out.println("   -> Action Required: " + run.getHtmlUrl());
        }
    }

    private void printWorkflowJob(WorkflowJob job, boolean printSteps, boolean printSuccessfulSteps) {
        // 1. Determine timestamp: priority is Completed -> Started -> Created
        OffsetDateTime timestamp = job.getCompletedAt();
        if (timestamp == null) timestamp = (job.getStartedAt() != null) ? job.getStartedAt() : job.getCreatedAt();

        // 2. Determine display status
        String statusDisplay = ((job.getStatus() == Status.COMPLETED) ? job.getConclusion() : job.getStatus()).toString();

        // 3. Print Job line (indented for hierarchy)
        out.println(String.format(
                "  %s [Job %s] %s",
                timestamp.atZoneSameInstant(zoneId).format(logTimeFormat),
                statusDisplay.toUpperCase(),
                job.getName()
        ));
        updateLastLoggedTimestampJob(job);

        if (printSteps) {
            List<JobStep> steps = job.getSortedSteps();

            // Filter out successful steps if requested
            if (!printSuccessfulSteps) {
                steps = steps.stream()
                        .filter(s -> s.getConclusion() != null && !Conclusion.SUCCESS.equals(s.getConclusion()))
                        .toList();
            }

            steps.forEach(this::printWorkflowJobStep);
        }
    }

    private void printWorkflowJobStep(JobStep step) {
        if (step.getStatus() != Status.COMPLETED && step.getStatus() != Status.IN_PROGRESS) {
            return;
        }

        OffsetDateTime timestamp = (step.getCompletedAt() == null) ? step.getStartedAt() : step.getCompletedAt();
        String statusDisplay = ((step.getStatus() == Status.COMPLETED) ? step.getConclusion() : step.getStatus()).toString();

        // 4. Print Step line (further indented)
        out.println(String.format(
                "    %s [Step %s] %s",
                timestamp.atZoneSameInstant(zoneId).format(logTimeFormat),
                statusDisplay.toUpperCase(),
                step.getName()
        ));
        updateLastLoggedTimestampStep(step);
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

    private void logWorkflowRunIfNeeded(WorkflowRun workflowRun, OffsetDateTime lastLoggedTimestamp, List<WorkflowJob> jobs) {
        if (workflowRun.getUpdatedAt() != null && workflowRun.getUpdatedAt().isAfter(lastLoggedTimestamp)) {
            if (jobs != null) {
                printCompletedWorkflow(workflowRun, jobs);
            } else {
                printWorkflowRun(workflowRun);
            }
        }  else if (workflowRun.getCreatedAt() != null && workflowRun.getCreatedAt().isAfter(lastLoggedTimestamp)) {
            if (jobs != null) {
                printCompletedWorkflow(workflowRun, jobs);
            } else {
                printWorkflowRun(workflowRun);
            }
        }
    }

    private void updateLastLoggedTimestampWorkflowRun(WorkflowRun workflowRun) {
        if (workflowRun.getUpdatedAt() != null && workflowRun.getUpdatedAt().isAfter(this.lastLoggedTimestamp)) {
            this.lastLoggedTimestamp = workflowRun.getUpdatedAt();
        } else if (workflowRun.getCreatedAt() != null && workflowRun.getCreatedAt().isAfter(this.lastLoggedTimestamp)) {
            this.lastLoggedTimestamp = workflowRun.getCreatedAt();
        }
    }

    private void updateLastLoggedTimestampJob(WorkflowJob job) {
        if (job.getCompletedAt() != null && job.getCompletedAt().isAfter(this.lastLoggedTimestamp))
            this.lastLoggedTimestamp = job.getCompletedAt();
        else if (job.getStartedAt() != null && job.getStartedAt().isAfter(this.lastLoggedTimestamp))
            this.lastLoggedTimestamp = job.getStartedAt();
        else if (job.getCreatedAt() != null && job.getCreatedAt().isAfter(this.lastLoggedTimestamp))
            this.lastLoggedTimestamp = job.getCreatedAt();
    }

    private void logJobIfNeeded(WorkflowJob job, OffsetDateTime lastLoggedTimestamp) {
        if (job.getCompletedAt() != null && job.getCompletedAt().isAfter(lastLoggedTimestamp))
            printWorkflowJob(job, true, true);
        else if (job.getStartedAt() != null && job.getStartedAt().isAfter(lastLoggedTimestamp))
            printWorkflowJob(job, true, false);
        else if (job.getCreatedAt() != null && job.getCreatedAt().isAfter(lastLoggedTimestamp))
            printWorkflowJob(job, false, false);
    }

    private void updateTimestamp(OffsetDateTime lastLoggedTimestamp) {
        this.lastLoggedTimestamp = lastLoggedTimestamp;
        this.repo.updateTimestamp(api.repo, api.owner, this.oldestNotCompletedJobTimestamp, this.lastLoggedTimestamp);
    }

    private OffsetDateTime getOldestActiveRunTimestamp(List<WorkflowRun> workflowRuns) {
        OffsetDateTime time = workflowRuns.stream()
                .filter(r -> r.getStatus() != Status.COMPLETED)
                .map(WorkflowRun::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(this.oldestNotCompletedJobTimestamp);
        return time == null ? OffsetDateTime.now() : time;
    }
}
