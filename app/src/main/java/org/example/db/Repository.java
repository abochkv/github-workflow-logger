package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class Repository {

    public static boolean exists(String repo) {
        String sql = "SELECT 1 FROM connected_repos WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void add(String repo) {
        String sql = """
            INSERT OR IGNORE INTO connected_repos (repo, connected_at)
            VALUES (?, ?)
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getConnectedAt(String repo) {
        String sql = "SELECT connected_at FROM connected_repos WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("connected_at");
                }
            }
            throw new RuntimeException("Repo not found: " + repo);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching timestamp for repo: " + repo, e);
        }
    }

    public static void updateTimestamp(String repo) {
        String sql = "UPDATE connected_repos SET connected_at = ? WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, Instant.now().toString());
            ps.setString(2, repo);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Error updating timestamp for repo: " + repo, e);
        }
    }
}
