# Proposal

## Why

Sur tablette, la fiche d'un film en paysage occupe tout l'écran avec son image d'en-tête : il faut faire défiler une page entière avant de voir les versions et la distribution.

Les informations de l'œuvre (année, durée, âge, genres) sont du texte gris sans hiérarchie, et la tranche d'âge ne se repère pas d'un coup d'œil.

La fenêtre des sources (appui long sur une version) aligne de longues phrases comme « Lire : RD · WEB · 12,3 Go · prête · langues : Français, Anglais · qualité d'après le nom ». On n'y repère ni la taille, ni l'état, ni les langues. Une source affiche même « 0 Mo ».

Dans la recherche, certaines cartes dessinent le titre par-dessus une image qui le contient déjà.

## What Changes

- **Repères avec icônes** dans le héros (Accueil, Films, Séries), les cartes dévoilées des carrousels et les fiches :
  - année, durée, heure de fin et nombre de saisons, chacun avec son icône ;
  - genres en puces discrètes ;
  - **badge d'âge en couleur** selon la tranche (Tous publics, -10, -12, -16, -18).
- **Fiche en paysage tactile** : l'image de l'œuvre reste en plein cadre, mais le premier écran est moins haut et laisse apparaître le début des versions ou des épisodes, pour qu'on voie qu'il y a une suite (une version en deux colonnes, essayée d'abord, découpait l'image et a été écartée).

  Portrait, téléphone et TV ne changent pas.
- **Fenêtre Sources lisible**. Chaque source tient sur une ligne structurée :
  - origine en badge ;
  - définition et type de fichier en titre ;
  - puces pour la taille, l'état (prête, en préparation, à télécharger) et les langues audio ;
  - la base de la qualité (mesurée ou d'après le nom) en petit.

  Ces règles s'appliquent au dialogue de la fiche et au panneau « Langue et qualité » du lecteur. Une taille inconnue ou nulle n'est pas affichée. Sur tablette en paysage, le panneau du lecteur est plus large.
- **Titre jamais en double sur les cartes paysage** : quand l'image peut contenir le titre et qu'aucun logo n'est connu, le titre passe sous l'image au lieu d'être dessiné dessus. L'image sans texte et le logo sont récupérés à la volée pour les cartes affichées.

## Capabilities

### New Capabilities
- `meta-chips` : affichage des repères d'une œuvre (année, durée, fin, saisons, genres, âge) avec icônes et badge d'âge coloré.
- `detail-layout` : disposition de la fiche film ou série selon l'appareil (première section visible en paysage tactile, fond plein cadre).
- `artwork` : choix des images et du titre des cartes (image sans texte, logo, titre jamais en double).

### Modified Capabilities
- `sources-panel` : présentation structurée de chaque source (puces, taille inconnue masquée) et largeur du panneau en paysage.

## Impact

- **Appareils** : surtout la tablette (portrait et paysage). Les repères et la fenêtre Sources changent aussi sur TV et téléphone, avec la même taille qu'aujourd'hui.
- **Code** :
  - `ui/components/Hero.kt`, `ui/components/Cards.kt` ;
  - nouveau `ui/components/MetaChips.kt` ;
  - `ui/screens/vod/DetailKit.kt`, `DetailScreens.kt`, `VersionsShelf.kt` ;
  - `ui/screens/player/PlayerPanels.kt`, `PlayerSources.kt` ;
  - `data/repo/EnrichmentRepository.kt`.
- **Thème** : couleurs des tranches d'âge ajoutées à `SokoulColors`.
- **Réseau** : quelques requêtes TMDB en plus pour les cartes sans logo, limitées et mises en cache.
- **Aucune** nouvelle dépendance ni migration de base.

## Hors périmètre

- Filtres et tri dans le panneau des sources.
- Nouvelles sources de métadonnées (autres que TMDB).
- Changement de la mise en page du héros sur TV.
- Fiche découpée en colonnes (essayée, écartée).
