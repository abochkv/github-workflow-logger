package org.example;


import org.example.db.Repository;
import org.example.logic.WorkflowLogger;

public class App {

    public static void main(String[] args) {
        final String repo = args[0].split("/")[1];
        final String owner = args[0].split("/")[0];
        final String token = args[1];

        WorkflowLogger logger = new WorkflowLogger(repo, owner, token);
        try {
            if (Repository.exists(repo, owner)) {
                logger.handleExistingRepository(Repository.getConnectedAt(repo, owner));
            } else {
                Repository.add(repo, owner);
                logger.handleNewRepository();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
