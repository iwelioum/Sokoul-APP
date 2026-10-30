# Tasks

## 1. Repères avec icônes

- [x] 1.1 Créer `ui/components/MetaChips.kt` (`MetaKind`, `MetaItem`, `MetaRow`, `AgeBadge`, `ageTone`) et les couleurs d'âge dans `SokoulColors` ; vérifier par un test JVM de `ageTone` (TP/U/0, 10, 12, 16, 18, inconnu) et `DesignTokensTest` au vert — *`MetaChipsTest`*
- [x] 1.2 Héros (`Hero.kt`) et carte dévoilée (`Cards.kt`) sur `MetaRow` + `AgeBadge` ; vérifier par capture tablette (Accueil, paysage et portrait) et sur la TV plus tard (taille de texte inchangée) — *capture Accueil paysage : icônes année / durée / fin, « Tous publics » vert, genres en puces ; carte dévoilée = TV seulement, à vérifier sur la Fire TV*
- [x] 1.3 Fiches : `DetailHeroData.facts` en `MetaItem` (film et série), `FactsLine` sur `MetaRow` ; vérifier par capture d'une fiche film et d'une fiche série en portrait (aucun « · » en début de ligne) — *captures Clayface (« -12 » orange) et Balaraw (« 1 saison ») ; genres en puces, badge qualité sans l'origine*

## 2. Fenêtre des sources

- [x] 2.1 `SourceLabels.size` sans « 0 Mo » et écart des sources Real-Debrid de moins de 50 Mo (`tooSmall`) ; vérifier par un test JVM et par capture du panneau « Langue et qualité » sans ligne « 0 Mo » — *`DebridSourcesTest` « fichiers trop petits écartés » ; fichier en cours (`inAccount`) jamais retiré*
- [x] 2.2 Ligne de source partagée et `SourcesDialog` (origine en badge une fois, titre type · taille, note langues · état, 8 au plus puis « Voir les n autres ») à la place du `groupMenu` texte ; vérifier par capture de l'appui long sur une version et par les tests existants du panneau — *capture : « WEB · 994 Mo · prête PAR DÉFAUT [RD] [H.264] », base de la qualité et nom du fichier en petit*
- [x] 2.3 Panneau du lecteur en paysage tactile (40 %, max 520 dp, fondu en bas) et puce « +n sources » touchable sur les cartes de version ; vérifier par captures tablette en paysage — *puce « +1 source » : ouvre la fenêtre des sources ; panneau du lecteur non recapturé (même composant, largeur seule changée)*

## 3. Fiche en paysage tactile

- [x] 3.1 Fond plein cadre conservé, premier écran à 74 % de la hauteur en paysage tactile (`detailPeek`) pour laisser apparaître la première section ; vérifier par capture d'une fiche film (boutons et début de « Versions disponibles » visibles ensemble, image non découpée) puis rotation en portrait (disposition actuelle) — *capture Clayface en paysage ; la version en deux colonnes, essayée d'abord, a été retirée à la demande de l'utilisateur (image découpée)*

## 4. Titre jamais en double

- [x] 4.1 Recherche redécorée sur `enrich.updates` et titre sous les cartes paysage sans logo en tactile ; vérifier par capture de la recherche « lupin » (aucun titre écrit sur une image, logos qui arrivent) — *capture : titres sous les images, plus aucun titre généré par-dessus*

## 5. Vérification d'ensemble

- [x] 5.1 `gradlew testDebugUnitTest assembleDebug` au vert, relecture par l'agent `android-reviewer`, audit avant / après par `tablet-ux-auditor`, rotation remise à l'état d'origine — *420 tests au vert ; audit avant (11 points) traité ; relecture : 6 problèmes corrigés (voir notes) ; rotation remise*

## Notes de mise en œuvre

- Le titre sous les cartes paysage sans logo ne vaut qu'en tactile ; la TV garde le titre généré dans l'image au focus.
- Corrections après relecture (`android-reviewer`) :
  - seuil des petits fichiers relatif : sous 50 Mio **et** sous le dixième de la plus grosse source du titre (un dessin animé de 35 Mo reste proposé) ; la source en cours de lecture n'est jamais écartée (`keep`) ;
  - recherche : la redécoration repart de la valeur vue avant la décoration (`dropWhile`), une fiche écrite pendant la recherche Real-Debrid n'est plus perdue ;
  - cartes paysage tactiles : ligne de titre toujours réservée (rangées de hauteur égale), comptée dans l'aperçu du clavier de la recherche ; Top 10 paysage : titre dans l'image (`caption = false`) ;
  - fondu du panneau des sources en tactile seulement (la ligne focalisée à la télécommande n'est plus recouverte) ;
  - puce « +n sources » : action TalkBack propre, rappel à jour (`rememberUpdatedState`).

## Workflow follow-up

- Essais Fire TV (repères, carte dévoilée, fenêtre des sources, fiche inchangée) quand la TV est allumée.
- `/opsx:verify tablette-navigation-design`, puis archivage des deux changements (`/opsx:bulk-archive`) une fois les essais faits.
