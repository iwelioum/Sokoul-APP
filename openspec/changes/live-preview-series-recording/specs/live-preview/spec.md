# Spec Delta

## Purpose

Montrer en petit ce que diffuse la chaîne sélectionnée dans l'écran Direct, sans ouvrir le plein écran ni dépasser la limite de connexions de l'abonnement.

## ADDED Requirements

### Requirement: Aperçu de la chaîne sélectionnée
Sur TV, quand le focus reste au moins 800 ms sur une chaîne de l'écran Direct, l'app SHALL lire cette chaîne en petit en haut du panneau d'informations, son coupé. Changer de chaîne remplace l'aperçu.

#### Scenario: Focus posé sur une chaîne
- **WHEN** l'utilisateur s'arrête 1 s sur une chaîne
- **THEN** son image s'affiche dans le panneau d'informations, sans le son

#### Scenario: Défilement rapide
- **WHEN** l'utilisateur fait défiler la liste sans s'arrêter
- **THEN** aucun flux n'est ouvert pour les chaînes traversées

### Requirement: Une seule connexion à la fois
L'aperçu SHALL être arrêté avant l'ouverture du plein écran, et SHALL ne pas démarrer pendant un enregistrement ni pendant la pause imposée après un refus du fournisseur.

#### Scenario: Ouverture du plein écran
- **WHEN** l'utilisateur appuie sur OK pendant l'aperçu
- **THEN** l'aperçu est arrêté puis le plein écran s'ouvre sur la même chaîne

#### Scenario: Enregistrement en cours
- **WHEN** un enregistrement occupe la connexion
- **THEN** l'aperçu ne démarre pas et le panneau affiche le logo et le programme comme avant

### Requirement: Aperçu désactivable
Réglages › Télévision SHALL proposer d'activer ou de désactiver l'aperçu ; désactivé, l'écran Direct se comporte comme avant.

#### Scenario: Aperçu désactivé
- **WHEN** l'utilisateur désactive l'aperçu
- **THEN** aucun flux n'est ouvert depuis l'écran Direct tant qu'on n'appuie pas sur OK

### Requirement: Aperçu arrêté quand l'écran n'est plus visible
L'aperçu SHALL s'arrêter dès que l'écran Direct est quitté ou que l'app passe en arrière-plan.

#### Scenario: Touche Accueil
- **WHEN** l'utilisateur appuie sur Accueil pendant l'aperçu
- **THEN** la connexion de l'aperçu est fermée
