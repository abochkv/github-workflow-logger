package org.example.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.model.Workflow;
import java.util.List;

public class WorkflowsDataContract implements CountableDataContract<Workflow> {
    @JsonProperty("total_count")
    private int totalCount;
    private List<Workflow> workflows;

    @Override
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public List<Workflow> getWorkflows() { return workflows; }
    public void setWorkflows(List<Workflow> workflows) { this.workflows = workflows; }

    @Override
    public List<Workflow> getItems() {
        return workflows;
    }
}