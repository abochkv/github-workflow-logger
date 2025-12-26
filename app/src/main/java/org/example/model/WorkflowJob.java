package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents a Job within a GitHub Actions Workflow Run.
 */
public class WorkflowJob {

    private long id;

    @JsonProperty("run_id")
    private long runId;

    @JsonProperty("run_url")
    private String runUrl;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("head_sha")
    private String headSha;

    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    private String status;
    private String conclusion;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("started_at")
    private OffsetDateTime startedAt;

    @JsonProperty("completed_at")
    private OffsetDateTime completedAt;

    private String name;
    private List<JobStep> steps;

    @JsonProperty("check_run_url")
    private String checkRunUrl;

    private List<String> labels;

    @JsonProperty("runner_id")
    private Long runnerId;

    @JsonProperty("runner_name")
    private String runnerName;

    @JsonProperty("runner_group_id")
    private Long runnerGroupId;

    @JsonProperty("runner_group_name")
    private String runnerGroupName;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("head_branch")
    private String headBranch;

    // --- Identity & Equality ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowJob that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    // --- Getters and Setters ---

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }

    public String getRunUrl() { return runUrl; }
    public void setRunUrl(String runUrl) { this.runUrl = runUrl; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getHeadSha() { return headSha; }
    public void setHeadSha(String headSha) { this.headSha = headSha; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<JobStep> getSteps() { return steps; }
    public void setSteps(List<JobStep> steps) { this.steps = steps; }

    public String getCheckRunUrl() { return checkRunUrl; }
    public void setCheckRunUrl(String checkRunUrl) { this.checkRunUrl = checkRunUrl; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public Long getRunnerId() { return runnerId; }
    public void setRunnerId(Long runnerId) { this.runnerId = runnerId; }

    public String getRunnerName() { return runnerName; }
    public void setRunnerName(String runnerName) { this.runnerName = runnerName; }

    public Long getRunnerGroupId() { return runnerGroupId; }
    public void setRunnerGroupId(Long runnerGroupId) { this.runnerGroupId = runnerGroupId; }

    public String getRunnerGroupName() { return runnerGroupName; }
    public void setRunnerGroupName(String runnerGroupName) { this.runnerGroupName = runnerGroupName; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getHeadBranch() { return headBranch; }
    public void setHeadBranch(String headBranch) { this.headBranch = headBranch; }
}