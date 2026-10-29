# device-support Specification

## Purpose
Fonctionner correctement sur Google TV, Fire TV (Android 7.1, 192 Mo par app) et tablette, en s'adaptant aux capacités de chaque appareil.

## Requirements

### Requirement: Polices sous Android 7
Sous Android 7 (API < 26), l'app SHALL utiliser des fichiers Manrope à graisse fixe, la police variable n'y étant pas lisible.

#### Scenario: Fire TV Stick
- **WHEN** l'app démarre sur Fire OS 6
- **THEN** le texte s'affiche en Manrope avec ses graisses, sans erreur « addFont failed »

### Requirement: Profil d'appareil modeste
Un appareil à faible mémoire (isLowRamDevice ou 256 Mo par app au plus) SHALL limiter le multi-écran à 2 chaînes, réduire les caches d'images de moitié et plafonner la pause du direct à 1 Go, démarrable dès 1,2 Go libres.

#### Scenario: Troisième chaîne en multi-écran
- **WHEN** deux chaînes sont lues en multi-écran sur un appareil modeste
- **THEN** une troisième est refusée avec un message explicite

### Requirement: Synchronisation en priorité basse
La synchronisation et le regroupement des doublons SHALL tourner sur des fils de priorité basse pour que la télécommande reste réactive.

#### Scenario: Import du catalogue
- **WHEN** un catalogue de 250 000 lignes est importé
- **THEN** l'interface reste utilisable pendant l'import

### Requirement: Capacités affichées à l'utilisateur
Réglages › À propos SHALL indiquer ce que l'app a détecté (télé HDR ou SDR, 4K, appareil modeste) et, sur Fire TV, les fonctions de Google TV indisponibles.

#### Scenario: Fire TV sur télé SDR
- **WHEN** l'utilisateur ouvre À propos sur un Fire TV Stick branché sur une télé SDR
- **THEN** il lit « Télé SDR · 4K » et la liste des fonctions indisponibles sur Fire TV
