package org.Cry_Legende.sHHypeVotes.network;

import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ce dont le Hype Train en réseau a besoin de SHNetwork. Seule {@link ShNetworkBus} connaît SHNetwork : sans lui, aucune
 * classe qui touche à son API n'est chargée.
 */
public interface NetBus {

    /** Candidature à une tâche de meneur. */
    interface Leader {

        boolean isLeader();

        void release();
    }

    /** Joueur connecté quelque part sur le réseau. */
    record Online(UUID uuid, String name, String server) {
    }

    /** Joueur connecté sur le réseau (pseudo sans tenir compte de la casse), ou null. */
    Online find(String name);

    /** Joueurs connectés sur tout le réseau. */
    int onlineCount();

    /** Publie sur un canal, à destination des autres serveurs. Ne bloque jamais. */
    void publish(String channel, JsonObject message);

    /** Écoute un canal ; le gestionnaire est appelé sur le fil principal. */
    void listen(String channel, Consumer<JsonObject> handler);

    /** Se porte candidat pour une tâche qui ne tourne que sur un serveur à la fois. */
    Leader leader(String task, Runnable onAcquire, Runnable onLose);
}
