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
            sender.sendMessage(configManager.getPrefixedMessage("not-initialized"));
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

    /** Mode réseau, serveur qui ne mène pas le Hype : la commande part au meneur. */
    private boolean forwarded(CommandSender sender, String action) {
        HypeNetwork network = plugin.getNetwork();
        if (network == null || network.isOwner()) {
            return false;
        }
        network.command(action);
        sender.sendMessage(configManager.getPrefixedMessage("network-forwarded"));
        return true;
    }

    private void handleStartCommand(CommandSender sender) {
        if (hypeTrainManager.isActive()) {
            sender.sendMessage(configManager.getPrefixedMessage("hype-already-active"));
            return;
        }
        if (configManager.getGoals().isEmpty()) {
            sender.sendMessage(configManager.getPrefixedMessage("start-no-goals"));
            return;
        }
        if (forwarded(sender, "start")) {
            return;
        }
        if (hypeTrainManager.startHypeTrain(true)) {
            sender.sendMessage(configManager.getPrefixedMessage("force-start-success"));
        } else {
            sender.sendMessage(configManager.getPrefixedMessage("start-failed"));
        }
    }

    private void handleStopCommand(CommandSender sender) {
        if (!hypeTrainManager.isActive()) {
            sender.sendMessage(configManager.getPrefixedMessage("hype-not-active"));
            return;
        }
        if (forwarded(sender, "stop")) {
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
        if (forwarded(sender, "end")) {
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
        sender.sendMessage(text("status-header"));
        if (hypeTrainManager.isActive()) {
            sender.sendMessage(text("status-active"));
            sender.sendMessage(text("status-votes", "%votes%", String.valueOf(hypeTrainManager.getCurrentVotes())));
            long remainingSeconds = (hypeTrainManager.getEndTimeMillis() - System.currentTimeMillis()) / 1000;
            sender.sendMessage(text("status-time-left", "%time%", hypeTrainManager.formatTime(remainingSeconds, configManager.countdownFormat)));

            Goal currentGoal = hypeTrainManager.getCurrentHighestGoalReached();
            if (currentGoal != null)
                sender.sendMessage(text("status-current-goal", "%goal%", coloredName(currentGoal), "%threshold%", String.valueOf(currentGoal.threshold())));
            else sender.sendMessage(text("status-current-goal-none"));

            Goal nextGoal = hypeTrainManager.getNextGoalToAnnounce();
            if (nextGoal != null)
                sender.sendMessage(text("status-next-goal", "%goal%", coloredName(nextGoal), "%threshold%", String.valueOf(nextGoal.threshold())));
            else if (currentGoal != null)
                sender.sendMessage(text("status-all-goals-reached"));
            else
                sender.sendMessage(text(configManager.getGoals().isEmpty() ? "status-no-goal-configured" : "status-next-goal-error"));

            sender.sendMessage(text("status-voters", "%voters%", String.valueOf(hypeTrainManager.getVoterCount())));
        } else {
            sender.sendMessage(text("status-inactive"));
            long nextAttemptTime = hypeTrainManager.getNextRandomHypeTaskTime();
            if (nextAttemptTime > System.currentTimeMillis()) {
                long secondsToNextAttempt = (nextAttemptTime - System.currentTimeMillis()) / 1000;
                sender.sendMessage(text("status-next-attempt", "%time%", hypeTrainManager.formatTime(secondsToNextAttempt, "HH'h'mm'm'ss's'")));
            } else if (nextAttemptTime > 0) {
                sender.sendMessage(text("status-attempt-imminent"));
            } else {
                sender.sendMessage(text("status-attempt-unplanned"));
            }
        }
        sender.sendMessage(text("status-goals-count", "%count%", String.valueOf(configManager.getGoals().size())));
        sender.sendMessage(text("status-footer"));
    }

    private void sendHelp(CommandSender sender) {
        for (String line : configManager.getMessageList("help")) {
            sender.sendMessage(line);
        }
    }

    /** Message de messages.yml dont les placeholders sont remplacés (paires placeholder, valeur). */
    private String text(String key, String... replacements) {
        String message = configManager.getMessage(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    private String coloredName(Goal goal) {
        return ChatColor.translateAlternateColorCodes('&', goal.displayName());
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
