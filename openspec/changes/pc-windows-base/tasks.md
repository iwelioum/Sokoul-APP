# Tasks

## 1. Module partagé `:core`

- [ ] 1.1 Créer le module JVM `:core` (Kotlin 2.0.21, JDK 17, OkHttp, kotlinx.serialization, coroutines, javax.inject) et le brancher sur `:app` ; vérifier que `gradlew :core:build :app:assembleDebug` passe sans code déplacé
- [ ] 1.2 Déplacer dans `:core`, paquets inchangés, les fichiers sans Android de `data/debrid`, `data/repo`, `data/net` (clients TMDB, Xtream, Real-Debrid), `domain`, `BackupCrypto`, et sortir les modèles de sauvegarde dans `BackupModels.kt` ; vérifier que `testDebugUnitTest assembleDebug` reste au vert (420 tests) et que les tests déplacés passent dans `:core`
- [ ] 1.3 `DebridSources` sur une interface `NetworkInfo` (implémentation Android inchangée) ; vérifier par les tests existants de `DebridSourcesTest`
- [ ] 1.4 Installer l'APK sur la tablette et vérifier par captures : accueil, fiche, lecture d'un film, Direct (aucun changement visible)

## 2. Squelette de l'app PC

- [ ] 2.1 Créer `:desktop` (Compose Multiplatform bureau 1.7.x), fenêtre « Sokoul », `DesktopTheme` (jetons recopiés de `ui/theme`), navigation latérale et pile d'écrans, taille et position retenues ; vérifier par `gradlew :desktop:run` et une capture de la fenêtre
- [ ] 2.2 `DesktopDb` (sqlite-jdbc) dans `%APPDATA%\Sokoul\` avec ACL limitée au compte courant ; vérifier par un test JVM (création des tables, écriture et lecture d'une playlist) et par `icacls` sur le dossier

## 3. Configuration

- [ ] 3.1 `DesktopImport` : lecture de la sauvegarde Android (chiffrée ou non) via `BackupModels` + `BackupCrypto`, fusion dans `DesktopDb` ; vérifier par un test JVM (aller-retour avec une sauvegarde chiffrée générée par `BackupCrypto`, mauvaise phrase refusée, second import sans perte)
- [ ] 3.2 Écran de premier lancement (« Importer depuis ma tablette » / « Ajouter une playlist ») et Réglages (playlists, clés, synchronisation) ; vérifier en important une vraie sauvegarde exportée de la tablette (fichier supprimé ensuite)

## 4. Direct et guide

- [ ] 4.1 `DesktopSync` : chaînes et guide via `:parser` et `XtreamClient`, au lancement si plus de 12 h et à la demande ; vérifier par un test JVM sur un échantillon M3U/XMLTV et par un essai réel (nombre de chaînes et de programmes cohérent avec la tablette)
- [ ] 4.2 Écran Direct (groupes, chaînes, programme en cours, recherche, favoris, clic droit, code parental) ; vérifier par captures de la fenêtre et des tests de la logique de filtrage
- [ ] 4.3 Écran Guide TV (grille, jours, détail d'un programme) ; vérifier par capture et défilement souris / clavier

## 5. Lecture

- [ ] 5.1 Lecteur vlcj (`SwingPanel`), une seule instance, stop avant play, pistes audio / sous-titres, format, reconnexion, détection de VLC absent ; vérifier en lisant une chaîne française et en zappant trois fois vite (un seul flux ouvert, vérifié par les connexions réseau du processus)
- [ ] 5.2 Plein écran (F, double-clic, Échap), raccourcis et molette ; vérifier à la main et par capture

## 6. Empaquetage et vérification

- [ ] 6.1 `gradlew :desktop:packageMsi` puis installation sur le PC : entrée dans le menu Démarrer, lancement, désinstallation propre ; vérifier par captures
- [ ] 6.2 Relecture par l'agent `android-reviewer` (et revue du module PC), `testDebugUnitTest assembleDebug` toujours au vert, essai d'ensemble sur le PC (import, Direct, guide, lecture)

## Workflow follow-up

- Changements suivants : `pc-windows-vod` (films, séries, fiches TMDB, reprise), puis `pc-windows-debrid` (Real-Debrid, fenêtre Sources).
- `/opsx:verify pc-windows-base`, puis archivage après essai sur le PC.
