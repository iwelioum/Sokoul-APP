# Design

## Context

Voir proposal.md (Why). Le dépôt `app-android/` contient déjà trois modules Gradle.
- **`:parser`** : module JVM pur, qui lit Xtream, M3U et XMLTV. XmlPullParser y est en `compileOnly`, car Android le fournit.
- **`:app`** : l'application Android.
- **`:volume`** : un module à part.

Dans `:app`, beaucoup de code est déjà sans Android :
- `data/debrid/` : `SourceRanker`, `ReleaseName`, `DebridSearch`, `DebridAddons` ;
- `data/repo/` : `VersionPicker`, `VodGrouping`, `VodQuality`, `EpisodeMerge`, `BackupCrypto`, etc. ;
- `domain/`.

Les clients réseau `TmdbClient`, `XtreamClient` et `RealDebridClient` n'utilisent que `javax.inject` et OkHttp, deux bibliothèques JVM. Le code qui reste lié à Android est l'interface (Compose Android, Hilt, Navigation), Room, Media3, WorkManager, DataStore et `DebridSources`, qui dépend de `ConnectivityManager`.

La sauvegarde (`BackupManager`, version 3) est du JSON kotlinx.serialization, éventuellement chiffré par `BackupCrypto` (PBKDF2 → AES-256-GCM).

## Goals / Non-Goals

**Goals :**
- partager le code pur sans le dupliquer, en gardant les mêmes paquets (aucun import à changer dans `:app`) ;
- une app PC native en Kotlin, avec la même charte (couleurs, typographies, jetons de `ui/theme`) ;
- importer la sauvegarde de l'app Android, avec les mêmes classes `@Serializable`.

**Non-Goals :**
- interface partagée entre Android et PC : les écrans PC sont écrits pour la souris et le clavier, et les composables Android ne sont pas réutilisés tels quels ;
- Room multiplateforme.

## Decisions

### 1. Module `:core` (JVM) extrait de `:app`, paquets inchangés, et qui grandit changement par changement
- Constat à la réalisation : beaucoup de fichiers « purs » dépendent en fait des entités Room (`data.db.*`), de `SettingsStore` (DataStore) ou de `TmdbDao`. Les déplacer tous demanderait de refondre une grande partie de `:app`.
- Ce changement déplace seulement les feuilles indépendantes : `BackupCrypto`, `PinHasher` et `XtreamClient` (qui ne dépend que de `:parser` et d'OkHttp). Ils passent dans `app-android/core/src/main/kotlin/` avec le même `package`.
- `:app` déclare `implementation(project(":core"))`.
- `XtreamClient` reste annoté `@Inject` : `javax.inject` est une simple dépendance JVM. Hilt le fournit toujours côté Android, et le PC le construit à la main.
- Les clients TMDB et Real-Debrid, ainsi que le classement des versions, rejoindront `:core` avec `pc-windows-vod` et `pc-windows-debrid`. Il faudra alors des interfaces à la place de `TmdbDao` et de `SettingsStore`.
- La sauvegarde est lue sur PC par des modèles minimaux propres (`ignoreUnknownKeys`), qui suivent le format JSON v3. On ne partage pas les classes de `BackupManager`, liées aux entités Room. Un test PC vérifie le format sur une sauvegarde chiffrée par `BackupCrypto`.

*Écarté* : copier les fichiers dans `:desktop`, ce qui ferait deux versions qui divergent. Écarté aussi : Kotlin Multiplatform complet (`expect`/`actual`), trop lourd pour une seule cible JVM de bureau.

### 2. `:desktop` en Compose Multiplatform (bureau)
- Plugin `org.jetbrains.compose` 1.7.x, compatible Kotlin 2.0.21, et `compose.desktop.currentOs`.
- Navigation simple par état (écran courant et pile).
- Jetons de couleur et de forme recopiés de `ui/theme` dans un `DesktopTheme`. On ne partage pas le code Android de thème, qui dépend de ressources Android.
- **Paquet** : image jpackage (`createDistributable`), avec `Sokoul.exe` et le JRE embarqué. WiX n'est pas installé sur le PC : le `.msi` est remis à plus tard, avec le même `nativeDistributions`.
- **Installation** : l'image est copiée dans `%LOCALAPPDATA%\Programs\Sokoul\`, avec des raccourcis Bureau et menu Démarrer (icône Sokoul) et un `Désinstaller Sokoul.cmd`.

### 3. Lecteur : vlcj sur VLC
- Les flux IPTV (MPEG-TS, HLS, parfois avec en-têtes User-Agent/Referer) sont lus de façon fiable par libVLC. JavaFX Media ne lit pas le TS, et un lecteur Java pur serait trop limité.
- vlcj 4.8 (`CallbackMediaPlayerComponent`) est rendu dans Compose par `SwingPanel`.
- Une seule instance de lecteur par fenêtre : un changement de chaîne fait `stop()` puis `play()` du nouveau média, jamais deux médias ouverts.
- libVLC est **fourni avec l'app** : `libvlc.dll`, `libvlccore.dll` et `plugins/` sont tirés de l'archive officielle VLC 3.0.21 win64 et copiés dans les ressources de l'image jpackage. vlcj les trouve par un chemin explicite, sans rien installer dans Windows. Si les fichiers manquent, un écran l'explique.

### 4. Données : SQLite JDBC + couche d'accès simple
- `org.xerial:sqlite-jdbc`, avec des tables reprises du schéma Room actuel, limitées à ce que le PC utilise : playlists, catégories, chaînes, programmes, favoris, progression, réglages.
- On reste en SQL simple, avec un petit `DesktopDb` et des requêtes préparées, sans ORM.
- Dossier `%APPDATA%\Sokoul\`. Les droits sont limités au compte Windows courant (ACL posées à la création, par `java.nio` `AclFileAttributeView`).

### 5. Synchronisation
- `SyncEngine` est lié à Room et WorkManager, et ne se partage donc pas. Sur PC, `DesktopSync` réutilise les parseurs de `:parser` et `XtreamClient` de `:core`, et écrit dans `DesktopDb` par lots dans une transaction.
- Il tourne au lancement et à la demande, dans une coroutine `Dispatchers.IO`.

### 6. Import de la sauvegarde
- `BackupModels` (`:core`) et `BackupCrypto` (`:core`) servent à lire le fichier.
- `DesktopImport` fait correspondre les modèles aux tables du PC. Les playlists sont reconnues par URL et identifiant ; favoris et progression sont fusionnés (le plus récent l'emporte).

## Risks / Trade-offs

- **[Refactorisation de `:app`]** Déplacer des fichiers risque de casser l'app Android. Parade : déplacement sans changer les paquets, puis `testDebugUnitTest assembleDebug` au vert, puis essai sur la tablette avant tout code PC.
- **[VLC absent]** Parade : détection au lancement et écran d'aide. L'installateur fourni avec libVLC sera traité plus tard.
- **[Connexion unique sur PC et tablette en même temps]** L'abonnement refuse une deuxième connexion. Le PC affiche le même message que l'app Android, en reprenant les libellés de `StreamResolver`.
- **[Taille de l'installateur]** Le JRE est embarqué par jpackage (environ 80 Mo), ce qui est acceptable.

