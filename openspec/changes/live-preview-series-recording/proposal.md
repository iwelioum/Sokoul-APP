# Proposal

## Why

Dans l'écran Direct, on choisit une chaîne à l'aveugle : il faut l'ouvrir en plein écran pour voir ce qui passe, comme le font TiviMate et les box opérateurs avec leur petite fenêtre d'aperçu. Pour les enregistrements, chaque diffusion d'une émission ou d'une série doit être programmée à la main depuis le guide. Une émission quotidienne ou une série hebdomadaire demande donc une manipulation à chaque fois.

## What Changes

- **Aperçu vidéo dans l'écran Direct (TV)** : la chaîne qui garde le focus au moins un court instant est lue en petit, en haut du panneau d'informations, sans le son par défaut. OK ouvre le plein écran.
- L'aperçu respecte la limite d'une connexion de l'abonnement :
  - il se coupe avant l'ouverture du plein écran ;
  - il ne démarre pas pendant un enregistrement ni pendant la pause imposée par le fournisseur ;
  - il est désactivable dans Réglages.
- **Enregistrer tous les épisodes** : nouvelle option dans le menu d'un programme du guide (et du direct).
  - Elle crée une règle (titre de l'émission et chaîne) qui programme automatiquement chaque future diffusion trouvée dans le guide, à chaque rafraîchissement du guide.
  - Les règles se gèrent et se suppriment depuis l'écran Enregistrements.
- Les règles existantes de l'enregistrement s'appliquent aussi aux diffusions programmées automatiquement : pas de chevauchement sur la même chaîne, limite de connexions, 12 h au plus. Une diffusion refusée est ignorée et signalée dans la règle, sans bloquer les autres.

## Capabilities

### New Capabilities
- `live-preview`: aperçu vidéo de la chaîne sélectionnée dans l'écran Direct, et son respect de la limite de connexions.
- `series-recording`: règles d'enregistrement récurrent (titre et chaîne), programmation automatique des diffusions du guide et gestion des règles.

### Modified Capabilities
<!-- aucune : les specs existantes ne couvrent ni l'écran Direct ni les enregistrements -->

## Impact

- **Écran Direct et réglages** : `ui/screens/live/LiveScreen.kt` (panneau d'informations TV), Réglages › Télévision.
- **Enregistrements et guide** :
  - `recording/RecordingScheduler.kt` et `RecordingRules.kt` ;
  - `ui/screens/guide/GuideScreen.kt` et `ui/screens/live/ChannelActions.kt` ;
  - `ui/screens/recordings/RecordingsScreen.kt`.
- **Base de données** : nouvelle table des règles. Le schéma Room passe en version 9, avec une migration 8 → 9 sans perte.
- **Synchronisation** : programmation des règles après chaque synchronisation du guide (`work/SyncWorker.kt`, `SyncEngine.syncEpg`).
- **Aucune nouvelle dépendance.**
