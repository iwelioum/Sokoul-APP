# Spec Delta

## MODIFIED Requirements

### Requirement: Aperçu de la chaîne sélectionnée
Sur TV, quand le focus reste au moins 800 ms sur une chaîne de l'écran Direct, l'app SHALL lire cette chaîne en petit en haut du panneau d'informations, son coupé. Sur tablette en paysage, l'aperçu SHALL suivre la chaîne sélectionnée par un appui, avec le même délai. Changer de chaîne remplace l'aperçu.

#### Scenario: Focus posé sur une chaîne
- **WHEN** l'utilisateur s'arrête 1 s sur une chaîne
- **THEN** son image s'affiche dans le panneau d'informations, sans le son

#### Scenario: Défilement rapide
- **WHEN** l'utilisateur fait défiler la liste sans s'arrêter
- **THEN** aucun flux n'est ouvert pour les chaînes traversées

#### Scenario: Appui sur une chaîne (tablette en paysage)
- **WHEN** l'utilisateur touche une chaîne de la liste en paysage
- **THEN** la chaîne est mise en évidence, le panneau affiche son programme, et son image apparaît sans le son après un court instant

#### Scenario: Défilement au doigt (tablette en paysage)
- **WHEN** l'utilisateur fait défiler la liste du doigt sans toucher de chaîne
- **THEN** l'aperçu ne change pas et aucun nouveau flux n'est ouvert

## ADDED Requirements

### Requirement: Plein écran au deuxième appui sur tablette
Sur tablette en paysage, un deuxième appui sur la chaîne déjà sélectionnée, ou le bouton « Regarder » du panneau d'informations, SHALL ouvrir le plein écran sur cette chaîne. En portrait et sur téléphone, un seul appui SHALL ouvrir le plein écran, comme avant.

#### Scenario: Deuxième appui (tablette en paysage)
- **WHEN** l'utilisateur touche à nouveau la chaîne qui est en aperçu
- **THEN** l'aperçu s'arrête et le plein écran s'ouvre sur cette chaîne sans erreur de connexion

#### Scenario: Bouton Regarder (tablette en paysage)
- **WHEN** l'utilisateur touche « Regarder » dans le panneau d'informations
- **THEN** le plein écran s'ouvre sur la chaîne sélectionnée

#### Scenario: Portrait (tablette)
- **WHEN** l'utilisateur touche une chaîne en portrait
- **THEN** le plein écran s'ouvre directement, sans aperçu
