# Design

## Context

L'app PC (`app-android/desktop`, Compose Desktop, vlcj 4 avec libVLC 3.0.21) copie les composables de la tablette dans `desktop/src/main/kotlin/com/sokoul/iptv/ui/...`, en gardant les mêmes paquets. Les écrans PC sont dans `desktop/tablet/`, le lecteur dans `desktop/ui/PlayerScreen.kt` avec `iptv/ui/screens/player/VodOverlay.kt`.

Contraintes fixes :
- un seul lecteur (`DesktopPlayer`) ;
- une seule connexion IPTV ;
- SQLite (`DesktopDb`) ;
- aucune clé dans le dépôt.

Les captures de référence sont dans `scratchpad/ref/`.

## Goals / Non-Goals

**Goals**
- Toutes les fonctions listées dans la proposition, avec le design de la tablette, à la souris et au clavier.
- La logique pure dans des objets testés par JVM : règles d'enregistrement, ordre d'une saga, prochain film, épisode suivant, décalage du guide, tri d'« À venir ».

**Non-Goals**
- Réveiller le PC pour un enregistrement, ou enregistrer app fermée : l'enregistrement est marqué « Manqué ».
- Synchroniser les enregistrements avec la tablette.
- Profils multiples sur PC.

## Decisions

1. **Enregistrements**
   - Table `recordings` : id, chaîne, titre, début, fin, état, chemin, octets, erreur.
   - `RecordingRules` (pur) :
     - marges de la tablette : 2 minutes avant et 5 minutes après ;
     - refus des chevauchements entre chaînes ;
     - doublons ;
     - état à l'ouverture de l'app : programmé dans le passé = manqué.
   - Côté Android, `RecordingRules` dépend de types Room. On écrit donc une version PC dans `desktop/data/`, avec les mêmes règles, plutôt que de déplacer le code Android.
   - `DesktopRecorder` lit le flux en continu avec OkHttp (même User-Agent que le lecteur) et l'écrit en `.ts` dans `AppDirs.data/recordings`. Le flux HLS n'est pas géré : le fournisseur Xtream donne du TS (`.ts` dans `LiveLogic.stream`). Un refus 4xx est noté en échec, sans relance immédiate.
   - Priorité à l'enregistrement : `AppState.play` refuse une autre chaîne pendant un enregistrement, avec le message de la tablette, « *chaîne* est en cours d'enregistrement ». La même chaîne se lit normalement : l'abonnement accepte alors deux lectures du même flux, ce qu'il faudra vérifier en réel, sinon on lira le fichier en cours.
   - La planification passe par une coroutine qui vérifie la table toutes les 20 s.

2. **Pause du direct** : libVLC sait mettre un flux en pause avec son module de différé (`--input-timeshift-granularity`, dossier temporaire). La pause est donc la pause du lecteur. « Retour au direct » relance le flux de la chaîne. Le bandeau de différé (`TimeshiftBar`) affiche le retard, calculé entre l'heure de la pause et l'heure de reprise.

3. **Aperçu du Direct** : c'est le lecteur unique, rendu dans le panneau (le même `VideoSurface`), son coupé. « Regarder » passe `app.playing` en Live sur la même chaîne, sans `play()` si le flux est déjà celui de la chaîne. Quitter le Direct (changement d'écran) arrête l'aperçu.

4. **À venir et Historique**
   - « À venir » : TVmaze (sans clé), retrouvé par l'IMDb de TMDB (`catalog.imdbId`), avec un cache dans la table `tmdb_cache` (12 h).
   - « Historique » : table `resume` (terminés compris), groupée par jour.

5. **Sagas**
   - `curated_sagas.json` est copié dans les ressources du PC, et `CuratedSagas` (pur) le lit : ordre du récit, collections réunies, séries.
   - `SagaOrder` (pur) donne l'ordre de sortie ou du récit, et le prochain film non vu (table `resume`).
   - L'exception à la vérification des `.json` avant commit est notée ici. Le fichier ne contient que des identifiants TMDB publics.

6. **Fiche** : « Finit à » = maintenant + durée. « Aussi sur » vient de TMDB `/{type}/{id}/watch/providers` (FR, flatrate), un nouvel appel de `TmdbClient` dans `:core`. Côté Android, c'est Movie of the Night, mais la ligne affichée est la même. La section saga vient de `collectionId`, déjà lu par `TmdbClient`.

7. **Lecteur de films**
   - `VersionPanel` et `SubtitlePanel` sont copiés de `PlayerPanels`, sous forme de panneau latéral (`SidePanel`), alimentés par `DebridStream` et les pistes libVLC au lieu de `PlayerSource`.
   - Les échecs sont mémorisés pour le titre en cours (clé → message) dans `AppState`.
   - Vitesse : `controls().setRate`. Format d'image : `video().setAspectRatio` et `setScale`, ou un recadrage dans `VideoSurface`.

8. **Fenêtre réduite** : `WindowState` est passé en Floating 480×270, `alwaysOnTop`, avec l'ancienne taille mémorisée. Le contenu montre la vidéo seule, avec un bouton « Agrandir ».

9. **Épisode suivant** : `NextEpisodeRule` (pur) donne l'épisode suivant (fin de saison → S+1E1, à partir de `TmdbInfo.seasons` et des épisodes de la saison). `NextUpCard` est copiée de la tablette.

10. **Réglages**
    - Table `settings` (existante), avec les clés `play.audio`, `play.subs`, `sub.size`, `sub.color`, `sub.bg`, `sub.pos`, `play.autonext`, `play.cache`, `tv.start`, `tv.zap`, `epg.offset`.
    - L'apparence des sous-titres (`--freetype-rel-fontsize`, `--freetype-color`, `--freetype-outline-thickness`, `--freetype-background-opacity`, `--sub-margin`) est une option de l'instance libVLC. Elle s'applique donc en recréant la fabrique quand rien n'est en lecture, sinon au prochain lancement.
    - `AudioChoice.pick` reçoit la langue préférée.

## Risks / Trade-offs

- **Deux lectures du même flux pendant un enregistrement** : si le fournisseur refuse, on lit le fichier en cours d'écriture (VLC lit un `.ts` qui grossit).
- **Différé de libVLC** : il dépend du flux. Si la pause casse sur certains flux, on garde la pause simple et on relance au direct.
- **Fenêtre réduite et plein écran** : on sort du plein écran avant de réduire.
- **Taille du changement** : l'implémentation se fait par groupes, chacun compilé, testé et capturé, puis commité séparément.
