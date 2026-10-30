# Spec Delta

## Purpose

Régler au doigt, pendant la lecture, la luminosité, le volume et le remplissage de l'écran, comme dans les applications vidéo mobiles, sans gêner le zapping du direct.

## ADDED Requirements

### Requirement: Luminosité au doigt
Sur écran tactile, glisser verticalement sur le tiers gauche de l'image SHALL augmenter (vers le haut) ou diminuer (vers le bas) la luminosité de l'écran pendant la lecture. Le réglage vaut pour la séance de lecture et l'écran retrouve sa luminosité habituelle en quittant le lecteur.

#### Scenario: Glisser vers le haut à gauche (tablette)
- **WHEN** l'utilisateur glisse vers le haut sur le tiers gauche pendant un film
- **THEN** l'écran devient plus lumineux et un indicateur de luminosité affiche le niveau

#### Scenario: Sortie du lecteur
- **WHEN** l'utilisateur quitte le lecteur après avoir baissé la luminosité
- **THEN** l'écran retrouve la luminosité réglée dans Android

### Requirement: Volume au doigt
Sur écran tactile, glisser verticalement sur le tiers droit de l'image SHALL augmenter ou diminuer le volume média de l'appareil, avec un indicateur de niveau.

#### Scenario: Glisser vers le bas à droite (tablette)
- **WHEN** l'utilisateur glisse vers le bas sur le tiers droit
- **THEN** le volume média baisse et l'indicateur affiche le niveau, jusqu'au silence au minimum

### Requirement: Zapping conservé au centre
En direct, glisser verticalement au centre de l'image SHALL toujours changer de chaîne comme avant ; en VOD, le centre ne réagit pas au glissement vertical.

#### Scenario: Zapping en direct (tablette)
- **WHEN** l'utilisateur glisse vers le haut au centre de l'image en direct
- **THEN** la chaîne change, dans le sens réglé dans Réglages, sans toucher au volume ni à la luminosité

### Requirement: Pincer pour remplir l'écran
En paysage sur écran tactile, pincer l'image (écarter deux doigts) SHALL passer le format d'image à « Remplir (rogner) » et le rapprocher SHALL revenir à « Adapter » ; le format choisi est mémorisé comme le réglage de format existant. En portrait, le format reste « Adapter ».

#### Scenario: Écarter deux doigts en paysage
- **WHEN** l'utilisateur écarte deux doigts sur l'image d'un film en paysage
- **THEN** l'image remplit l'écran, un message bref indique « Remplir », et le prochain film s'ouvre dans ce format

#### Scenario: Portrait
- **WHEN** l'utilisateur pince l'image en portrait
- **THEN** le format ne change pas

### Requirement: Gestes inactifs quand un panneau est ouvert
Les gestes de luminosité, de volume, de zapping et de pincement SHALL être ignorés tant qu'une liste, un panneau ou un menu du lecteur est ouvert.

#### Scenario: Panneau Langue et qualité ouvert
- **WHEN** l'utilisateur fait défiler le panneau « Langue et qualité » du doigt
- **THEN** ni le volume ni la luminosité ne changent

### Requirement: Pas de gestes sur TV
Sur TV (télécommande), aucun de ces gestes SHALL être actif ; les touches gardent leur rôle actuel.

#### Scenario: Fire TV
- **WHEN** le lecteur est ouvert sur la Fire TV
- **THEN** les touches ▲▼◀▶ et OK fonctionnent comme avant et aucun indicateur de geste n'apparaît
