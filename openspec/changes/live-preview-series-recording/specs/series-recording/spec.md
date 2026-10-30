# Spec Delta

## Purpose

Enregistrer automatiquement toutes les diffusions d'une émission ou d'une série sur une chaîne, d'après le guide TV, sans programmer chaque épisode à la main.

## ADDED Requirements

### Requirement: Créer une règle « tous les épisodes »
Le menu d'un programme à venir ou en cours (guide et direct) SHALL proposer « Enregistrer tous les épisodes », qui crée une règle associant le titre de l'émission (sans casse ni accents) et la chaîne.

#### Scenario: Depuis le guide
- **WHEN** l'utilisateur choisit « Enregistrer tous les épisodes » sur « Le Journal de 20h » de France 2
- **THEN** une règle est créée et toutes les diffusions à venir de ce titre sur France 2 présentes dans le guide sont programmées

#### Scenario: Règle déjà existante
- **WHEN** une règle existe déjà pour ce titre et cette chaîne
- **THEN** aucune règle en double n'est créée et l'utilisateur en est informé

### Requirement: Programmation à chaque rafraîchissement du guide
Après chaque synchronisation du guide, chaque règle SHALL programmer les nouvelles diffusions correspondantes, avec les mêmes marges qu'un enregistrement manuel (2 min avant, 5 min après), sans programmer deux fois la même diffusion.

#### Scenario: Nouvel épisode dans le guide
- **WHEN** le guide rafraîchi contient une nouvelle diffusion du titre suivi
- **THEN** elle est programmée une seule fois

### Requirement: Règles d'enregistrement respectées
Une diffusion trouvée par une règle SHALL être soumise aux mêmes contrôles qu'un enregistrement manuel (pas de chevauchement sur la même chaîne, limite de connexions, 12 h au plus). Une diffusion refusée est ignorée sans bloquer les autres.

#### Scenario: Chevauchement
- **WHEN** une diffusion suivie chevauche un enregistrement déjà programmé sur une autre chaîne et que l'abonnement n'autorise qu'une connexion
- **THEN** cette diffusion n'est pas programmée et la règle indique « 1 diffusion ignorée (conflit) »

#### Scenario: Émission en deux parties
- **WHEN** deux diffusions du titre suivi se suivent sur la même chaîne (18:35–19:10 puis 19:10–19:55)
- **THEN** la seconde commence à la fin de l'enregistrement de la première (19:15, sans marge avant) et aucune n'est ignorée

#### Scenario: Épisode déjà en cours d'enregistrement
- **WHEN** la règle est créée alors que l'épisode du moment s'enregistre déjà depuis son milieu
- **THEN** il n'est ni programmé une deuxième fois ni compté comme conflit

### Requirement: Gestion des règles
L'écran Enregistrements SHALL lister les règles (titre, chaîne, prochaine diffusion programmée) et permettre d'en supprimer une ; supprimer une règle annule ses enregistrements à venir non commencés et garde ceux déjà faits.

#### Scenario: Suppression d'une règle
- **WHEN** l'utilisateur supprime une règle qui a 3 diffusions programmées et 2 enregistrements terminés
- **THEN** les 3 programmations sont annulées et les 2 enregistrements restent disponibles

#### Scenario: Épisode en cours (tablette)
- **WHEN** un épisode de la règle est en train d'être enregistré
- **THEN** la règle affiche « en cours jusqu'à HH:MM » et sa suppression laisse cet enregistrement continuer

### Requirement: Règles conservées
Les règles SHALL survivre au redémarrage de l'appareil et à la mise à jour de l'app, et figurer dans la sauvegarde de configuration.

#### Scenario: Redémarrage
- **WHEN** l'appareil redémarre
- **THEN** les règles existent toujours et les diffusions programmées sont réarmées
