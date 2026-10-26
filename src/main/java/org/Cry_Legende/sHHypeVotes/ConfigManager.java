package org.Cry_Legende.sHHypeVotes;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigManager {

    private final SHHypeVotes plugin;
    private FileConfiguration config;
    private File configFile;
    private FileConfiguration messages;
    private File messagesFile;

    // General settings
    public int minPlayersOnlineForRandomStart;
    public int randomStartIntervalMinMinutes;
    public int randomStartIntervalMaxMinutes;
    public int hypeDurationSeconds;
    public String countdownFormat;
    public boolean onlyCountOnlinePlayerVotes;

    // Logging settings
    public boolean loggingEnabled;
    public boolean loggingIncludeVoterList;

    // Sound settings
    public static class SoundEffect {
        public final boolean enabled;
        public final String soundName;
        public final float volume;
        public final float pitch;

        public SoundEffect(boolean enabled, String soundName, float volume, float pitch) {
            this.enabled = enabled;
            this.soundName = soundName;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public SoundEffect soundHypeStart;
    public SoundEffect soundGoalReach;
    public SoundEffect soundHypeEnd;

    // Goals
    private List<Goal> goalsList;

    // BossBar settings
    public boolean bossBarEnabled;
    public int bossBarUpdateIntervalTicks;
    public String bossBarFormat;
    public String bossBarStyle;
    public String bossBarColor;


    public ConfigManager(SHHypeVotes plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public void reloadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        try (InputStream defaultConfigStream = plugin.getResource("config.yml")) {
            if (defaultConfigStream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.logWarn("Lecture des valeurs par défaut de config.yml impossible: " + e.getMessage());
        }

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        FileConfiguration bundledMessages = loadBundledMessages();
        if (bundledMessages != null) {
            messages.setDefaults(bundledMessages);
        }
        parseConfigValues();
    }

    /** Messages par défaut embarqués dans le jar (flux refermé), ou null s'ils sont illisibles. */
    private FileConfiguration loadBundledMessages() {
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }


    private void parseConfigValues() {
        // General
        minPlayersOnlineForRandomStart = config.getInt("general.min-players-online-for-random-start", 10);
        randomStartIntervalMinMinutes = config.getInt("general.random-start-interval-minutes.min", 30);
        randomStartIntervalMaxMinutes = config.getInt("general.random-start-interval-minutes.max", 70);

        if (randomStartIntervalMinMinutes <= 0) {
            plugin.logWarn("'general.random-start-interval-minutes.min' doit être positif. Réglage sur 1.");
            randomStartIntervalMinMinutes = 1;
        }
        if (randomStartIntervalMaxMinutes <= randomStartIntervalMinMinutes) {
            plugin.logWarn("'general.random-start-interval-minutes.max' (" + randomStartIntervalMaxMinutes +
                ") doit être supérieur à 'min' (" + randomStartIntervalMinMinutes +
                "). Ajustement de max à min + 30 minutes.");
            randomStartIntervalMaxMinutes = randomStartIntervalMinMinutes + 30;
        }

        hypeDurationSeconds = config.getInt("general.hype-duration-seconds", 1800);
        countdownFormat = config.getString("general.countdown-format", "mm:ss");
        onlyCountOnlinePlayerVotes = config.getBoolean("general.only-count-online-player-votes", false);

        // Logging
        loggingEnabled = config.getBoolean("logging.enabled", true);
        loggingIncludeVoterList = config.getBoolean("logging.include-voter-list", true);

        // Sounds
        soundHypeStart = loadSoundEffect("sounds.hype-start", "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
        soundGoalReach = loadSoundEffect("sounds.goal-reach", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.2f);
        soundHypeEnd = loadSoundEffect("sounds.hype-end", "ENTITY_FIREWORK_ROCKET_LARGE_BLAST", 1.0f, 0.8f);


        // Goals
        goalsList = new ArrayList<>();
        ConfigurationSection goalsSection = config.getConfigurationSection("goals");
        if (goalsSection != null) {
            for (String key : goalsSection.getKeys(false)) {
                ConfigurationSection goalSection = goalsSection.getConfigurationSection(key);
                if (goalSection != null) {
                    String displayName = goalSection.getString("displayName", "&cUnnamed Goal");
                    int threshold = goalSection.getInt("threshold");
                    int timeExtensionSeconds = goalSection.getInt("time-extension-seconds", 0); // NOUVEAU: Lire l'extension de temps
                    List<String> commands = goalSection.getStringList("commands");
                    Optional<String> themeColor = Optional.ofNullable(goalSection.getString("bossbar-theme-color"));
                    goalsList.add(new Goal(key, displayName, threshold, commands, themeColor, timeExtensionSeconds)); // NOUVEAU: Ajouter au constructeur
                }
            }
            goalsList.sort(Comparator.comparingInt(Goal::threshold));
        } else {
            plugin.logWarn("Aucun palier (goal) n'est défini dans config.yml!");
        }

        // BossBar
        bossBarEnabled = config.getBoolean("bossbar.enabled", true);
        bossBarUpdateIntervalTicks = config.getInt("bossbar.update-interval-ticks", 20);
        bossBarFormat = config.getString("bossbar.format", "&b&lHYPE TRAIN &f&l⚡ &7Votes: &e%current_votes%&7/&e%goal_votes% &7(&6%goal_name%&7) &7- Temps: &c%time_remaining%");
        bossBarStyle = config.getString("bossbar.style", "SOLID").toUpperCase();
        bossBarColor = config.getString("bossbar.color", "GREEN").toUpperCase();
    }

    private SoundEffect loadSoundEffect(String path, String defaultSound, float defaultVolume, float defaultPitch) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        String name = config.getString(path + ".sound", defaultSound);
        float volume = (float) config.getDouble(path + ".volume", defaultVolume);
        float pitch = (float) config.getDouble(path + ".pitch", defaultPitch);
        return new SoundEffect(enabled, name, volume, pitch);
    }

    public String getMessage(String path) {
        String message = messages.getString(path);
        if (message == null) {
            plugin.logWarn("Message non trouvé dans messages.yml: " + path + ". Utilisation d'un message par défaut.");
            FileConfiguration defaultMessages = loadBundledMessages();
            if (defaultMessages != null) {
                message = defaultMessages.getString(path);
            }
            if (message == null) {
                return ChatColor.RED + "Message manquant: " + path;
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public List<String> getMessageList(String path) {
        List<String> messageList = messages.getStringList(path);
        if (messageList.isEmpty() && messages.contains(path) && messages.isString(path)) {
            messageList.add(messages.getString(path));
        }
        if (messageList.isEmpty()) {
            plugin.logWarn("Liste de messages non trouvée ou vide dans messages.yml: " + path + ". Utilisation d'un message par défaut.");
            List<String> fallbackList = new ArrayList<>();
            FileConfiguration defaultMessages = loadBundledMessages();
            if (defaultMessages != null) {
                fallbackList = defaultMessages.getStringList(path);
                if (fallbackList.isEmpty() && defaultMessages.contains(path) && defaultMessages.isString(path)) {
                    fallbackList.add(defaultMessages.getString(path));
                }
            }
            if (fallbackList.isEmpty()) {
                fallbackList.add(ChatColor.RED + "Liste de messages manquante: " + path);
            }
            messageList = fallbackList;
        }
        return messageList.stream()
            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
            .collect(Collectors.toList());
    }

    public String getPrefixedMessage(String path) {
        String prefix = getMessage("prefix"); // Récupère le préfixe (qui peut être vide)
        return prefix + getMessage(path); // Concatène, si le préfixe est vide, ça ne change rien.
    }

    public List<Goal> getGoals() {
        return goalsList;
    }
}
