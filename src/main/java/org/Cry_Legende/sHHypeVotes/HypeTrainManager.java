package org.Cry_Legende.sHHypeVotes;

import fr.topicfly.shvotesreward.SHVotesReward;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HypeTrainManager {

    private final HypeStateStore stateStore;

    private final SHHypeVotes plugin;
    private final ConfigManager configManager;

    private boolean isActive;
    private int currentVotes;
    private long endTimeMillis;
    private BossBar bossBar;
    private BukkitTask hypeTrainTask;
    private BukkitTask randomHypeTask;
    private long nextRandomHypeAttemptTimeMillis = 0;

    private Goal currentHighestGoalReached;
    private Goal nextGoalToAnnounce;
    private Set<UUID> votersInCurrentHype;
    // Pseudo de chaque votant, pour lancer ses récompenses même s'il n'est jamais venu sur ce serveur
    private final Map<UUID, String> voterNames = new HashMap<>();
    private long startTimeMillis;
    // Mode réseau : un seul serveur mène le Hype Train, les autres l'affichent (null sur un serveur seul)
    private HypeNetwork network;
    // Mode réseau, serveur qui ne mène pas : nombre de votants annoncé par le meneur
    private int mirrorVoterCount;

    public HypeTrainManager(SHHypeVotes plugin, boolean networked) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.isActive = false;
        this.votersInCurrentHype = new HashSet<>();
        this.stateStore = new HypeStateStore(JavaPlugin.getPlugin(SHVotesReward.class).database());
        stateStore.createTables();
        stateStore.saveGoals(configManager.getGoals());
        if (networked) {
            // Mode réseau : le Hype en cours reste en base, le serveur qui mène le reprend
            stateStore.createVotersTable();
        } else {
            // Un arrêt brutal laisse le Hype actif en base : il repart à zéro avec le serveur.
            stateStore.save(false, 0, 0);
        }
    }

    void setNetwork(HypeNetwork network) {
        this.network = network;
    }

    HypeStateStore stateStore() {
        return stateStore;
    }

    /** Vrai si ce serveur mène le Hype Train : toujours sur un serveur seul. */
    private boolean leads() {
        return network == null || network.isOwner();
    }

    /** Annonce à tous les joueurs de ce serveur, et, si ce serveur mène le Hype du réseau, à ceux des autres. */
    private void broadcast(String line) {
        Bukkit.broadcastMessage(line);
        if (network != null && network.isOwner()) {
            network.line(line);
        }
    }

    private void playSoundToAll(ConfigManager.SoundEffect soundEffect) {
        if (soundEffect == null || !soundEffect.enabled) return;
        if (network != null && network.isOwner()) {
            network.sound(soundEffect.soundName, soundEffect.volume, soundEffect.pitch);
        }
        Sound sound = sound(soundEffect.soundName);
        if (sound == null) {
            plugin.logWarn("Son invalide configuré: " + soundEffect.soundName);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, soundEffect.volume, soundEffect.pitch);
        }
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
    static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    public boolean startHypeTrain(boolean forced) {
        if (isActive) {
            if (forced) plugin.logInfo(configManager.getPrefixedMessage("hype-already-active"));
            return false;
        }
        if (configManager.getGoals().isEmpty()) {
            plugin.logWarn("Impossible de démarrer le Hype Train: aucun palier n'est configuré.");
            return false;
        }

        isActive = true;
        currentVotes = 0;
        votersInCurrentHype.clear();
        voterNames.clear();
        currentHighestGoalReached = null;
        nextGoalToAnnounce = findNextGoalInternal(0);

        this.startTimeMillis = System.currentTimeMillis();
        this.endTimeMillis = this.startTimeMillis + (configManager.hypeDurationSeconds * 1000L);
        if (network != null) {
            stateStore.clearVoters();
        }
        stateStore.save(true, currentVotes, endTimeMillis);

        if (configManager.bossBarEnabled) {
            createBossBar();
        }

        announceHypeStarted();
        playSoundToAll(configManager.soundHypeStart);
        publishState();

        startTicker();

        cancelRandomHypeTask();
        nextRandomHypeAttemptTimeMillis = 0;
        return true;
    }

    /** Tâche qui fait avancer la barre de boss et termine le Hype à l'heure. */
    private void startTicker() {
        this.hypeTrainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive) {
                    cancel();
                    return;
                }
                long remainingSeconds = getRemainingSeconds();
                if (remainingSeconds <= 0) {
                    stopCurrentHypeTrain(true, false);
                    cancel();
                    return;
                }
                if (configManager.bossBarEnabled && bossBar != null) {
                    updateBossBar();
                }
            }
        }.runTaskTimer(plugin, 0L, configManager.bossBarUpdateIntervalTicks);
    }

    public void stopCurrentHypeTrain(boolean distributeRewards, boolean forcedByCommandOrDisable) {
        if (!isActive) {
            return;
        }
        boolean wasActive = isActive;
        isActive = false;

        if (hypeTrainTask != null && !hypeTrainTask.isCancelled()) {
            hypeTrainTask.cancel();
        }
        hypeTrainTask = null;

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (network != null && network.isOwner()) {
            network.barOff();
        }

        boolean rewardsActuallyGiven = false;
        if (distributeRewards && currentHighestGoalReached != null && !votersInCurrentHype.isEmpty()) {
            rewardsActuallyGiven = true;
        }

        announceHypeEnded(rewardsActuallyGiven);

        if (rewardsActuallyGiven) {
            plugin.logInfo("Distribution des récompenses du Hype Train pour le palier: " + ChatColor.stripColor(currentHighestGoalReached.displayName()));
            plugin.logInfo("Votants participant (" + votersInCurrentHype.size() + "):");

            for (UUID voterUUID : votersInCurrentHype) {
                String playerName = voterNames.get(voterUUID);
                if (playerName == null) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(voterUUID);
                    playerName = offlinePlayer.getName();
                }

                if (playerName == null) {
                    plugin.logWarn("Impossible d'obtenir le nom du joueur pour l'UUID: " + voterUUID + ". Récompenses pour ce joueur ignorées.");
                    continue;
                }

                for (String commandTemplate : currentHighestGoalReached.commands()) {
                    String commandToExecute = commandTemplate.replace("%player%", playerName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                }
            }
            plugin.logInfo("Distribution des récompenses terminée.");
        }


        // Log natif complet
        if (wasActive && plugin.getHypeLogger() != null) {
            long actualDurationMs = System.currentTimeMillis() - startTimeMillis;
            plugin.getHypeLogger().logHypeTrainEnd(
                currentVotes,
                votersInCurrentHype,
                currentHighestGoalReached,
                configManager.getGoals(),
                configManager.hypeDurationSeconds,
                actualDurationMs,
                rewardsActuallyGiven,
                Bukkit.getOnlinePlayers().size(),
                forcedByCommandOrDisable
            );
        }

        if (wasActive) {
            playSoundToAll(configManager.soundHypeEnd);
        }

        votersInCurrentHype.clear();
        voterNames.clear();
        currentVotes = 0;
        currentHighestGoalReached = null;
        nextGoalToAnnounce = null;
        if (network != null) {
            stateStore.clearVoters();
        }
        stateStore.save(false, 0, 0);
        publishState();

        scheduleRandomHypeTrainAttempt();
    }

    public void handleVote(Player voter) {
        handleVote(voter != null ? voter.getUniqueId() : null, voter != null ? voter.getName() : null, voter != null);
    }

    /**
     * Vote compté par le serveur qui mène le Hype.
     *
     * @param uuid   votant, ou null s'il n'est connecté nulle part
     * @param name   pseudo du votant
     * @param online vrai si le votant est connecté (sur ce serveur ou, en mode réseau, sur un autre)
     */
    public void handleVote(UUID uuid, String name, boolean online) {
        String playerNameForLog = online && name != null ? name : "Anonyme";

        if (!isActive) {
            plugin.logInfo("Vote de " + playerNameForLog + " reçu hors Hype Train.");
            return;
        }

        if (configManager.onlyCountOnlinePlayerVotes && !online) {
            plugin.logInfo("Vote de " + playerNameForLog + " ignoré (joueur hors-ligne, only-count-online-player-votes activé).");
            return;
        }

        currentVotes++;
        if (online && uuid != null) {
            if (votersInCurrentHype.add(uuid) && network != null) {
                stateStore.addVoter(uuid, name);
            }
            voterNames.put(uuid, name);
            if (!configManager.getMessage("vote-counted-in-hype").isEmpty()) { // Check si le message n'est pas vide
                String text = formatMessage(configManager.getPrefixedMessage("vote-counted-in-hype"));
                Player voter = Bukkit.getPlayer(uuid);
                if (voter != null) {
                    voter.sendMessage(text);
                } else if (network != null) {
                    network.message(uuid, text);
                }
            }
        }
        plugin.logInfo("Vote de " + playerNameForLog + " compté. Total: " + currentVotes);

        checkGoals();
        stateStore.save(true, currentVotes, endTimeMillis);
        if (configManager.bossBarEnabled && bossBar != null) {
            updateBossBar();
        }
        publishState();
    }

    /** Mode réseau : le meneur annonce l'état du Hype aux autres serveurs (commande /shhypevotes status). */
    private void publishState() {
        if (network != null && network.isOwner()) {
            network.state(isActive, currentVotes, endTimeMillis,
                currentHighestGoalReached != null ? currentHighestGoalReached.threshold() : -1, votersInCurrentHype.size());
        }
    }

    // ------------------------------------------------------------------ mode réseau

    /**
     * Ce serveur devient meneur : il reprend le Hype gardé en base. Un Hype dont l'heure de fin est passée (serveurs
     * arrêtés entre-temps) est terminé tout de suite, avec ses récompenses.
     */
    void resumeFrom(HypeStateStore.Saved saved) {
        if (isActive) {
            return;
        }
        if (!saved.active()) {
            scheduleRandomHypeTrainAttempt();
            return;
        }
        isActive = true;
        currentVotes = saved.votes();
        endTimeMillis = saved.endsAt();
        startTimeMillis = System.currentTimeMillis();
        votersInCurrentHype.clear();
        voterNames.clear();
        votersInCurrentHype.addAll(saved.voters().keySet());
        voterNames.putAll(saved.voters());
        currentHighestGoalReached = null;
        for (Goal goal : configManager.getGoals()) {
            if (currentVotes >= goal.threshold()) {
                currentHighestGoalReached = goal;
            } else {
                break;
            }
        }
        nextGoalToAnnounce = findNextGoalInternal(currentHighestGoalReached != null ? currentHighestGoalReached.threshold() : 0);
        cancelRandomHypeTask();
        nextRandomHypeAttemptTimeMillis = 0;
        plugin.logInfo("Hype Train repris : " + currentVotes + " vote(s), " + votersInCurrentHype.size() + " votant(s).");
        if (endTimeMillis <= System.currentTimeMillis()) {
            stopCurrentHypeTrain(true, false);
            return;
        }
        if (configManager.bossBarEnabled) {
            createBossBar();
        }
        publishState();
        startTicker();
    }

    /** Ce serveur cesse de mener : le Hype s'arrête ici sans annonce ni récompense, il continue sur le nouveau meneur. */
    void silentStop() {
        cancelRandomHypeTask();
        nextRandomHypeAttemptTimeMillis = 0;
        if (hypeTrainTask != null && !hypeTrainTask.isCancelled()) {
            hypeTrainTask.cancel();
        }
        hypeTrainTask = null;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        isActive = false;
        currentVotes = 0;
        votersInCurrentHype.clear();
        voterNames.clear();
        currentHighestGoalReached = null;
        nextGoalToAnnounce = null;
    }

    /** Ce serveur devient meneur : l'affichage reçu de l'ancien meneur est effacé avant la reprise. */
    void clearMirror() {
        silentStop();
        mirrorVoterCount = 0;
    }

    /** Serveur qui ne mène pas : état annoncé par le meneur, pour /shhypevotes status. */
    void mirrorState(boolean active, int votes, long endsAt, int goalThreshold, int voters) {
        isActive = active;
        currentVotes = votes;
        endTimeMillis = endsAt;
        mirrorVoterCount = voters;
        currentHighestGoalReached = null;
        for (Goal goal : configManager.getGoals()) {
            if (goal.threshold() == goalThreshold) {
                currentHighestGoalReached = goal;
            }
        }
        nextGoalToAnnounce = findNextGoalInternal(currentHighestGoalReached != null ? currentHighestGoalReached.threshold() : 0);
        if (!active) {
            mirrorBarOff();
        }
    }

    /** Serveur qui ne mène pas : barre de boss du meneur, recopiée pour les joueurs d'ici. */
    void mirrorBar(String title, double progress, String color, String style) {
        if (!configManager.bossBarEnabled) {
            return;
        }
        BarColor barColor;
        BarStyle barStyle;
        try {
            barColor = BarColor.valueOf(color);
            barStyle = BarStyle.valueOf(style);
        } catch (IllegalArgumentException e) {
            barColor = BarColor.GREEN;
            barStyle = BarStyle.SOLID;
        }
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, barColor, barStyle);
            Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        }
        bossBar.setTitle(title);
        bossBar.setColor(barColor);
        bossBar.setStyle(barStyle);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    void mirrorBarOff() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    /** Son joué par le meneur, joué ici pour les joueurs de ce serveur. */
    void playSoundLocal(String name, float volume, float pitch) {
        Sound sound = sound(name);
        if (sound == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Votants du Hype en cours : ceux de ce serveur s'il mène, sinon le nombre annoncé par le meneur. */
    public int getVoterCount() {
        return leads() ? votersInCurrentHype.size() : mirrorVoterCount;
    }

    private void checkGoals() {
        Goal newlyReachedGoal = null;
        for (Goal goal : configManager.getGoals()) {
            if (currentVotes >= goal.threshold()) {
                if (currentHighestGoalReached == null || goal.threshold() > currentHighestGoalReached.threshold()) {
                    newlyReachedGoal = goal;
                }
            } else {
                break;
            }
        }

        if (newlyReachedGoal != null && (currentHighestGoalReached == null || !newlyReachedGoal.key().equals(currentHighestGoalReached.key()))) {
            currentHighestGoalReached = newlyReachedGoal;
            nextGoalToAnnounce = findNextGoalInternal(currentHighestGoalReached.threshold());

            // NOUVEAU: Logique d'extension de temps
            int timeExtensionSeconds = newlyReachedGoal.timeExtensionSeconds();
            if (timeExtensionSeconds > 0) {
                this.endTimeMillis += timeExtensionSeconds * 1000L;
                plugin.logInfo("Palier '" + newlyReachedGoal.key() + "' atteint. Ajout de " + timeExtensionSeconds + " secondes au Hype Train.");
            }

            announceGoalReached(currentHighestGoalReached, nextGoalToAnnounce, timeExtensionSeconds);
            playSoundToAll(configManager.soundGoalReach);

            if (configManager.bossBarEnabled && bossBar != null && currentHighestGoalReached.bossbarThemeColor().isPresent()) {
                try {
                    BarColor newColor = BarColor.valueOf(currentHighestGoalReached.bossbarThemeColor().get().toUpperCase());
                    bossBar.setColor(newColor);
                } catch (IllegalArgumentException e) {
                    plugin.logWarn("Couleur de bossbar invalide '" + currentHighestGoalReached.bossbarThemeColor().get() + "' pour le palier '" + currentHighestGoalReached.key() + "'.");
                }
            }
        }
        if (currentHighestGoalReached == null && nextGoalToAnnounce == null) { // Assurer que nextGoalToAnnounce est initialisé si aucun palier atteint
            nextGoalToAnnounce = findNextGoalInternal(0);
        }
    }

    private Goal findNextGoalInternal(int currentThreshold) {
        return configManager.getGoals().stream()
            .filter(g -> g.threshold() > currentThreshold)
            .findFirst()
            .orElse(null);
    }

    private void createBossBar() {
        if (!configManager.bossBarEnabled) return;
        Goal initialDisplayGoal = nextGoalToAnnounce != null ? nextGoalToAnnounce :
            (configManager.getGoals().isEmpty() ? null : configManager.getGoals().get(0));
        if (initialDisplayGoal == null && !configManager.getGoals().isEmpty()) { // Fallback si nextGoalToAnnounce est null
            initialDisplayGoal = configManager.getGoals().get(0);
        }

        BarColor color;
        try {
            color = BarColor.valueOf(configManager.bossBarColor);
        } catch (IllegalArgumentException e) {
            plugin.logWarn("Couleur de bossbar par défaut invalide: " + configManager.bossBarColor);
            color = BarColor.GREEN;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(configManager.bossBarStyle);
        } catch (IllegalArgumentException e) {
            plugin.logWarn("Style de bossbar par défaut invalide: " + configManager.bossBarStyle);
            style = BarStyle.SOLID;
        }

        this.bossBar = Bukkit.createBossBar("Chargement...", color, style); // Titre temporaire
        this.bossBar.setProgress(0.0);
        Bukkit.getOnlinePlayers().forEach(this.bossBar::addPlayer); // Ajouter joueurs existants
        updateBossBar(); // Mettre à jour avec le titre et progrès corrects
    }

    private void updateBossBar() {
        if (bossBar == null || !configManager.bossBarEnabled || !isActive) return;
        Goal displayGoalForBar = nextGoalToAnnounce; // Viser le prochain
        if (displayGoalForBar == null)
            displayGoalForBar = currentHighestGoalReached; // Si pas de prochain, afficher le dernier atteint
        if (displayGoalForBar == null && !configManager.getGoals().isEmpty())
            displayGoalForBar = configManager.getGoals().get(0); // Si aucun atteint, afficher le premier

        if (displayGoalForBar == null) { // Si toujours aucun palier (config vide)
            bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', "&cErreur: Aucun palier configuré !"));
            bossBar.setProgress(0);
            return;
        }

        bossBar.setTitle(formatMessage(configManager.bossBarFormat, displayGoalForBar, findNextGoalInternal(displayGoalForBar.threshold()), null, 0));
        double progress = displayGoalForBar.threshold() > 0 ? Math.min(1.0, (double) currentVotes / displayGoalForBar.threshold()) : (currentVotes > 0 ? 1.0 : 0.0);
        bossBar.setProgress(progress);
        if (network != null && network.isOwner()) {
            network.bar(bossBar.getTitle(), progress, bossBar.getColor().name(), bossBar.getStyle().name());
        }
    }

    private void announceHypeStarted() {
        Goal firstGoal = nextGoalToAnnounce != null ? nextGoalToAnnounce : (!configManager.getGoals().isEmpty() ? configManager.getGoals().get(0) : null);
        List<String> messages = configManager.getMessageList("hype-started");
        for (String line : messages) {
            broadcast(formatMessage(line, firstGoal, findNextGoalInternal(firstGoal != null ? firstGoal.threshold() : 0), null, 0));
        }
    }

    private void announceGoalReached(Goal reachedGoal, Goal nextUpcomingGoal, int timeAddedSeconds) {
        List<String> messages = configManager.getMessageList("goal-reached");

        boolean inNextGoalExistsBlock = false;
        boolean inNoNextGoalBlock = false;
        boolean inTimeAddedBlock = false; // NOUVEAU
        boolean printCurrentLine;

        for (String line : messages) {
            String trimmedLine = line.trim();
            printCurrentLine = true;

            if (trimmedLine.equals("{IF_NEXT_GOAL_EXISTS}")) {
                inNextGoalExistsBlock = true;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{ENDIF_NEXT_GOAL_EXISTS}")) {
                inNextGoalExistsBlock = false;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{IF_NO_NEXT_GOAL}")) {
                inNoNextGoalBlock = true;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{ENDIF_NO_NEXT_GOAL}")) {
                inNoNextGoalBlock = false;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{IF_TIME_ADDED}")) { // NOUVEAU
                inTimeAddedBlock = true;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{ENDIF_TIME_ADDED}")) { // NOUVEAU
                inTimeAddedBlock = false;
                printCurrentLine = false;
            } else { // Ligne de contenu
                if ((inNextGoalExistsBlock && nextUpcomingGoal == null) ||
                    (inNoNextGoalBlock && nextUpcomingGoal != null) ||
                    (inTimeAddedBlock && timeAddedSeconds <= 0)) { // NOUVEAU
                    printCurrentLine = false;
                }
            }

            if (printCurrentLine && !trimmedLine.isEmpty()) {
                broadcast(formatMessage(line, reachedGoal, nextUpcomingGoal, null, timeAddedSeconds));
            }
        }
    }

    private void announceHypeEnded(boolean rewardsWereGiven) {
        List<String> messages = configManager.getMessageList("hype-ended");

        boolean inRewardsGivenBlock = false;
        boolean inNoRewardsGivenBlock = false;
        boolean printCurrentLine;

        for (String line : messages) {
            String trimmedLine = line.trim();
            printCurrentLine = true;

            if (trimmedLine.equals("{IF_REWARDS_GIVEN}")) {
                inRewardsGivenBlock = true;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{IF_NO_REWARDS_GIVEN}")) {
                inNoRewardsGivenBlock = true;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{ENDIF_REWARDS_GIVEN}")) {
                inRewardsGivenBlock = false;
                printCurrentLine = false;
            } else if (trimmedLine.equals("{ENDIF_NO_REWARDS_GIVEN}")) {
                inNoRewardsGivenBlock = false;
                printCurrentLine = false;
            } else { // Ligne de contenu
                if (inRewardsGivenBlock && !rewardsWereGiven) {
                    printCurrentLine = false;
                }
                if (inNoRewardsGivenBlock && rewardsWereGiven) {
                    printCurrentLine = false;
                }
            }

            if (printCurrentLine && !trimmedLine.isEmpty()) {
                broadcast(formatMessage(line, null, null, currentHighestGoalReached, 0));
            }
        }
    }

    public void scheduleRandomHypeTrainAttempt() {
        cancelRandomHypeTask();
        // Mode réseau : seul le serveur qui mène le Hype en lance un au hasard
        if (!leads()) {
            nextRandomHypeAttemptTimeMillis = 0;
            return;
        }
        // Arrêt du Hype Train pendant onDisable : le plugin est déjà désactivé, aucune tâche ne peut être planifiée
        if (!plugin.isEnabled()) {
            nextRandomHypeAttemptTimeMillis = 0;
            return;
        }
        if (configManager.minPlayersOnlineForRandomStart <= 0 || configManager.randomStartIntervalMinMinutes <= 0) {
            plugin.logInfo("Démarrage aléatoire du Hype Train désactivé.");
            nextRandomHypeAttemptTimeMillis = 0;
            return;
        }
        long delayMinutes = ThreadLocalRandom.current().nextLong(configManager.randomStartIntervalMinMinutes, configManager.randomStartIntervalMaxMinutes + 1);
        long delayTicks = delayMinutes * 60 * 20;
        this.nextRandomHypeAttemptTimeMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L);
        plugin.logInfo("Prochaine tentative de Hype Train aléatoire dans " + delayMinutes + " minute(s).");

        this.randomHypeTask = new BukkitRunnable() {
            @Override
            public void run() {
                nextRandomHypeAttemptTimeMillis = 0;
                if (isActive) {
                    scheduleRandomHypeTrainAttempt();
                    return;
                }
                int online = network != null ? network.onlineCount() : Bukkit.getOnlinePlayers().size();
                if (!leads()) {
                    return;
                }
                if (online >= configManager.minPlayersOnlineForRandomStart) {
                    plugin.logInfo("Conditions remplies pour un Hype Train aléatoire (" + online + "/" + configManager.minPlayersOnlineForRandomStart + "), tentative...");
                    if (!startHypeTrain(false)) {
                        plugin.logWarn("Échec du démarrage du Hype Train aléatoire. Reprogrammation...");
                        scheduleRandomHypeTrainAttempt();
                    }
                } else {
                    plugin.logInfo("Pas assez de joueurs (" + online + "/" + configManager.minPlayersOnlineForRandomStart + ") pour un Hype aléatoire. Reprogrammation...");
                    scheduleRandomHypeTrainAttempt();
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    public void cancelScheduledTasks() {
        cancelRandomHypeTask();
        if (hypeTrainTask != null && !hypeTrainTask.isCancelled()) hypeTrainTask.cancel();
    }

    private void cancelRandomHypeTask() {
        if (randomHypeTask != null && !randomHypeTask.isCancelled()) randomHypeTask.cancel();
        randomHypeTask = null;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getCurrentVotes() {
        return currentVotes;
    }

    public Goal getCurrentHighestGoalReached() {
        return currentHighestGoalReached;
    }

    public Goal getNextGoalToAnnounce() {
        return nextGoalToAnnounce != null ? nextGoalToAnnounce : findNextGoalInternal(currentHighestGoalReached != null ? currentHighestGoalReached.threshold() : 0);
    }

    public Set<UUID> getVotersInCurrentHype() {
        return votersInCurrentHype;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public long getNextRandomHypeTaskTime() {
        return nextRandomHypeAttemptTimeMillis;
    }

    private long getRemainingSeconds() {
        if (!isActive) return 0;
        return Math.max(0, (endTimeMillis - System.currentTimeMillis()) / 1000);
    }

    public String formatTime(long totalSeconds, String formatString) {
        if (formatString == null || formatString.trim().isEmpty()) formatString = "mm:ss";
        Date date = new Date(totalSeconds * 1000L);
        SimpleDateFormat sdf;
        try {
            sdf = new SimpleDateFormat(formatString);
        } catch (IllegalArgumentException e) {
            sdf = new SimpleDateFormat("mm:ss");
        }
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(date);
    }

    private String formatMessage(String message, Goal contextGoal, Goal nextContextGoal, Goal finalContextGoal, int timeAddedSeconds) {
        if (message == null) return "";
        String result = message;

        result = result.replace("%current_votes%", String.valueOf(currentVotes))
            .replace("%total_votes_for_hype%", String.valueOf(currentVotes))
            .replace("%time_remaining%", formatTime(getRemainingSeconds(), configManager.countdownFormat))
            .replace("%duration%", formatTime(configManager.hypeDurationSeconds, configManager.countdownFormat))
            .replace("%voter_count%", String.valueOf(votersInCurrentHype.size()))
            .replace("%time_added%", formatTime(timeAddedSeconds, configManager.countdownFormat)); // NOUVEAU Placeholder

        Goal goalForPlaceholder = contextGoal;
        if (goalForPlaceholder == null && isActive) {
            goalForPlaceholder = nextGoalToAnnounce != null ? nextGoalToAnnounce :
                (!configManager.getGoals().isEmpty() ? configManager.getGoals().get(0) : null);
        }

        if (goalForPlaceholder != null) {
            result = result.replace("%goal_name%", ChatColor.translateAlternateColorCodes('&', goalForPlaceholder.displayName()))
                .replace("%goal_votes%", String.valueOf(goalForPlaceholder.threshold()));
        } else {
            result = result.replace("%goal_name%", configManager.getMessage("no-final-goal-placeholder"))
                .replace("%goal_votes%", "-");
        }

        Goal actualNextGoal = nextContextGoal;
        if (actualNextGoal == null && goalForPlaceholder != null) {
            actualNextGoal = findNextGoalInternal(goalForPlaceholder.threshold());
        }

        if (actualNextGoal != null) {
            result = result.replace("%next_goal_name%", ChatColor.translateAlternateColorCodes('&', actualNextGoal.displayName()))
                .replace("%next_goal_votes%", String.valueOf(actualNextGoal.threshold()));
        } else {
            result = result.replace("%next_goal_name%", configManager.getMessage("no-next-goal-placeholder"))
                .replace("%next_goal_votes%", "-");
        }

        if (finalContextGoal != null) {
            result = result.replace("%final_goal_name%", ChatColor.translateAlternateColorCodes('&', finalContextGoal.displayName()))
                .replace("%final_reward_display_name%", ChatColor.translateAlternateColorCodes('&', finalContextGoal.displayName()));
        } else if (result.contains("%final_goal_name%")) {
            result = result.replace("%final_goal_name%", configManager.getMessage("no-final-goal-placeholder"))
                .replace("%final_reward_display_name%", configManager.getMessage("no-final-reward-placeholder"));
        }

        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private String formatMessage(String message) {
        return formatMessage(message, null, null, null, 0);
    }

    public void reload() {
        cancelRandomHypeTask();
        stateStore.saveGoals(configManager.getGoals());
        if (isActive) {
            refreshGoals();
            // Nouvel intervalle de la barre de boss : la tâche en cours est relancée
            if (hypeTrainTask != null && !hypeTrainTask.isCancelled()) {
                hypeTrainTask.cancel();
                startTicker();
            }
        }
        if (isActive && bossBar != null) {
            if (!configManager.bossBarEnabled) {
                bossBar.removeAll();
                bossBar = null;
            } else {
                try {
                    bossBar.setColor(BarColor.valueOf(configManager.bossBarColor));
                } catch (Exception ignored) {
                }
                try {
                    bossBar.setStyle(BarStyle.valueOf(configManager.bossBarStyle));
                } catch (Exception ignored) {
                }
                updateBossBar();
            }
        } else if (isActive && bossBar == null && configManager.bossBarEnabled) {
            createBossBar();
        }
        scheduleRandomHypeTrainAttempt();
        plugin.logInfo("HypeTrainManager a pris en compte la configuration rechargée.");
    }

    /**
     * Rattache le Hype en cours aux paliers rechargés : le palier atteint est repris par sa clé (ses commandes de
     * récompense sont celles de la nouvelle configuration), ou déduit des votes s'il n'existe plus.
     */
    private void refreshGoals() {
        Goal reached = null;
        if (currentHighestGoalReached != null) {
            String key = currentHighestGoalReached.key();
            for (Goal goal : configManager.getGoals()) {
                if (goal.key().equals(key)) {
                    reached = goal;
                }
            }
        }
        if (reached == null) {
            for (Goal goal : configManager.getGoals()) {
                if (currentVotes >= goal.threshold()) {
                    reached = goal;
                } else {
                    break;
                }
            }
        }
        currentHighestGoalReached = reached;
        nextGoalToAnnounce = findNextGoalInternal(reached != null ? reached.threshold() : 0);
    }

    public void addPlayerToBossBar(Player player) {
        if (isActive && bossBar != null && configManager.bossBarEnabled) {
            bossBar.addPlayer(player);
        }
    }

    public void removePlayerFromBossBar(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }
}
