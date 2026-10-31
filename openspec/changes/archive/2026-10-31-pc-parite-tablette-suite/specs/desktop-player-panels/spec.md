# Spec Delta

## Purpose

Panneaux et actions du lecteur de la tablette sur PC : langue et qualité, sous-titres, menu, fenêtre réduite, épisode suivant, actions du direct, sources d'une version.

## ADDED Requirements

### Requirement: Langue et qualité
Dans le lecteur de films, la pastille « *langue* · *définition* · *origine* » SHALL ouvrir le panneau latéral « Langue et qualité » de la tablette : « Vous regardez : … », nombre de sources et position de reprise, pistes du fichier (« Même image, changement immédiat ») puis versions par langue avec « EN COURS », « n autres sources » dépliables et les échecs (« a échoué : … »). Choisir une autre version reprend à la même position.

#### Scenario: Changer de version
- **WHEN** l'utilisateur choisit la version 1080p dans le panneau pendant la lecture à 01:25
- **THEN** la nouvelle version démarre à 01:25

#### Scenario: Version en échec
- **WHEN** une version était « pas prête »
- **THEN** sa ligne affiche « a échoué : version pas prête sur Real-Debrid » en orange

### Requirement: Sous-titres et menu
La pastille de sous-titres SHALL ouvrir le panneau « Sous-titres » (Désactivés, pistes du fichier, décalage). Le menu « … » SHALL proposer la vitesse de lecture et le format d'image (Ajusté, Remplir, 16:9, 4:3).

#### Scenario: Vitesse
- **WHEN** l'utilisateur choisit la vitesse 1,25× dans le menu
- **THEN** la lecture passe à 1,25× et le menu marque ce choix

### Requirement: Fenêtre réduite
Le bouton « Fenêtre réduite » SHALL réduire la fenêtre à 480×270 dp, toujours au premier plan, la vidéo seule avec un bouton pour revenir. C'est l'équivalent PC de l'image dans l'image.

#### Scenario: Réduire puis revenir
- **WHEN** l'utilisateur clique « Fenêtre réduite » puis « Agrandir »
- **THEN** la fenêtre retrouve sa taille et sa position d'avant

### Requirement: Épisode suivant
À la fin du générique d'un épisode (dernières 30 s, ou fin du fichier), la carte « Épisode suivant » de la tablette SHALL proposer l'épisode suivant avec un compte à rebours de 20 s. Il est lancé automatiquement si le réglage « Épisode suivant automatique » est actif.

#### Scenario: Enchaînement
- **WHEN** l'épisode S1E3 se termine et le réglage est actif
- **THEN** S1E4 démarre après le compte à rebours, vérifié par un test JVM de la règle de suite (dernier épisode de saison → S2E1)

### Requirement: Actions du direct
Le lecteur du direct SHALL afficher le bandeau de la tablette avec les actions « Pause du direct », « Programmes », « Chaînes », « Enregistrer », « Favori » et « Réglages », la santé du flux (« Flux stable », « Flux fragile », « Flux interrompu ») et les boutons du haut (Retour, Chaînes, Fenêtre réduite, menu). « Pause du direct » met le direct en pause puis reprend en différé, avec « Retour au direct ».

#### Scenario: Pause du direct
- **WHEN** l'utilisateur met en pause pendant 30 s puis reprend
- **THEN** la lecture reprend là où elle s'était arrêtée, et « Retour au direct » ramène au direct

#### Scenario: Chaînes
- **WHEN** l'utilisateur clique « Chaînes »
- **THEN** la liste des chaînes du groupe s'ouvre sur le côté, et un clic zappe

### Requirement: Sources d'une version
Sur la fiche, « +n sources » d'une carte de version SHALL ouvrir la fenêtre de la tablette : « *Langue* · *définition* », « n sources · la première est lue par défaut », une ligne par source (origine, taille, codec, « qualité d'après le nom », « à télécharger », nom du fichier). Un clic lance la source.

#### Scenario: +4 sources
- **WHEN** l'utilisateur clique « +4 sources » sur la carte VF 1080p
- **THEN** la fenêtre « Français (VF) · 1080p » liste 5 sources, la première marquée « PAR DÉFAUT »

### Requirement: Aperçu du Direct
Dans le Direct, la chaîne choisie SHALL s'afficher dans le panneau de droite avec un aperçu vidéo muet, « EN CE MOMENT », le programme, sa progression, son résumé, « ENSUITE » et « Regarder ». L'aperçu utilise le lecteur unique (une connexion). Il s'arrête en quittant le Direct, et « Regarder » passe en plein cadre sans relancer le flux.

#### Scenario: Aperçu puis regarder
- **WHEN** l'utilisateur choisit France 2 puis clique « Regarder »
- **THEN** l'aperçu passe en plein cadre sans nouvelle connexion

#### Scenario: Quitter le Direct
- **WHEN** l'utilisateur ouvre l'accueil pendant l'aperçu
- **THEN** le flux est arrêté
