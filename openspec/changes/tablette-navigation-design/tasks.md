# Tasks

## 1. Repères avec icônes

- [ ] 1.1 Créer `ui/components/MetaChips.kt` (`MetaKind`, `MetaItem`, `MetaRow`, `AgeBadge`, `ageTone`) et les couleurs d'âge dans `SokoulColors` ; vérifier par un test JVM de `ageTone` (TP/U/0, 10, 12, 16, 18, inconnu) et `DesignTokensTest` au vert
- [ ] 1.2 Héros (`Hero.kt`) et carte dévoilée (`Cards.kt`) sur `MetaRow` + `AgeBadge` ; vérifier par capture tablette (Accueil, paysage et portrait) et sur la TV plus tard (taille de texte inchangée)
- [ ] 1.3 Fiches : `DetailHeroData.facts` en `MetaItem` (film et série), `FactsLine` sur `MetaRow` ; vérifier par capture d'une fiche film et d'une fiche série en portrait (aucun « · » en début de ligne)

## 2. Fenêtre des sources

- [ ] 2.1 `SourceLabels.size` sans « 0 Mo » et écart des sources Real-Debrid de moins de 50 Mo (`tooSmall`) ; vérifier par un test JVM et par capture du panneau « Langue et qualité » sans ligne « 0 Mo »
- [ ] 2.2 Ligne de source partagée et `SourcesDialog` (origine en badge une fois, titre type · taille, note langues · état, 8 au plus puis « Voir les n autres ») à la place du `groupMenu` texte ; vérifier par capture de l'appui long sur une version et par les tests existants du panneau
- [ ] 2.3 Panneau du lecteur en paysage tactile (40 %, max 520 dp, fondu en bas) et puce « +n sources » touchable sur les cartes de version ; vérifier par captures tablette en paysage

## 3. Fiche en deux colonnes

- [ ] 3.1 Branche `TabletLandscapeDetail` dans `DetailScaffold` (héros à gauche défilant seul, sections à droite) et épisodes en liste verticale ; vérifier par captures d'une fiche film (bouton Lecture et versions visibles ensemble) et d'une fiche série en paysage, puis rotation en portrait (disposition actuelle)

## 4. Titre jamais en double

- [ ] 4.1 Recherche redécorée sur `enrich.updates` et titre sous les cartes paysage sans logo en tactile ; vérifier par capture de la recherche « lupin » (aucun titre écrit sur une image, logos qui arrivent)

## 5. Vérification d'ensemble

- [ ] 5.1 `gradlew testDebugUnitTest assembleDebug` au vert, relecture par l'agent `android-reviewer`, audit avant / après par `tablet-ux-auditor`, rotation remise à l'état d'origine

## Workflow follow-up

- Essais Fire TV (repères, fenêtre des sources, fiche inchangée) quand la TV est allumée.
- `/opsx:verify tablette-navigation-design`, puis archivage des deux changements (`/opsx:bulk-archive`) une fois les essais faits.
