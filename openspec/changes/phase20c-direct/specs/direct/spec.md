# Spec Delta

## ADDED Requirements

### Requirement: Aperçu de chaîne focalisée
L'écran Direct SHALL afficher un aperçu de la chaîne focalisée après 600 ms de stabilité et SHALL réutiliser la connexion lors du passage en plein écran.

#### Scenario: Focus stable
- **WHEN** une chaîne garde le focus pendant au moins 600 ms
- **THEN** son flux est lancé dans un aperçu sans interrompre les autres fonctions de navigation

#### Scenario: Passage en plein écran
- **WHEN** l'utilisateur valide la chaîne en aperçu
- **THEN** le même lecteur est transféré au plein écran sans ouvrir une seconde connexion fournisseur

### Requirement: Enregistrement en série
Le système SHALL permettre de créer une règle identifiée par le titre normalisé et la chaîne normalisée.

#### Scenario: Programme correspondant
- **WHEN** le guide est rafraîchi et contient une occurrence correspondant à une règle active
- **THEN** l'occurrence est programmée une seule fois si elle est encore enregistrable

#### Scenario: Doublon de synchronisation
- **WHEN** la même occurrence est revue lors d'un second rafraîchissement
- **THEN** aucun second enregistrement n'est créé
