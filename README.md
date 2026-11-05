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

## Dépendances

- Obligatoire : `SHVotesReward`, qui reçoit les votes
- Optionnelle : `SHLogs`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shh` | `/hypevotes`, `/hv`, `/shhvote` | Commande principale pour SHHypeVotes. | `shh.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shh.admin` | Permet d'utiliser toutes les commandes de SHHypeVotes. | `op` |

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
