# playback-start Specification

## Purpose
Lancer un film ou un épisode directement dans la meilleure version disponible (IPTV ou Real-Debrid), avec un écran de lancement clair, sans démarrer une version puis en changer.

## Requirements

### Requirement: Choix de la meilleure version avant le premier lancement
Quand l'utilisateur appuie sur Lecture sans avoir choisi de version, le lecteur SHALL attendre au plus 6 secondes la recherche Real-Debrid, classer ensemble les versions IPTV et Real-Debrid, puis lancer la première version lisible.

#### Scenario: Real-Debrid classé devant l'IPTV
- **WHEN** la recherche Real-Debrid répond en moins de 6 s et une version prête passe devant l'IPTV
- **THEN** cette version Real-Debrid est lancée, sans ouvrir le flux IPTV avant

#### Scenario: Recherche trop longue
- **WHEN** la recherche Real-Debrid dépasse 6 s
- **THEN** la meilleure version déjà connue est lancée et la recherche continue en arrière-plan pour le menu « Langue et qualité »

#### Scenario: Version à télécharger ou en échec
- **WHEN** la première version Real-Debrid du classement est « à télécharger » ou a échoué récemment
- **THEN** elle est ignorée pour le lancement automatique et la suivante est prise

### Requirement: Démarrage HLS en haute qualité
Les flux HLS SHALL démarrer avec une estimation de débit élevée (20 Mb/s) puis s'adapter au réseau réel.

#### Scenario: Flux HLS adaptatif
- **WHEN** un flux HLS propose plusieurs qualités
- **THEN** la lecture commence dans une qualité haute plutôt que la plus basse

### Requirement: Écran de lancement avec le logo de l'œuvre
Du lancement à la première image, le lecteur SHALL afficher le fond de l'œuvre assombri, son logo TMDB qui pulse (le titre à défaut) et l'état en cours.

#### Scenario: Préparation Real-Debrid
- **WHEN** une version Real-Debrid est en préparation
- **THEN** l'écran affiche « Préparation de la version … » sous le logo, sans bandeau

#### Scenario: Première image
- **WHEN** la vidéo affiche sa première image ou qu'une erreur survient
- **THEN** l'écran de lancement disparaît

### Requirement: Classement adapté à l'écran
À définition égale, une version sans HDR SHALL passer devant une version HDR quand l'écran ne déclare aucune capacité HDR, et la Dolby Vision SHALL passer en dernier.

#### Scenario: Télé SDR
- **WHEN** l'écran ne déclare pas de HDR et que trois versions 4K existent (SDR, HDR10, Dolby Vision)
- **THEN** l'ordre est SDR, HDR10, Dolby Vision ; une 4K HDR reste devant une 1080p SDR
