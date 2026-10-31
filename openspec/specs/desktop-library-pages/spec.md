# desktop-library-pages Specification

## Purpose
Pages de bibliothèque de la tablette sur PC : « À venir », « Historique », page d'une saga et section saga de la fiche.

## Requirements

### Requirement: À venir
« À venir » (bouton de Ma liste) SHALL lister les prochains épisodes annoncés par TMDB des séries de Ma liste et des séries commencées, par date, avec le jour et la saison/épisode. Sans épisode annoncé, l'état vide de la tablette s'affiche : « Aucun épisode annoncé ».

#### Scenario: Sans épisode
- **WHEN** aucune série suivie n'a d'épisode annoncé
- **THEN** la page affiche « Aucun épisode annoncé » et l'explication de la tablette

#### Scenario: Tri
- **WHEN** deux séries ont des épisodes annoncés le 3 et le 1er novembre
- **THEN** celui du 1er novembre est en premier, vérifié par un test JVM

### Requirement: Historique
« Historique » SHALL lister tout ce qui a été regardé (terminé compris), du plus récent au plus ancien, groupé par jour (« Aujourd'hui », « Hier », date). Un clic ouvre la fiche.

#### Scenario: Film terminé
- **WHEN** un film a été vu jusqu'au bout hier
- **THEN** il apparaît sous « Hier » dans l'Historique alors qu'il n'est plus dans « Reprendre »

### Requirement: En-tête d'une saga
L'en-tête de la page d'une saga SHALL reprendre celui de la tablette : fond, kicker « SAGA », logo, repères (films, années, note moyenne, au catalogue, séries), bouton « Commencer · ordre de sortie » (« Continuer » si un film est entamé) et « Prochain : *film (année)* ».

#### Scenario: Repères
- **WHEN** l'utilisateur ouvre la saga Star Wars
- **THEN** il voit « 11 films · 1977 → 2019 · ★ … de moyenne · … au catalogue » sous le logo

### Requirement: Films d'une saga
La page d'une saga SHALL afficher le résumé, une frise numérotée par année avec les affiches, puis les séries de l'univers. Quand la saga a un ordre du récit (`curated_sagas.json`), un choix « Ordre de sortie » ou « Ordre du récit » SHALL réordonner la frise.

#### Scenario: Star Wars
- **WHEN** l'utilisateur ouvre la saga Star Wars
- **THEN** il voit « Ordre du récit » et, une fois choisi, Épisode I en premier (capture comparée à `ref/saga-page.png`)

#### Scenario: Prochain film
- **WHEN** les trois premiers films sont vus jusqu'au bout
- **THEN** « Prochain » indique le quatrième, vérifié par un test JVM

### Requirement: Fiche enrichie
La fiche SHALL afficher « Finit à HHhMM » (heure actuelle + durée), « Aussi sur » (plateformes TMDB en France) et, pour un film d'une collection, la section « Saga : *nom* » (« Toute l'histoire, dans l'ordre de sortie. ») avec les films de la collection.

#### Scenario: Film d'une saga
- **WHEN** l'utilisateur ouvre Star Wars : Le Réveil de la Force
- **THEN** il voit « Aussi sur Disney+ », « Finit à » et la rangée « Saga : Star Wars »
