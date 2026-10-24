# Sokoul

Dossier central de tous les projets streaming de **iwelioum**.

| Dossier | Contenu |
|---|---|
| `app-android/` | **Sokoul TV** — application IPTV Android TV / Fire TV / mobile (Xtream Codes + M3U). C'est le projet actif. |
| `dist/` | APK prêts à installer (générés par le build). |
| `docs/` | Inventaire des anciens projets, architecture, feuille de route. |
| `legacy/` | Clones des anciens dépôts (`SokoulIPTV`, `Sokoul`, `GinBox`), conservés en référence avec leur historique git. |

## Installer l'application

1. Sur la box / Fire TV : activer *Options développeur › Débogage ADB* (ou *Sources inconnues*).
2. Copier `dist/SokoulTV-release.apk` sur l'appareil (clé USB, app *Downloader*, ou `adb install`).
3. Au premier lancement, ajouter l'abonnement :
   - **Xtream Codes** : adresse du serveur (ex. `http://exemple.com:8080`), identifiant, mot de passe ;
   - **M3U** : lien de la playlist ou fichier local. Un lien `get.php?username=…&password=…` est automatiquement converti en compte Xtream.

Installation par câble ou Wi-Fi depuis ce PC :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" connect 192.168.1.XX:5555
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\SokoulTV-release.apk
```

## Compiler

Prérequis installés sur ce PC : JDK 17 (Microsoft), Android SDK (`%LOCALAPPDATA%\Android\Sdk`), Git, GitHub CLI.

```powershell
cd app-android
.\gradlew.bat :parser:test :app:assembleRelease
```

Les dossiers `build/` sont redirigés vers `%LOCALAPPDATA%\SokoulBuild` pour ne pas être synchronisés par OneDrive.
Pour ouvrir le projet dans Android Studio : *File › Open* → `app-android`.

Voir `docs/ARCHITECTURE.md` pour le fonctionnement interne et `docs/ROADMAP.md` pour la suite.
