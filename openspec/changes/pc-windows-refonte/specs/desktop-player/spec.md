# Spec Delta

## Purpose

Un lecteur de bureau soigné, avec des commandes par-dessus l'image comme sur la tablette.

## ADDED Requirements

### Requirement: Commandes en surimpression
La vidéo SHALL être dessinée dans l'interface. Par-dessus, une barre translucide s'affiche au mouvement de la souris et se masque après 3 s d'inactivité. Elle comprend :
- en haut : retour et titre ;
- en bas : progression cliquable avec temps écoulé et restant, lecture/pause, ±10 s, volume, pistes audio et sous-titres, Sources, plein écran.

Une chaîne en direct affiche son programme à la place de la progression.

#### Scenario: Barre au survol
- **WHEN** l'utilisateur bouge la souris pendant la lecture
- **THEN** la barre apparaît par-dessus l'image, puis disparaît, avec le curseur, 3 s après l'arrêt du mouvement

### Requirement: Raccourcis
Le lecteur SHALL répondre aux raccourcis suivants :

| Action | Raccourci |
|---|---|
| Lecture/pause | Espace ou clic sur l'image |
| Reculer / avancer de 10 s | ← / → |
| Volume | ↑ / ↓ ou molette |
| Muet | M |
| Plein écran | F, F11 ou double-clic |
| Fermer | Échap (quitte le plein écran d'abord) |
| Chaîne précédente / suivante | PgUp / PgDn (Direct) |

#### Scenario: Plein écran
- **WHEN** l'utilisateur double-clique sur l'image
- **THEN** la fenêtre passe en plein écran ; Échap la rétablit

### Requirement: Reprise
La position d'un film ou d'un épisode SHALL être enregistrée toutes les 10 s et à la fermeture.
- La fiche propose de reprendre là où la lecture s'était arrêtée.
- L'accueil la montre dans « Reprendre ».

Une œuvre lue à plus de 92 % est considérée comme vue.

#### Scenario: Reprise
- **WHEN** l'utilisateur ferme un film à 40 min puis relance Lecture
- **THEN** la lecture reprend à 40 min

### Requirement: Une seule connexion IPTV
Le lecteur SHALL rester unique : ouvrir une nouvelle lecture arrête la précédente avant d'ouvrir le flux.

#### Scenario: Zapping rapide
- **WHEN** l'utilisateur change trois fois de chaîne en une seconde
- **THEN** un seul flux reste ouvert
