# Tasks

## 1. Code d'interface de la tablette sur PC

- [x] 1.1 Copier les fichiers `theme/` et `components/` de l'app Android (mêmes paquets) et n'adapter que les dépendances Android (polices, images, touches, taille de fenêtre, molette). Vérifier que `:desktop:compileKotlin` passe sans toucher à l'app Android.
- [x] 1.2 Survol = focus (`FocusSurface`, `focusOnTop`), clic droit = appui long. Vérifier par capture (carte agrandie au survol).

## 2. Coque et pages

- [x] 2.1 Rail de la tablette et barre du haut des pages. Vérifier par capture : rail avec pastille d'accent.
- [x] 2.2 Accueil, Films et Séries (`TabletData`, `TabletBrowse`). Vérifier par captures p1/p2 :
  - héros « TOP N° 3 des films cette semaine » ;
  - Reprendre la lecture ;
  - Top 10 ;
  - « Parce que vous avez regardé *Inception* » ;
  - plateformes ;
  - rangées Netflix.
- [x] 2.3 Fiche de la tablette, avec notes OMDb, « Versions disponibles », épisodes, distribution et suggestions. Vérifier par captures p2 :
  - Inception : IMDb 8,8 · RT 86 % · MC 74, « Reprendre · MULTI · 4K » ;
  - Breaking Bad : saisons et épisodes.
- [x] 2.4 Ma liste (ajout et retrait depuis la fiche) et Sagas (collections TMDB, page d'une saga). Vérifier par test (ajout et retrait) et par capture p2-sagas.

## 3. Écrans encore à reprendre de la tablette

- [x] 3.1 Recherche (`SearchScreen` de la tablette : pastilles Films, Séries, Chaînes et Programmes TV ; Top 10 en tendance ; meilleurs résultats, puis rangées ; « Au programme » via `DesktopDb.searchPrograms`). Vérifié par captures s1/s2 et par un test.
- [x] 3.2 Direct (`LiveScreen` de la tablette : groupes, chaînes, aperçu). Vérifié par capture p3.
- [x] 3.3 Guide TV (`GuideScreen` de la tablette). Vérifié par capture p3.
- [x] 3.4 Réglages (`SettingsPanes` : rubriques Compte et playlists, Lecture, Télévision et guide, Films et séries, Real-Debrid, Contrôle parental, Profils, Sources de données, Sauvegarde, À propos). Lignes de la tablette, avec seulement ce que fait le PC (rien de factice). Masquer et verrouiller un groupe d'un clic droit dans le Direct ; changer le code exige l'ancien. Vérifié par captures r1 et par le test d'interface.
- [ ] 3.5 Surimpression du lecteur (`VodOverlay`, direct), avec le rendu vidéo Compose actuel.

## 4. Vérification

- [x] 4.1 `:desktop:test` au vert : tests de logique et tests d'interface du rail et de Ma liste.
- [ ] 4.2 Comparaison côte à côte avec la tablette en paysage, dès que l'adb répond (débogage sans fil coupé pendant ce changement).
- [ ] 4.3 Réinstallation, commit et push.
