# Spec Delta

## MODIFIED Requirements

### Requirement: Coque de la tablette
La fenêtre SHALL afficher le rail latéral de la tablette : logo S, Recherche, Accueil, Films, Séries, Ma liste, Sagas, Direct, Guide TV, Enreg. et Réglages, avec la pastille d'accent sur l'entrée active. Les pages de navigation ont la barre du haut de la tablette : titre et recherche, transparente sur le héros puis opaque.

#### Scenario: Navigation
- **WHEN** l'utilisateur clique « Films » dans le rail
- **THEN** la page Films s'ouvre avec son héros, et « Films » est marqué dans le rail

#### Scenario: Enregistrements
- **WHEN** l'utilisateur clique « Enreg. » dans le rail
- **THEN** la page Enregistrements s'ouvre et « Enreg. » est marqué dans le rail
