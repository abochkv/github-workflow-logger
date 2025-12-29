package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Status {
    @JsonProperty("queued")
    QUEUED,

    @JsonProperty("in_progress")
    IN_PROGRESS,

    @JsonProperty("completed")
    COMPLETED,

    @JsonProperty("waiting")
    WAITING,

    @JsonProperty("requested")
    REQUESTED,

    @JsonProperty("pending")
    PENDING;
}