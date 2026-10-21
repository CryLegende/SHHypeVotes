package org.Cry_Legende.sHHypeVotes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SHHypeVotes extends JavaPlugin {

    private static SHHypeVotes instance;
    private ConfigManager configManager;
    private HypeTrainManager hypeTrainManager;
    private HypeLogger hypeLogger;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Charger la configuration
        configManager = new ConfigManager(this); // Crée les fichiers par défaut si besoin
        // Recharger une fois pour s'assurer que toutes les valeurs par défaut sont bien lues après création
        configManager.reloadConfigs();


        // 2. Vérifier la dépendance Votifier
        if (getServer().getPluginManager().getPlugin("Votifier") == null) {
            getLogger().severe("Votifier (ou NuVotifier) n'a pas été trouvé. SHHypeVotes ne peut pas fonctionner sans lui.");
            getLogger().severe("Veuillez installer Votifier: https://www.spigotmc.org/resources/votifier.134/");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Votifier trouvé, SHHypeVotes va s'accrocher aux événements de vote.");

        // 3. Initialiser le logger et le gestionnaire du Hype Train
        hypeLogger = new HypeLogger(this);
        hypeTrainManager = new HypeTrainManager(this);

        if (configManager.getGoals().isEmpty()) {
            getLogger().warning("Aucun palier (goal) n'est configuré dans config.yml. Le Hype Train ne démarrera pas automatiquement et les démarrages forcés pourraient ne pas fonctionner comme prévu.");
            // Ne pas désactiver le plugin, mais le Hype Train sera limité.
        }

        // 4. Enregistrer les listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new VoteListener(this), this);
        pm.registerEvents(new PlayerConnectionListener(this), this);

        // 5. Enregistrer les commandes
        HypeCommands hypeCommandsExecutor = new HypeCommands(this);
        Objects.requireNonNull(getCommand("shh")).setExecutor(hypeCommandsExecutor);
        Objects.requireNonNull(getCommand("shh")).setTabCompleter(hypeCommandsExecutor);

        // 6. Démarrer la tâche pour les lancements aléatoires
        hypeTrainManager.scheduleRandomHypeTrainAttempt();

        getLogger().info("SHHypeVotes a été activé avec succès !");
        getLogger().info("Développé par Cry_Legende.");
    }

    @Override
    public void onDisable() {
        if (hypeTrainManager != null) {
            hypeTrainManager.stopCurrentHypeTrain(false, true); // Arrêter proprement, pas de récompenses si disable.
            hypeTrainManager.cancelScheduledTasks();
        }
        getLogger().info("SHHypeVotes a été désactivé.");
    }

    public static SHHypeVotes getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HypeTrainManager getHypeTrainManager() {
        return hypeTrainManager;
    }

    public HypeLogger getHypeLogger() {
        return hypeLogger;
    }

    public void logInfo(String message) {
        getLogger().info(message);
    }

    public void logWarn(String message) {
        getLogger().warning(message);
    }

    public void logSevere(String message) {
        getLogger().severe(message);
    }

    // Méthode utilitaire pour envoyer des messages colorés à la console
    public void consoleMessage(String message) {
        getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}