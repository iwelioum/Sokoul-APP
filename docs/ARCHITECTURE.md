# Architecture de Sokoul TV

```
app-android/
  parser/   Kotlin pur (testable sans Android) : M3U, XMLTV, API Xtream, URLs, nettoyage des titres
  app/      Application Android (Compose, Hilt, Room, Media3, WorkManager)
```

## Module `parser`

| Fichier | Rôle |
|---|---|
| `M3uParser` | Lecture en flux de playlists M3U/M3U8 : attributs `tvg-*`, `group-title`, `catchup*`, `#EXTVLCOPT`, `#KODIPROP`, en-têtes `url|User-Agent=…` |
| `M3uClassifier` | Répartit les entrées en chaîne / film / épisode |
| `XmltvParser` | Lecture en flux du XMLTV (mémoire constante), fuseaux horaires, langue préférée, `episode-num` |
| `Xtream` | Conversion tolérante des réponses `player_api.php` (types incohérents, `[]` au lieu d'objets, base64) |
| `StreamUrls` | URLs Xtream (`/live`, `/movie`, `/series`, `/timeshift`) et catch-up M3U (default, append, shift, flussonic, xc) |
| `TitleTagger` | « FR\| Le Parrain (1972) [VFF] 4K » → titre, année, langue, qualité ; clé de rapprochement EPG |
| `ServerUrl` | Normalisation de l'adresse serveur, détection d'un lien M3U Xtream |

Tests : `.\gradlew.bat :parser:test` (ajouter `-DrealWorld=true` pour tester sur une vraie playlist et un vrai guide).

## Module `app`

**Données** (`data/`)
- `db/` : Room. Clés composites `(playlistId, …)` → plusieurs playlists sans collision. Table `category_prefs`
  séparée pour que masquer/verrouiller un groupe survive aux synchronisations.
- `repo/SyncEngine` : synchronisation par « génération » — les nouvelles lignes sont écrites par lots, puis les
  anciennes supprimées. L'interface reste utilisable pendant la synchro et un échec réseau ne vide jamais la base.
  L'EPG ne garde que les chaînes présentes dans la playlist (par `tvg-id` ou par nom normalisé).
- `repo/EpgRepository` : programme en cours/suivant ; repli sur `get_short_epg` Xtream si le XMLTV ne couvre pas la chaîne.
- `repo/StreamResolver` : construit l'URL de lecture (direct, replay, film, épisode, enregistrement).
- `SecretBox` : mots de passe chiffrés AES-GCM avec une clé Android Keystore.
- `work/SyncWorker` : rafraîchissement périodique (WorkManager), et synchro immédiate à la demande.

**Lecteur** (`player/`)
- `PlayerFactory` : ExoPlayer réglé IPTV (tampon de démarrage court, décodeur FFmpeg pour AC3/E-AC3/DTS,
  extracteur TS tolérant, OkHttp partagé pour réutiliser les connexions au zapping).
- `StreamPlayer` : reconnexion automatique avec temporisation, alternance TS ↔ HLS, HLS forcé pour les URLs
  sans extension, relance d'un direct bloqué plus de 12 s.

**Enregistrement** (`recording/`) : service au premier plan, copie directe MPEG-TS ou suivi de playlist HLS,
reconnexion jusqu'à l'heure de fin, programmation par alarme exacte, rappels de programmes.

**Interface** (`ui/`) : Compose. Composants `FocusSurface` / `dpadClickable` communs à la télécommande
(OK, OK long, touche Menu) et au tactile. Barre latérale sur TV/tablette, barre du bas sur téléphone.
La grille EPG gère elle-même la navigation au D-pad (aucun élément focalisable par case) pour rester fluide
avec des milliers de chaînes.

## Raccourcis télécommande (lecteur, direct)

| Touche | Action |
|---|---|
| ▲ / ▼, CH+ / CH− | Chaîne précédente / suivante |
| OK ou ◀ | Liste des chaînes (▲▼ pour choisir, OK pour lancer) |
| ▶ | Bandeau d'informations |
| 0-9 | Accès direct par numéro |
| Menu / Info | Options : audio, sous-titres, format, favoris, enregistrement, replay, multi-écran |
| Rouge | Enregistrer le programme en cours |

En VOD/replay : ◀ ▶ avance/recul (accéléré en restant appuyé), OK pause, ⏭ épisode suivant.
