# player-comfort Specification

## Purpose
Réglages de confort pendant la lecture : vitesse, minuterie, sous-titres, décalage du son.

## Requirements

### Requirement: Vitesse de lecture
Le menu ⋯ d'un film ou d'un épisode SHALL proposer les vitesses ×0,5 à ×2, conservées si le lecteur est recréé pendant la séance. Le direct reste à ×1.

#### Scenario: Passage à ×1,5
- **WHEN** l'utilisateur choisit ×1,5
- **THEN** la lecture passe à 1,5 et le menu affiche « Vitesse : ×1,5 »

### Requirement: Minuterie de mise en veille
Le menu ⋯ SHALL proposer une minuterie de 15, 30, 45, 60 ou 90 minutes, ou « fin de l'épisode / du film ». À l'échéance, la lecture se met en pause, la position est enregistrée et le lecteur se ferme.

#### Scenario: Fin de l'épisode
- **WHEN** la minuterie « fin de l'épisode » est active et que l'épisode se termine
- **THEN** l'épisode suivant n'est pas lancé et le lecteur se ferme

### Requirement: Apparence des sous-titres
Réglages › Lecture SHALL permettre de choisir la taille, la couleur (blanc ou jaune), la lisibilité (contour, fond noir, ombre) et la position (basse ou relevée), appliquées aux sous-titres du fichier, en ligne et décalés.

#### Scenario: Sous-titres grands et jaunes
- **WHEN** l'utilisateur choisit « Grande » et « Jaune »
- **THEN** les sous-titres affichés pendant la lecture sont grands, jaunes, avec contour

### Requirement: Décalage du son
Le menu ⋯ SHALL permettre de retarder le son de 100 à 1 500 ms sans fausser l'horloge du lecteur ; sans effet quand le son est envoyé tel quel à l'ampli, ce que le menu indique.

#### Scenario: Son en avance
- **WHEN** l'utilisateur choisit « son retardé de 500 ms »
- **THEN** la lecture continue sans erreur et le son est retardé de 500 ms
