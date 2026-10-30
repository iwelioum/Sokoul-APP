# Spec Delta

## Purpose

Lire films et épisodes via Real-Debrid depuis le PC, avec le même choix de versions que sur la tablette.

## ADDED Requirements

### Requirement: Recherche des versions
Pour une œuvre TMDB, l'app SHALL :
- obtenir l'identifiant IMDb ;
- interroger en parallèle les extensions Torrentio et Comet configurées avec le jeton Real-Debrid (10 s au plus chacune) ;
- fusionner les résultats sans doublon et écarter les versions filmées en salle, les autres œuvres et les échantillons.

Le jeton n'apparaît jamais dans un journal ni à l'écran.

#### Scenario: Film disponible
- **WHEN** l'utilisateur ouvre Sources sur un film récent
- **THEN** une liste de versions s'affiche, chacune avec sa définition, son HDR, sa langue, son origine (Remux, Blu-ray, WEB) et sa taille

### Requirement: Classement et Lecture
Les versions SHALL être classées dans cet ordre :
1. langue préférée (VF, puis MULTI, VOSTFR, VO) ;
2. définition ;
3. HDR ;
4. origine du fichier ;
5. taille.

« Lecture » SHALL lancer la première version, et la fenêtre Sources permet d'en choisir une autre.

#### Scenario: Lecture directe
- **WHEN** l'utilisateur clique Lecture sur la fiche d'un film
- **THEN** la meilleure version se lance dans le lecteur, sans autre choix à faire

#### Scenario: Aucune version
- **WHEN** aucune extension ne répond ou ne trouve de version
- **THEN** l'app l'indique clairement (« Aucune version trouvée sur Real-Debrid ») sans bloquer la fiche

### Requirement: Compte Real-Debrid
Réglages SHALL afficher le compte Real-Debrid : nom, premium ou non, nombre de jours restants.

#### Scenario: Compte premium
- **WHEN** la clé Real-Debrid est valide
- **THEN** Réglages affiche le nom du compte et « Premium, N jours restants »
