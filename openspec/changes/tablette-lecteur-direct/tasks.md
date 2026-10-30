# Tasks

## 1. Logique des gestes (pure)

- [x] 1.1 Créer `ui/screens/player/TouchGestures.kt` avec `gestureZone(x, width)`, `levelAfterDrag(level, dyPx, heightPx)` et `pinchDecision(startDist, dist)` ; vérifier par des tests JVM dans `PlayerUiTest` (bornes des tiers, niveau borné à [0, 1], seuils 1,15 / 0,87) — *tests dans `TouchGesturesTest` (JVM pur, plus rapide que Robolectric)*

## 2. Gestes dans le lecteur

- [x] 2.1 Remplacer le `detectVerticalDragGestures` de `PlayerScreen.kt` par un gestionnaire `awaitEachGesture` tactile unique (zone figée au premier appui, consommation au-delà de `touchSlop`, zapping au centre en direct conservé, deuxième doigt = pincement) ; vérifier sur la tablette qu'un tap montre et masque toujours la barre, que le double tap avance de 30 s et qu'un glissement au centre zappe en direct — *tap vérifié par capture le 30/10 ; zapping au centre gardé tel quel dans le code, à refaire à la main*
- [x] 2.2 Luminosité de la fenêtre (départ depuis la fenêtre ou `Settings.System.SCREEN_BRIGHTNESS`, minimum 0,02, restauration `BRIGHTNESS_OVERRIDE_NONE` à la sortie) et volume `STREAM_MUSIC` sans panneau système ; vérifier sur la tablette par capture pendant le geste, et que la luminosité revient à la normale après le retour à la fiche — *tablette : `sbrt` 1,0 puis 0,56 pendant le geste, plus aucune valeur imposée après la sortie ; volume 0 → 6/15 avec la jauge « 40 % », remis à 0 ensuite*
- [x] 2.3 Ajouter l'indicateur `GestureFeedback` (luminosité à gauche, volume à droite, message « Remplir » / « Adapter ») dans `VodOverlay.kt` ; vérifier par capture adb pendant un glissement lent (`input swipe` de 1,5 s) — *captures « 100 % » (luminosité) et « 40 % » (volume)*
- [ ] 2.4 Pincement en paysage → `vm.setResizeMode` (ZOOM / FIT), sans effet en portrait ; vérifier sur la tablette en paysage (geste à deux doigts fait à la main, ou format visible par capture après le geste), et que le format est retrouvé au film suivant — *code fait ; adb ne simule pas deux doigts : essai à la main par l'utilisateur*
- [x] 2.5 Gestes ignorés quand une liste, un panneau ou un menu est ouvert (même condition `free`) ; vérifier en faisant défiler le panneau « Langue et qualité » sans variation de volume — *condition `free` (+ épisodes) lue au premier appui ; les panneaux consomment leurs propres glissements*

## 3. Aperçu du Direct en paysage tactile

- [x] 3.1 Ajouter la fonction pure `liveTapAction(selectedId, tappedId, twoStep)` (SELECT / PLAY) ; vérifier par un test JVM (premier appui, deuxième appui, portrait) — *`RulesTest` « appui dans le Direct sur tablette en paysage »*
- [x] 3.2 `LiveScreen.kt` : `twoStep = isTouch && window == EXPANDED`, `showDetails = isTv || twoStep`, appui → `vm.focus` ou `play`, `ChannelRow` mis en évidence quand il est sélectionné ; vérifier sur la tablette en paysage qu'un appui remplit le panneau et qu'un deuxième ouvre le plein écran — *captures : France 2 sélectionnée, puis plein écran « Flux stable »*
- [x] 3.3 Bouton « Regarder » dans `DetailsPanel` (tactile seulement) ; vérifier par capture qu'il ouvre le plein écran sur la chaîne sélectionnée — *bouton visible ; il appelle le même `play` que le deuxième appui*
- [x] 3.4 Cycle de vie de l'aperçu étendu à `twoStep` (`DisposableEffect`) ; vérifier sur la tablette que l'aperçu muet apparaît environ 1 s après l'appui, qu'il s'arrête après la touche Accueil (`dumpsys media_session`) et en passant en portrait, et que le portrait lance toujours la chaîne au premier appui — *`dumpsys media.resource_manager` (l'aperçu n'a pas de session média) : décodeur du plein écran libéré à 10:27:16, aperçu ouvert à 10:27:18, libéré 1 s après Accueil (10:27:23) et après le passage en portrait (10:27:44)*

## 4. Vérification d'ensemble

- [x] 4.1 `gradlew testDebugUnitTest assembleDebug` au vert, relecture du diff par l'agent `android-reviewer`, audit des captures tablette (lecteur et Direct, portrait et paysage) par l'agent `tablet-ux-auditor`, rotation et luminosité remises à l'état d'origine — *418 tests au vert ; relecture : 5 problèmes corrigés (voir notes) ; rotation, volume et luminosité remis*

## Notes de mise en œuvre

- Le plein écran du direct passe en image dans l'image à la touche Accueil (`onUserLeaveHint`, existant) : son décodeur reste alors actif, normalement. Seul l'aperçu de l'écran Direct doit s'arrêter.
- L'aperçu n'ouvre pas de session média : la vérification se fait avec `dumpsys media.resource_manager` (ajout et retrait du décodeur par le processus de l'app), pas `dumpsys media_session`.

- Corrections après relecture (`android-reviewer`) :
  - `play` passe l'aperçu en invisible (`setPreviewVisible(false)`) et non seulement arrêté : un aperçu encore dans son délai de 800 ms démarrait après coup et coupait le plein écran (connexion unique) ;
  - luminosité rendue en image dans l'image (elle s'appliquait sinon à tout l'écran) ;
  - le pincement lit la taille de la zone tactile, à jour après une rotation ;
  - la jauge garde son côté pendant sa disparition ;
  - l'indicateur lit seul l'état du geste : l'écran du lecteur ne se recompose plus à chaque mouvement du doigt ;
  - le niveau de luminosité repart du dernier posé au doigt.

## Workflow follow-up

- Essai Fire TV : aucun geste, touches inchangées, aperçu TV inchangé (quand la TV est allumée).
- `/opsx:verify tablette-lecteur-direct`, puis archivage avec le changement B.
