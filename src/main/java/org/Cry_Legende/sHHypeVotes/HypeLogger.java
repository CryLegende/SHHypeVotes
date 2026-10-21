package org.Cry_Legende.sHHypeVotes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HypeLogger {

    private final SHHypeVotes plugin;
    private final File logsDir;

    public HypeLogger(SHHypeVotes plugin) {
        this.plugin = plugin;
        this.logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    /**
     * Enregistre un résumé complet de la session Hype Train terminée.
     *
     * @param totalVotes         Nombre total de votes reçus
     * @param uniqueVoters       Set des UUID des votants uniques
     * @param highestGoalReached Le palier le plus haut atteint (peut être null)
     * @param allGoals           Liste de tous les paliers configurés
     * @param durationSeconds    Durée configurée de base en secondes
     * @param actualDurationMs   Durée réelle (startTime à endTime) en millisecondes
     * @param rewardsDistributed Si les récompenses ont été distribuées
     * @param playersOnlineAtEnd Nombre de joueurs en ligne à la fin
     * @param forcedStop         Si arrêté manuellement par commande
     */
    public void logHypeTrainEnd(int totalVotes, Set<UUID> uniqueVoters, Goal highestGoalReached,
                                List<Goal> allGoals, int durationSeconds, long actualDurationMs,
                                boolean rewardsDistributed, int playersOnlineAtEnd, boolean forcedStop) {
        if (!plugin.getConfigManager().loggingEnabled) return;

        SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date now = new Date();

        String fileName = "hype-" + fileDateFormat.format(now) + ".log";
        File logFile = new File(logsDir, fileName);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("========================================");
            writer.println("  HYPE TRAIN - " + timestampFormat.format(now));
            writer.println("========================================");
            writer.println("Statut: " + (forcedStop ? "ARRET FORCE" : "TERMINE NATURELLEMENT"));
            writer.println("Récompenses distribuées: " + (rewardsDistributed ? "OUI" : "NON"));
            writer.println();

            // Statistiques de votes
            writer.println("--- VOTES ---");
            writer.println("Votes totaux: " + totalVotes);
            writer.println("Votants uniques: " + uniqueVoters.size());
            writer.println();

            // Palier atteint
            writer.println("--- PALIERS ---");
            if (highestGoalReached != null) {
                writer.println("Palier atteint: " + ChatColor.stripColor(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', highestGoalReached.displayName()))
                    + " (" + highestGoalReached.key() + ", seuil: " + highestGoalReached.threshold() + ")");
            } else {
                writer.println("Palier atteint: AUCUN");
            }

            // Progression dans les paliers
            writer.println("Progression:");
            for (Goal goal : allGoals) {
                boolean reached = totalVotes >= goal.threshold();
                String status = reached ? "[OK]" : "[  ]";
                String goalName = ChatColor.stripColor(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', goal.displayName()));
                writer.println("  " + status + " " + goal.key() + " - " + goalName
                    + " (seuil: " + goal.threshold() + ")");
            }
            writer.println();

            // Durée
            writer.println("--- DUREE ---");
            long actualSeconds = actualDurationMs / 1000;
            writer.println("Durée de base configurée: " + formatDuration(durationSeconds));
            writer.println("Durée réelle (avec extensions): " + formatDuration(actualSeconds));
            writer.println();

            // Joueurs
            writer.println("--- SERVEUR ---");
            writer.println("Joueurs en ligne (fin): " + playersOnlineAtEnd);
            writer.println();

            // Liste des votants
            if (plugin.getConfigManager().loggingIncludeVoterList && !uniqueVoters.isEmpty()) {
                writer.println("--- VOTANTS ---");
                for (UUID uuid : uniqueVoters) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = op.getName() != null ? op.getName() : uuid.toString();
                    writer.println("  - " + name);
                }
                writer.println();
            }

            writer.println("========================================");
            writer.println();

            plugin.logInfo("Log Hype Train écrit dans " + logFile.getName());
        } catch (IOException e) {
            plugin.logSevere("Impossible d'écrire le log Hype Train: " + e.getMessage());
        }
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }
}
