package org.example.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.model.JobStep;
import org.example.model.Workflow;
import org.example.model.WorkflowJob;
import org.example.model.WorkflowRun;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowLogger {
    private final String owner;
    private final ApiDataRetriever api;
    private final Map<Long, Workflow> existingWorkflows = new HashMap<>();
    private final Map<WorkflowRun, WorkflowJob> workflowRunToJobMap = new HashMap<>();

    public WorkflowLogger(String repo, String token) {
        this.owner = repo.split("/")[1];

        this.api = new ApiDataRetriever(repo, owner, token);

        initWorkflows();
    }

    public void handleNewRepository() throws Exception {
        List<WorkflowRun> queued = api.getQueuedWorkflowRuns();
        for (WorkflowRun queuedRun : queued) {
            System.out.println(formatQueuedWorkflowRun(queuedRun));
        }

        List<WorkflowRun> active = api.getActiveWorkflowRuns();

        for (WorkflowRun activeRun : active) {
            System.out.println(formatRunningWorkflow(activeRun));
            List<WorkflowJob> jobs = api.getJobsForWorkflowRun(activeRun.getId());

            for (WorkflowJob job : jobs) {
                if (job.getStatus().equals("in_progress") || job.getStatus().equals("queued"))
                    System.out.println(formatJob(job));
            }
        }

        pollingMode();
    }

    public void handleExistingRepository(String lastRetrieved) throws Exception {
        List<WorkflowRun> workflowsSinceLast = api.getWorkflowRunsFrom(lastRetrieved);

        for (WorkflowRun workflowRun : workflowsSinceLast) {
            List<WorkflowJob> jobs;
            switch (workflowRun.getStatus()) {
                case "completed":
                    System.out.println(formatCompletedWorkflow(workflowRun));
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());

                    for (WorkflowJob job : jobs) {
                        System.out.println(formatJob(job));

                        for (JobStep step : job.getSteps()) {
                            System.out.println(formatJobStep(step));
                        }
                    }
                    break;
                case "in_progress":
                    System.out.println(formatRunningWorkflow(workflowRun));
                    jobs = api.getJobsForWorkflowRun(workflowRun.getId());

                    for (WorkflowJob job : jobs) {
                        if (job.getStatus().equals("in_progress")
                                || job.getStatus().equals("queued")
                                || job.getStatus().equals("completed")) {
                            System.out.println(formatJob(job));

                            for (JobStep step : job.getSteps()) {
                                System.out.println(formatJobStep(step));
                            }
                        }
                    }
                    break;
                case "queued":
                    System.out.println(formatQueuedWorkflowRun(workflowRun));
                    break;
                default:
                    System.out.println(formatFailedWorkflow(workflowRun));
                    break;
            }
        }

        pollingMode();
    }

    private void pollingMode() {

    }

    private String formatQueuedWorkflowRun(WorkflowRun queued) {
        StringBuilder queuedStr = new StringBuilder();
        Workflow assosiatedWorkflow = existingWorkflows.get(queued.getWorkflowId());
        queuedStr.append("[Workflow Queued]: ").append(assosiatedWorkflow.getName());
        queuedStr.append("\t")
                .append("[RUN] ")
                .append(queued.getName())
                .append(": ")
                .append(queued.getStatus())
                .append(" ")
                .append("Commit SHA: ")
                .append(queued.getHeadSha())
                .append(" ")
                .append("Branch: ")
                .append(queued.getHeadBranch())
                .append("\n");
        return queuedStr.toString();
    }

    private String formatRunningWorkflow(WorkflowRun running) {
        StringBuilder queuedStr = new StringBuilder();
        Workflow assosiatedWorkflow = existingWorkflows.get(running.getWorkflowId());
        queuedStr.append("[Workflow In Progress]: ").append(assosiatedWorkflow.getName());
        queuedStr.append("\n")
                .append("\t")
                .append("[RUN] ")
                .append(running.getName())
                .append(" ")
                .append("Branch: ")
                .append(running.getHeadBranch())
                .append(" ")
                .append("Commit SHA: ")
                .append(running.getHeadSha())
                .append("\n");
        return queuedStr.toString();
    }

    private String formatCompletedWorkflow(WorkflowRun completed) {
        return "";
    }

    private String formatFailedWorkflow(WorkflowRun failed) {
        return "";
    }

    private String formatJob(WorkflowJob job) {
        return "";
    }

    private String formatJobStep(JobStep step) {
        return "";
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
