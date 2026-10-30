# Spec Delta

## Purpose

Brancher les services externes sans configuration manuelle sur le PC, tout en gardant les clés secrètes.

## ADDED Requirements

### Requirement: Clés intégrées
L'app de bureau SHALL lire les clés API d'une classe générée au build depuis `local.properties`, ignorée par Git. Les services concernés sont TMDB, Real-Debrid, OMDb, fanart.tv, TheTVDB, TVmaze, Watchmode, TasteDive, Movie of the Night, OpenSubtitles et Subdl.

Une clé saisie dans Réglages SHALL remplacer la clé intégrée. Elle est enregistrée dans les données protégées de l'utilisateur.

#### Scenario: App installée
- **WHEN** l'app est lancée depuis le raccourci du Bureau, hors du dossier du projet
- **THEN** TMDB et Real-Debrid fonctionnent sans rien saisir

### Requirement: État des services
Réglages › Clés API SHALL montrer pour chaque service :
- son état (configuré ou absent) et l'origine de la clé ;
- un bouton « Tester » qui fait un vrai appel et affiche « Connexion réussie » ou la raison de l'échec.

Une clé n'est jamais affichée en entier : au plus ses 4 derniers caractères.

#### Scenario: Test TMDB
- **WHEN** l'utilisateur clique Tester sur TMDB
- **THEN** l'app appelle TMDB et affiche « Connexion réussie » en vert

#### Scenario: Erreur sans fuite
- **WHEN** un appel échoue
- **THEN** le message montre le code HTTP ou « Réseau indisponible », sans l'adresse ni la clé
