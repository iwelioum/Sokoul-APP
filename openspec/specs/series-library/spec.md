# Séries et bibliothèque

## Purpose

Décrire le calendrier, l'historique, les nouveaux épisodes et la reprise des séries suivies.

## Requirements

### Requirement: Calendrier des séries suivies
Le système SHALL agréger les séries de Ma liste et les séries commencées, puis afficher leur prochain épisode connu.

#### Scenario: Prochain épisode
- **WHEN** TVmaze fournit un prochain épisode pour une série suivie
- **THEN** il apparaît dans la page Calendrier et la rangée À venir, trié par date

### Requirement: Historique
Le système SHALL afficher les progressions passées avec leur date et permettre leur suppression.

#### Scenario: Supprimer une entrée
- **WHEN** l'utilisateur supprime un élément de l'historique
- **THEN** sa progression est retirée sans supprimer le catalogue

### Requirement: Nouvel épisode
Après une synchronisation, le système SHALL détecter les épisodes apparus depuis la synchronisation précédente et les signaler dans la bibliothèque.

#### Scenario: Épisode ajouté
- **WHEN** un épisode inédit est trouvé pour une série suivie
- **THEN** sa carte porte la pastille Nouveau et il peut être affiché dans la rangée dédiée
