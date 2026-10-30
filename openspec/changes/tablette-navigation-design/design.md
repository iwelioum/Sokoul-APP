# Design

## Context

Voir proposal.md (Why) et les specs `meta-chips`, `detail-layout`, `artwork` et `sources-panel`. Captures et audit tablette du 30/10 (agent `tablet-ux-auditor`) : fiche en paysage, étagère des versions, fenêtre des sources, panneau « Langue et qualité ».

**Repères**
- `Hero.kt` : `heroMeta` (l. 268) renvoie des textes et `HeroPage` (l. 240-256) les pose en `FlowRow`. La classification passe par `certificationLabel` (l. 278).
- Fiches : `DetailHeroData.facts` est une `List<String>` posée par `FactsLine` (`DetailKit.kt:458`). Elle est remplie dans `DetailScreens.kt:389` (film) et `:801-805` (série).
- Carte dévoilée des carrousels : `WorkCard` (`Cards.kt:313-338`).

**Fiche**
- `DetailScaffold` (`DetailKit.kt:187-273`) a deux branches :
  - `PhoneDetail` quand `isCompact` ;
  - sinon un héros plein écran (`l.heightDp`) sur `DetailBackdrop`, suivi des sections.
- La branche plein écran sert à la tablette comme à la TV.

**Sources**
- Fenêtre ouverte par appui long : `groupMenu` (`DetailScreens.kt:494-513`). C'est un `OptionsDialog` en texte seul (« Lire : RD · … »), qui liste toutes les sources.
- Panneau du lecteur : `SidePanel` (`PlayerPanels.kt:73`) fait 46 % de large, au plus 620 dp. Il contient `PanelRow`, `OriginChips` et `sourceNote`, avec `ALT_LIMIT = 8`.
- Taille : `SourceLabels.size` (`SourceBadges.kt:37`) écrit « 0 Mo » sous 1 Mo, et `PlayerSources.fromDebrid` (l. 100-124) garde toutes les tailles.

**Images**
- La recherche (`SearchScreen.kt:199-201`) appelle `enrich.request` puis `decorate` une seule fois. Elle ne suit pas `enrich.updates` (les grilles le font, `BrowseGridScreen.kt:147`), donc les logos arrivés ensuite ne s'affichent jamais.
- `LandscapeCard` (`Cards.kt:346-393`) dessine `TitleArt` sur l'image quand il n'y a pas de logo.

## Goals / Non-Goals

**Goals :**
- un composant de repères partagé (héros, carte dévoilée, fiche) ;
- une ligne de source unique, partagée par le dialogue de la fiche et le panneau du lecteur ;
- la fiche en deux colonnes ajoutée en nouvelle branche de `DetailScaffold`, sans toucher aux sections.

**Non-Goals :**
- aucun changement de navigation TV ;
- pas de nouvelle requête TMDB par carte au-delà de la file `EnrichmentRepository.request`, qui existe déjà et est limitée.

## Decisions

### 1. `MetaChips` : repères typés plutôt que des chaînes
- Nouveau `ui/components/MetaChips.kt`, avec `MetaKind` (YEAR, DURATION, ENDS, SEASONS, LANGUAGE) et `MetaItem(kind, text)`.
- `MetaRow(items, modifier)` est un `FlowRow` de puces non focusables (icône 16 dp et texte `labelLarge`). Une puce entière passe à la ligne, donc pas de « · » orphelin.
- `AgeBadge(cert)` affiche un fond selon la tranche, choisie par `ageTone(cert)` (fonction pure testée) :
  - « TP », « U », « 0 », « Tous publics » → ALL ;
  - « 10 » → TEN ;
  - « 12 » → TWELVE ;
  - « 16 » → SIXTEEN ;
  - « 18 » → EIGHTEEN.
- Cinq couleurs sont ajoutées à `SokoulColors` (`ui/theme/Theme.kt`, hors du contrôle de `DesignTokensTest`), avec un texte clair.
- `DetailHeroData.facts` devient `List<MetaItem>`, et `heroMeta` renvoie des `MetaItem`.

*Écarté* : reconnaître le type à partir du texte (« 2026 », « 1 h 49 »). C'est fragile, car le format de la durée change selon l'écran.

### 2. Fiche en deux colonnes
- Condition `twoColumn = l.isTouch && !l.isCompact && l.widthDp > l.heightDp`.
- Nouvelle branche `TabletLandscapeDetail` de `DetailScaffold` : une `Row`.
  - **Colonne de gauche (44 %)** : `DetailBackdrop` en fond, un dégradé vers la droite, puis le `HeroBlock` en `verticalScroll`, avec sa limite de largeur levée par un paramètre `fullWidth`.
  - **Colonne de droite** : un `LazyColumn` des `sections()`, à partir du haut, sur fond `Background`.
- Les épisodes suivent ce même drapeau pour passer en liste verticale (`DetailScreens.kt:865/873`).

### 3. Ligne de source partagée
- `SourceLineContent(s, selected)` est rendu `internal` dans `PlayerPanels.kt` :
  - titre : type et taille (`PlayerSources.facts`) ou définition ;
  - `OriginChips` ;
  - note : langues audio et état ;
  - base de la qualité en petit.
- `groupMenu` passe à un `SourcesDialog` (dans `DetailScreens.kt`) construit sur `SokoulDialog`, qui reprend la forme d'`OptionsDialog` avec ces lignes. Il affiche au plus `ALT_LIMIT` lignes, puis « Voir les n autres sources ». « Toujours lancer en premier » reste en fin de liste.
- Taille :
  - `SourceLabels.size` renvoie `null` sous 1 Mo, et les appelants passent à `?.let` ;
  - les sources Real-Debrid de moins de 50 Mo sont écartées à la construction de la liste du lecteur et de la fiche (`DebridSources`), par la fonction pure `tooSmall(bytes)` testée.
- `SidePanel` en tactile paysage : 40 % de large, au plus 520 dp, et un fondu en bas du `LazyColumn` quand `canScrollForward`.
- `VersionCard` en tactile : le « +n » devient une puce « +n sources », cible de 48 dp, qui appelle `onMenu`.

### 4. Titre jamais en double
- `SearchViewModel.results` suit `enrich.updates` (debounce de 1,5 s) et redécore les films et séries déjà trouvés, sans relancer la recherche.
- `LandscapeCard` :
  - sans logo, avec un fond venu d'une liste (`work.logo == null`), le `TitleArt` sur l'image est remplacé par un titre texte sous la carte (`titleSmall`, 1 ligne) ;
  - avec un logo, rien ne change ;
  - paramètre `titleBelow` (vrai par défaut en tactile) : sur TV, le titre au focus reste dans l'image comme aujourd'hui.

## Risks / Trade-offs

- **[Hauteur des rangées]** Un titre sous les cartes paysage ajoute environ 22 dp à la rangée. On l'accepte en tactile ; la TV ne change pas.
- **[Fire TV et redécoration de la recherche]** La redécoration se fait au plus toutes les 1,5 s, et seulement quand de nouvelles fiches sont écrites.
- **[Couleurs d'âge]** Le contraste du texte doit rester lisible. On choisit des tons foncés saturés, vérifiés par capture sur la tablette.
- **[Petits fichiers RD]** Un vrai film de moins de 50 Mo n'existe pas en pratique. Le seuil ne vise que les échantillons et les fichiers annexes.

## Migration Plan

Aucune donnée à migrer.
