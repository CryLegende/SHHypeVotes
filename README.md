<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHHypeVotes

*Un système de Hype Train pour les votes avec récompenses par paliers.*

<img src="https://img.shields.io/badge/Paper-1.20.2-f2a900?style=for-the-badge" alt="Paper 1.20.2"> <img src="https://img.shields.io/badge/Java-17-e76f00?style=for-the-badge" alt="Java 17"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Un système de Hype Train pour les votes avec récompenses par paliers.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `Votifier`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shh` | `/hypevotes`, `/hv`, `/shhvote` | Commande principale pour SHHypeVotes. | `shh.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shh.admin` | Permet d'utiliser toutes les commandes de SHHypeVotes. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`
- `messages.yml`

## Compilation

Java 17 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
