# Profils

## Purpose

Décrire les profils utilisateur, les favoris, les reprises et le contrôle parental.

## Requirements

### Requirement: Isolation des profils
Les favoris et progressions SHALL être associés au profil courant et à la playlist.

#### Scenario: Changer de profil
- **WHEN** l'utilisateur sélectionne un autre profil
- **THEN** les favoris et reprises affichés sont ceux de ce profil uniquement

### Requirement: Filtre Enfant
Le système SHALL appliquer le filtrage d'âge du profil Enfant aux grilles, à la recherche, à Explorer et à l'accueil.

#### Scenario: Contenu interdit dans une recherche
- **WHEN** un profil Enfant recherche un contenu dépassant sa limite
- **THEN** le contenu n'est pas affiché dans les résultats
