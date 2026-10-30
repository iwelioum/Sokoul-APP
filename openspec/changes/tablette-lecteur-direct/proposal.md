# Proposal

## Why

Sur tablette, le lecteur ne répond qu'au tap et au double tap. Les gestes habituels des applications vidéo mobiles manquent : on ne règle pas la luminosité ou le volume en glissant, ni le remplissage de l'écran en pinçant. Dans l'écran Direct en paysage, la moitié droite de l'écran reste vide alors qu'elle pourrait montrer, comme sur TV, le programme et l'image de la chaîne avant de l'ouvrir.

## What Changes

**Gestes du lecteur (tablette et téléphone)**
- Glisser verticalement sur le tiers gauche règle la luminosité de l'écran, pour la séance seulement.
- Glisser verticalement sur le tiers droit règle le volume média.
- Au centre, en direct, le glissement vertical zappe comme aujourd'hui ; en VOD, il ne fait rien.
- Un indicateur affiche le niveau pendant le geste.
- En paysage, pincer passe le format d'image en « Remplir », écarter le ramène à « Adapter ». Le choix est mémorisé comme le réglage de format actuel.
- Les gestes sont inactifs quand un panneau, une liste ou un menu est ouvert.

**Aperçu du Direct en paysage tactile**
- Le panneau d'informations (programme, logo, aperçu vidéo muet) apparaît aussi sur tablette en paysage.
- Un premier appui sur une chaîne la sélectionne et lance l'aperçu après le délai habituel.
- Un deuxième appui sur la même chaîne, ou le bouton « Regarder » du panneau, ouvre le plein écran.
- En portrait, et sur téléphone, un appui lance toujours la chaîne directement.
- Les garde-fous de l'aperçu TV restent les mêmes : une seule connexion, rien pendant un enregistrement, désactivable, arrêt hors de l'écran.

## Capabilities

### New Capabilities
- `player-gestures`: gestes tactiles du lecteur (luminosité, volume, zapping, pincer pour le format) et leur indicateur.

### Modified Capabilities
- `live-preview`: l'aperçu de la chaîne sélectionnée s'étend à la tablette en paysage, avec sélection au premier appui et plein écran au deuxième.

## Impact

- **Appareils** : tablette (portrait et paysage) et téléphone pour les gestes ; tablette en paysage pour l'aperçu. Rien ne change sur TV.
- **Code** :
  - `ui/screens/player/PlayerScreen.kt` (gestionnaires `pointerInput`) ;
  - `ui/screens/player/VodOverlay.kt` (indicateur) ;
  - `ui/screens/player/PlayerViewModel.kt` (format d'image) ;
  - `ui/screens/live/LiveScreen.kt` (panneau, sélection, cycle de vie de l'aperçu).
- **Système** : luminosité via la fenêtre de l'activité, sans permission. Volume via `AudioManager`, sans permission.
- **Aucune nouvelle dépendance**, aucun changement de base de données.

## Hors périmètre

- Luminosité système permanente (permission `WRITE_SETTINGS`).
- Gestes sur TV.
- Aperçu en portrait ou sur téléphone.
- Avance ou recul en glissant horizontalement (la barre de progression et le double tap existent déjà).
