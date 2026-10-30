# Sokoul TV

Application Android (Kotlin, Compose) d'IPTV et de VOD : Xtream/M3U, TMDB, Real-Debrid, Trakt.
Elle tourne sur Google TV, Fire TV, tablette et téléphone. Réponses et artefacts en français.

## Compiler et tester (Windows, PowerShell 5.1)
```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User') + ";$env:APPDATA\npm"   # git et openspec
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'
& "$PWD\app-android\gradlew.bat" -p "$PWD\app-android" testDebugUnitTest assembleDebug --console=plain
```
- Sorties dans `%LOCALAPPDATA%\SokoulBuild\…`. Ce dossier est partagé entre sessions : en cas d'erreur Hilt « Could not get element… », lancer `:app:clean`.
- Pour essayer sur un appareil, utiliser le skill `sokoul-device-check` et le script `tools/adb-tablet.ps1`.

## Organisation
- Code : `app-android/app/src/main/java/com/sokoul/iptv/`. Tests JVM : `app-android/app/src/test/`.
- `ui/theme/Layout.kt` définit les classes d'écran : COMPACT (< 600 dp), MEDIUM (tablette portrait), EXPANDED (tablette paysage), TV. `isTouch` vaut `window != TV`.
- Specs et changements : `openspec/`. Le flux de travail est spec-driven, avec les commandes `/opsx:*` : explore, new, continue, ff, propose, apply, verify, sync, archive, bulk-archive, update, onboard.

## Règles
- **Jetons de design** : dans `ui/`, passer par `SokoulColors`, `Radius`, `Type` et `Space`. `DesignTokensTest` interdit `Color(0x…)`, `Color.White/Black`, `RoundedCornerShape`, `CircleShape` et les tailles `.sp` en dur.
- **Une seule connexion IPTV** autorisée par l'abonnement. Un seul `StreamPlayer` actif par usage, toujours arrêté et libéré quand l'écran n'est plus visible. Les enregistrements sont prioritaires (`ProviderGate`).
- **Room** : tout changement de schéma demande une nouvelle version, une migration et un test (`MigrationTest`). Ne jamais modifier une version déjà publiée.
- **Tactile et TV** : un même composable sert au doigt et à la télécommande. Tout changement doit garder les deux fonctionnels.
- **Installation** : uniquement la version debug `com.sokoul.iptv.debug`. Pas de keystore release sur ce PC.
- **Secrets** : jamais de clé API dans le dépôt (`app-android/local.properties` est ignoré par Git).
- **Essais sur appareil** : les supprimer à la fin (enregistrements, règles, rotation, luminosité). Les données existantes de l'utilisateur ne se nettoient qu'avec son accord.
- **Avant un commit** : faire relire le diff par l'agent `android-reviewer`.
