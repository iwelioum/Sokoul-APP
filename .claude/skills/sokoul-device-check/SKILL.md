---
name: sokoul-device-check
description: Compiler, tester, installer et essayer Sokoul sur la tablette (ou la Fire TV) avec captures adb. À utiliser pour vérifier une modification sur un appareil réel, faire des captures avant/après, ou cocher une tâche OpenSpec qui demande un essai sur appareil.
---

# Essai de Sokoul sur appareil

## 1. Compiler et tester
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'
$d = "$PWD\app-android"
& "$d\gradlew.bat" -p $d testDebugUnitTest assembleDebug --console=plain -q
```
- Résultats des tests : `%LOCALAPPDATA%\SokoulBuild\app\test-results\testDebugUnitTest\*.xml` (compter `tests`, `failures`, `errors`).
- Le dossier de build est partagé avec d'autres sessions : en cas d'erreur Hilt « Could not get element for … », lancer `:app:clean`.
- Ne jamais installer si un test échoue.

## 2. Installer et lancer
```powershell
.\tools\adb-tablet.ps1 install
.\tools\adb-tablet.ps1 launch
```
- **Une réinstallation tue le processus** : un enregistrement en cours est coupé puis relancé au démarrage. Prévenir l'utilisateur s'il en a un.
- Seule la version debug `com.sokoul.iptv.debug` est installée ; ne jamais toucher à `com.sokoul.iptv`.
- Tablette injoignable : `adb mdns services` donne le nouveau port ; sinon demander à l'utilisateur d'ouvrir Débogage sans fil.

## 3. Captures
```powershell
.\tools\adb-tablet.ps1 shot -Cmd "input tap 94 1180; sleep 3" -Out "$env:TEMP\guide.png"
```
Puis lire l'image avec l'outil Read. Repères (tablette, portrait 1600×2560 px, image affichée réduite à 1250×2000 : multiplier par 1,28) :
- rail de gauche (x ≈ 94) : Recherche 250, Accueil 400, Films 510, Séries 650, Ma liste 780, Sagas 910, Direct 1050, Guide 1180, Enreg. 2310, Réglages 2440 ;
- ne pas glisser depuis le bord droit (x > 1495) : c'est le geste Retour ;
- les commandes du lecteur se masquent après 5 s : les afficher (`input tap` au centre) dans la même commande que le geste testé.

Paysage : `.\tools\adb-tablet.ps1 rotate landscape`, puis **toujours** `.\tools\adb-tablet.ps1 rotate restore` à la fin.

## 4. Journaux
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s (.\tools\adb-tablet.ps1 serial) logcat -d -b crash
```
- Lecture arrêtée après Accueil : `adb shell dumpsys media_session` ne doit plus montrer d'état PLAYING.
- Base : `run-as com.sokoul.iptv.debug` puis copier `databases/sokoul.db*` **app arrêtée** (`am force-stop`), lire avec `platform-tools\sqlite3.exe`, supprimer la copie ensuite.

## 5. Règles
- Une seule connexion IPTV : ne pas laisser un flux ou un enregistrement de test occuper l'abonnement ; tout essai qui crée un enregistrement ou une règle est nettoyé à la fin.
- Remettre l'appareil comme il était (rotation, luminosité, veille).
- Pour un audit d'écran, passer les captures à l'agent `tablet-ux-auditor`.
