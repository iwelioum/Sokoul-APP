# Spec Delta

## MODIFIED Requirements

### Requirement: Origine affichée une seule fois
Chaque ligne de source SHALL montrer son origine (IPTV, RD) une seule fois, en badge, et une note courte (rip, taille, état) sans répéter l'origine. Cette règle vaut aussi pour la fenêtre des sources ouverte par un appui long sur une version de la fiche.

#### Scenario: Ligne Real-Debrid
- **WHEN** une source Real-Debrid « Remux · 58,3 Go · prête » est listée
- **THEN** la ligne affiche le badge RD et la note « Remux · 58,3 Go · prête »

#### Scenario: Ligne IPTV
- **WHEN** une source IPTV vient de la rubrique « [EN] SCI-FI/FANTASY »
- **THEN** la rubrique brute n'apparaît qu'en petit, sous la ligne, jamais en titre

#### Scenario: Fenêtre des sources d'une fiche (tablette)
- **WHEN** l'utilisateur fait un appui long sur la version « MULTI · 4K · HDR10 » qui a 3 sources Real-Debrid
- **THEN** chaque ligne montre le badge RD une fois, le type et la taille en titre (« WEB · 12,3 Go »), l'état et les langues audio en note, sans « Lire : RD » écrit en toutes lettres

### Requirement: Longues listes repliées
Une version dépliée SHALL montrer au plus 8 autres sources, suivies de « Voir les n autres ». La fenêtre des sources d'une fiche SHALL appliquer la même limite.

#### Scenario: Douze sources pour une version
- **WHEN** l'utilisateur déplie une version qui a 11 autres sources
- **THEN** 8 sont affichées et une ligne « Voir les 3 autres sources » les révèle

#### Scenario: Fenêtre des sources avec 12 sources
- **WHEN** l'utilisateur ouvre la fenêtre des sources d'une version qui en a 12
- **THEN** 8 sont listées, suivies de « Voir les 4 autres sources »

## ADDED Requirements

### Requirement: Taille inconnue masquée
Une source dont la taille est inconnue ou inférieure à 1 Mo SHALL être affichée sans taille ; une source Real-Debrid de moins de 50 Mo (fichier annexe, échantillon) SHALL ne pas être proposée comme version.

#### Scenario: Fichier vide dans un torrent
- **WHEN** Real-Debrid renvoie un fichier de 0 octet pour une œuvre
- **THEN** aucune ligne « 0 Mo » n'apparaît dans le panneau Langue et qualité ni dans la fiche

### Requirement: Panneau des sources adapté au paysage tactile
Sur tablette en paysage, le panneau « Langue et qualité » du lecteur SHALL laisser au moins la moitié de l'écran à la vidéo et signaler qu'il reste des lignes à faire défiler (fondu en bas de liste).

#### Scenario: Panneau ouvert en paysage (tablette)
- **WHEN** l'utilisateur ouvre « Langue et qualité » pendant la lecture, tablette en paysage
- **THEN** la vidéo reste visible sur plus de la moitié de l'écran et le bas de la liste s'estompe s'il y a d'autres lignes

### Requirement: Accès aux sources au doigt
Sur appareil tactile, une carte de version qui a d'autres sources SHALL proposer une cible visible « +n sources » d'au moins 48 dp qui ouvre la fenêtre des sources, sans avoir à découvrir l'appui long.

#### Scenario: Carte avec deux autres sources (tablette)
- **WHEN** l'utilisateur touche « +2 sources » sur une carte de l'étagère Versions disponibles
- **THEN** la fenêtre des sources de cette version s'ouvre
