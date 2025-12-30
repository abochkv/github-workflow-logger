package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class JobStep {
    private String name;
    private Status status;
    private Conclusion conclusion;
    private int number;

    @JsonProperty("started_at")
    private OffsetDateTime startedAt;

    @JsonProperty("completed_at")
    private OffsetDateTime completedAt;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Conclusion getConclusion() { return conclusion; }
    public void setConclusion(Conclusion conclusion) { this.conclusion = conclusion; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
