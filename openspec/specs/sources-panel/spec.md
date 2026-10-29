# sources-panel Specification

## Purpose
Présenter les versions et sources d'une œuvre (IPTV et Real-Debrid) de façon lisible, sur TV comme au doigt.

## Requirements

### Requirement: Origine affichée une seule fois
Chaque ligne de source SHALL montrer son origine (IPTV, RD) une seule fois, en badge, et une note courte (rip, taille, état) sans répéter l'origine.

#### Scenario: Ligne Real-Debrid
- **WHEN** une source Real-Debrid « Remux · 58,3 Go · prête » est listée
- **THEN** la ligne affiche le badge RD et la note « Remux · 58,3 Go · prête »

#### Scenario: Ligne IPTV
- **WHEN** une source IPTV vient de la rubrique « [EN] SCI-FI/FANTASY »
- **THEN** la rubrique brute n'apparaît qu'en petit, sous la ligne, jamais en titre

### Requirement: Longues listes repliées
Une version dépliée SHALL montrer au plus 8 autres sources, suivies de « Voir les n autres ».

#### Scenario: Douze sources pour une version
- **WHEN** l'utilisateur déplie une version qui a 11 autres sources
- **THEN** 8 sont affichées et une ligne « Voir les 3 autres sources » les révèle

### Requirement: Dépliage au doigt
Sur appareil tactile, toucher « n autres sources » SHALL déplier ou replier la version.

#### Scenario: Tablette
- **WHEN** l'utilisateur touche « 5 autres sources » dans le panneau Langue et qualité
- **THEN** les 5 sources apparaissent sous la version

### Requirement: Libellés courts et uniformes
Le bouton Lecture SHALL n'afficher que langue, définition et HDR (« MULTI · 4K HDR10 ») ; les libellés HDR10 et HDTV SHALL être les mêmes partout.

#### Scenario: Bouton Lecture d'une fiche
- **WHEN** la meilleure version est une MULTI 4K HDR10 Remux de 58 Go
- **THEN** le bouton affiche « Lecture · MULTI · 4K HDR10 »
