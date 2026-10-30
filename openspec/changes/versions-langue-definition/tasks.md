# Tasks

## 1. Lecture des noms

- [x] 1.1 Tests JVM dans `:core` pour les trois exigences : DUAL turc, DUAL TRUEFRENCH, UHD + 1080p, UHD seul, liste avec FRE. Trois échouaient avant la correction (`ReleaseNameTest`).
- [x] 1.2 Corriger `ReleaseName.language()` (DUAL, FRE, FRA) et `ReleaseName.quality()` (résolution écrite d'abord). `:core:test` au vert.
- [x] 1.3 Trouvé pendant l'essai 2.2 : la lecture automatique sur PC n'essayait que les 3 premières versions, toutes « pas prêtes ». Elle fait désormais 3 essais au plus, pris dans toute la liste, avec seulement des versions annoncées prêtes après un « pas prête » (`AppState.playTitle`).
- [x] 1.4 Trouvé pendant l'essai 2.2 : une « MULTI » d'un groupe étranger (BTM, 11 langues sans français) passait. Le nom ne le dit pas, donc le PC vérifie les pistes à l'ouverture : une version annoncée MULTI ou VF sans piste française laisse la place à la suivante. Elle reste lue si rien d'autre ne passe, et une version choisie à la main n'est jamais remplacée (`AudioChoice.hasFrench`, avec test).

## 2. Vérification

- [x] 2.1 `testDebugUnitTest` Android (420 tests), `:core:test` (4) et `:desktop:test` (15) au vert.
- [x] 2.2 Essai réel sur PC : le fichier HDM n'est plus en tête des MULTI 4K, et Inception démarre en français (« VFF AC3 5.1 - [French] », capture l3), son coupé.
- [x] 2.3 APK debug installé sur la tablette. L'app démarre, la recherche et la fiche d'Inception (versions, distribution, suggestions) s'affichent sans erreur. Pas de lecture lancée sur la tablette.
- [x] 2.4 Réinstallation sur PC, commit et push.
