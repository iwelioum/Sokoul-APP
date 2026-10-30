# Spec Delta

## Purpose

Montrer d'un coup d'œil les informations clés d'une œuvre (année, durée, heure de fin, saisons, genres, tranche d'âge) avec des icônes et un badge d'âge coloré, de la même façon dans le héros, les carrousels et les fiches.

## ADDED Requirements

### Requirement: Repères avec icônes
Le héros des écrans Accueil, Films et Séries, la carte dévoilée d'un carrousel et la ligne d'informations d'une fiche SHALL afficher l'année, la durée (films), l'heure de fin (films) et le nombre de saisons (séries), chacun précédé de son icône. Les genres sont affichés en puces discrètes, sans icône.

#### Scenario: Héros d'un film (tablette)
- **WHEN** le héros présente un film de 2026 de 1 h 49 qui finit à 12 h 18
- **THEN** on lit, chacun avec son icône, « 2026 », « 1 h 49 » et « Finit à 12h18 », suivis des genres en puces

#### Scenario: Série
- **WHEN** la carte dévoilée présente une série de 3 saisons
- **THEN** elle affiche « 3 saisons » avec l'icône des saisons et sans durée

### Requirement: Badge d'âge coloré
La tranche d'âge SHALL être affichée en badge dont la couleur dépend de la tranche : Tous publics, -10, -12, -16 et -18 ont chacune une couleur distincte, et le texte du badge reste lisible sur chacune.

#### Scenario: Film déconseillé aux moins de 12 ans
- **WHEN** la classification du film est « 12 »
- **THEN** le badge affiche « -12 » dans la couleur de la tranche -12

#### Scenario: Classification inconnue
- **WHEN** l'œuvre n'a pas de classification connue
- **THEN** aucun badge d'âge n'est affiché

### Requirement: Repères sans débordement
Quand les repères ne tiennent pas sur une ligne, ils SHALL passer à la ligne par éléments entiers, sans séparateur orphelin en début de ligne ni texte coupé au milieu d'un repère.

#### Scenario: Tablette en portrait
- **WHEN** la ligne d'informations d'une fiche dépasse la largeur en portrait
- **THEN** la deuxième ligne commence par un repère complet (icône et texte), jamais par « · »

### Requirement: Même lisibilité sur TV
Sur TV, les repères SHALL garder la taille de texte actuelle des informations et rester lisibles à distance ; seules les icônes et le badge coloré s'ajoutent.

#### Scenario: Héros sur Fire TV
- **WHEN** le héros s'affiche sur la Fire TV
- **THEN** la ligne d'informations garde la même hauteur de texte qu'avant, avec les icônes en plus
