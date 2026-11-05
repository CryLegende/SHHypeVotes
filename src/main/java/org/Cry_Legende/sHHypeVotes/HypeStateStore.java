package org.Cry_Legende.sHHypeVotes;

import fr.topicfly.shvotesreward.data.Database;
import org.bukkit.ChatColor;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * État du Hype Train en base, lu par le site : shh_state (une seule ligne) et shh_goals (les paliers).
 * Les écritures passent par la file de SHVotesReward : une base lente ou absente ne bloque jamais le jeu.
 */
final class HypeStateStore {

    private final Database database;

    HypeStateStore(Database database) {
        this.database = database;
    }

    void createTables() {
        database.write("tables du Hype Train", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS shh_state (
                          id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
                          active TINYINT(1) NOT NULL,
                          votes INT NOT NULL,
                          ends_at BIGINT NOT NULL,
                          updated_at BIGINT NOT NULL
                        ) DEFAULT CHARSET=utf8mb4""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS shh_goals (
                          votes INT NOT NULL PRIMARY KEY,
                          name VARCHAR(128) NOT NULL
                        ) DEFAULT CHARSET=utf8mb4""");
            }
        });
    }

    void saveGoals(List<Goal> goals) {
        List<Goal> saved = List.copyOf(goals);
        database.write("paliers du Hype Train", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM shh_goals");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO shh_goals (votes, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)")) {
                for (Goal goal : saved) {
                    statement.setInt(1, goal.threshold());
                    statement.setString(2, plainName(goal));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    void save(boolean active, int votes, long endsAt) {
        long now = System.currentTimeMillis();
        database.write("état du Hype Train", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO shh_state (id, active, votes, ends_at, updated_at) VALUES (1, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE active = VALUES(active), votes = VALUES(votes),
                      ends_at = VALUES(ends_at), updated_at = VALUES(updated_at)""")) {
                statement.setBoolean(1, active);
                statement.setInt(2, votes);
                statement.setLong(3, endsAt);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    private static String plainName(Goal goal) {
        String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', goal.displayName()));
        return name.length() > 128 ? name.substring(0, 128) : name;
    }
}
