# Tasks

## 1. Code partagé et clés

- [x] 1.1 Déplacer dans `:core` :
  - `TmdbClient` (cache `TmdbCache`, journal `CoreLog`) ;
  - `VodGrouping`, `ReleaseName`, `DebridAddons`, `SpokenLanguages` ;
  - les types de sources, les modèles Real-Debrid et la lecture des réponses (`DebridStreams`).

  Vérifier que `testDebugUnitTest assembleDebug` reste au vert (420 tests). *Fait : 420 tests au vert, commit 5fc6220.*
- [ ] 1.2 Clés : tâche `generateDesktopKeys` (classe générée dans `build/`), `ProviderConfig` (Réglages, puis clés intégrées), sondes sans fuite. Vérifier par un test que la clé saisie l'emporte, et que `git status` ne montre aucun fichier de clés.

## 2. Design et outils

- [ ] 2.1 Système de design : polices Manrope et DM Serif, `Type`, jetons, et les composants `Hero`, `PosterCard`, `LandscapeCard`, `CardRow` (molette et flèches), `TopNav`, boutons, puces, `MetaRow`, `AgeBadge`, squelettes, `SokoulDialog`. Vérifier par les rendus `renderScreens`.
- [ ] 2.2 `renderScreens` (`ImageComposeScene`, données d'exemple) et tests d'interface `runComposeUiTest` (navigation, fiche, Échap, `/`) ; vérifier que `:desktop:test` est au vert.
- [ ] 2.3 Skill `sokoul-desktop-check`, agent `desktop-designer`, agents `android-reviewer` et `tablet-ux-auditor` étendus au bureau, `CLAUDE.md`.

## 3. Écrans

- [ ] 3.1 Accueil : héros tournant, rangées TMDB, Reprendre, bandeau « ajouter une playlist ». Vérifier par rendu et par essai réel avec TMDB.
- [ ] 3.2 Films et Séries : grille de découverte, genres, tri, pagination. Vérifier par essai réel.
- [ ] 3.3 Fiche plein cadre, saisons et épisodes, distribution, suggestions. Vérifier par essai réel (un film et une série).
- [ ] 3.4 Recherche TMDB et chaînes. Vérifier par essai réel (« dune »).
- [ ] 3.5 Direct et Guide redessinés. Vérifier par rendu avec des données d'exemple (pas d'IPTV sur ce PC pour l'instant).
- [ ] 3.6 Réglages par sections : playlists, clés API avec Tester, compte Real-Debrid, lecture, contrôle parental, à propos. Vérifier que tous les tests de clés sont réussis.

## 4. Real-Debrid et lecteur

- [ ] 4.1 `DesktopDebrid` (IMDb, Torrentio et Comet en parallèle, fusion, filtres) et `DesktopRanker` ; fenêtre Sources. Vérifier par un test du classement et par un essai réel sur un film.
- [ ] 4.2 Lecteur : rendu Compose (`CallbackVideoSurface`), barre en surimpression, raccourcis, plein écran, pistes, une seule instance. Vérifier par la lecture réelle d'un film Real-Debrid et une capture.
- [ ] 4.3 Reprise (toutes les 10 s, à la fermeture, rangée Reprendre, seuil de 92 %). Vérifier par un test et par un essai réel.

## 5. Vérification

- [ ] 5.1 Revue `desktop-designer` des rendus, puis corrections.
- [ ] 5.2 Revue `android-reviewer`, `openspec validate --all --strict`, réinstallation (`tools/install-desktop.ps1`), lancement depuis le raccourci, commit et push.
