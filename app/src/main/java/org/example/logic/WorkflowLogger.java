package org.example.logic;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.*;

import java.util.*;

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
        List<WorkflowRun> queuedWorkflowRuns = api.getQueuedWorkflowRuns();
        for (WorkflowRun queuedWorkflowRun : queuedWorkflowRuns) {
            if (!this.queuedWorkflowRuns.contains(queuedWorkflowRun)) {
                printWorkflowRun(queuedWorkflowRun);
            }
        }
        this.queuedWorkflowRuns.clear();
        this.queuedWorkflowRuns.addAll(queuedWorkflowRuns);

        List<WorkflowRun> activeWorkflows = api.getActiveWorkflowRuns();

        for (WorkflowRun activeWorkflow : activeWorkflows) {
            if (workflowRunToJobMap.containsKey(activeWorkflow)) { // the workflow was active and still active
                checkForJobUpdatesForWorkflowRun(activeWorkflow);
            } else { // new workflow run started
                printWorkflowRun(activeWorkflow);
                workflowRunToJobMap.put(activeWorkflow, new HashMap<>());

                for (WorkflowJob job : api.getJobsForWorkflowRun(activeWorkflow.getId())) {
                    workflowRunToJobMap.get(activeWorkflow).put(job.getId(), job);
                    printWorkflowJob(job);
                    if (job.getStatus() == Status.IN_PROGRESS) {
                        for (JobStep step : job.getSortedSteps()) {
                            printWorkflowJobStep(step);
                        }
                    }
                }
            }
        }

        Set<WorkflowRun> activeWorkflowsSet = new HashSet<>(activeWorkflows);

        Set<WorkflowRun> completedWorkflows = new HashSet<>(workflowRunToJobMap.keySet());
        completedWorkflows.removeAll(activeWorkflowsSet);

        for (WorkflowRun workflowRun : completedWorkflows) { // check for completed workflows
            printCompletedWorkflow(workflowRun, workflowRunToJobMap.get(workflowRun).values().stream().toList());
            workflowRunToJobMap.remove(workflowRun);
        }
    }

    private void checkForJobUpdatesForWorkflowRun(WorkflowRun workflowRun) throws Exception {
        List<WorkflowJob> jobs = api.getJobsForWorkflowRun(workflowRun.getId());
        Map<Long, WorkflowJob> lastFetchedJobs = this.workflowRunToJobMap.get(workflowRun);

        for (WorkflowJob job : jobs) {
            List<JobStep> lastJobSteps =  lastFetchedJobs.get(job.getId()).getSortedSteps();
            List<JobStep> currentJobSteps = job.getSortedSteps();
            if (job.getStatus() != lastFetchedJobs.get(job.getId()).getStatus()) {
                lastFetchedJobs.put(job.getId(), job);
                printWorkflowJob(job);
            }

            for (int i = 0; i < lastJobSteps.size(); i++) {
                if (lastJobSteps.get(i).getStatus() != currentJobSteps.get(i).getStatus()) {
                    printWorkflowJobStep(currentJobSteps.get(i));
                }
            }
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
