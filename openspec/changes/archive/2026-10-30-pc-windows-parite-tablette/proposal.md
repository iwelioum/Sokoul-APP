## Why

L'utilisateur rejette le design de la refonte PC (`pc-windows-refonte`). Il veut **le même design que son app tablette et Fire TV**, pas une interface PC différente.

La refonte imitait l'esprit de l'Android mais divergeait partout :
- barre d'onglets en haut au lieu du rail latéral ;
- héros, cartes, fiche et lecteur redessinés ;
- notes IMDb, RT et MC absentes ;
- pas de « Ma liste » ni de « Sagas » ;
- rangées sans « Tout voir ».

## What Changes

L'interface PC est reconstruite **à partir du code Compose de l'app Android**, en reprenant ses composables, sa mise en page et ses textes. Les composables sont copiés puis adaptés : seules les dépendances Android sont remplacées (images Coil, polices `R.font`, ViewModels Hilt, focus télécommande). Le PC reproduit la tablette en paysage (classe EXPANDED, au doigt devenu souris).

- **Coque.** Le rail latéral `TabletRail` remplace la barre du haut : logo S, Recherche, Accueil, Films, Séries, Ma liste, Sagas, Direct, Guide TV, Enreg. (masqué tant que l'enregistrement n'existe pas sur PC) et Réglages.
- **Accueil, Films, Séries.** Ils reprennent la mise en page de `BrowseScreen` :
  - fond d'ambiance et panneau de l'œuvre survolée (`AmbientBackdrop`, `SpotPanel`) ;
  - rangées `RowHeader` avec kicker « POUR VOUS », titre en DM Serif avec mot en italique, sous-titre et « Tout voir » ;
  - `WorkCard`, `PosterTile`, `LandscapeCard`, `Top10Card`, `SagaTile`, `SeeAllCard`, avec les mêmes tailles et le même survol (effet de respiration et contour).
- **Fiche.** Elle reprend `DetailScaffold` et `HeroBlock` :
  - logo ;
  - slogan en italique ;
  - ligne de notes : ★ TMDB, IMDb, RT, MC (OMDb) ;
  - année, durée, « Finit à… », langue ;
  - badges « Tous publics » et « 4K · HDR10 · Real-Debrid » ;
  - genres ;
  - boutons « Lecture · MULTI · 4K HDR10 », « Ma liste », « Bande-annonce », « Plus » ;
  - « Versions disponibles » (`VersionsShelf`) ;
  - saisons et épisodes (`SeasonTabs`, `EpisodeShelf`) ;
  - distribution et suggestions.
- **Lecteur.** Il reprend la surimpression de `VodOverlay` (barres, pistes, sources, épisode suivant) et celle du direct, avec le rendu vidéo actuel (Compose).
- **Direct, Guide, Réglages et Recherche** reprennent `LiveScreen`, `GuideScreen`, `SettingsPanes` et `SearchScreen` dans leur version tablette.
- **Nouvelles données sur PC** :
  - Ma liste (favoris de films et séries) ;
  - Sagas (collections TMDB) ;
  - notes OMDb ;
  - rangées « Parce que vous avez regardé… » (recommandations TMDB depuis la dernière lecture).
- **Contrôle de conformité.** Chaque écran PC est capturé à côté de la même vue de la tablette en paysage, à taille comparable. Les écarts sont relevés par l'agent `desktop-designer`, avec pour seule consigne « identique à la tablette ».

## Capabilities

### New Capabilities
- `desktop-tablet-parity` : l'app PC reprend la mise en page, les composants et les textes de la tablette en paysage, écran par écran.

### Modified Capabilities
<!-- desktop-design, desktop-home, desktop-catalog (pc-windows-refonte, non archivé) : remplacés par la parité tablette ; leurs exigences fonctionnelles (TMDB, Real-Debrid, reprise, clés, essais sans focus) sont gardées. -->

## Impact

- **Code** : `app-android/desktop/ui` est réécrit à partir de `app-android/app/.../ui`. Les données et le lecteur PC (`Catalog`, `DesktopDebrid`, `DesktopPlayer`, `DesktopSync`) sont gardés.
- **App Android** : aucun changement de comportement. Le code y est copié, pas déplacé, ce qui évite de casser la TV.
- **Références** : des captures de la tablette en paysage sont faites pour chaque écran (accueil, films, séries, fiche, lecteur, direct, guide, réglages, recherche, ma liste, sagas), avec l'accord de l'utilisateur, car il faut piloter la tablette.
- **Hors périmètre** : enregistrements, profils, multivue, Trakt. Ce sont des fonctions et non du design ; elles feront l'objet d'un changement suivant.
