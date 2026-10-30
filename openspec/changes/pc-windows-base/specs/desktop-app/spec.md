# Spec Delta

## Purpose

Fournir Sokoul comme une application Windows à part entière : installée, lancée depuis le menu Démarrer, pensée pour la souris et le clavier.

## ADDED Requirements

### Requirement: Installation Windows
L'application SHALL s'installer sur Windows 10/11 64 bits dans le dossier des programmes de l'utilisateur, avec son propre environnement Java et son lecteur vidéo, et créer les raccourcis « Sokoul » sur le Bureau et dans le menu Démarrer, ainsi qu'un moyen de la désinstaller qui retire le dossier et les raccourcis.

#### Scenario: Installation sur le PC
- **WHEN** Sokoul est installé sur le PC
- **THEN** une icône Sokoul est sur le Bureau et dans le menu Démarrer, et elle ouvre l'app dans sa propre fenêtre, sans émulateur Android ni installation de VLC ou de Java

#### Scenario: Désinstallation
- **WHEN** l'utilisateur lance « Désinstaller Sokoul »
- **THEN** le dossier de l'app et les raccourcis sont retirés

### Requirement: Fenêtre de bureau
L'application SHALL s'ouvrir dans une fenêtre redimensionnable qui retient sa taille et sa position, avec un passage en plein écran (F11) et une navigation latérale (Direct, Guide TV, Réglages ; Films, Séries et Real-Debrid arrivent avec les changements suivants).

#### Scenario: Redimensionnement
- **WHEN** l'utilisateur réduit la fenêtre à la moitié de l'écran
- **THEN** les listes et le guide se réorganisent sans texte coupé ni défilement horizontal de la page

#### Scenario: Réouverture
- **WHEN** l'utilisateur ferme puis relance Sokoul
- **THEN** la fenêtre reprend sa taille, sa position et le dernier écran ouvert

### Requirement: Souris et clavier
Toutes les actions SHALL être possibles à la souris (clic, clic droit pour le menu d'une chaîne, molette pour défiler) et au clavier (flèches, Entrée, Échap pour revenir, raccourcis du lecteur).

#### Scenario: Menu d'une chaîne
- **WHEN** l'utilisateur fait un clic droit sur une chaîne
- **THEN** le menu de la chaîne s'ouvre (favori, regarder) au même endroit que sur la tablette avec l'appui long

### Requirement: Données du PC protégées
Les identifiants de playlist et les clés API SHALL être stockés dans le dossier de l'utilisateur Windows, lisibles par lui seul, et jamais écrits dans un journal.

#### Scenario: Autre compte Windows
- **WHEN** un autre utilisateur Windows ouvre le dossier de données de Sokoul
- **THEN** il n'a pas accès aux identifiants
