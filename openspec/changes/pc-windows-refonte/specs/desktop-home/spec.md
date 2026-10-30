# Spec Delta

## Purpose

Un accueil de bureau riche, alimenté par TMDB, utilisable même sans abonnement IPTV.

## ADDED Requirements

### Requirement: Héros tournant
L'accueil SHALL ouvrir sur un héros plein cadre tiré des tendances TMDB du jour. Il comprend :
- l'image de fond, puis le logo du titre, sinon son titre ;
- les repères (année, note, genres) et le résumé ;
- les boutons Lecture et Plus d'infos.

Le héros SHALL changer toutes les 9 secondes, avec des points de pagination cliquables. Il s'arrête au survol.

#### Scenario: Héros
- **WHEN** l'accueil s'ouvre avec une clé TMDB valide
- **THEN** un film ou une série en tendance occupe le haut de l'écran, avec son image et son logo

### Requirement: Rangées éditoriales
Sous le héros, l'accueil SHALL proposer des rangées :
- Reprendre (si des lectures sont en cours) ;
- Top 10 du jour ;
- Films populaires, Séries populaires, Mieux notés ;
- au moins deux plateformes (Netflix, Prime Video, Disney+, Apple TV+).

Ces rangées sont chargées en parallèle et gardées en cache, et se remplissent avec des squelettes pendant le chargement.

#### Scenario: Sans IPTV
- **WHEN** aucune playlist n'est configurée
- **THEN** l'accueil TMDB s'affiche quand même, et un bandeau discret propose d'ajouter une playlist

#### Scenario: Sans clé TMDB
- **WHEN** la clé TMDB est absente ou refusée
- **THEN** l'accueil indique comment la renseigner dans Réglages › Clés API, au lieu d'un écran vide
