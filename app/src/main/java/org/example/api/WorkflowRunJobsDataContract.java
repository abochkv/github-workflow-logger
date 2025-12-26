package org.example.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.model.WorkflowJob;

import java.util.List;

public class WorkflowRunJobsDataContract implements CountableDataContract<WorkflowJob> {
    @JsonProperty("total_count")
    int totalCount;
    List<WorkflowJob> jobs;

    public List<WorkflowJob> getJobs() {
        return jobs;
    }

    public void setJobs(List<WorkflowJob> jobs) {
        this.jobs = jobs;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int total_count) {
        this.totalCount = total_count;
    }

    public List<WorkflowJob> getItems() { return getJobs(); }
}
