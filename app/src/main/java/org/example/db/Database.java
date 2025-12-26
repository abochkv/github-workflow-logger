package org.example.db;

import java.sql.*;

final class Database {

    private static final String DB_URL = "jdbc:sqlite:data/repos.db";

    static {
        init();
    }

    private static void init() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS connected_repos (
                    repo TEXT PRIMARY KEY,
                    connected_at TEXT NOT NULL
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
