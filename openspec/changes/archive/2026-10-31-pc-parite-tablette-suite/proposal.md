# Proposal

## Why

L'app Windows reprend déjà le design de la tablette : rail, accueil, fiche, Direct, guide, recherche, réglages et lecteur. Mais la comparaison avec 61 captures de la tablette (31 octobre) montre des écrans et des fonctions encore absents ou réduits :
- pas d'enregistrements ;
- pas de pages « À venir » ni « Historique » ;
- une page de saga sans frise ni ordre du récit ;
- un lecteur sans panneau « Langue et qualité » ni actions du direct ;
- des réglages affichés mais non modifiables.

L'utilisateur veut **tout** intégrer, avec le design et les fonctions de la tablette, optimisés pour la souris et le clavier.

## What Changes

- **Enregistrements**
  - Entrée « Enreg. » dans le rail.
  - Page Enregistrements : « PRÊTS À REGARDER · Terminés », « Programmés », « À VÉRIFIER · Échecs et annulés », avec l'espace utilisé et l'espace libre.
  - Programmation depuis le guide et depuis le lecteur du direct.
  - Enregistrement du flux sur disque pendant que l'app est ouverte. Il a priorité sur la lecture, l'abonnement n'autorisant qu'une connexion.
  - Lecture des enregistrements dans le lecteur du PC.
- **Ma liste** : boutons « À venir » (prochains épisodes annoncés par TMDB des séries suivies) et « Historique » (tout ce qui a été regardé, terminé compris).
- **Sagas**
  - Accroche « Les grandes histoires du catalogue, film après film. ».
  - Page d'une saga façon tablette : logo, repères (films, années, note, au catalogue, séries), « Commencer · ordre de sortie », « Ordre de sortie » ou « Ordre du récit », « Prochain : … », frise numérotée par année, séries de l'univers.
  - Grandes franchises reprises du fichier `curated_sagas.json` de la tablette.
- **Fiche** : « Finit à », « Aussi sur » (plateformes TMDB France) et section « Saga : *nom* » (« Toute l'histoire, dans l'ordre de sortie. »).
- **Sources d'une version** : fenêtre « Français (VF) · 1080p », « n sources · la première est lue par défaut », ouverte par « +n sources » sur une carte de version.
- **Lecteur de films**
  - Pastille « Français · 4K · RD » qui ouvre le panneau latéral « Langue et qualité » : « Vous regardez… », pistes du fichier, versions par langue, autres sources, échecs.
  - Pastille de sous-titres, menu « … » (vitesse, format d'image).
  - Bouton « Fenêtre réduite » : petite fenêtre toujours au premier plan, l'équivalent PC de l'image dans l'image.
  - Carte « Épisode suivant » avec compte à rebours.
- **Lecteur du direct** : boutons du haut (Retour, Chaînes, Fenêtre réduite, menu), et actions « Pause du direct », « Programmes », « Chaînes », « Enregistrer », « Favori », « Réglages », avec la santé du flux (« Flux stable »).
- **Direct** : panneau de la chaîne choisie avec aperçu vidéo, « EN CE MOMENT », progression, « ENSUITE » et bouton « Regarder ».
- **Réglages modifiables**
  - **Lecture** : langue audio, sous-titres, taille, couleur, lisibilité, position, épisode suivant automatique, et « Avancé » (mise en mémoire, User-Agent).
  - **Télévision et guide** : au démarrage, sens du zapping, décalage du guide.

## Capabilities

### New Capabilities
- `desktop-recordings` : enregistrements sur PC (programmation, capture du flux, page, lecture).
- `desktop-library-pages` : « À venir », « Historique », page de saga et section saga de la fiche.
- `desktop-player-panels` : panneaux et actions du lecteur (film et direct), fenêtre réduite, épisode suivant.
- `desktop-settings-editable` : réglages de lecture et de télévision modifiables, appliqués au lecteur.

### Modified Capabilities
- `desktop-tablet-parity` : la coque inclut l'entrée « Enreg. ». Le Direct présente l'aperçu de la chaîne choisie.

## Impact

- **Code** : `app-android/desktop` (écrans `tablet/`, `player/`, `data/`). Sont copiés depuis `app/` : `RecordingsScreen`, `HistoryScreen`, `CalendarScreen`, `SagaScreens`, `PlayerPanels`, `VodOverlay` (`NextUpCard`), `TimeshiftBar`, `TelevisionSection` et `PlaybackSection`.
- **Données** : nouvelles tables SQLite `recordings` et `settings_*`. La ressource `curated_sagas.json` est copiée de la tablette. C'est un fichier public, sans clé, autorisé explicitement malgré la vérification des `.json` avant commit.
- **App Android** : rien ne change. `:core` peut recevoir des règles pures déplacées (`RecordingRules`), avec les 420 tests Android à garder au vert.
- **Connexion IPTV** : toujours une seule. L'enregistrement en cours passe avant la lecture, et l'aperçu du Direct utilise le lecteur unique.
