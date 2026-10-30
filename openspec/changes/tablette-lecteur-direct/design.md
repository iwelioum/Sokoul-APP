# Design

## Context

Voir proposal.md (Why) et les specs `player-gestures` et `live-preview`.

**Gestes actuels du lecteur** (`ui/screens/player/PlayerScreen.kt`)
- l. 551-568 : `detectTapGestures` pour le tap (afficher ou masquer, fermer les panneaux) et le double tap (−10 s / +30 s en VOD).
- l. 570-583 : en tactile et en direct seulement, `detectVerticalDragGestures` zappe au-delà de `SwipeZapDistance` (`LiveKeys.kt:101`), si aucun panneau n'est ouvert (condition `free`).
- Il n'y a aucun code de luminosité, de volume ou de pincement.
- Le format d'image passe par `vm.setResizeMode` (`PlayerViewModel.kt:278`, mémorisé dans `AppSettings.resizeMode`). `effectiveResizeMode` (`PlayerScreen.kt:160`) force FIT en portrait.
- L'indicateur `SeekFeedback` (`VodOverlay.kt:450-464`) est affiché par `AnimatedVisibility` (`PlayerScreen.kt:637-646`).

**Écran Direct** (`ui/screens/live/LiveScreen.kt`)
- En mise en page large (`wide`), on a une `Row` avec les catégories, `ChannelList` et `DetailsPanel`. Le panneau n'existe que si `l.isTv` (l. 281).
- `ChannelRow` passe par `FocusSurface` : `onFocus` alimente `vm.focus` (donc `details` et l'aperçu), et `onClick` lance `play`.
- Au doigt, `onFocus` ne se déclenche pas, d'où un panneau vide. C'est pour cela qu'il est réservé à la TV.
- L'aperçu (`LiveViewModel` l. 137-170, `LivePreview.kt`) suit `focused` avec l'anti-rebond de 800 ms. Il ne joue que si `setPreviewVisible(true)`, appelé par le `DisposableEffect` (l. 263-276) quand `isTv`.

## Goals / Non-Goals

**Goals :**
- un seul gestionnaire de gestes tactiles dans le lecteur (glissement par zone et pincement), sans casser le tap, le double tap ni les panneaux ;
- logique de décision pure et testée (zone, niveau, pincement, appui dans le Direct) ;
- sur tablette en paysage, l'aperçu réutilise tel quel le pipeline TV (`focused` → anti-rebond → `LivePreview`).

**Non-Goals :**
- aucun changement côté télécommande ;
- pas de nouveau lecteur ni de deuxième connexion.

## Decisions

### 1. Un seul `pointerInput` pour les gestes tactiles
Le `detectVerticalDragGestures` actuel est remplacé par un `awaitEachGesture` maison, actif seulement si `l.isTouch` :
- **au premier appui**, la zone est figée par `gestureZone(x, width)` : LEFT (x < 1/3), RIGHT (x > 2/3), sinon CENTER ;
- **au-delà du seuil vertical** (`touchSlop`), les déplacements sont consommés :
  - LEFT règle la luminosité ;
  - RIGHT règle le volume ;
  - CENTER cumule comme aujourd'hui et zappe au relâchement, en direct seulement ;
- **un deuxième doigt** fait passer le geste en pincement : rapport des distances supérieur à 1,15 pour ZOOM, inférieur à 0,87 pour FIT, une seule décision par geste ;
- **tant que le seuil n'est pas franchi**, rien n'est consommé : le `detectTapGestures` existant reçoit les taps et les double taps comme avant.

*Écarté* : `detectTransformGestures` plus `detectVerticalDragGestures` en parallèle. Deux détecteurs se disputent les mêmes pointeurs : le zapping se déclenchait pendant un pincement imprécis.

### 2. Niveaux
- `levelAfterDrag(level, dyPx, heightPx)` : parcourir toute la hauteur de l'image fait varier le niveau de 100 %, borné à [0, 1]. Vers le haut, le niveau augmente.
- **Luminosité** : `window.attributes.screenBrightness`, au moins 0,02 pour ne pas éteindre l'écran.
  - Le niveau de départ est la valeur de la fenêtre. S'il vaut `BRIGHTNESS_OVERRIDE_NONE`, on part de `Settings.System.SCREEN_BRIGHTNESS / 255`.
  - On restaure `BRIGHTNESS_OVERRIDE_NONE` dans `onDispose` du lecteur.
- **Volume** : `AudioManager.setStreamVolume(STREAM_MUSIC, round(level × max), 0)`. Le drapeau 0 évite le panneau système, puisque notre indicateur le remplace.

### 3. Indicateur
`GestureFeedback(kind, level)` reprend la forme de `SeekFeedback` : panneau latéral en dégradé, icône (`BrightnessMedium`, `VolumeUp` ou `VolumeOff`) et barre verticale `ProgressLine`. Il est aligné à gauche pour la luminosité et à droite pour le volume, et disparaît 600 ms après le relâchement. Le pincement affiche un message court (« Remplir » ou « Adapter ») avec le même mécanisme.

### 4. Direct : sélection au premier appui (tablette en paysage)
- `twoStep = l.isTouch && l.window == EXPANDED` : `EXPANDED` correspond au paysage tablette, `MEDIUM` au portrait.
- `showDetails = l.isTv || twoStep`.
- Fonction pure `liveTapAction(selectedId, tappedId, twoStep)` → `SELECT` ou `PLAY`.
- `SELECT` appelle `vm.focus(c)`, comme le focus TV. Le panneau et l'aperçu suivent sans autre code.
- `ChannelRow` reçoit `selected` pour être mis en évidence au doigt, faute de focus visible.
- `DetailsPanel` reçoit un bouton « Regarder » (`SokoulButton`, Primary) en tactile seulement, qui appelle `play`.
- Le `DisposableEffect` passe de `isTv` à `isTv || twoStep`. `play` appelle déjà `vm.stopPreview()` avant la navigation.

*Écarté* : un appui long pour l'aperçu. Il ouvre déjà le menu de la chaîne, et l'aperçu serait introuvable.

## Risks / Trade-offs

- **[Rotation pendant l'aperçu]** Passer en portrait fait passer `twoStep` à faux : le `DisposableEffect` redémarre avec `setPreviewVisible(false)`, et l'aperçu est libéré.
- **[Bord droit = geste Retour d'Android]** Un glissement qui part du bord est pris par le système. Le tiers droit reste assez large (plus de 400 px en paysage) pour être utilisable.
- **[Volume au maximum, son coupé]** Le niveau suit `getStreamVolume` à chaque geste, donc les touches physiques restent cohérentes.
- **[Connexion unique]** Rien de nouveau : la sélection au doigt passe par le même pipeline et les mêmes garde-fous (`ProviderGate`, réglage, ON_STOP).

## Migration Plan

Aucune migration de données. Le réglage `resizeMode` existant sert de mémoire au pincement.
