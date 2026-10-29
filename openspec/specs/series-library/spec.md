# series-library Specification

## Purpose
Suivre ses séries et ce qu'on a regardé : calendrier des sorties, historique, alerte de nouvel épisode, filtre du profil Enfant.

## Requirements

### Requirement: Calendrier « À venir »
Ma liste › À venir SHALL lister, jour par jour, le prochain épisode annoncé (TVmaze) des séries de Ma liste et des séries commencées encore en production, une ligne par série même présente en plusieurs versions.

#### Scenario: Série terminée
- **WHEN** TMDB indique qu'une série suivie n'est plus en production
- **THEN** elle n'apparaît pas dans le calendrier

#### Scenario: Aucun épisode annoncé
- **WHEN** aucune série suivie n'a d'épisode annoncé
- **THEN** la page explique comment remplir le calendrier

### Requirement: Historique de visionnage
Ma liste › Historique SHALL lister films, épisodes et chaînes regardés (30 s au moins), regroupés par jour, avec l'état (vu ou pourcentage), et permettre d'ouvrir la fiche ou la chaîne, de retirer une ligne ou de tout effacer sans toucher aux marquages manuels.

#### Scenario: Ouvrir un épisode
- **WHEN** l'utilisateur touche un épisode de l'historique
- **THEN** la fiche de la série s'ouvre

### Requirement: Notification de nouvel épisode
Après chaque synchronisation, l'app SHALL notifier une seule fois chaque mise à jour d'une série suivie ayant du nouveau depuis le dernier visionnage ; jamais en profil Enfant. L'appui SHALL ouvrir la fiche de la série.

#### Scenario: Même mise à jour deux fois
- **WHEN** deux synchronisations voient la même mise à jour d'une série
- **THEN** une seule notification est envoyée

### Requirement: Filtre du profil Enfant partout
En profil Enfant, les œuvres classées 12 ans et plus ou de genre adulte SHALL être retirées de l'accueil, des grilles « Tout voir », d'Explorer (« Surprends-moi » compris) et de la recherche ; la recherche n'y propose pas de titres Real-Debrid.

#### Scenario: Recherche en profil Enfant
- **WHEN** un profil Enfant cherche un titre classé -12
- **THEN** ce titre n'apparaît pas dans les résultats
