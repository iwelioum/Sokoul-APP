# Spec Delta

## Purpose

Adapter la disposition de la fiche d'un film ou d'une série à l'appareil, sans casser le fond plein cadre de l'œuvre, pour qu'on voie dès l'ouverture qu'il y a une suite (versions, épisodes) sous les boutons.

## ADDED Requirements

### Requirement: Première section visible en paysage tactile
Sur tablette en paysage, la fiche SHALL garder l'image de l'œuvre en plein cadre derrière le titre, les repères, le résumé et les boutons, et le premier écran SHALL laisser apparaître le début de la première section (« Versions disponibles » ou « Épisodes ») sur ce même fond, sans découper l'écran en colonnes.

#### Scenario: Fiche film en paysage (tablette)
- **WHEN** l'utilisateur ouvre la fiche d'un film qui a plusieurs versions, la tablette tenue en paysage
- **THEN** l'image occupe tout l'écran, et le titre « Versions disponibles » avec le haut des cartes de versions est visible sous les boutons sans défilement

#### Scenario: Fiche série en paysage (tablette)
- **WHEN** l'utilisateur ouvre la fiche d'une série en paysage
- **THEN** le début de la section « Épisodes » apparaît en bas du premier écran, sur l'image de la série

### Requirement: Dispositions inchangées ailleurs
En portrait, sur téléphone et sur TV, la fiche SHALL garder sa disposition actuelle.

#### Scenario: Rotation vers le portrait
- **WHEN** l'utilisateur tourne la tablette en portrait sur une fiche
- **THEN** la fiche reprend la disposition plein écran actuelle et reste sur la même œuvre

#### Scenario: Fire TV
- **WHEN** la fiche s'ouvre sur la Fire TV
- **THEN** la disposition et la navigation à la télécommande sont les mêmes qu'avant
