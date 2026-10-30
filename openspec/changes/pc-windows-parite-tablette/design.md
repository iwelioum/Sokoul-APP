## Context

La refonte PC (`pc-windows-refonte`) a été rejetée : l'utilisateur veut l'interface de sa tablette et de sa Fire TV, pas une interface PC différente.

L'interface Android repose sur Jetpack Compose, et Compose Multiplatform exécute le même langage d'interface sur le bureau.

## Decisions

1. **Copier le code de la tablette plutôt que l'imiter.** Ces fichiers de `app/.../ui` sont copiés dans `desktop/src/main/kotlin/com/sokoul/iptv/...`, avec les **mêmes paquets** :
   - `theme/` : Theme, Tokens, Layout, TitleStyle, TitleArtSpecs ;
   - `components/` : Focus, Buttons, Cards, Hero, MetaChips, SourceBadges, Common, Surfaces, Skeletons, Brand, Chips, RowTitle, TouchTopBar, Dialogs, Controls ;
   - `util/Format` ;
   - `data/repo/Platforms`, `RowKicker`.

   Les imports restent donc identiques. Seuls ces points sont modifiés :
   - `R.font.*` devient un objet `R` du PC, dont les valeurs sont les chemins des polices dans les ressources ;
   - `NetImage` (Coil) devient le chargeur d'images du PC ;
   - `dpadClickable` gère le clic souris, le clic droit (appui long), Entrée et Espace ;
   - `FocusSurface` vaut focus clavier **ou** survol ;
   - `focusOnTop` tient aussi compte du survol ;
   - `rememberAppLayout` utilise la taille de la fenêtre ;
   - `CardRow` convertit la molette en défilement horizontal ;
   - `FlowRow` n'a pas `itemVerticalAlignment` (Compose 1.7) ;
   - `GlassPanel` floute l'image avec Skia.

   *Alternative écartée :* transformer `:app` en module multiplateforme. C'est trop risqué pour la TV et la tablette (Hilt, Room, Media3).

2. **Modèles d'affichage.** `Work`, `BrowseRow`, `SagaCard`, `PlatformTile` et `tmdbSized` sont repris sans Room. Sur PC, `Work.id` est l'identifiant TMDB, et `Work.resume` est la reprise du PC.

3. **Données.** `TabletData` construit les pages à partir de TMDB, avec les mêmes rangées, kickers et libellés que `BrowseRepository`. Les notes IMDb, RT et MC viennent d'OMDb (clé intégrée). Les versions Real-Debrid sont regroupées comme `VersionsShelf` : par langue, puis définition et HDR.

4. **Écrans pas encore repris** (Direct, Guide, Recherche, Réglages, surimpression du lecteur). Ils gardent leurs fonctions actuelles, sous la coque de la tablette, en attendant leur portage (tâches 3.x).

## Risks / Trade-offs

- **Deux copies de l'interface à maintenir** (Android et PC). Pour en limiter le coût, les fichiers sont gardés identiques, aux adaptations près, et repérés en tête de fichier. Un écart se voit par un simple diff.
- **Pas de captures de référence de la tablette.** L'adb sans fil s'est coupé avant la série de captures. La comparaison s'appuie sur le code (qui fait foi) et sur les captures déjà faites (fiche, versions, lecteur, réglages).
