package org.Cry_Legende.sHHypeVotes;

import fr.topicfly.shvotesreward.api.SHVoteEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class VoteListener implements Listener {

    private final SHHypeVotes plugin;
    private final HypeTrainManager hypeTrainManager;

    public VoteListener(SHHypeVotes plugin) {
        this.plugin = plugin;
        this.hypeTrainManager = plugin.getHypeTrainManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVote(SHVoteEvent event) {
        String playerName = event.getUsername();

        if (hypeTrainManager == null) {
            plugin.logSevere("HypeTrainManager est null. Impossible de traiter le vote de " + playerName + ".");
            return;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        hypeTrainManager.handleVote(player);
    }
}
