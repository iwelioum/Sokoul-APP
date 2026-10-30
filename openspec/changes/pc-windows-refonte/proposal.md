## Why

La première version Windows (`pc-windows-base`) fonctionne mais son interface est jugée « affreuse » : écrans Material par défaut, police système, aucune image, listes de formulaire, barre du lecteur collée sous la vidéo. Elle ne sert aussi à rien sans les identifiants IPTV, restés sur la tablette. L'utilisateur demande une refonte totale qui reprenne l'identité de l'app Android et branche toutes les API (TMDB, Real-Debrid, OMDb, fanart, TVmaze, sous-titres…), pour que l'app de bureau soit utilisable tout de suite.

## What Changes

- **Système de design du bureau.** Il reprend la charte Android :
  - polices Manrope et DM Serif Display embarquées ;
  - jetons de couleurs, rayons et espaces ;
  - héros plein cadre, cartes affiche et paysage avec survol, rangées à la molette ;
  - barre de navigation du haut translucide ;
  - repères avec icônes et badge d'âge ;
  - boutons verre et plein, squelettes de chargement.
- **Accueil TMDB.** Héros tournant sur les tendances, puis des rangées : Reprendre, Top 10, films et séries populaires, mieux notés, par plateforme. L'accueil fonctionne même sans playlist IPTV.
- **Films et Séries.** Catalogue TMDB (découverte par genre et tri), complété par le catalogue IPTV quand une playlist existe.
- **Fiche plein cadre** (jamais en colonnes) : fond, logo, repères, résumé, Lecture, Sources, saisons et épisodes, distribution, suggestions.
- **Real-Debrid.** Recherche des versions par les extensions Torrentio et Comet, classées comme sur la tablette (langue, définition, origine du fichier, taille), dans une fenêtre Sources. « Lecture » lance la meilleure.
- **Lecteur.** La vidéo libVLC est dessinée par Compose. Une barre translucide s'affiche par-dessus : titre, progression cliquable, pistes audio et sous-titres, Sources, plein écran. Elle se masque d'elle-même. Raccourcis clavier et souris. Reprise de la position.
- **Recherche unifiée** : TMDB (films, séries) et chaînes IPTV.
- **Direct et Guide** redessinés : colonnes groupes / chaînes / aperçu, grille du guide avec en-tête du programme.
- **Réglages par sections** : Playlists, Clés API (état de chaque service, test de connexion), Lecture, Contrôle parental, À propos.
- **Clés API.** Elles sont lues au build depuis `local.properties`, dans une classe générée jamais commitée, et l'utilisateur peut les remplacer dans Réglages. L'app installée fonctionne donc sans fichier de configuration.
- **Code partagé.** `TmdbClient`, `VodGrouping`, `ReleaseName`, `DebridAddons`, `DebridStreams`, les modèles Real-Debrid et les types de sources sont dans `:core`, utilisés par l'Android et le PC.
- **Outils.** Ajout de rendus d'écrans sans fenêtre (`renderScreens`), de tests d'interface Compose, d'un skill `sokoul-desktop-check` et d'un agent `desktop-designer`.

## Capabilities

### New Capabilities
- `desktop-design` : identité visuelle, composants, survol, focus clavier, densité.
- `desktop-home` : accueil TMDB (héros, rangées).
- `desktop-catalog` : films, séries, fiche plein cadre, saisons et épisodes, recherche.
- `desktop-debrid` : sources Real-Debrid, classement, fenêtre Sources.
- `desktop-player` : vidéo dessinée par Compose, barre en surimpression, raccourcis, reprise.
- `desktop-keys` : clés API intégrées, remplaçables, testées.

### Modified Capabilities
<!-- desktop-app et desktop-live sont encore dans pc-windows-base, non archivé : leurs écrans sont redessinés ici, et leurs exigences restent valables. -->

## Impact

- **Code PC** : `app-android/desktop` (interface entièrement réécrite ; données et synchronisation gardées). Ajout des dépendances `compose.uiTest` (tests).
- **Code partagé** : `app-android/core`. L'Android est adapté par un fournisseur Hilt `TmdbCache` et le crochet `CoreLog`, sans changement visible ; les 420 tests restent au vert.
- **Sécurité** : les clés sont générées dans `build/` (ignoré par Git) et ne sont jamais journalisées ni affichées en entier. Le jeton Real-Debrid n'est mis dans les adresses des extensions qu'au moment de la requête.
- **Changement remplacé** : les tâches d'interface de `pc-windows-base` (2.1, 3.2, 4.2, 4.3, 5.3).
