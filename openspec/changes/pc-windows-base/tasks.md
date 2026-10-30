# Tasks

## 1. Module partagé `:core`

- [x] 1.1 Créer le module JVM `:core` (Kotlin 2.0.21, JDK 17, OkHttp, kotlinx.serialization, coroutines, javax.inject, `:parser`) et le brancher sur `:app` ; y déplacer, paquets inchangés, `BackupCrypto`, `PinHasher` et `XtreamClient` ; vérifier que `testDebugUnitTest assembleDebug` reste au vert (420 tests) — *plus `HttpStreams.kt` (openStream, HttpException, httpMessage) et `Redact` (masquage des identifiants), sortis de `Http.kt` ; 420 tests au vert*
- [ ] 1.2 Installer l'APK sur la tablette et vérifier par capture que le Direct charge et qu'une chaîne se lit (aucun changement visible) — *tablette injoignable le 30/10 (débogage sans fil coupé)*

## 2. Squelette de l'app PC

- [ ] 2.1 Créer `:desktop` (Compose Multiplatform bureau 1.7.3), fenêtre « Sokoul », `DesktopTheme` (jetons recopiés de `ui/theme`), rail de navigation et pile d'écrans, taille et position retenues ; vérifier par `gradlew :desktop:run` et une capture de la fenêtre
- [x] 2.2 `DesktopDb` (sqlite-jdbc) dans `%APPDATA%\Sokoul\` avec ACL limitée au compte courant ; vérifier par un test JVM (création des tables, écriture et lecture d'une playlist) et par `icacls` sur le dossier — *`DesktopTest` ; `icacls` : seul le compte Windows de l'utilisateur, contrôle total, hérité*

## 3. Configuration

- [x] 3.1 `DesktopImport` : lecture de la sauvegarde Android (JSON v3, chiffrée ou non) par des modèles minimaux et `BackupCrypto`, fusion dans `DesktopDb` ; vérifier par un test JVM (sauvegarde chiffrée par `BackupCrypto`, mauvaise phrase refusée, second import sans perte) — *`DesktopTest` (6 tests au vert)*
- [ ] 3.2 Écran de premier lancement (« Importer depuis ma tablette » / « Ajouter une playlist ») et Réglages (playlists, synchronisation) ; vérifier en important une vraie sauvegarde exportée de la tablette (fichiers supprimés ensuite)

## 4. Direct et guide

- [ ] 4.1 `DesktopSync` : chaînes et guide via `:parser` et `XtreamClient`, au lancement si plus de 12 h et à la demande ; vérifier par un test JVM sur un échantillon M3U/XMLTV et par un essai réel (nombre de chaînes cohérent avec la tablette)
- [ ] 4.2 Écran Direct (groupes, chaînes, programme en cours, recherche, favoris, clic droit, code parental) ; vérifier par captures et tests de la logique de filtrage
- [ ] 4.3 Écran Guide TV (grille, jours, détail d'un programme) ; vérifier par capture et défilement souris / clavier

## 5. Lecture

- [ ] 5.1 VLC fourni : extraire `libvlc.dll`, `libvlccore.dll` et `plugins/` de l'archive officielle VLC 3.0.21 win64 dans `desktop/vlc/` (ignoré par Git) ; vérifier que vlcj charge libVLC depuis ce dossier — *archive vérifiée (SHA-256 officiel), copiée dans `desktop/resources/windows/vlc` et dans l'image installée (`tools/fetch-vlc.ps1`) ; chargement par vlcj à vérifier avec une vraie chaîne*
- [ ] 5.2 Lecteur vlcj (`SwingPanel`), une seule instance, stop avant play, pistes audio / sous-titres, format, reconnexion ; vérifier en lisant une chaîne française et en zappant trois fois vite (un seul flux ouvert, vu par les connexions réseau du processus)
- [ ] 5.3 Plein écran (F, double-clic, Échap), raccourcis et molette ; vérifier par capture

## 6. Installation et vérification

- [x] 6.1 `gradlew :desktop:createDistributable`, copie dans `%LOCALAPPDATA%\Programs\Sokoul\`, raccourcis Bureau et menu Démarrer, `Désinstaller Sokoul.cmd` ; vérifier en lançant l'app depuis le raccourci du Bureau — *`tools/install-desktop.ps1` ; lancement depuis le raccourci : fenêtre « Sokoul » ouverte (premier lancement lent, environ 1 min 30, analyse antivirus) ; contenu non capturé, l'utilisateur regardait une vidéo protégée*
- [ ] 6.2 Relecture par l'agent `android-reviewer`, `testDebugUnitTest assembleDebug` toujours au vert, essai d'ensemble sur le PC (import, Direct, guide, lecture)

## Notes de mise en œuvre

- **Fichiers** : écrans dans `desktop/src/main/kotlin/com/sokoul/desktop/` (`ui/LiveScreen.kt`, `ui/GuideScreen.kt`, `ui/PlayerScreen.kt`, `ui/SetupScreens.kt`), données dans `data/` (`DesktopDb`, `BackupImport`, `DesktopSync`, `LiveLogic`) et lecteur dans `player/DesktopPlayer.kt`.
- **Vidéo VLC native** : rien ne se dessine par-dessus. La barre du lecteur est donc sous l'image, et masquée en plein écran. Les raccourcis passent par `KeyboardFocusManager` pour fonctionner même quand la vidéo a le focus.
- **Erreurs non rattrapées** : écrites dans `%APPDATA%\Sokoul\crash.log`, identifiants masqués.

## Workflow follow-up

- `.msi` quand WiX sera installé (même configuration `nativeDistributions`).
- Changements suivants : `pc-windows-vod` (films, séries, fiches TMDB, reprise), puis `pc-windows-debrid` (Real-Debrid, fenêtre Sources).
- `/opsx:verify pc-windows-base`, puis archivage après essai sur le PC.
