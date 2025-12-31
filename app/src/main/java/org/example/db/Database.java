package org.example.db;

import java.sql.*;

final class Database {

    private static final String DB_URL = "jdbc:sqlite:data/repos.db";

    static {
        init();
    }

    private static void init() {
        // 1. Get the parent directory from the URL (or hardcode it if it matches DB_URL)
        java.io.File dbFile = new java.io.File("data/repos.db");
        java.io.File directory = dbFile.getParentFile();

        // 2. Create the directory if it doesn't exist
        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS connected_repos (
                    repo TEXT PRIMARY KEY,
                    last_not_completed_workflow_run_timestamp TEXT NOT NULL
                )
            """);

        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}
