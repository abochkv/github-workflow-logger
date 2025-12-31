package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;

public class Repository {

    public static boolean exists(String repo, String owner) {
        String sql = "SELECT 1 FROM connected_repos WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo + "/" + owner);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void add(String repo, String owner) {
        String sql = """
            INSERT OR IGNORE INTO connected_repos (repo, connected_at)
            VALUES (?, ?)
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo + "/" + owner);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static RepoMetadata getConnectedAt(String repo, String owner) {
        String sql = "SELECT connected_at FROM connected_repos WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, repo + "/" + owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RepoMetadata(
                            rs.getString("connected_at")
                    );
                }
            }
            throw new RuntimeException("Repo not found: " + repo);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching timestamp for repo: " + repo, e);
        }
    }

    public static void updateTimestamp(String repo, String owner, OffsetDateTime lastPolled) {
        String sql = "UPDATE connected_repos SET connected_at = ? WHERE repo = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, lastPolled.toString());
            ps.setString(2, repo + "/" + owner);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Error updating timestamp for repo: " + repo, e);
        }
    }
}
