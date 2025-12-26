package org.example;


import org.example.db.Repository;
import org.example.logic.WorkflowLogger;

public class App {

    public static void main(String[] args) {
        final String repo = args[0];
        final String token = args[1];

        WorkflowLogger logger = new WorkflowLogger(repo, token);
        try {
            if (Repository.exists(repo)) {
                logger.handleExistingRepository(Repository.getConnectedAt(repo));
            } else {
                Repository.add(repo);
                logger.handleNewRepository();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
