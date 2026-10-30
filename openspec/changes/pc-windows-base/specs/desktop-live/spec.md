# Spec Delta

## Purpose

Regarder la télévision en direct sur le PC : parcourir les groupes et les chaînes, consulter le guide TV et lire une chaîne, avec les mêmes garanties de connexion que sur Android.

## ADDED Requirements

### Requirement: Chaînes et groupes
L'écran Direct SHALL lister les groupes de la playlist active (groupes masqués exclus, groupes verrouillés protégés par le code parental), puis les chaînes du groupe choisi avec leur logo, leur numéro et le programme en cours, avec une recherche par nom.

#### Scenario: Choix d'un groupe
- **WHEN** l'utilisateur clique sur le groupe « EU | FRANCE GENERALE »
- **THEN** ses chaînes s'affichent avec le programme en cours et sa progression

#### Scenario: Groupe verrouillé
- **WHEN** l'utilisateur ouvre un groupe verrouillé sur la tablette
- **THEN** le code parental est demandé avant d'afficher les chaînes

### Requirement: Guide TV
L'écran Guide TV SHALL afficher une grille chaînes × heures (aujourd'hui et les jours suivants présents dans le guide), défilable à la souris et au clavier, avec le détail d'un programme au survol ou à la sélection.

#### Scenario: Programme de ce soir
- **WHEN** l'utilisateur fait défiler la grille jusqu'à 21 h et clique sur un programme
- **THEN** son titre, son horaire et son résumé s'affichent, avec « Regarder » si le programme est en cours

### Requirement: Lecture en direct
Choisir une chaîne SHALL la lire dans le lecteur VLC intégré, en fenêtre ou en plein écran, avec zapping (flèches haut/bas, molette, Page préc./suiv.), choix de la piste audio et des sous-titres, format d'image et reconnexion automatique en cas de coupure.

#### Scenario: Zapping au clavier
- **WHEN** l'utilisateur appuie sur flèche bas pendant la lecture
- **THEN** la chaîne suivante du groupe se lance et le bandeau affiche son nom et son programme

#### Scenario: Plein écran
- **WHEN** l'utilisateur double-clique sur l'image ou appuie sur F
- **THEN** la vidéo passe en plein écran ; Échap revient à la fenêtre

### Requirement: Une seule connexion à l'abonnement
L'app PC SHALL n'ouvrir qu'un flux à la fois vers le fournisseur IPTV : changer de chaîne ferme le flux précédent avant d'ouvrir le suivant, et fermer le lecteur ou réduire la fenêtre au-delà de la pause du direct libère la connexion.

#### Scenario: Zapping rapide
- **WHEN** l'utilisateur change trois fois de chaîne en deux secondes
- **THEN** un seul flux est ouvert à la fin, sur la dernière chaîne choisie

### Requirement: Mise à jour des chaînes et du guide
L'app PC SHALL synchroniser les chaînes et le guide de la playlist active au lancement s'ils datent de plus de 12 h, et à la demande depuis Réglages, sans bloquer l'interface.

#### Scenario: Lancement le matin
- **WHEN** l'utilisateur ouvre Sokoul et que le guide date de la veille
- **THEN** la liste s'affiche aussitôt avec les données existantes et le guide se met à jour en arrière-plan
