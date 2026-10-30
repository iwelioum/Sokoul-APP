# Spec Delta

## Purpose

Reprendre sur le PC la configuration déjà faite sur l'app Android (playlists, clés, favoris, réglages) grâce à sa sauvegarde chiffrée, sans rien ressaisir.

## ADDED Requirements

### Requirement: Import de la sauvegarde Android
L'app PC SHALL importer un fichier de sauvegarde exporté par l'app Android (format actuel, version 3), chiffré ou non, après saisie de la phrase de passe si besoin, et en reprendre les playlists, les clés API, les favoris, Ma liste, la progression de lecture, les groupes masqués ou verrouillés, le code parental et les réglages qui ont un sens sur PC.

#### Scenario: Sauvegarde chiffrée
- **WHEN** l'utilisateur choisit le fichier de sauvegarde de sa tablette et saisit la bonne phrase de passe
- **THEN** ses playlists et favoris apparaissent sur le PC et la synchronisation des chaînes démarre

#### Scenario: Mauvaise phrase de passe
- **WHEN** la phrase de passe est fausse
- **THEN** rien n'est importé et un message demande de la ressaisir

### Requirement: Premier lancement guidé
Au premier lancement, l'app PC SHALL proposer soit d'importer la sauvegarde de l'app Android, soit d'ajouter une playlist à la main (Xtream ou M3U).

#### Scenario: Aucune configuration
- **WHEN** l'utilisateur ouvre Sokoul pour la première fois sur le PC
- **THEN** un écran d'accueil propose « Importer depuis ma tablette » et « Ajouter une playlist »

### Requirement: Import sans écraser
Un nouvel import SHALL mettre à jour les playlists et réglages existants et fusionner les favoris et Ma liste, sans supprimer ce qui a été ajouté sur le PC.

#### Scenario: Deuxième import
- **WHEN** l'utilisateur importe une sauvegarde plus récente de la tablette après avoir ajouté un favori sur le PC
- **THEN** le favori ajouté sur le PC est toujours là, avec ceux de la tablette
