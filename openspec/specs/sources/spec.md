# Sources

## Purpose

Décrire la résolution des sources IPTV, VOD et Real-Debrid sans coupler l'interface aux fournisseurs.

## Requirements

### Requirement: Résolution selon l'origine
Le système SHALL résoudre les chaînes, films, épisodes, replays et fichiers de compte avec `StreamResolver` et les clients existants.

#### Scenario: Version VOD indisponible
- **WHEN** une version échoue
- **THEN** le lecteur essaie la version suivante en conservant la progression

### Requirement: Protection du fournisseur
Le système SHALL réutiliser `ProviderGate` pour limiter les connexions simultanées et respecter les refus temporaires.

#### Scenario: Connexion unique
- **WHEN** un nouveau lecteur principal est acquis
- **THEN** le lecteur précédent est arrêté avant l'ouverture du nouveau flux
