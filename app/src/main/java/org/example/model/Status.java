package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

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

    public String toString() {
        String[] split = super.toString().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            sb.append(split[i].substring(0, 1).toUpperCase())
                    .append(split[i].substring(1))
                    .append(" ");
        }

        return sb.toString().trim();
    }
}