package org.Cry_Legende.sHHypeVotes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HypeLogger {

    private static final String LOG_FILE = "hype";

    private final SHHypeVotes plugin;

    public HypeLogger(SHHypeVotes plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre dans SHLogs le résumé de la session Hype Train terminée.
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

        String reached = highestGoalReached != null
            ? plainName(highestGoalReached) + " (" + highestGoalReached.key() + ", seuil: " + highestGoalReached.threshold() + ")"
            : "AUCUN";
        long actualSeconds = actualDurationMs / 1000;

        ActionLog.write(plugin, LOG_FILE, "Hype Train terminé"
            + " | Statut: " + (forcedStop ? "ARRET FORCE" : "TERMINE NATURELLEMENT")
            + " | Récompenses distribuées: " + (rewardsDistributed ? "OUI" : "NON")
            + " | Votes totaux: " + totalVotes
            + " | Votants uniques: " + uniqueVoters.size()
            + " | Palier atteint: " + reached
            + " | Durée de base configurée: " + formatDuration(durationSeconds)
            + " | Durée réelle (avec extensions): " + formatDuration(actualSeconds)
            + " | Joueurs en ligne (fin): " + playersOnlineAtEnd);

        List<String> progression = new ArrayList<>();
        for (Goal goal : allGoals) {
            boolean reachedGoal = totalVotes >= goal.threshold();
            progression.add((reachedGoal ? "[OK]" : "[  ]") + " " + goal.key() + " - " + plainName(goal)
                + " (seuil: " + goal.threshold() + ")");
        }
        ActionLog.write(plugin, LOG_FILE, "Hype Train progression: " + String.join(", ", progression));

        if (plugin.getConfigManager().loggingIncludeVoterList && !uniqueVoters.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (UUID uuid : uniqueVoters) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                names.add(op.getName() != null ? op.getName() : uuid.toString());
            }
            ActionLog.write(plugin, LOG_FILE, "Hype Train votants: " + String.join(", ", names));
        }
    }

    private String plainName(Goal goal) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', goal.displayName()));
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
