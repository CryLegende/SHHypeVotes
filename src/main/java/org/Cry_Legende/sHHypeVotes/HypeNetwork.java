package org.Cry_Legende.sHHypeVotes;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHHypeVotes.network.NetBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hype Train en mode réseau (SHNetwork présent) : un seul serveur, le meneur, fait tourner le Hype Train de tout le
 * réseau. Les autres lui transmettent les votes qu'ils reçoivent et affichent ce qu'il publie (annonces, sons, barre
 * de boss). Le Hype en cours est gardé en base (shh_state, shh_voters) : un nouveau meneur le reprend là où il en était.
 */
final class HypeNetwork {

    private static final String LEADER_TASK = "shhypevotes";

    private final SHHypeVotes plugin;
    private final NetBus bus;
    private final String channel;
    private NetBus.Leader leader;

    HypeNetwork(SHHypeVotes plugin, NetBus bus, String channel) {
        this.plugin = plugin;
        this.bus = bus;
        this.channel = channel;
    }

    void enable() {
        bus.listen(channel, this::onMessage);
        leader = bus.leader(LEADER_TASK, this::onAcquire, this::onLose);
    }

    /** Arrêt du plugin : le Hype en cours reste en base pour le prochain meneur, rien n'est annoncé. */
    void disable() {
        if (isOwner()) {
            plugin.getHypeTrainManager().silentStop();
        }
        if (leader != null) {
            leader.release();
            leader = null;
        }
    }

    boolean isOwner() {
        NetBus.Leader current = leader;
        return current != null && current.isLeader();
    }

    int onlineCount() {
        return bus.onlineCount();
    }

    private HypeTrainManager manager() {
        return plugin.getHypeTrainManager();
    }

    // ------------------------------------------------------------------ meneur

    private void onAcquire() {
        plugin.logInfo("Ce serveur mène le Hype Train pour tout le réseau.");
        manager().clearMirror();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HypeStateStore.Saved saved;
            try {
                saved = manager().stateStore().read();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "État du Hype Train illisible, aucun Hype repris", e);
                saved = new HypeStateStore.Saved(false, 0, 0, java.util.Map.of());
            }
            HypeStateStore.Saved state = saved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isOwner()) {
                    manager().resumeFrom(state);
                }
            });
        });
    }

    private void onLose() {
        plugin.logInfo("Ce serveur ne mène plus le Hype Train.");
        manager().silentStop();
    }

    // ------------------------------------------------------------------ envois

    /** Vote reçu par SHVotesReward sur ce serveur : compté ici si ce serveur mène, sinon transmis au meneur. */
    void vote(String name, Player local) {
        UUID uuid = null;
        String exact = name;
        boolean online = false;
        if (local != null) {
            uuid = local.getUniqueId();
            exact = local.getName();
            online = true;
        } else {
            NetBus.Online found = bus.find(name);
            if (found != null) {
                uuid = found.uuid();
                exact = found.name();
                online = true;
            }
        }
        if (isOwner()) {
            manager().handleVote(uuid, exact, online);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "vote");
        o.addProperty("name", exact);
        if (uuid != null) {
            o.addProperty("uuid", uuid.toString());
        }
        o.addProperty("online", online);
        bus.publish(channel, o);
    }

    /** /shh start, stop ou end lancé sur un serveur qui ne mène pas : la demande part au meneur. */
    void command(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "cmd");
        o.addProperty("action", action);
        bus.publish(channel, o);
    }

    void line(String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "line");
        o.addProperty("text", text);
        bus.publish(channel, o);
    }

    void sound(String name, float volume, float pitch) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "sound");
        o.addProperty("name", name);
        o.addProperty("volume", volume);
        o.addProperty("pitch", pitch);
        bus.publish(channel, o);
    }

    void bar(String title, double progress, String color, String style) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "bar");
        o.addProperty("title", title);
        o.addProperty("progress", progress);
        o.addProperty("color", color);
        o.addProperty("style", style);
        bus.publish(channel, o);
    }

    void barOff() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "bar-off");
        bus.publish(channel, o);
    }

    void state(boolean active, int votes, long endsAt, int goalThreshold, int voters) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "state");
        o.addProperty("active", active);
        o.addProperty("votes", votes);
        o.addProperty("endsAt", endsAt);
        o.addProperty("goal", goalThreshold);
        o.addProperty("voters", voters);
        bus.publish(channel, o);
    }

    void message(UUID target, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "msg");
        o.addProperty("uuid", target.toString());
        o.addProperty("text", text);
        bus.publish(channel, o);
    }

    // ------------------------------------------------------------------ réception

    private void onMessage(JsonObject o) {
        String type = o.has("type") ? o.get("type").getAsString() : "";
        boolean owner = isOwner();
        try {
            switch (type) {
                case "vote" -> {
                    if (owner) {
                        UUID uuid = o.has("uuid") ? UUID.fromString(o.get("uuid").getAsString()) : null;
                        manager().handleVote(uuid, o.get("name").getAsString(), o.get("online").getAsBoolean());
                    }
                }
                case "cmd" -> {
                    if (owner) {
                        switch (o.get("action").getAsString()) {
                            case "start" -> manager().startHypeTrain(true);
                            case "stop" -> manager().stopCurrentHypeTrain(false, true);
                            case "end" -> manager().stopCurrentHypeTrain(true, true);
                            default -> {
                            }
                        }
                    }
                }
                case "line" -> {
                    if (!owner) {
                        Bukkit.broadcastMessage(o.get("text").getAsString());
                    }
                }
                case "sound" -> {
                    if (!owner) {
                        manager().playSoundLocal(o.get("name").getAsString(), o.get("volume").getAsFloat(), o.get("pitch").getAsFloat());
                    }
                }
                case "bar" -> {
                    if (!owner) {
                        manager().mirrorBar(o.get("title").getAsString(), o.get("progress").getAsDouble(), o.get("color").getAsString(),
                            o.get("style").getAsString());
                    }
                }
                case "bar-off" -> {
                    if (!owner) {
                        manager().mirrorBarOff();
                    }
                }
                case "state" -> {
                    if (!owner) {
                        manager().mirrorState(o.get("active").getAsBoolean(), o.get("votes").getAsInt(), o.get("endsAt").getAsLong(),
                            o.get("goal").getAsInt(), o.get("voters").getAsInt());
                    }
                }
                case "msg" -> {
                    Player player = Bukkit.getPlayer(UUID.fromString(o.get("uuid").getAsString()));
                    if (player != null) {
                        player.sendMessage(o.get("text").getAsString());
                    }
                }
                default -> {
                    // type inconnu : message d'une version plus récente, ignoré
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Message réseau du Hype Train illisible : " + o, e);
        }
    }
}
