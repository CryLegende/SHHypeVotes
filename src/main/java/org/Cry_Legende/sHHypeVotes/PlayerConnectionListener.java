package org.Cry_Legende.sHHypeVotes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final SHHypeVotes plugin;
    private final HypeTrainManager hypeTrainManager;

    public PlayerConnectionListener(SHHypeVotes plugin) {
        this.plugin = plugin;
        this.hypeTrainManager = plugin.getHypeTrainManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hypeTrainManager != null && hypeTrainManager.isActive() && plugin.getConfigManager().bossBarEnabled) {
            hypeTrainManager.addPlayerToBossBar(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (hypeTrainManager != null) {
            hypeTrainManager.removePlayerFromBossBar(player);
        }
    }
}