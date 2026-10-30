---
name: sokoul-desktop-check
description: Vérifier en conditions réelles l'app Sokoul pour Windows (module :desktop) — compiler, tester, lancer un écran précis sans voler le focus de l'utilisateur, capturer la fenêtre, lire un film Real-Debrid son coupé, installer. À utiliser après toute modification de app-android/desktop ou app-android/core, avant de dire qu'un écran PC fonctionne.
---

# Vérifier l'app PC Sokoul

Toutes les commandes sont lancées depuis la racine du worktree, dans PowerShell 5.1.

## 1. Compiler et tester

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'; $d="$PWD\app-android"
& "$d\gradlew.bat" -p $d :core:test :desktop:test --console=plain -q
```

Si `:core` a changé, relancer aussi `testDebugUnitTest assembleDebug`. L'app Android utilise le même code, et 420 tests doivent rester au vert.

## 2. Lancer un écran précis (essai réel, sans déranger)

La variable `SOKOUL_ROUTE` ouvre directement un écran, dans une fenêtre **qui ne prend pas le focus**. Le son est **coupé**, car l'utilisateur regarde peut-être autre chose.

| Écran | Valeur de `SOKOUL_ROUTE` |
|---|---|
| Accueil, Films, Séries, Direct, Guide, Réglages | `home`, `movies`, `series`, `live`, `guide`, `settings` |
| Recherche avec un texte | `search:dune` |
| Fiche | `detail:movie:27205`, `detail:tv:1396` |
| Fiche défilée jusqu'aux saisons | `detail:tv:1396:1` |
| Fenêtre Sources | `sources:movie:27205` |
| Lecture Real-Debrid (épisode : `play:tv:1396:1:1`) | `play:movie:27205` |

Pour garder la barre du lecteur affichée pendant la capture, ajouter `SOKOUL_CONTROLS=1`.

Procédure :
1. Définir `$env:SOKOUL_ROUTE`, puis lancer `gradlew :desktop:run` avec `Start-Process -WindowStyle Hidden`.
2. Attendre une fenêtre titrée « Sokoul ». Sur le processus `java`, comptez de 20 à 40 s.
3. Capturer avec `tools\desktop-shot.ps1 -Out <png>`, qui utilise PrintWindow sans passer la fenêtre au premier plan.
4. Fermer la fenêtre en arrêtant le processus `java` dont le titre est « Sokoul ».

## 3. Juger

- Lire chaque PNG.
- Pour un avis visuel, lancer l'agent `desktop-designer` (`.claude/agents/desktop-designer.md`) avec les chemins des captures.
- Pour le code, lancer `android-reviewer`.

## Pièges connus

- **Capture noire** : une vidéo protégée (DRM, Disney+…) est à l'écran chez l'utilisateur, ou la fenêtre est réduite. N'insistez pas.
- **Premier lancement de l'app installée** : environ 1 min 30, le temps de l'analyse antivirus. Les erreurs sont dans `%APPDATA%\Sokoul\crash.log`.
- **Real-Debrid ne dit plus ce qu'il a en cache.** Une version « pas prête » renvoie une vidéo d'attente (« Not ready yet »). `DesktopDebrid.resolve` n'accepte qu'un lien servi par `*.real-debrid.com`.
- **HDR** : l'image passe par Compose en RV32, sans conversion HDR vers SDR. Les versions SDR sont donc classées d'abord (`DesktopRanker`).
- **Clés API** : elles sont générées au build depuis `local.properties`, dans `build/generated/keys`, et ne sont jamais commitées. `git diff --cached --name-only` ne doit jamais montrer de fichier `DesktopKeys` ni `local.properties`.
- **Encodage** : ne jamais réécrire un source avec `Get-Content`/`Set-Content`, qui cassent les accents. Utiliser Edit, ou `[IO.File]` en UTF-8.
- **Abonnement IPTV** : une seule connexion à la fois. Ne pas lancer le Direct sur le PC pendant que la tablette regarde.

## 4. Installer

```powershell
& "$d\gradlew.bat" -p $d :desktop:createDistributable --console=plain -q; & .\tools\install-desktop.ps1
```

L'installation copie l'app dans `%LOCALAPPDATA%\Programs\Sokoul` et crée les raccourcis Bureau et menu Démarrer. Vérifier ensuite en lançant le raccourci.
