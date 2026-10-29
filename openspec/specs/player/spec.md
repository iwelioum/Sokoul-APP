# Lecteur

## Purpose

Décrire le lecteur partagé pour les contenus directs, replay et VOD sur tablette, Android TV et Fire TV.

## Requirements

### Requirement: Contrôles de lecture VOD
Le lecteur SHALL permettre la pause, la reprise, la navigation avant/arrière, la vitesse de lecture et la minuterie de mise en veille.

#### Scenario: Modifier la vitesse
- **WHEN** l'utilisateur choisit une vitesse comprise entre 0,5× et 2× dans le menu du lecteur
- **THEN** la vitesse ExoPlayer est appliquée au contenu VOD en cours

#### Scenario: Minuterie à durée fixe
- **WHEN** l'échéance de la minuterie est atteinte
- **THEN** la lecture est mise en pause, la progression est sauvegardée et le lecteur se ferme

### Requirement: Apparence des sous-titres
Le lecteur SHALL appliquer les préférences de taille, couleur, fond et position définies dans Réglages > Lecture.

#### Scenario: Style Fire TV
- **WHEN** un profil choisit de grands sous-titres jaunes avec fond noir
- **THEN** la vue de sous-titres applique ce style indépendamment des réglages système de Fire TV

### Requirement: Décalage audio
Le lecteur SHALL permettre un décalage audio réglable autour de zéro pour corriger une désynchronisation.

#### Scenario: Corriger une piste
- **WHEN** l'utilisateur choisit un décalage positif ou négatif
- **THEN** le traitement audio l'applique au flux courant sans changer la position vidéo
