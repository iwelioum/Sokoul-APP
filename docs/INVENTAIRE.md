# Inventaire des projets streaming (GitHub iwelioum)

Relevé effectué le 24/10/2026 sur les dépôts publics. Les dépôts privés n'ont pas encore été inspectés
(nécessite `gh auth login`).

## SokoulIPTV (Kotlin, Android TV) — base de Sokoul TV

Un seul commit (« initialisation du projet Sokoul IPTV Android TV »). Architecture propre (multi-modules,
Hilt, Room, Retrofit, Media3, Compose for TV) mais **inutilisable en l'état** :

| Problème | Conséquence | Corrigé dans Sokoul TV |
|---|---|---|
| Trafic HTTP en clair interdit | aucun serveur Xtream `http://` ne fonctionnait | oui (`network_security_config`) |
| URL VOD sans `/movie/` | films en erreur 404 | oui (`XtreamUrls`) |
| EPG : timestamps lus sur les mauvais champs, titres base64 non décodés | guide vide / illisible | oui (`XtreamParse.shortEpg`) + XMLTV complet |
| Clés Room : catégories et saisons écrasées entre types / séries | catégories mélangées | oui (clés composites) |
| Favoris impossibles à ajouter, reprise jamais enregistrée | fonctions mortes | oui |
| Lecteur jamais libéré, pas de reconnexion | son en arrière-plan, coupures définitives | oui (`StreamPlayer`) |
| Wrapper Gradle absent, Gradle 9 + AGP 8.7 incompatibles | ne compilait pas | oui (Gradle 8.11.1) |
| Mot de passe dans les logs HTTP | fuite d'identifiants | oui (logs masqués, debug uniquement) |
| Pas de M3U, pas de catch-up, pas d'enregistrement, pas de grille EPG, 0 test | — | ajoutés |

## Sokoul (Rust + SvelteKit)

Plateforme VOD auto-hébergée : catalogue TMDB, recherche torrents (Prowlarr/Jackett), téléchargement
librqbit, lecture via iframes d'embed, petit module « TV en direct » basé sur iptv-org.
Pas de client Xtream/M3U, EPG jamais rempli.

**Repris dans Sokoul TV :** conception du client TMDB (français par défaut, cache), conventions du lecteur
(VF prioritaire, repli sur la source suivante), idées d'accueil (reprise, « Ma liste », rangées).

## GinBox (Electron + Rust)

Application de bureau Windows films/séries via Real-Debrid, interface très soignée.
Aucune fonction IPTV.

**Repris dans Sokoul TV :** palette sombre et accent violet (design tokens), expressions de détection
langue/qualité (`parser.rs` → `TitleTagger`), ordre de repli des visuels, modèles de progression de lecture
et de listes.

## Hors sujet

`Riada`, `Riadadesignv1`, `Microsoft-Full-Stack` : sans rapport avec le streaming, non rapatriés.
