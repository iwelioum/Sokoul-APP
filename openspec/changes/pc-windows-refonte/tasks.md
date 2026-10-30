# Tasks

## 1. Code partagé et clés

- [x] 1.1 Déplacer dans `:core` :
  - `TmdbClient` (cache `TmdbCache`, journal `CoreLog`) ;
  - `VodGrouping`, `ReleaseName`, `DebridAddons`, `SpokenLanguages` ;
  - les types de sources, les modèles Real-Debrid et la lecture des réponses (`DebridStreams`).

  Vérifier que `testDebugUnitTest assembleDebug` reste au vert (420 tests). *Fait : 420 tests au vert, commit 5fc6220.*
- [x] 1.2 Clés : tâche `generateDesktopKeys` (classe générée dans `build/`), `ProviderConfig` (Réglages, puis clés intégrées), sondes sans fuite. Vérifier par un test que la clé saisie l'emporte, et que `git status` ne montre aucun fichier de clés.
  - *Test `cle saisie l'emporte…` au vert.*
  - *Aucun `DesktopKeys` ni `local.properties` dans les commits.*
  - *Les sondes ne renvoient que le code HTTP.*

## 2. Design et outils

- [x] 2.1 Système de design : polices Manrope et DM Serif, `Type`, jetons, et les composants `Hero`, `PosterCard`, `LandscapeCard`, `CardRow` (molette et flèches), `TopNav`, boutons, puces, `MetaRow`, `AgeBadge`, squelettes, `SokoulDialog`.
  - *Vérifié par captures réelles plutôt que par `renderScreens`.*
  - *La fenêtre réelle se capture sans focus (PrintWindow), avec de vraies données.*
- [x] 2.2 Tests d'interface `runComposeUiTest` : navigation, fiche poussée, Échap, réglages, recherche. Vérifier que `:desktop:test` est au vert.
  - *12 tests au vert (10 de logique, 2 d'interface).*
  - *`renderScreens` remplacé par `SOKOUL_ROUTE` + `tools/desktop-shot.ps1`.*
- [x] 2.3 Outils :
  - skill `sokoul-desktop-check` ;
  - agent `desktop-designer` ;
  - agents `android-reviewer` et `tablet-ux-auditor` étendus au bureau ;
  - `CLAUDE.md`.

## 3. Écrans

- [x] 3.1 Accueil : héros tournant, rangées TMDB, Reprendre, bandeau « ajouter une playlist ». Vérifier par rendu et par essai réel avec TMDB.
  - *Captures r1/r3-home : tendances du jour, logo, badge -16, Reprendre avec Inception.*
- [x] 3.2 Films et Séries : grille de découverte, genres, tri, pagination. Vérifier par essai réel. *Capture r-movies.*
- [x] 3.3 Fiche plein cadre, saisons et épisodes, distribution, suggestions. Vérifier par essai réel (un film et une série). *Captures : Inception, Breaking Bad et la même fiche défilée.*
- [x] 3.4 Recherche TMDB et chaînes. Vérifier par essai réel (« dune »). *Capture r-search-dune.*
- [ ] 3.5 Direct et Guide redessinés. Vérifier par rendu avec des données d'exemple (pas d'IPTV sur ce PC pour l'instant).
  - *Écrans réécrits ; état « sans playlist » vérifié par le test d'interface.*
  - *Essai avec de vraies chaînes : il faut les identifiants (tablette).*
- [x] 3.6 Réglages par sections : playlists, clés API avec Tester, compte Real-Debrid, lecture, contrôle parental, à propos. Vérifier que tous les tests de clés sont réussis.
  - *Capture r-settings : compte Real-Debrid Premium, 175 jours.*

## 4. Real-Debrid et lecteur

- [x] 4.1 `DesktopDebrid` (IMDb, Torrentio et Comet en parallèle, fusion, filtres) et `DesktopRanker` ; fenêtre Sources. Vérifier par un test du classement et par un essai réel sur un film.
  - *Essai : 1 513 versions pour Inception.*
  - *Le classement passe les versions SDR d'abord (pas de conversion HDR) et applique une taille limite selon le réseau.*
  - *Une vidéo d'attente « Not ready yet » est refusée : la version suivante est essayée.*
- [x] 4.2 Lecteur : rendu Compose (`CallbackVideoSurface`), barre en surimpression, raccourcis, plein écran, pistes, une seule instance. Vérifier par la lecture réelle d'un film Real-Debrid et une capture.
  - *Captures r2/r3-play : Inception (Blu-ray MULTI, 1920×800) lu et dessiné par Compose, barre par-dessus.*
- [x] 4.3 Reprise (toutes les 10 s, à la fermeture, rangée Reprendre, seuil de 92 %). Vérifier par un test et par un essai réel.
  - *Test de la reprise au vert.*
  - *Inception apparaît dans Reprendre après l'essai.*

## 5. Vérification

- [ ] 5.1 Revue `desktop-designer` des rendus, puis corrections.
- [ ] 5.2 Revue `android-reviewer`, `openspec validate --all --strict`, réinstallation (`tools/install-desktop.ps1`), lancement depuis le raccourci, commit et push.

## Notes de mise en œuvre

- **Essais sans déranger l'utilisateur.**
  - `SOKOUL_ROUTE` ouvre un écran précis, dans une fenêtre qui ne prend pas le focus et avec le son coupé.
  - `SOKOUL_CONTROLS=1` garde la barre du lecteur affichée pendant la capture.
- **IPTV dans Sources.**
  - Les films de l'abonnement sont proposés en tête de la fenêtre Sources quand ils correspondent : même identifiant TMDB, ou même titre normalisé avec l'année à ±1 an.
  - Ce n'est pas encore essayé faute d'identifiants sur ce PC.
  - Les épisodes de séries IPTV (`get_series_info`) suivront dans un prochain changement.
