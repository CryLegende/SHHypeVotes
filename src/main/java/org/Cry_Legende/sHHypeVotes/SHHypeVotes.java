package org.Cry_Legende.sHHypeVotes;

import org.Cry_Legende.sHHypeVotes.network.NetBus;
import org.Cry_Legende.sHHypeVotes.network.ShNetworkBus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public class SHHypeVotes extends JavaPlugin {

    private static SHHypeVotes instance;
    private ConfigManager configManager;
    private HypeTrainManager hypeTrainManager;
    private HypeLogger hypeLogger;
    // Mode réseau (SHNetwork présent) : un seul serveur mène le Hype Train ; null sur un serveur seul
    private HypeNetwork network;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Charger la configuration
        configManager = new ConfigManager(this); // Crée les fichiers par défaut si besoin
        // Recharger une fois pour s'assurer que toutes les valeurs par défaut sont bien lues après création
        configManager.reloadConfigs();


        // 2. Initialiser le logger et le gestionnaire du Hype Train
        hypeLogger = new HypeLogger(this);
        NetBus bus = openNetwork();
        hypeTrainManager = new HypeTrainManager(this, bus != null);
        if (bus != null) {
            network = new HypeNetwork(this, bus, configManager.networkChannel);
            hypeTrainManager.setNetwork(network);
        }

        if (configManager.getGoals().isEmpty()) {
            getLogger().warning("Aucun palier (goal) n'est configuré dans config.yml. Le Hype Train ne démarrera pas automatiquement et les démarrages forcés pourraient ne pas fonctionner comme prévu.");
            // Ne pas désactiver le plugin, mais le Hype Train sera limité.
        }

        // 3. Enregistrer les listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new VoteListener(this), this);
        pm.registerEvents(new PlayerConnectionListener(this), this);

        // 4. Enregistrer les commandes
        HypeCommands hypeCommandsExecutor = new HypeCommands(this);
        Objects.requireNonNull(getCommand("shh")).setExecutor(hypeCommandsExecutor);
        Objects.requireNonNull(getCommand("shh")).setTabCompleter(hypeCommandsExecutor);

        // 5. Démarrer la tâche pour les lancements aléatoires (en mode réseau : celle du meneur, une fois désigné)
        if (network != null) {
            network.enable();
            getLogger().info("SHNetwork détecté : un seul Hype Train pour tout le réseau, mené par un seul serveur.");
        } else {
            hypeTrainManager.scheduleRandomHypeTrainAttempt();
        }

        getLogger().info("SHHypeVotes a été activé avec succès !");
        getLogger().info("Développé par Cry_Legende.");
    }

    /**
     * Mode réseau si SHNetwork est installé et reseau.actif vrai. Les classes qui touchent à SHNetwork ne sont résolues
     * qu'ici : sans lui, rien n'est chargé et le plugin garde son fonctionnement d'origine.
     */
    private NetBus openNetwork() {
        if (!configManager.networkEnabled || !getServer().getPluginManager().isPluginEnabled("SHNetwork")) {
            return null;
        }
        try {
            return ShNetworkBus.open();
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, Hype Train propre à ce serveur", e);
            return null;
        }
    }

    @Override
    public void onDisable() {
        if (network != null) {
            // Le Hype en cours reste en base : le serveur qui devient meneur le reprend
            network.disable();
            if (hypeTrainManager != null) {
                hypeTrainManager.cancelScheduledTasks();
                hypeTrainManager.mirrorBarOff();
            }
            network = null;
            getLogger().info("SHHypeVotes a été désactivé.");
            return;
        }
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

    /** Mode réseau, ou null sur un serveur seul. */
    HypeNetwork getNetwork() {
        return network;
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