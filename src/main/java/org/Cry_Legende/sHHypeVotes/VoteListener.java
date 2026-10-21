package org.Cry_Legende.sHHypeVotes;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
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
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String playerName = vote.getUsername();
        // String serviceName = vote.getServiceName(); // Non utilisé pour l'instant mais disponible

        if (hypeTrainManager == null) {
            plugin.logSevere("HypeTrainManager est null. Impossible de traiter le vote de " + playerName + ".");
            return;
        }
        // plugin.logInfo("Vote reçu de " + playerName + ". Traitement par SHHypeVotes..."); // Un peu verbeux si beaucoup de votes

        Player player = Bukkit.getPlayerExact(playerName);
        hypeTrainManager.handleVote(player);
    }
}