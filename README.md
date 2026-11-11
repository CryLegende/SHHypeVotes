<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHHypeVotes

*Un système de Hype Train pour les votes avec récompenses par paliers.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Un système de Hype Train pour les votes avec récompenses par paliers.

Le site affiche le Hype en cours : le plugin écrit son état dans `shh_state` et ses paliers dans `shh_goals`, dans la base de SHVotesReward.

Utilisé sur : **Opale**.

## Mode réseau (plusieurs Opale)

Avec **SHNetwork** chargé et `reseau.actif: true` (par défaut), un seul Hype Train sert tout le réseau. Sans SHNetwork, rien ne change.

- **Un seul meneur** (`leader` SHNetwork, tâche `shhypevotes`) fait tourner le Hype : départs au hasard (joueurs comptés sur tout le réseau), votes, paliers, temps ajouté, fin et récompenses. Les commandes de paliers passent par `qc queue` : la récompense arrive sur le serveur où se trouve le joueur.
- **Les autres serveurs** transmettent au meneur les votes reçus par SHVotesReward chez eux, et affichent ce qu'il publie sur le bus (`reseau.canal`) : annonces, sons, barre de boss, état pour `/shhypevotes status`. `/shhypevotes start`, `stop` et `end` y sont transmis au meneur.
- **Votant en ligne** : connecté sur n'importe quel serveur du réseau (présence SHNetwork), et plus seulement sur celui qui a reçu le vote.
- **Reprise** : le Hype en cours (`shh_state`) et ses votants (`shh_voters`) sont gardés en base. Si le meneur s'arrête, le serveur qui prend sa place reprend le Hype sans rien annoncer ; un Hype dont l'heure de fin est passée pendant l'arrêt est terminé tout de suite, avec ses récompenses. Sur un serveur seul, le Hype repart toujours à zéro au démarrage, comme avant.

## Dépendances

- Obligatoire : `SHVotesReward`, qui reçoit les votes
- Optionnelles : `SHLogs`, `SHNetwork` (mode réseau)

## Commandes

Racine d'administration `/shhypevotes`, alias `/shh`, `/hypevotes`, `/hv`, `/shhvote`. Chaque sous-commande a sa permission ; elles marchent aussi depuis la console.

| Commande | Description | Permission |
|---|---|---|
| `/shhypevotes start` | Démarre de force un Hype Train | `shhypevotes.admin.start` |
| `/shhypevotes stop` | Arrête le Hype Train en cours, sans récompenses | `shhypevotes.admin.stop` |
| `/shhypevotes end` | Termine le Hype Train en cours en distribuant les récompenses atteintes | `shhypevotes.admin.end` |
| `/shhypevotes reload` | Recharge `config.yml` et `messages.yml` (le bloc `reseau` demande un redémarrage) ; un fichier invalide annule le rechargement | `shhypevotes.admin.reload` |
| `/shhypevotes status` | Affiche l'état du Hype Train | `shhypevotes.admin.status` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shhypevotes.admin` | Toutes les commandes d'administration (parent des suivantes) | `op` |
| `shhypevotes.admin.start`, `.stop`, `.end`, `.reload`, `.status` | Une permission par sous-commande | `false` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHHypeVotes/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

Le résumé de chaque Hype Train terminé est écrit dans le fichier `hype` du dossier `SHHypeVotes` de SHLogs (`plugins/SHLogs/`), si `logging.enabled` est actif. Sans SHLogs, il passe dans la console.

## Compilation

Java 25 et Maven. SHLogs et SHVotesReward ne sont sur aucun dépôt Maven : il faut d'abord les installer depuis leurs sources avec `mvn install`, puis :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
