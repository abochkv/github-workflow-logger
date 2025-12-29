package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class WorkflowRun {
    private long id;
    private String name;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("head_branch")
    private String headBranch;

    @JsonProperty("head_sha")
    private String headSha;

    private String path;

    @JsonProperty("display_title")
    private String displayTitle;

    @JsonProperty("run_number")
    private int runNumber;

    private String event;
    private Status status;
    private Conclusion conclusion;

    @JsonProperty("workflow_id")
    private long workflowId;

    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("pull_requests")
    private List<Object> pullRequests; // Can create a class if needed

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @JsonProperty("run_attempt")
    private int runAttempt;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return (obj instanceof WorkflowRun)
                && (getId() == ((WorkflowRun) obj).getId())
                && getWorkflowId() == ((WorkflowRun) obj).getWorkflowId()
                && getRunNumber() == ((WorkflowRun) obj).getRunNumber()
                && getRunAttempt() == ((WorkflowRun) obj).getRunAttempt();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getWorkflowId(), getRunNumber(), getRunAttempt());
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getHeadBranch() { return headBranch; }
    public void setHeadBranch(String headBranch) { this.headBranch = headBranch; }

    public String getHeadSha() { return headSha; }
    public void setHeadSha(String headSha) { this.headSha = headSha; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getDisplayTitle() { return displayTitle; }
    public void setDisplayTitle(String displayTitle) { this.displayTitle = displayTitle; }

    public int getRunNumber() { return runNumber; }
    public void setRunNumber(int runNumber) { this.runNumber = runNumber; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Conclusion getConclusion() { return conclusion; }
    public void setConclusion(Conclusion conclusion) { this.conclusion = conclusion; }

    public long getWorkflowId() { return workflowId; }
    public void setWorkflowId(long workflowId) { this.workflowId = workflowId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public List<Object> getPullRequests() { return pullRequests; }
    public void setPullRequests(List<Object> pullRequests) { this.pullRequests = pullRequests; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getRunAttempt() { return runAttempt; }
    public void setRunAttempt(int runAttempt) { this.runAttempt = runAttempt; }
}
