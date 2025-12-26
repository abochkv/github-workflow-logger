package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class WorkflowJob {
    private long id;
    private String name;
    private String status;
    private String conclusion;
    private List<JobStep> steps;

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public List<JobStep> getSteps() { return steps; }
    public void setSteps(List<JobStep> steps) { this.steps = steps; }
}
