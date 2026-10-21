package org.Cry_Legende.sHHypeVotes;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HypeCommands implements CommandExecutor, TabCompleter {

    private final SHHypeVotes plugin;
    private final ConfigManager configManager;
    private final HypeTrainManager hypeTrainManager;

    public HypeCommands(SHHypeVotes plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.hypeTrainManager = plugin.getHypeTrainManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shh.admin")) {
            sender.sendMessage(configManager.getPrefixedMessage("no-permission"));
            return true;
        }
        if (configManager == null || hypeTrainManager == null) {
            sender.sendMessage(ChatColor.RED + "Erreur: Le plugin SHHypeVotes n'est pas correctement initialisé.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                handleStartCommand(sender);
                break;
            case "stop":
                handleStopCommand(sender);
                break;
            case "end":
                handleEndCommand(sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            case "status":
                handleStatusCommand(sender);
                break;
            default:
                sender.sendMessage(configManager.getPrefixedMessage("unknown-subcommand"));
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleStartCommand(CommandSender sender) {
        if (hypeTrainManager.isActive()) {
            sender.sendMessage(configManager.getPrefixedMessage("hype-already-active"));
            return;
        }
        if (configManager.getGoals().isEmpty()) {
            sender.sendMessage(configManager.getPrefixedMessage("prefix") + ChatColor.RED + "Impossible de démarrer: Aucun palier configuré !");
            return;
        }
        if (hypeTrainManager.startHypeTrain(true)) {
            sender.sendMessage(configManager.getPrefixedMessage("force-start-success"));
        } else {
            sender.sendMessage(configManager.getPrefixedMessage("prefix") + ChatColor.RED + "Le Hype Train n'a pas pu démarrer. Vérifiez la console.");
        }
    }

    private void handleStopCommand(CommandSender sender) {
        if (!hypeTrainManager.isActive()) {
            sender.sendMessage(configManager.getPrefixedMessage("hype-not-active"));
            return;
        }
        hypeTrainManager.stopCurrentHypeTrain(false, true);
        sender.sendMessage(configManager.getPrefixedMessage("force-stop-success"));
    }

    private void handleEndCommand(CommandSender sender) {
        if (!hypeTrainManager.isActive()) {
            sender.sendMessage(configManager.getPrefixedMessage("hype-not-active"));
            return;
        }
        hypeTrainManager.stopCurrentHypeTrain(true, true);
        sender.sendMessage(configManager.getPrefixedMessage("force-end-success"));
    }

    private void handleReloadCommand(CommandSender sender) {
        configManager.reloadConfigs();
        hypeTrainManager.reload();
        sender.sendMessage(configManager.getPrefixedMessage("reload-success"));
        plugin.logInfo("Configuration rechargée par " + sender.getName() + ".");
    }

    private void handleStatusCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--- &eStatut SHHypeVotes &6---"));
        if (hypeTrainManager.isActive()) {
            sender.sendMessage(ChatColor.GREEN + "Hype Train: ACTIF");
            sender.sendMessage(ChatColor.YELLOW + "  Votes: " + ChatColor.WHITE + hypeTrainManager.getCurrentVotes());
            long remainingSeconds = (hypeTrainManager.getEndTimeMillis() - System.currentTimeMillis()) / 1000;
            sender.sendMessage(ChatColor.YELLOW + "  Temps restant: " + ChatColor.WHITE + hypeTrainManager.formatTime(remainingSeconds, configManager.countdownFormat));

            Goal currentGoal = hypeTrainManager.getCurrentHighestGoalReached();
            if (currentGoal != null)
                sender.sendMessage(ChatColor.YELLOW + "  Dernier palier atteint: " + ChatColor.translateAlternateColorCodes('&', currentGoal.displayName()) + ChatColor.GRAY + " (" + currentGoal.threshold() + ")");
            else sender.sendMessage(ChatColor.YELLOW + "  Dernier palier atteint: " + ChatColor.WHITE + "Aucun");

            Goal nextGoal = hypeTrainManager.getNextGoalToAnnounce();
            if (nextGoal != null)
                sender.sendMessage(ChatColor.YELLOW + "  Prochain palier: " + ChatColor.translateAlternateColorCodes('&', nextGoal.displayName()) + ChatColor.GRAY + " (" + nextGoal.threshold() + ")");
            else if (currentGoal != null)
                sender.sendMessage(ChatColor.YELLOW + "  Prochain palier: " + ChatColor.GREEN + "Tous les paliers atteints !");
            else
                sender.sendMessage(ChatColor.YELLOW + "  Prochain palier: " + ChatColor.RED + (configManager.getGoals().isEmpty() ? "Aucun palier configuré" : "Erreur"));

            sender.sendMessage(ChatColor.YELLOW + "  Votants uniques: " + ChatColor.WHITE + hypeTrainManager.getVotersInCurrentHype().size());
        } else {
            sender.sendMessage(ChatColor.RED + "Hype Train: INACTIF");
            long nextAttemptTime = hypeTrainManager.getNextRandomHypeTaskTime();
            if (nextAttemptTime > System.currentTimeMillis()) {
                long secondsToNextAttempt = (nextAttemptTime - System.currentTimeMillis()) / 1000;
                sender.sendMessage(ChatColor.YELLOW + "  Prochaine tentative aléatoire dans: " + ChatColor.WHITE + hypeTrainManager.formatTime(secondsToNextAttempt, "HH'h'mm'm'ss's'"));
            } else if (nextAttemptTime > 0) {
                sender.sendMessage(ChatColor.YELLOW + "  Tentative de lancement aléatoire: " + ChatColor.WHITE + "Imminente ou en cours...");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "  Prochaine tentative aléatoire: " + ChatColor.WHITE + "Non planifiée.");
            }
        }
        sender.sendMessage(ChatColor.YELLOW + "Paliers configurés: " + ChatColor.WHITE + configManager.getGoals().size());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6-------------------------"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(configManager.getPrefixedMessage("prefix") + ChatColor.AQUA + "Commandes disponibles:"); // Utilise le préfixe configuré
        sender.sendMessage(ChatColor.GOLD + "/shh start " + ChatColor.GRAY + "- Démarre de force un Hype Train.");
        sender.sendMessage(ChatColor.GOLD + "/shh stop " + ChatColor.GRAY + "- Arrête de force le Hype Train en cours.");
        sender.sendMessage(ChatColor.GOLD + "/shh end " + ChatColor.GRAY + "- Termine le Hype en distribuant les récompenses atteintes.");
        sender.sendMessage(ChatColor.GOLD + "/shh reload " + ChatColor.GRAY + "- Recharge la configuration du plugin.");
        sender.sendMessage(ChatColor.GOLD + "/shh status " + ChatColor.GRAY + "- Affiche le statut actuel du Hype Train.");
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("shh.admin")) return null;
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "end", "reload", "status").stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
