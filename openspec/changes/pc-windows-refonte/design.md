## Context

App de bureau Compose Multiplatform 1.7.3 (JVM 17), lecteur vlcj 4.8.3 avec le libVLC 3.0.21 fourni, base SQLite. La tablette, qui détient les identifiants IPTV, est injoignable : la refonte doit donc rendre l'app utile **sans IPTV**, grâce à TMDB pour le contenu et à Real-Debrid pour la lecture.

## Goals / Non-Goals

**Goals**
- Une identité identique à l'Android, adaptée à la souris : survol, molette, clavier.
- Toutes les API branchées et testables depuis Réglages.
- Une lecture Real-Debrid réelle, avec une barre en surimpression.

**Non-Goals**
- Trakt et les profils.
- Les enregistrements.
- L'installeur `.msi` : il faut WiX.

## Decisions

1. **Rendu vidéo par Compose.** vlcj `CallbackVideoSurface` en RV32 (BGRA) copie chaque image dans un `ByteArray`, puis une `Bitmap` Skia s'affiche dans un `Image`.
   - Cela permet de dessiner la barre et les menus par-dessus la vidéo, ce que la sortie native (heavyweight AWT) interdit.
   - Coût : une copie par image (8 Mo en 1080p), acceptable sur un PC.
   - La définition décodée est limitée à 2560 px de large.
   - *Alternative écartée :* une fenêtre transparente au-dessus du composant natif, fragile sous Windows (DPI, focus).

2. **Clés au build.** La tâche Gradle `generateDesktopKeys` lit `../local.properties` (`sokoul.key.*`) et écrit `build/generated/keys/DesktopKeys.kt`.
   - `ProviderConfig.key()` cherche d'abord la clé saisie dans Réglages (base protégée par ACL), puis `DesktopKeys`.
   - La lecture de `local.properties` à l'exécution, dépendante du dossier courant, est supprimée.

3. **Code partagé minimal.** Le cœur Real-Debrid de l'Android (`DebridSources`, `SourceRanker`) dépend de Room, des réglages et de l'appareil.
   - Le PC reprend donc la lecture des réponses (`DebridStreams`) et des noms (`ReleaseName`).
   - Il classe avec `DesktopRanker` : langue préférée (VF, MULTI, VOSTFR, VO), définition, origine du fichier, taille, en écartant les échantillons (`tooSmall`, même règle).
   - *Alternative écartée :* déplacer `SourceRanker`, qui entraîne VodVersion, VersionPicker, DeviceCaps et les entités Room.

4. **Navigation.**
   - Pile d'écrans simple (`Nav`) : un écran racine (Accueil, Films, Séries, Direct, Guide, Recherche, Réglages) et des écrans poussés (Fiche, Lecteur). Échap revient en arrière.
   - Barre du haut translucide au-dessus du héros. Elle s'opacifie au défilement.

5. **Images.** `NetImage` repose sur un chargeur maison : cache mémoire LRU de 200 Mo et cache disque dans `%LOCALAPPDATA%\Sokoul\images`. Le décodage se fait hors du fil d'interface.

6. **Rendus sans fenêtre.**
   - `renderScreens` (JavaExec du source set de test) dessine chaque écran avec des données d'exemple dans `ImageComposeScene` (1600×900), puis écrit des PNG dans `build/screens`.
   - Le design s'évalue ainsi sans ouvrir de fenêtre devant l'utilisateur.

## Risks / Trade-offs

- **Torrentio ou Comet indisponibles, ou bloqués** → message clair dans Sources, et les deux extensions sont essayées.
- **Coût du rendu vidéo** → copie unique par image, et repli possible sur la sortie native (réglage « Lecture compatible »).
- **Clés intégrées dans le binaire installé** → le binaire reste sur le PC de l'utilisateur et n'est pas distribué. C'est documenté.
