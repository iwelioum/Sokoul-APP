# Spec Delta

## Purpose

Choisir l'image et la façon d'afficher le titre des cartes d'œuvres pour que le titre ne soit jamais lu deux fois et que les images restent propres.

## ADDED Requirements

### Requirement: Titre jamais en double sur une carte paysage
Une carte paysage SHALL afficher le logo de l'œuvre sur une image sans texte quand ils sont connus. Sinon, le titre est écrit sous l'image et non par-dessus, pour ne pas doubler un titre déjà incrusté dans l'image.

#### Scenario: Résultat de recherche sans logo connu
- **WHEN** la recherche « lupin » montre une carte dont seul le fond fourni par la liste est connu
- **THEN** le titre de l'œuvre apparaît sous l'image, et l'image n'est recouverte d'aucun texte

#### Scenario: Logo connu
- **WHEN** le logo et une image sans texte de l'œuvre sont connus
- **THEN** la carte affiche le logo sur cette image, sans titre écrit en plus

### Requirement: Images complétées à la volée
Pour les cartes paysage affichées sans logo, l'app SHALL récupérer l'image sans texte et le logo de l'œuvre, en limitant le nombre de requêtes simultanées et en gardant le résultat pour les affichages suivants.

#### Scenario: Défilement d'une rangée de résultats
- **WHEN** l'utilisateur fait défiler une rangée de 20 cartes sans logo
- **THEN** les cartes visibles passent au logo dès qu'il est trouvé, sans plus de 3 requêtes en même temps, et une deuxième recherche identique n'en refait aucune
