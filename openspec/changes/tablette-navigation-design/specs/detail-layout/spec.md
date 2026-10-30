# Spec Delta

## Purpose

Adapter la disposition de la fiche d'un film ou d'une série à l'appareil, pour que l'essentiel (titre, repères, boutons, versions ou épisodes) soit visible sans faire défiler une page entière.

## ADDED Requirements

### Requirement: Deux colonnes en paysage tactile
Sur tablette en paysage, la fiche SHALL être disposée en deux colonnes : à gauche, l'image de l'œuvre, le titre ou logo, les repères, le résumé et les boutons ; à droite, défilant indépendamment, les sections (versions, épisodes, distribution, saga, suggestions).

#### Scenario: Fiche film en paysage (tablette)
- **WHEN** l'utilisateur ouvre la fiche d'un film qui a plusieurs versions, la tablette tenue en paysage
- **THEN** le bouton Lecture et l'étagère « Versions disponibles » sont visibles ensemble, sans défilement

#### Scenario: Fiche série en paysage (tablette)
- **WHEN** l'utilisateur ouvre la fiche d'une série en paysage
- **THEN** la liste des épisodes de la saison s'affiche dans la colonne de droite, en liste verticale, à côté du titre et des boutons

### Requirement: Dispositions inchangées ailleurs
En portrait, sur téléphone et sur TV, la fiche SHALL garder sa disposition actuelle.

#### Scenario: Rotation vers le portrait
- **WHEN** l'utilisateur tourne la tablette en portrait sur une fiche
- **THEN** la fiche reprend la disposition plein écran actuelle et reste sur la même œuvre

#### Scenario: Fire TV
- **WHEN** la fiche s'ouvre sur la Fire TV
- **THEN** la disposition et la navigation à la télécommande sont les mêmes qu'avant
