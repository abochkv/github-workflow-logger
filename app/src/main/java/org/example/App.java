package org.example;

import org.example.api.ApiDataRetriever;
import org.example.db.Repository;
import org.example.logic.WorkflowLogger;

public class App {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <url> <token>");
            return;
        }

        String inputUrl = args[0];
        String token = args[1];

        // Remove protocol and trailing slashes for consistent splitting
        String path = inputUrl.replace("https://", "")
                .replace("http://", "")
                .replace("github.com/", "");

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String[] parts = path.split("/");

        if (parts.length < 2) {
            System.err.println("Invalid GitHub URL. Expected format: owner/repo or github.com/owner/repo");
            return;
        }

        final String owner = parts[0];
        final String repo = parts[1];

        System.out.println("Starting workflow logger for " + owner + "/" + repo);

        WorkflowLogger logger = new WorkflowLogger(new ApiDataRetriever(repo, owner, token));
        try {
            logger.registerShutdownHook();

            if (Repository.exists(repo, owner)) {
                logger.handleExistingRepository(Repository.getConnectedAt(repo, owner));
            } else {
                Repository.add(repo, owner);
                logger.handleNewRepository();
            }
            logger.startPolling();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}