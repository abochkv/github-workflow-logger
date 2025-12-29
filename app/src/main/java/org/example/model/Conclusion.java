package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Conclusion {
    @JsonProperty("success")
    SUCCESS,

    @JsonProperty("failure")
    FAILURE,

    @JsonProperty("neutral")
    NEUTRAL,

    @JsonProperty("cancelled")
    CANCELLED,

    @JsonProperty("skipped")
    SKIPPED,

    @JsonProperty("timed_out")
    TIMED_OUT,

    @JsonProperty("action_required")
    ACTION_REQUIRED,

    @JsonProperty("stale")
    STALE,

    @JsonProperty("startup_failure")
    STARTUP_FAILURE;
}