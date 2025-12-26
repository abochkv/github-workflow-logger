package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Workflow {
    private long id;
    @JsonProperty("node_id")
    private String nodeId;
    private String name;
    private String path;
    private String state; // e.g., "active"
    @JsonProperty("html_url")
    private String htmlUrl;

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Workflow) && (getId() == ((Workflow) obj).getId());
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getId());
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
}