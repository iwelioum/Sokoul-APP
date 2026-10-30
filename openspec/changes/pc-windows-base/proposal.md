# Proposal

## Why

Sokoul ne tourne que sous Android (TV, tablette, téléphone). L'utilisateur veut l'utiliser aussi sur son PC Windows, comme une vraie application de bureau installée à part, et non dans un émulateur ou une fenêtre de tablette. C'est le premier de trois changements ; celui-ci pose la base et livre le Direct.

## What Changes

- **Application Windows « Sokoul »** :
  - installée par un programme d'installation (`.msi`), avec une entrée dans le menu Démarrer ;
  - fenêtre redimensionnable, plein écran possible ;
  - pilotée à la souris et au clavier.
- **Code commun** : la logique sans Android est sortie dans un module partagé. On y trouve :
  - lecture des playlists (Xtream, M3U, XMLTV) ;
  - clients TMDB, Xtream et Real-Debrid ;
  - classement des versions ;
  - chiffrement des sauvegardes.

  L'app Android et l'app PC l'utilisent toutes les deux ; l'app Android ne change pas de comportement.
- **Reprise de la configuration de la tablette** : l'app PC importe le fichier de sauvegarde chiffrée de l'app Android (phrase de passe). Elle en reprend les playlists, les clés, les favoris et Ma liste, les réglages et le contrôle parental, sans rien ressaisir.
- **Direct et guide TV** :
  - groupes et chaînes de la playlist active, avec recherche ;
  - favoris ;
  - guide EPG en grille ;
  - lecture en direct en plein écran ou en fenêtre, avec zapping au clavier et à la molette.
- **Lecteur vidéo** fondé sur VLC, pour lire les flux IPTV (TS, HLS) comme sur Android :
  - pistes audio et sous-titres ;
  - formats d'image ;
  - une seule connexion à l'abonnement à la fois.

## Capabilities

### New Capabilities
- `desktop-app` : application Windows installable (fenêtre, navigation souris et clavier, mise à jour des données).
- `desktop-live` : Direct, guide TV et lecture en direct sur PC.
- `config-import` : reprise de la configuration de l'app Android par sa sauvegarde chiffrée.

### Modified Capabilities
<!-- aucune : le comportement de l'app Android ne change pas (seule l'organisation du code change) -->

## Impact

- **Appareils** : PC Windows 10/11 (64 bits). Aucun changement visible sur TV, tablette ou téléphone.
- **Code** :
  - nouveau module Gradle `:core` (JVM), où partent les fichiers sans Android ;
  - nouveau module `:desktop` (Compose Desktop) ;
  - `:app` dépend de `:core`.
- **Dépendances nouvelles** :
  - Compose Multiplatform (bureau) ;
  - vlcj (VLC doit être installé, ou fourni avec l'installateur) ;
  - SQLite JDBC.
- **Données** : base SQLite propre au PC dans le dossier utilisateur. Les clés et identifiants n'y sont lisibles que par l'utilisateur Windows.

## Hors périmètre

- Films, séries et fiches TMDB : changement `pc-windows-vod`.
- Real-Debrid et la fenêtre Sources : changement `pc-windows-debrid`.
- Enregistrement, multi-écran, profils multiples, synchronisation automatique en continu entre PC et tablette (l'import se fait à la demande).
- macOS et Linux (possibles plus tard avec le même code).
