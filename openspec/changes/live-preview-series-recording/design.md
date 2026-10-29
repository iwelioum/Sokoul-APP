# Design

## Context

- L'écran Direct (`LiveScreen.kt`) a sur TV trois colonnes : groupes, chaînes et un panneau d'informations de 300 dp. Ce panneau suit `details`, la chaîne qui a le focus (anti-rebond de 200 ms) avec son programme en cours et à venir.
- Aucun aperçu vidéo n'existe aujourd'hui. `PlayerView` n'est utilisé que dans le lecteur et le multi-écran.
- La règle « une connexion à la fois » est tenue par `ProviderGate` :
  - un `StreamPlayer` exclusif qui démarre arrête le précédent ;
  - un enregistrement bloque les flux du fournisseur ;
  - une pause de 30 s suit un refus.
- Les enregistrements :
  - `RecordingScheduler.schedule` applique `RecordingRules.check`, insère un `RecordingEntity` puis l'arme avec AlarmManager ;
  - les programmes du guide (`ProgramEntity`) n'ont pas d'identifiant d'émission : seul le titre permet de les relier ;
  - le guide est rafraîchi par `SyncWorker` (toutes les 3 h si l'échéance est passée, ou à la demande).
- La base est en version 8.

## Goals / Non-Goals

**Goals:**
- Aperçu sur TV uniquement. Au doigt, toucher une chaîne lance déjà le plein écran.
- Règles par titre et chaîne, programmées à partir du guide déjà téléchargé.

**Non-Goals:**
- Passer le flux de l'aperçu au plein écran sans le rouvrir.
- Aperçu sur tablette, aperçu avec le son, aperçu dans le guide.
- Règles « toutes chaînes », par mot-clé, ou limitées à un créneau horaire.

## Decisions

- **Aperçu = `StreamPlayer` exclusif créé par l'écran Direct**, avec `PlayerFactory.create(forMultiview = true)` (720p au plus, son PCM), volume à 0.
  - Comme il est exclusif, `ProviderGate.acquire` empêche déjà deux flux à la fois. La garde « enregistrement / pause » de `StreamPlayer` le met en attente tout seul.
  - Écarté : réutiliser le lecteur plein écran (`PlayerViewModel`). Il est lié à la navigation et ajoute ses propres écouteurs, ce qui demanderait une grosse refonte pour un gain limité (le plein écran rouvre de toute façon le flux).
- **Déclenchement** : anti-rebond de 800 ms sur la chaîne qui a le focus (en plus des 200 ms du panneau), pour ne pas ouvrir de flux en faisant défiler.
- **Arrêt avant le plein écran** : le `play` de l'écran Direct appelle `stop()` puis `release()` sur l'aperçu avant de naviguer. L'arrêt se fait aussi sur `ON_STOP` et à la sortie de l'écran.
  - Risque accepté : certains fournisseurs libèrent la connexion lentement. Le lecteur plein écran a déjà une reconnexion avec pause, qui couvre ce cas.
- **Réglage** : `livePreview: Boolean = true` dans `AppSettings`, dans Réglages › Télévision.
  - Désactivé d'office quand l'abonnement n'a qu'une connexion et qu'un enregistrement est programmé dans les 2 minutes : on évite de prendre la connexion juste avant qu'il démarre.
- **Règles** : nouvelle table `recording_rules` (id, playlistId, channelId, channelName, titleKey, title, createdAt, lastRunAt, skipped), avec la migration 8 → 9.
  - `titleKey` = titre normalisé : minuscules, sans accents ni ponctuation, sans numéro d'épisode final comme « (12) » ou « - Épisode 3 ».
  - C'est la même idée que `VodGrouping.normalizeTitle`, mais écrite comme une fonction pure à part dans `RecordingRules`, parce qu'un programme TV n'a pas d'année.
- **Programmation** : `SeriesRecorder.run(pid)` est appelé à la fin de la synchronisation du guide, à la création d'une règle et au démarrage (`restoreAll`).
  - Il cherche les programmes à venir de la chaîne dont le `titleKey` correspond, puis appelle `RecordingScheduler.schedule` pour chacun.
  - Les doublons sont évités en comparant aux `RecordingEntity` existants (même chaîne, début à ±5 min).
  - Les refus sont comptés dans `skipped`.
- **Sauvegarde** : les règles sont ajoutées à `BackupManager`. Elles ne contiennent aucun secret, donc rien à chiffrer.

## Risks / Trade-offs

- [Un titre trop générique (« Journal », « Météo ») programme beaucoup de diffusions] → la règle est liée à une seule chaîne, et le nombre d'enregistrements à venir est affiché dans la règle pour qu'on la supprime si besoin.
- [Le guide de certains fournisseurs met le numéro d'épisode dans le titre] → normalisation du titre testée sur des cas réels du guide de l'abonnement (« Plus belle la vie - Épisode 4512 »).
- [Stockage du Fire TV Stick (1,7 Go libres)] → aucune protection nouvelle. Le contrôle d'espace existant de `RecordingRules` s'applique, et on le dit dans le compte rendu.
- [Aperçu sur box modeste : deux décodeurs (aperçu, puis plein écran) en peu de temps] → l'aperçu est libéré avant la navigation. Ce cas sera vérifié sur le Fire TV.

## Migration Plan

- Room passe en version 9 avec une migration 8 → 9 qui ne fait que créer la table : aucune donnée existante n'est modifiée.
- Retour arrière : désactiver l'aperçu dans les Réglages. Les règles se suppriment depuis Enregistrements.
