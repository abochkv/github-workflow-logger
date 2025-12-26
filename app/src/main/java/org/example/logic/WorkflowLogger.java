package org.example.logic;

import org.example.db.Repository;

public class WorkflowLogger {
    private final String repo;
    private final String token;
    private final String owner;

    public WorkflowLogger(String repo, String token) {
        this.repo = repo;
        this.token = token;
        this.owner = repo.split("/")[1];
    }

    public void handleNewRepository() {
        Repository.add(repo);
        pollingMode();
    }

    public void handleExistingRepository() {

    }

    private void pollingMode() {

    }
}
