# Spec Delta

## Purpose

Réglages de lecture et de télévision de la tablette, modifiables sur PC et appliqués au lecteur.

## ADDED Requirements

### Requirement: Réglages de lecture
La rubrique Lecture SHALL proposer, comme la tablette :
- langue audio préférée ;
- sous-titres par défaut ;
- taille, couleur, lisibilité (contour, fond, ombre) et position des sous-titres ;
- épisode suivant automatique ;
- « Avancé » : mise en mémoire et User-Agent.

Chaque réglage MUST être enregistré, puis appliqué au prochain titre lu. L'apparence des sous-titres s'applique dès la lecture suivante.

#### Scenario: Langue audio
- **WHEN** l'utilisateur choisit l'anglais comme langue audio préférée
- **THEN** la piste anglaise est prise à l'ouverture d'un fichier qui en a une, vérifié par un test JVM de `AudioChoice`

#### Scenario: Taille des sous-titres
- **WHEN** l'utilisateur choisit « Grande »
- **THEN** la valeur est enregistrée et la ligne affiche « Grande »

### Requirement: Réglages de télévision
La rubrique Télévision et guide SHALL proposer « Au démarrage » (Accueil ou dernière chaîne), le sens du zapping et le décalage horaire du guide. Chacun est appliqué au Direct, au zapping et au guide.

#### Scenario: Décalage du guide
- **WHEN** l'utilisateur règle le décalage à +1 h
- **THEN** les horaires du guide et du programme en cours sont décalés d'une heure, vérifié par un test JVM
