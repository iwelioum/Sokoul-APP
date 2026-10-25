# Rapport de tests — Sokoul TV 2.0 (phase « conformité avant design »)

Date : 24 octobre 2026
Appareil : Google TV Streamer (Android 14, 1920×1080), abonnement Xtream réel (1 connexion simultanée, 28 317 chaînes, 159 256 films, 64 311 séries).
Build : release (R8), Compose 1.8 (BOM 2025.05.01).

## 1. Tests automatisés

| Suite | Contenu | Résultat |
|---|---|---|
| `:parser` | M3U, XMLTV, Xtream (cas limites) | 18 tests, 2 ignorés (test réel `XtreamLiveCheck`, lancé seulement si `SOKOUL_XT_URL/USER/PASS` sont définis) |
| `:app` `RulesTest` | PIN (hash, format, blocage progressif), bornes des réglages, chiffrement des sauvegardes, validation des sauvegardes, règles d'enregistrement, seuils de reprise / « vu », échappement LIKE, fenêtre de replay, liste vide du serveur, masquage des identifiants dans les erreurs, multi-écran | OK |
| `:app` `DataTest` (Robolectric, Room en mémoire) | contrôle parental (expiration, blocage persistant, ancien code en clair re-haché), recherche avec `%`/`_` et groupes verrouillés, listes Tout/Favoris/Récents sans groupes verrouillés, favoris vides, export sans secrets, aller-retour chiffré, import piégé, fichiers invalides, suppression en cascade, schéma M3U interdit, conflits d'enregistrement | OK |
| Lint release | — | 0 erreur |

Total : **62 tests, 0 échec**. Commande : `gradlew :parser:test :app:testReleaseUnitTest :app:lintRelease`.

## 2. Correctifs de sécurité

| # | Problème | Correctif |
|---|---|---|
| S1 | Une playlist M3U pouvait lire un fichier interne (`file://`) | Seuls `http(s)://` et `content://` (hors fournisseur de l'app) sont acceptés |
| S2 | Sauvegarde avec mots de passe, PIN et clé TMDB en clair | Export sans secrets par défaut ; option « avec identifiants » chiffrée (PBKDF2 100 000 itérations + AES-256-GCM) |
| S3 | Import non validé | 5 Mo maximum, vérification de l'app et de la version, URL http(s) avec hôte valide, bornes, transaction tout-ou-rien, PIN existant jamais remplacé |
| S4 | PIN en clair, essais illimités, déverrouillé pour toute la session | PIN haché (PBKDF2 + sel). Après 5 essais : 30 s, 1 min, 2 min… jusqu'à 15 min, et le blocage survit au redémarrage. Re-verrouillage après 10 min d'inactivité et à la mise en arrière-plan. |
| S5 | Intent externe « lire la chaîne X » qui contournait le contrôle parental | Identifiant filtré, chaîne vérifiée avant ouverture, code demandé par le lecteur, extras effacés après usage |
| S6 | URL avec identifiants dans les messages d'erreur | Messages masqués ; `adb logcat` vérifié en release : 0 occurrence |
| S7 | Codes de requête d'alarme pouvant entrer en collision | Codes séparés par type (enregistrement / rappel), `FLAG_IMMUTABLE` |
| S9 | APK signé avec la clé debug | Signature lue dans `keystore.properties` (ignoré par git), repli sur debug si absent |
| S10 | TMDB en clair possible | TMDB forcé en https (network security config) |
| + | Contrôle parental contournable depuis Réglages › Groupes (trouvé sur la box) | Le déverrouillage d'un groupe demande le code |
| + | Contenu des groupes verrouillés visible dans Tout / Favoris / Récents / Accueil / Recherche | Masqué tant que le code n'a pas été saisi |
| + | Groupe verrouillé alors qu'il est affiché : il restait visible | Retour immédiat sur « Tout » |

S8 (migrations Room) : le schéma reste en version 1. La prochaine évolution du schéma devra ajouter une migration explicite.

## 3. Correctifs de logique métier

- **Suppression d'une playlist** : annule la synchro et les alarmes, arrête les enregistrements, vide le multi-écran, purge les données en transaction.
- **Synchro** :
  - Une liste vide renvoyée par le serveur n'efface plus la base.
  - Un compte expiré ou désactivé donne un message clair.
  - Une playlist supprimée pendant une synchro est purgée.
- **Enregistrements** :
  - Refusés en cas de chevauchement sur la même chaîne, de limite de connexions atteinte, de dates incohérentes ou de durée de plus de 12 h. Avertissement si une seule connexion est autorisée.
  - Vérification de l'espace disque (200 Mo minimum).
  - Erreurs 4xx sur les segments HLS prises en compte.
  - Noms de fichiers uniques et sûrs.
  - Arrêt manuel enregistré correctement (avant ce correctif, l'enregistrement restait « en cours » indéfiniment).
- **Multi-écran** : n'écrase plus une case quand la grille est pleine.
- **Favoris vides** : le zapping ne parcourt plus les 28 000 chaînes.
- **Replay** : refusé hors de la fenêtre catch-up.
- **Reprise de lecture** : à partir de 30 s, avec 5 s de recul. Contenu « vu » à 92 % ou quand il reste moins de 3 min.
- **Lecteur** : la sauvegarde de la position ne bloque plus le thread principal.
- **Recherche** : `%` et `_` échappés, au moins 2 caractères, 100 caractères maximum.
- **Android TV sans sélecteur de fichiers** :
  - Export dans `Android/data/com.sokoul.iptv/files/Sauvegardes`.
  - Restauration depuis cette liste ou une adresse web.
  - Bouton « fichier local » masqué.
- **Plantage corrigé** : `IllegalStateException: Release should only be called once`, déclenché en naviguant au D-pad dans les grilles (bug de Compose 1.7, `focusRestorer`). Corrigé en passant à Compose 1.8.
- **Focus** : au retour du lecteur, le focus revient sur la chaîne lancée, même si la liste « Récents » a été réordonnée.
- **Télécommande** : la touche Menu ouvre les options. L'appui long système est aussi reconnu.

## 4. Matrice de tests sur la box

| Page / fonction | Cas | Résultat |
|---|---|---|
| Accueil | Rangées Reprendre / Récentes / Films / Séries, ouverture d'une tuile | OK |
| Direct | Groupes, mémoire du dernier groupe, lecture, retour avec focus | OK (focus corrigé) |
| Direct | Verrouiller un groupe → code ; mauvais code ×6 → blocage 1 min ; bon code → accès | OK |
| Direct | Mise en arrière-plan → re-verrouillage, retour sur « Tout » | OK |
| Guide | Grille EPG, ligne « maintenant », choix du groupe, groupe verrouillé → code | OK |
| Guide | Programme passé → replay (TF1, 2 jours) | OK |
| Guide | Programme futur → rappel (alarme vérifiée avec `dumpsys alarm`, conservé après redémarrage) | OK |
| Lecteur | Direct, film avec reprise (06:43), épisode, bouton « épisode suivant » | OK |
| Lecteur | Chaîne refusée (403) : 2 essais espacés puis message clair, pas de rafale | OK |
| Lecteur | Chaîne d'un groupe verrouillé ouverte depuis l'Accueil → code demandé | OK |
| Films | Grille, catégories, fiche TMDB (casting, note, durée), bande-annonce (YouTube), retour avec focus | OK |
| Séries | Grille, fiche, saison, épisodes, « Reprendre S01E01 » | OK |
| Recherche | `%%` → aucun résultat ; « congo » → chaînes du groupe verrouillé absentes | OK |
| Enregistrements | Enregistrement immédiat, avertissement 1 connexion, conflit refusé, lecture pendant l'enregistrement, arrêt manuel, suppression (fichier effacé) | OK |
| Multi-écran | Ajout d'une chaîne, avertissement 1 connexion | OK (2ᵉ flux non testé pour ne pas faire bloquer l'abonnement) |
| Réglages | Création, changement et suppression du PIN ; clé TMDB ; export sans mot de passe (fichier vérifié : aucun secret) ; restauration (doublon ignoré) | OK |
| Sécurité | Intent externe avec identifiant piégé ou inexistant → ignoré, pas de plantage | OK |
| Sécurité | `adb logcat -b all` (≈15 000 lignes) : aucun identifiant ni URL Xtream | OK |
| Téléphone | Simulation 1080×2400 sur la box | Partiel : la box reste en mode TV (voir limites) |

## 5. Limites connues et points pour la phase design

- Film 1507785 : flux cassé chez le fournisseur.
- « FR| TF1 +1 FHD » diffuse Cartoon Network chez le fournisseur (identique en direct et en enregistrement).
- Certaines chaînes renvoient 403 (« AF | CONGO ») : refus du fournisseur, message clair affiché.
- Android 15 limite les services de premier plan « dataSync » à 6 h par jour : les enregistrements très longs peuvent être interrompus (statut correct).
- Google TV n'a pas de sélecteur de fichiers : sauvegardes dans le dossier de l'app ou par URL.
- Release signée avec la clé debug tant que `keystore.properties` n'existe pas.
- Mise en page téléphone : à vérifier sur un vrai téléphone. Sur écran étroit, la barre d'outils du Guide et la colonne des chaînes ne s'adaptent pas.
- Design :
  - Libellé « Bande-annonce » coupé sur la fiche film.
  - Pas d'image pour un épisode dans « Reprendre la lecture » (utiliser l'affiche de la série).
  - Le nom de fichier d'un enregistrement répète la chaîne quand le titre est identique (« FR TF1 1 FHD_FR TF1 1 FHD »).
- « Reprendre la lecture » peut afficher un film d'une catégorie verrouillée (le lecteur demande quand même le code).

## 6. Phase 4 — Regroupement des doublons films/séries

Une seule fiche par œuvre ; les doublons (VF, MULTI, VOSTFR, 4K, autres pays, plusieurs catégories) deviennent des **versions**. Aucune ligne du fournisseur n'est modifiée ni supprimée : seule une clé calculée (`groupKey`) est ajoutée.

**Règles** (`VodGrouping`, version 3) :
- même identifiant TMDB, sinon même titre normalisé **et** même année ;
- jamais de regroupement sans année ni TMDB ;
- titres génériques ou trop courts : TMDB uniquement ;
- homonymes TMDB distincts jamais fusionnés ;
- codes pays collés au titre ignorés (« HU Chernobyl ») : liste fermée, en majuscules seulement.

**Lecture** :
- ordre de lancement : choix manuel → langue (VF › MULTI › VOSTFR › VO, réglable) → meilleure qualité décodable → ordre du fournisseur ;
- repli automatique vers la version suivante si une source échoue (pause de 4 s, une tentative par version) ;
- reprise partagée entre versions, progression stockée par version ;
- menu « Version » dans le lecteur.

**Base** :
- migration v1 → v2 explicite, sans migration destructive ;
- tables `tmdb_links` (identifiant TMDB trouvé à l'ouverture d'une fiche, année identique exigée) et `vod_choice` ;
- les clés sont recalculées au lancement quand les règles changent.

| Test | Résultat |
|---|---|
| 83 tests automatisés (+21 : `GroupingTest`, invariants « rien ne se perd » dans `DataTest`, `MigrationTest` 1 → 2) | OK |
| Lint release | 0 erreur |
| Mise à jour par-dessus l'install existante (migration réelle, 159 256 films / 64 311 séries) | OK, pas de plantage |
| Statistiques : films 159 256 → ~156 200 œuvres, séries 64 311 → ~41 100 œuvres ; les plus gros groupes vérifiés à la main | OK |
| Films › Récemment ajoutés : 300 → 289 cartes ; Séries : 300 → 221 | OK |
| Fiche Nightborn (4 versions) : « Lecture · VF », liste VF / VOSTFR / AR / EN avec catégorie et nom brut | OK |
| Lecteur : VF lancée, bascule VOSTFR depuis le menu à la même position | OK |
| Reprise partagée : vue en VOSTFR, la fiche propose « Reprendre à 01:19 · VF » ; une seule carte dans « Reprendre la lecture » | OK |
| Série Chernobyl 2019 : 16 versions réunies (VF, EN · 4K, GE, IN, HU…), épisodes de la VF | OK |
| Interrupteur désactivé : affichage d'origine (300 cartes) ; réactivé : regroupé | OK |

**Limites** :
- Le repli automatique n'a pas pu être testé sur la box : aucun film cassé connu n'a plusieurs versions (le film 1507785 n'en a qu'une). La logique est relue, mais pas couverte par un test automatisé.
- Le fournisseur ne transmet pas d'identifiant TMDB (0 %) : les films sans année restent séparés, par prudence. Le rattachement TMDB à l'ouverture d'une fiche exige aussi une année concordante, pour éviter un homonyme.
- Le nombre « N versions » d'une carte compte aussi les versions verrouillées ; la fiche, elle, ne les liste pas sans le code.
- La 1ʳᵉ ouverture d'une série en plusieurs versions charge les épisodes de 5 versions au plus (appels API, pas de flux).

## 7. Phase 5 — Expérience streaming (design, données TMDB, adaptatif)

**Navigation éditorialisée** (Accueil, Films, Séries) : une carte par œuvre ; les catégories brutes du fournisseur restent dans « Toutes les catégories » (icône liste) et dans la Recherche.
- Héros qui défile toutes les 8 s : fond, logo TMDB, « N° 1 des films cette semaine », note, durée, classification (« -12 »), résumé en français. Effets : zoom lent, fondu.
- Rangées avec phrase d'accroche :
  - Reprendre la lecture, Ma liste ;
  - Top 10 (chiffres détourés), tuiles plateformes (Netflix, Disney+, Prime Video, Max…), « À voir sur Netflix »… ;
  - Tout juste arrivés, Le meilleur de 2026, Spécial Halloween / Noël selon la date, thèmes (frissons, super-héros, animés…) ;
  - « Parce que vous avez regardé… », Sagas, Les mieux notés.
  - Chaque rangée a « Tout voir » : grille avec bandeau (fond de saga, couleurs de la plateforme).
- TV : rail latéral replié qui s'ouvre au focus ; panneau d'aperçu de la carte sélectionnée et fond d'ambiance.

**Fiches** :
- fond plein cadre animé, logo, accroche, méta, boutons qui passent à la ligne ;
- résumé dans l'ordre TMDB français → fournisseur s'il est en français → anglais avec la mention « Résumé disponible en anglais uniquement » ;
- versions en pastilles ;
- saisons en onglets ; épisodes avec vignette, titre et résumé TMDB en français (repli : image du fournisseur, puis fond de la série) ;
- distribution, saga, « Vous aimerez aussi ».

**Données** :
- base v3 : tables `tmdb_meta` (fiche TMDB en français) et `tmdb_match` (correspondance œuvre ↔ TMDB, résultats négatifs retentés après 30 jours). La migration 2 → 3 est explicite et testée.
- Pas de table d'épisodes : les saisons TMDB passent par le cache JSON `tmdb_cache` (7 jours).
- Enrichissement en file : 4 requêtes TMDB en parallèle au plus, jamais vers le serveur IPTV.
- Parseur : vignettes d'épisode lues dans plus de champs (`movie_image`, `cover_big`, `still_path`, `thumbnail`…).
- Titres affichés nettoyés quand TMDB ne fournit pas de titre : « The Musical - 2026 [ -SUB] » devient « The Musical ».

**Adaptatif** (jetons `LocalLayout` : téléphone < 600 dp, tablette, grand écran, TV) :
- téléphone : barre du bas (Accueil, Films, Séries, Direct, Plus), fiches en colonne avec bouton Lecture pleine largeur ;
- tablette : rail latéral ;
- Direct : panneau de détails seulement si l'écran est assez large ;
- Guide : barre d'outils qui passe à la ligne, en-tête compact ;
- lecteur : liste des chaînes à 90 % de la largeur (420 dp au plus), barre d'info compacte, carte « Épisode suivant » avec vignette ;
- dialogues : 92 % de la largeur ;
- Recherche : résultats en rangées de cartes enrichies ; les tendances s'affichent quand le champ est vide.

| Test | Résultat |
|---|---|
| 95 tests automatisés (app + parseur), dont `BrowseTest` : plateformes et thèmes sur des noms réels, langue des catégories, détection du français, ordre des résumés, nettoyage des titres, fusion des épisodes, clés TMDB, une carte par titre, routes de grille ; `MigrationTest` 2 → 3 | OK |
| Lint release | 0 erreur |
| Box (TV 1080p) : Accueil, Films, Séries, Top 10, plateformes, rangées thématiques | OK |
| Fiche film Bad Apples : logo, résumé FR, 6 versions, distribution, « Vous aimerez aussi » | OK |
| Série Tchernobyl : titres, résumés et vignettes des épisodes en français | OK |
| Téléphone simulé (1080×2340, densité 440, mode tactile forcé) : Accueil, Films, fiche série, Direct | OK |
| Tablette simulée (1600×2560, densité 320) : rail + Accueil | OK |
| Écran de la box rétabli (1920×1080 / 320) | OK |

**Bugs trouvés pendant les tests et corrigés** :
- Plantage au démarrage : le moteur d'expressions régulières d'Android (ICU) refuse une accolade fermante non échappée, alors que le JVM des tests l'accepte. Corrigé ; à retenir pour toute nouvelle expression régulière.
- Une rangée cachait un remake (Dune 1984 / Dune 2021) comme doublon : le dédoublonnage tient maintenant compte de l'année.
- Barre du haut (téléphone) transparente au défilement : elle devient opaque.

**Test du mode tactile sur la box** : `adb shell setprop debug.sokoul.touch 1`, puis `wm size` / `wm density`. À remettre à 0 ensuite.

**Limites** :
- Le cadrage des écrans téléphone et tablette est simulé sur la box ; à confirmer sur un vrai téléphone.
- Le héros peut indiquer « 1 saison » (catalogue du fournisseur) alors que la fiche TMDB en compte davantage.
- Les œuvres jamais vues par TMDB gardent l'affiche du fournisseur et, souvent, un résumé en anglais tant que la file d'enrichissement ne les a pas traitées.
## 8. Phase 6 — Parcours Films & Séries « zéro friction » (navigation → fiche → lecteur)

Objectif : chaque étape du parcours vérifiée à la télécommande et au tactile, sans erreur ni requête de trop vers le fournisseur (1 connexion maximum).

**Sécurité fournisseur et lecteur** :
- `ProviderGate` (unique dans l'app) : après un refus 401/403/406/429, aucune nouvelle tentative pendant 30 s, ni automatique ni vers une autre version. « Réessayer » affiche un compte à rebours et reste bloqué jusqu'à la fin. Les erreurs réseau gardent leur nouvel essai progressif.
- Un seul flux à la fois : un nouveau lecteur libère l'ancien tout de suite. Anti-double-appui sur toute la navigation (`navigateOnce`), donc un double appui n'ouvre jamais deux lecteurs.
- Accueil (Home) pendant un film : passage en image dans l'image ; sinon pause et sauvegarde de la position.
- Le curseur tactile ne demande la nouvelle position qu'au relâchement, donc plus de rafales de requêtes.
- « Épisode suivant », changement de version et démarrage protégés : une erreur s'affiche au lieu de faire planter l'app.

**Reprise juste** (base Room v4, migration 3 → 4 testée : saison, épisode, « vu à la main ») :
- la reprise se fait par saison et épisode, toutes versions confondues ;
- « Reprendre S1 · É3 — 18 min restantes » avec barre de progression ;
- un épisode marqué « vu » à la main ne détourne plus le bouton principal ;
- une série dont le dernier épisode est terminé reste dans « Reprendre la lecture » avec « Épisode suivant · S1 · É4 » ;
- même seuil partout (30 s) : aucune carte « Reprendre » ne repart de 0.

**Lecteur** :
- barre navigable au D-pad : progression (◀ −10 s / ▶ +30 s, aperçu du temps), puis ▼ vers Pause, Audio et sous-titres, Version, Épisodes, Suivant, Format. Tout marche sans touche Menu ;
- heure de fin estimée (« Fin à 23:42 ») ;
- indicateur de chargement en VOD ;
- panneau Épisodes intégré ;
- carte « Épisode suivant » 20 s avant la fin, avec compte à rebours, Lire maintenant ou Annuler (lecture auto désactivable) ;
- choix audio et sous-titres mémorisés par langue.

**Fiches** :
- états chargement, introuvable (avec Retour) et TMDB indisponible (la fiche reste utilisable) ;
- bouton principal intelligent ; saison de départ = celle à reprendre ;
- focus restauré au retour du lecteur ;
- choisir une version ne la rend plus permanente (appui long pour « Toujours cette version ») ;
- bande-annonce corrigée.

**Navigation** :
- Accueil, Films et Séries progressifs : les rangées locales arrivent en moins de 1,5 s, TMDB ensuite (12 s au plus, puis abandon propre). Les requêtes TMDB sont maintenant annulables et les calculs se font hors du fil principal ;
- plus de flash au rechargement ;
- écran d'erreur avec « Réessayer » ;
- retour sur la carte d'origine (accueil, grille, fiche) ;
- fond et aperçu temporisés pendant le défilement ;
- héros en pause hors écran ;
- grille « Tout voir » : tri (Sélection, A → Z, Plus récents, Mieux notés), appui long, enrichissement au défilement, « Les N premiers titres ».

| Test | Résultat |
|---|---|
| 131 tests automatisés (113 app + 18 parseur), dont `ParcoursLogicTest` (20 : porte fournisseur, reprise, épisode suivant, seuils, tris), `ParcoursDataTest` (6, base Room réelle : rangée Reprendre, série terminée, TMDB très lent → page locale < 3 s et finale < 6 s, TMDB en panne), `ParcoursUiTest` (9, interface Compose : focus Réessayer, compte à rebours bloquant, barre D-pad, carte épisode suivant focalisée et lisible sur TV, double appui, pas de lecteurs empilés, focus retrouvé, carte remplacée → focus dans la même rangée), `MigrationTest` 3 → 4 | OK |
| Lint release | 0 erreur |
| Box : accueil, migration v4 sur données réelles | OK |
| Box : retour d'une fiche → focus sur la carte d'origine (Top 10, Reprendre) | OK |
| Box : fiche série « Reprendre S1 · É1 » + temps restant ; lecture reprise à la bonne position | OK |
| Box : barre lecteur (▶ +30 s, ▼ Pause, libellés au focus, « Fin à… ») | OK |
| Box : pause → Retour → OK reprend la lecture | OK (bug corrigé, voir plus bas) |
| Box : sortie du lecteur → carte Reprendre mise à jour, flux coupé | OK |
| Box : double OK → un seul lecteur | OK |
| Box : enchaînement automatique S1 · É1 → S1 · É2 | OK |
| Box : carte « Épisode suivant » 20 s avant la fin (compte à rebours, « Lire maintenant » focalisé, « Annuler » sur une ligne) ; Annuler → fin de l'épisode → retour accueil, focus sur la carte « Épisode suivant · S1 · É4 » | OK après correctif (focus) |
| Box : journal système sans plantage ni refus 4xx sur toute la session | OK |

Tests réels faits avec l'abonnement : un flux à la fois, au moins 30 s entre deux lectures, aucun refus du fournisseur observé.

**Bugs trouvés pendant les tests et corrigés** :
- Télécommande : dans le lecteur, Retour ne faisait qu'effacer le focus (comportement de Compose), donc ensuite OK ne relançait plus la lecture. Retour est maintenant intercepté avant Compose et ferme dans l'ordre : panneau, liste, aperçu, puis la barre. Le focus est toujours rendu au lecteur.
- ▼ depuis la barre de progression tombait sur un bouton au milieu (trouvé par un test d'interface) : il mène toujours à Lecture/Pause.
- Carte « Épisode suivant » : « Annuler » s'affichait lettre par lettre en colonne sur TV, à cause de l'ordre des modificateurs de largeur. Corrigé et couvert par un test qui mesure le vrai texte.
- Retour d'une fiche ouverte depuis le héros : le focus partait sur le menu latéral. Il revient sur « Lecture ».
- « Reprise à … » réapparaissait à chaque fois que la barre se cachait : il ne s'affiche plus qu'une fois.
- Note « 0,5 » affichée pour des titres sans vraie note : masquée sous 1.
- Titre de secours centré au lieu d'aligné à gauche quand il n'y a pas de logo.
- Requêtes TMDB impossibles à annuler, qui bloquaient la page jusqu'à 60 s.
- Fin d'épisode sans enchaînement : retour à l'accueil avec le focus sur le menu latéral. Deux causes : `requestFocus()` ne signale rien quand la carte a disparu (la mémoire du focus croyait avoir réussi), et Compose envoie le focus au menu quand la carte focalisée est remplacée. Corrigé : vrai résultat lu (`requestFocus(Enter)`), repli sur la même rangée puis sur « Lecture », et un gardien qui ramène le focus dans les rangées s'il part sans appui de touche. Test ajouté.

**Passe design (avant/après, critique sur captures de la box)** :
- Focus plus lisible à 3 m : sur TV, seul le bouton focalisé est blanc, les autres restent translucides (héros et lecteur). Avant, « Lecture » paraissait toujours sélectionné.
- Ligne de reprise compacte (barre de 140 dp + texte) au lieu d'une barre de 420 dp qui écrasait la fiche.

**Limites** :
- Le lecteur (ExoPlayer) n'est pas testé de bout en bout avec un faux serveur : la politique d'erreur est testée à travers `ErrorPolicy` et `ProviderGate`.
- L'atomicité de `ensureEpisodes` (suppression + insertion en transaction) n'a pas de test dédié.
- Dans « Reprendre la lecture », l'épisode suivant n'est cherché que dans la même version de la série.
- Pas de tests instrumentés (`androidTest`) : les tests d'interface tournent sous Robolectric, sans flux réel, et les vérifications sur la box ont été faites à la main.

## 9. Phase 7 — Fiche « style Apple TV », lecteur « icônes + texte », fluidité

Objectif : refaire la fiche détail et le lecteur (emplacement des boutons, design, animations), corriger ce qui restait, et une interface fluide sur la box (Google TV Streamer, 1080p).

**Fiche détail** (`DetailKit.kt`, `DetailScreens.kt`) :
- grand fond plein écran avec effet Ken Burns lent, logo du titre, ligne d'infos, résumé et boutons ronds ;
- le contenu monte par-dessus le fond quand on descend ;
- onglets de saison : ▼ arrive sur la saison sélectionnée et ne change plus de saison par erreur ;
- vignette de secours pour les épisodes sans image (fond de la série et grand numéro) ;
- Retour depuis le bas de la fiche remonte en haut et remet le focus sur « Lecture ».

**Lecteur** (`VodOverlay.kt`, `PlayerScreen.kt`, nouveau `TracksPanel.kt`) :
- barre du haut : titre, épisode, version et heure ;
- en bas : barre de progression, −10 / ⏯ / +30, puis les boutons Épisodes · Suivant · Audio et sous-titres · Plus (icône + texte, toujours dans le même ordre) ;
- panneau « Audio et sous-titres » à deux colonnes, qui s'ouvre sur la piste en cours ;
- noms de pistes lisibles : « Anglais · SDH », « Français · 5.1 · Dolby Digital+ », « Chinois · traditionnel ». Jamais de nom d'encodeur (« GalaxyTV - small excellence! ») ni de format interne (« X-MEDIA3-CUES »). Les doublons sont numérotés ;
- panneau Épisodes avec les titres et vignettes TMDB, comme la fiche. Les noms de release (« 720p.NF.WEBRip.x264-GalaxyTV ») ne sont plus affichés comme titre ;
- écran « En pause » après 10 s (titre, résumé TMDB en français, temps restant). OK reprend la lecture ; Retour ferme l'écran de pause, un second Retour quitte le lecteur ;
- retour visuel cumulé des sauts (« +1 min 30 », heure visée), indicateur de chargement après 300 ms seulement, carte « Épisode suivant » avec bouton qui se remplit pendant le compte à rebours ;
- téléphone : boutons sur deux lignes centrées (plus de rangée coupée), barre de progression fine avec un repère rond.

**Fluidité** (mesurée avec `dumpsys gfxinfo` sur la box, même parcours à la télécommande avant et après) :

| Écran | Avant | Après |
|---|---|---|
| Fiche détail, navigation | 32–34 % d'images saccadées | **1,9 %** (médiane 9 ms) |
| Fiche détail, immobile | 12 % | **0 %** |
| Accueil, navigation dans les rangées | 12–16 % | **4,2 %** (90e centile 61 → 40 ms) |
| Accueil, immobile | — | **0,8 %** |

Causes trouvées et corrigées :
- fond et dégradés plein écran redessinés à chaque image de l'effet Ken Burns : ils sont maintenant rendus une seule fois dans une texture (calque matériel), et l'animation ne fait que l'agrandir. Le flou a été retiré ;
- héros de l'accueil toujours dessiné sous le panneau d'aperçu opaque : il n'est plus dessiné quand le panneau le cache ;
- quatre fonds opaques identiques empilés (fenêtre, racine, coque, écran) : seul celui de la fenêtre est gardé ;
- fond d'ambiance de l'accueil en fondu enchaîné entre deux images plein écran : il coûtait à lui seul environ 5 points. Il passe à une seule image ;
- chaque déplacement du focus recomposait tout l'écran d'accueil : la lecture du focus est isolée dans de petits composables.

| Test | Résultat |
|---|---|
| 153 tests automatisés (135 app + 18 parseur), dont `DetailUiTest` (fiche : focus, saisons, vignettes de secours) et `PlayerUiTest` (ordre des boutons, parcours D-pad barre → ⏯ → +30 → boutons → ▲, tout sur une ligne en 960 dp, panneau audio/sous-titres, carte suivante, noms de pistes, sauts bornés) | OK |
| Lint release | 0 erreur |
| Box, vrais flux (un seul à la fois, ≥ 30 s d'écart) : barre du lecteur, panneau Épisodes (vignettes TMDB), panneau Audio et sous-titres, écran de pause, Retour | OK |
| Box en mode téléphone (1080 × 2340) : accueil, lecteur | OK après correctifs |
| Journal système pendant les tests : aucune erreur de l'app ni du lecteur | OK |

**Bugs trouvés sur la box et corrigés** :
- Panneau Épisodes du lecteur : nom de release affiché comme titre et vignettes vides.
- Pistes audio et sous-titres affichées avec le nom de l'encodeur et « X-MEDIA3-CUES ».
- En pause, Retour sur l'écran de pause réaffichait la barre. Avec la réapparition de l'écran de pause toutes les 10 s, on pouvait tourner en rond.
- Téléphone : « Audio et sous-titres » coupé et « Plus » invisible ; logo affiché par-dessus une affiche qui porte déjà le titre (« HOPE » en double).

**Limites** :
- Quand TMDB n'a pas de titre français pour un épisode, il s'appelle « Épisode N ». Les titres TVDB arriveront avec la phase 8.
- Le résumé affiché sur l'écran de pause vient du fournisseur (souvent en anglais) quand TMDB n'en a pas en français.
- Un film (version AR, puis VF en secours) a renvoyé « Format de flux non reconnu » pendant une séquence de touches involontaire. Le fournisseur semble avoir renvoyé autre chose qu'une vidéo. Ce cas n'a pas été reproduit volontairement, pour respecter la limite d'une connexion.
