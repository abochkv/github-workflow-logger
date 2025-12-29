package org.example.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.model.WorkflowJob;
import org.example.model.WorkflowRun;

import java.util.List;

public class WorkflowRunsDataContract implements CountableDataContract<WorkflowRun> {
    @JsonProperty("total_count")
    int totalCount;

    @JsonProperty("workflow_runs")
    List<WorkflowRun> runs;

    public List<WorkflowRun> getRuns() {
        return runs;
    }

    public void setRuns(List<WorkflowRun> runs) {
        this.runs = runs;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    @Override
    public List<WorkflowRun> getItems() {
        return getRuns();
    }
}
