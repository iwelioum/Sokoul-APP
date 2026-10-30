# Spec Delta

## Purpose

Parcourir films et séries, ouvrir une fiche plein cadre et trouver quoi lire.

## ADDED Requirements

### Requirement: Films et Séries
Les écrans Films et Séries SHALL :
- présenter une grille d'affiches TMDB (découverte) avec filtre par genre et tri (populaires, mieux notés, récents) ;
- charger la page suivante en arrivant en bas.

#### Scenario: Filtre
- **WHEN** l'utilisateur choisit le genre « Science-Fiction »
- **THEN** la grille ne montre que des œuvres de ce genre

### Requirement: Fiche plein cadre
La fiche SHALL occuper toute la fenêtre, avec une image de fond plein cadre et un dégradé, sans jamais se découper en colonnes. Elle montre :
- le logo ou le titre ;
- les repères avec icônes et le badge d'âge ;
- le slogan et le résumé ;
- les boutons Lecture, Sources et Bande-annonce ;
- puis, au défilement : saisons et épisodes (séries), distribution, suggestions.

#### Scenario: Ouvrir une fiche
- **WHEN** l'utilisateur clique une affiche
- **THEN** la fiche du titre s'ouvre en plein cadre, et Échap ramène à la page précédente

#### Scenario: Série
- **WHEN** la fiche est celle d'une série
- **THEN** un sélecteur de saison montre les épisodes (vignette, numéro, titre, durée, résumé), et chacun se lance d'un clic

### Requirement: Recherche
La recherche SHALL interroger, pendant la frappe (après 300 ms), TMDB (films et séries) et les chaînes IPTV, et présenter les résultats par rangées.

#### Scenario: Recherche d'un film
- **WHEN** l'utilisateur tape « dune »
- **THEN** les films et séries correspondants apparaissent avec leur affiche, et un clic ouvre la fiche
