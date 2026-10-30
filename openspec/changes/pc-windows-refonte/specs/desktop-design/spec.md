# Spec Delta

## Purpose

Donner à l'app Windows la même identité visuelle que l'app Android, adaptée à la souris et au clavier.

## ADDED Requirements

### Requirement: Charte Sokoul
L'app de bureau SHALL reprendre la charte de l'app Android :
- polices Manrope (texte) et DM Serif Display (titres éditoriaux), embarquées ;
- fond quasi noir et accent violet de `SokoulColors` ;
- rayons et espaces des jetons Android.

Elle ne SHALL afficher aucun composant Material à son style par défaut.

#### Scenario: Premier coup d'œil
- **WHEN** l'utilisateur ouvre l'accueil
- **THEN** il voit un héros plein cadre avec image, logo ou titre en DM Serif, repères avec icônes et boutons Sokoul, et non une liste de formulaires

### Requirement: Survol et focus
Chaque élément cliquable SHALL :
- montrer un état de survol (agrandissement léger ou éclaircissement) et le curseur main ;
- montrer un contour de focus visible au clavier (Tab).

#### Scenario: Survol d'une carte
- **WHEN** la souris passe sur une affiche
- **THEN** la carte grandit légèrement, son contour s'éclaire et le curseur devient une main

### Requirement: Rangées à la souris
Les rangées horizontales SHALL défiler :
- à la molette (verticale convertie en horizontale au survol) ;
- avec des flèches de bord qui apparaissent au survol.

#### Scenario: Molette sur une rangée
- **WHEN** l'utilisateur tourne la molette au-dessus d'une rangée
- **THEN** la rangée défile horizontalement, et la page ne défile pas

### Requirement: Navigation du haut
Une barre de navigation en haut SHALL donner accès à Accueil, Films, Séries, Direct, Guide, Recherche et Réglages, avec l'onglet courant marqué.
- Elle est translucide au-dessus du héros, et opaque après défilement.
- Échap revient à l'écran précédent.
- La touche `/` ouvre la recherche.

#### Scenario: Retour
- **WHEN** l'utilisateur est sur une fiche et appuie sur Échap
- **THEN** il revient à l'écran d'où il l'a ouverte, à la même position de défilement
