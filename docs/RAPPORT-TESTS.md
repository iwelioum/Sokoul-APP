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

## 10. Phase 8 — Sources de données externes (25/10/2026)

**Principe : rien ne casse.**
- TMDB reste la source principale. Chaque nouveau service est un complément facultatif :
  - sans clé, il ne fait aucun appel ;
  - en cas d'erreur, il ne renvoie rien et la fiche garde son affichage habituel.
- Pas de nouvelle table ni de migration : le cache réutilise la table `tmdb_cache`, avec un préfixe par service et **sans la clé** dans la clé de cache.
- Disjoncteur par service :
  - 401/403 coupent le service 24 h, et 429 le coupent 1 h, sans rafale de requêtes ;
  - 2 requêtes au plus en même temps par service.
- Les clés sont dans `local.properties` (ignoré par git) et peuvent être remplacées dans Réglages. Elles ne sont jamais écrites dans le dépôt ni dans les journaux : `redact()` est étendu, et le logcat de la box a été vérifié (0 clé sur 2 109 lignes).

### État des services (vérifié sur la box, Réglages › Sources de données › Tester)
| Service | État | Ce que ça apporte dans l'app |
|---|---|---|
| TheTVDB v4 | ✅ OK (62 épisodes de Breaking Bad) | Titres, résumés et vignettes d'épisodes en français quand TMDB n'en a pas (ordre : TMDB → TVDB → fournisseur → fond de la série) |
| fanart.tv | ✅ OK (logo et fond) | Logo HD et fond de secours sur la fiche |
| TVmaze (sans clé) | ✅ OK | « Prochain épisode : S1 · É5 le mardi 27 octobre » (VisionQuest) |
| Movie of the Night | ✅ OK | « Aussi sur ● Prime Video » (Masterplan), « Aussi sur ● Disney+ » (Bleach, VisionQuest) |
| TasteDive | ✅ OK (5 titres similaires) | Complète « Vous aimerez aussi » quand TMDB donne moins de 6 titres disponibles |
| xmltv.fr (sans clé) | ✅ OK (guide de 8 Mo) | Guide TV FR/BE/CH en complément, à activer dans Réglages |
| iptv-org (sans clé) | Prêt | Logos de chaînes manquants (icône du guide d'abord), après chaque synchronisation |
| OMDb | ✅ OK (Interstellar : IMDb 8,7 · RT 73 % · MC 74) | Badges « IMDb 6,2 » et « MC 46 » à côté de ★ (Street Fighter) ; rien quand OMDb n'a pas encore de note (Masterplan) |
| OpenSubtitles | ✅ OK (10 résultats FR, 1 780 répliques téléchargées ; 8 pour Breaking Bad S1E1) | Sous-titres en ligne dans le panneau Audio et sous-titres |
| Subdl | ✅ OK (10 résultats, 2 149 répliques) | Complète OpenSubtitles ; vu sur la box : « Français · Subdl · Street Fighter… » choisi, répliques affichées sur la vidéo |
| Watchmode | Non utilisé | L'offre gratuite n'inclut pas la France |
| ThePosterDB, Mediux, Addic7ed | Abandonnés | Pas d'API publique (404, jeton de compte privé, ou scraping fragile) |

### Pour activer les 3 services restants (inscription gratuite, à faire vous-même)
1. **OMDb** : https://www.omdbapi.com/apikey.aspx → choisir « FREE » (1 000 appels par jour) → valider le lien reçu par e-mail.
2. **OpenSubtitles** : créer un compte sur https://www.opensubtitles.com, puis https://www.opensubtitles.com/consumers → « New consumer » → copier l'« API key ».
3. **Subdl** : créer un compte sur https://subdl.com, puis https://subdl.com/panel/api → copier la clé.

Ensuite, deux façons de donner la clé :
- dans l'app : Réglages › Sources de données › le service › « Saisir une clé… », puis « Tester » ;
- dans `app-android/local.properties` : `sokoul.key.omdb=…`, `sokoul.key.opensubtitles=…`, `sokoul.key.subdl=…`, puis reconstruire l'APK.

### Sous-titres en ligne (lecteur)
- La recherche part à l'ouverture du panneau Audio et sous-titres, une fois par œuvre ou épisode.
- Le fichier est téléchargé **seulement quand on le choisit**, pour respecter les quotas.
- Le texte s'affiche par-dessus la vidéo, sans relancer le flux. C'est important avec la limite d'une connexion du fournisseur.
- Choisir une piste du flux ou « Désactivés » retire le sous-titre en ligne. L'épisode suivant repart de zéro.

### Tests
- `ProvidersTest` (16 tests) :
  - formats réels des services ;
  - cache sans clé ;
  - disjoncteur 401 et 429 ;
  - 404 mémorisé ;
  - aucun appel sans clé ;
  - masquage des clés dans les journaux.
- `ExtrasTest` (6 tests) :
  - une source en panne ne bloque pas les autres ;
  - prochain épisode uniquement pour une série en cours ;
  - ordre des replis d'épisodes ;
  - libellés (notes, services, dates) ;
  - le bouton « Tester » ignore le cache (une clé refusée n'est pas masquée) et ne fait aucun appel sans clé.
- `ApiLiveCheck` : vrais appels avec les clés lues dans l'environnement (`SOKOUL_LIVE_API=1`). Ce test est ignoré sinon.
- Suite complète : 158 tests app et 18 tests parser verts, `lintRelease` OK.

### Corrections après les essais sur la box (clés OMDb, OpenSubtitles, Subdl)
- **Subdl** renvoie un lien de téléchargement qui contient la clé (`?api_key=…`). L'app ne garde plus que le chemin public du fichier, et le téléchargement marche toujours.
- **Libellé des sous-titres en ligne** : « Français · Subdl · Interstellar 2014 1080p BluRay… » au lieu de « Sous-titres · Subdl ». La langue est lue depuis le code (« FR ») ou le nom anglais (« French »), et le nom de version permet de distinguer les résultats.
- **Lecteur** : quand la barre réapparaît, le focus est replacé sur le curseur dès qu'il est prêt (nouvel essai à chaque image, 600 ms au plus). Avant, le focus restait sur l'écran et le premier ▼ ne servait à rien.
- Suite : 159 tests app (vérification réelle incluse) et 18 tests parser verts, `lintRelease` OK.

## 11. Phase 9 — Cohérence visuelle et navigation par « univers » (25/10/2026)

Branche `phase9-design`, 7 étapes, un commit local chacune (`e331767` → `e8cd11c`), plus `a4d79c7` (lint) et `6c12563` (fluidité). Rien n'est poussé sur GitHub.

### Ce qui change pour l'utilisateur
- **Deux univers, un seul langage visuel.**
  - **Streaming** (Accueil, Films, Séries, Ma liste, Sagas) : onglets en haut qui s'effacent quand on descend dans le contenu.
  - **TV** : plein écran, sans barre, avec des surimpressions sur la vidéo (bandeau de zapping, liste des chaînes, groupes, mini-guide).
- **Système de design unique** :
  - jetons `Radius`, `Space`, `Type` et couleurs ;
  - 3 styles de focus seulement (carte, ligne, pastille) ;
  - une seule affiche, une seule pastille, un seul en-tête, un seul logo ;
  - un test refuse les couleurs et les arrondis écrits en dur dans les écrans.
- **Recherche contextuelle** : le périmètre dépend de la page d'origine (Films → films, TV → chaînes et programmes…) et se modifie avec des pastilles. Clavier TV avec 3 suggestions par préfixe.
- **Explorer, ambiances et micro-genres** :
  - filtres Genre, Décennie, Note, Durée, Langue et Qualité ;
  - 6 ambiances (Frissons, Esprit léger, En famille…) et des micro-genres (« Pépites méconnues », « Grand spectacle en 4K »…) ;
  - le catalogue est analysé en arrière-plan, et l'analyse se met en pause pendant la lecture.
- **Sagas** :
  - hub de toutes les collections ;
  - page « musée » : frise, ordre de sortie ou du récit, visages de la saga, coulisses, séries du même univers ;
  - les films absents sont marqués « HORS CATALOGUE » et peuvent quand même être ajoutés à Ma liste.
- **Préparation de Real-Debrid** : une source de lecture est décrite par des valeurs typées (origine, qualité, HDR, langue, état), pas par du texte. Les badges `[VF] [1080p] [4K]` sont calculés par l'interface.

### Recommandations de l'audit (« Analyse UX du Plan TV.pdf »)
| Recommandation | Décision | Raison / ce qui a été fait |
|---|---|---|
| Carte focalisée au premier plan (`zIndex`) | ✔ Gardée | La voisine ne recouvre plus la carte agrandie |
| Double anneau de focus (filet sombre + contour dégradé) | ✔ Gardée | Focus visible même sur les affiches à bord blanc ; dessiné sans recomposition |
| Zones de sécurité TV (48 dp, haut 27 dp, résumés ≤ 600 dp) | ✔ Gardée | Fonds toujours bord à bord |
| Faux verre (image réduite et agrandie) au lieu d'un vrai flou | ✔ Gardée | `GlassPanel` : aperçu, liste des chaînes, mini-guide, pistes |
| Onglets qui changent de page au focus | ✔ Gardée | Après 320 ms d'arrêt ; 🔍, TV et ⚙ demandent OK : jamais de bascule en TV par accident |
| `focusRestorer` partout | ✖ Écartée | Ferait double emploi avec `focusMemory`, déjà validé sur la box |
| Ligne de repère du défilement (~35 %) | ✔ Gardée | Sur le `BringIntoViewSpec` existant |
| Lueur d'ambiance (Palette, bornes HSL, cache) | ✔ Gardée et précisée | Calculée sur une miniature de 64 px ; fond « cuit » en basse définition (voir Fluidité) |
| Jauge de 2 dp pendant le délai de zapping | ✔ Gardée | 600 ms, puis le flux démarre |
| ◀ = chaîne précédente, appui long sur Retour = tout fermer | ✔ Gardées | — |
| Réglage « Sens du zapping » | ✔ Gardée | ✚ Ajouts : pavé numérique et réglage « Au démarrage » |
| Suggestions de complétion | ✔ Adaptée | Préfixe sur les noms du catalogue, pas sur les titres TMDB (seule une partie du catalogue est enrichie) |
| Touches Lecture/Pause = espace, Retour rapide = effacer | ✔ Gardée | — |
| Indexation par lots, pause pendant la lecture | ✔ Gardée | ✚ Elle cède aussi la base pendant le chargement d'un écran |
| Mots-clés TMDB par identifiant | ✔ Adaptée | Stockés par **nom** : `Moods.kt` reste lisible et vérifiable |
| Titres de rangées en Majuscules À Chaque Mot | ✔ Adaptée | Pastilles courtes gardées ; titres en français naturel (« Frissons garantis ») |
| Durées « 90-120 min » | ✔ Adaptée | « Moins de 1 h 30 », « 1 h 30 à 2 h », « Plus de 2 h » |
| `curated_sagas.json` (ordre du récit, séries liées) | ✔ Gardée | Chaque identifiant vérifié par un vrai appel TMDB ; `CuratedSagasTest` |
| Badge « HORS CATALOGUE IPTV » | ✔ Adaptée | « HORS CATALOGUE » : avec Real-Debrid, le catalogue ne sera plus seulement l'IPTV |
| `PlaybackSource` avec libellés | ✔ Gardée et typée | Énumérations ; libellé calculé par l'interface |
| Vrai flou, parallaxe forte, bandes-annonces au focus | ✖ Écartées | Puce graphique de la box trop faible, limite d'une connexion du fournisseur, YouTube |

### Fluidité mesurée sur la box (`dumpsys gfxinfo`, part d'images saccadées)
| Écran | Avant | Après | Ce qui a changé |
|---|---|---|---|
| Accueil (à chaud) | 17,6 % / 7,9 % | **6,3 % / 3,3 %** (médiane ~30 ms) | Le fond d'ambiance plein écran est peint une fois dans une image de 320 px, puis étiré |
| Films (à chaud) | 5,6 % / 10,1 % (1er passage) | **3,8 % / 4,4 %** | — |
| Hub des Sagas | 18–19 % | **6,1 % / 6,4 %** (médiane 21 ms) | Fond en double retiré, dégradé limité au bas des tuiles, tuile sans fond opaque, requête des ambiances corrigée |
| Explorer pendant l'indexation (31 %) | — | **5,8 % / 7,8 %** (médiane 15 ms) | L'indexation cède la base aux écrans |
| Ouverture d'une page saga | 5 à 10 s | **0,16 à 0,23 s** | Lecture de la base non bloquée par l'indexation, films affichés avant les crédits |
| Requête des ambiances | ~1 s × 12 par page (et > 20 s dans le pire cas) | indexée | Jointure directe au lieu d'une sous-requête relancée par ligne ; filtre 4K séparé en deux recherches indexées ; 2 requêtes au plus en même temps |

L'objectif « sous 5 % » est atteint sur Films. Accueil, Sagas et Explorer en restent proches (3 à 8 %, contre 4,2 % en phase 7 pour l'Accueil, qui n'avait pas encore de fond d'ambiance).

**Zapping TV : non mesuré.** Le fournisseur a refusé le flux (401, connexion sans doute ouverte ailleurs), puis l'a coupé au second essai, plus de 30 s après. Les règles d'utilisation de l'abonnement interdisent d'insister. La logique du zapping (un seul flux après 5 appuis rapides, ouvert 600 ms après le dernier) est couverte par les tests automatiques.

### Correctif vu pendant ces essais
- Sur l'écran « Connexion en pause » ou « Lecture impossible », **Retour retirait seulement le focus du bouton**, et l'essai automatique repartait. Retour quitte maintenant le lecteur directement. Vérifié sur la box : un seul Retour ramène aux Sagas.

### Tests
- Nouveaux fichiers de tests :
  - `DesignTokensTest` : aucune couleur ni arrondi écrit en dur hors du thème ;
  - `ShellUiTest` : onglets effacés et restaurés, traversée de la barre sans charger de page, TV sur OK seulement, chaîne de Retour ;
  - `AmbientTintTest` : choix et bornes de la teinte ;
  - `TvZapTest` : cinq appuis rapides = un seul flux, ouvert après l'arrêt et après fermeture du précédent ; liste qui boucle ; Retour pendant le zapping ; sens du zapping ;
  - `SearchKeyboardTest` : saisie et touches de la télécommande ;
  - `ExploreTest` : identifiants de genres films et séries, ambiances, filtres de l'Explorer, indexation par lots de 45, pause pendant la lecture ;
  - `SagaTest` : frise, hors catalogue, ordre du récit, visages et coulisses, Ma liste par identifiant TMDB, format des sagas éditorialisées ;
  - `PlaybackSourceTest` : sources typées et libellés, avec un garde-fou (aucun libellé de source écrit en dur dans les écrans) ;
  - `ScreenLoadingTest` : l'indexation attend la fin des chargements d'écran.
- Tests enrichis :
  - `MigrationTest` (4→5, 5→6) ;
  - `DataTest` (recherche contextuelle, suggestions par préfixe) ;
  - `ParcoursDataTest` et `ParcoursUiTest`.
- Suite complète : **195 tests app verts**, 1 ignoré (`ApiLiveCheck`, la vérification réelle, ignorée sans clés). Tests du parser verts. `lintRelease` : **0 erreur**, 18 avertissements.

## 12. Phase 10 — Finitions premium (26/10/2026)

Branche `phase10-finitions` (créée depuis `phase9-design`). Six commits locaux, de `a7b88c9` à `4ab618e` ; les étapes 1 et 2 partagent le même commit. Rien n'est poussé sur GitHub.

### Retours de l'utilisateur et réponses
| Retour | Réponse |
|---|---|
| Titre, année et genre sous les affiches : inutiles | Plus aucune légende. Au focus, les infos montent **dans** l'affiche : logo ou titre, puis « ★ 7,8 · 2021 · 2 h 10 ». Sur téléphone, le titre seul reste sous l'affiche, faute de focus. |
| Il ne se passe rien au focus d'une carte | Agrandissement ×1,12 et léger soulèvement. Ombre, halo violet, contour blanc net, liseré d'accent sous la carte. Après 350 ms, l'image zoome lentement : la carte « respire ». |
| Police et centrage de la barre d'onglets | Police **Manrope**, embarquée sous licence OFL. Trois zones : logo à gauche, rubriques centrées dans l'espace libre, recherche, TV, réglages et heure à droite. L'onglet actif est souligné d'un trait rectangulaire. |
| Préférence pour les formes rectangulaires, avec plus de détail dans les contours | Coins de 4 dp (cartes) et de 6 dp (panneaux). Pastilles et boutons rectangulaires. Le rond est réservé aux visages et au bouton de lecture. Filet clair de 1 dp autour des cartes au repos. Un test refuse `CircleShape` et les polices écrites en dur. |
| Trop peu de catégories à l'accueil, des titres répétitifs, un accueil qui ne s'adapte pas | Voir « Accueil personnalisé » ci-dessous. |
| Sagas : page et sélection décevantes, sans effet | Voir « Sagas » ci-dessous. |
| Recherche collée à gauche | Plein écran **centré** : grand champ avec curseur, suggestions et périmètre en pastilles, clavier **AZERTY** compact avec touche « 123 ». ▼ replie le clavier et les résultats prennent l'écran ; ▲ jusqu'au champ le rouvre. Recherches récentes (8) quand le champ est vide. |
| On se perd dans la page Réglages | **Deux volets** : 8 rubriques à gauche, le contenu à droite avec un titre et une phrase d'explication. Réglages techniques regroupés sous « Avancé », replié par défaut. Une rubrique par fichier (`settings/sections/`). |

### Accueil personnalisé
- **Profil de goûts** (`Taste.kt`), calculé à partir de l'historique de lecture et de Ma liste, sans migration de la base.
  - Chaque œuvre compte selon la part regardée, avec une demi-vie d'un mois. Un abandon avant 15 % compte peu ; Ma liste ajoute 0,6.
  - On en tire des poids par genre (films et séries séparés), par décennie, par langue et par mot-clé, ainsi que les 3 œuvres de départ les plus marquantes.
- **Catalogue d'environ 80 rangées** (`RowCatalog.kt`) :
  - tous les genres ;
  - les époques (années 80, 90, 2000, 2010, classiques) ;
  - les cinémas étrangers (français, coréen, japonais, espagnol, indien) ;
  - une vingtaine de thèmes par mots-clés (casses, espace, survie, histoires vraies, zombies, espionnage…) ;
  - les ambiances et micro-genres, les mini-séries, les films courts, les « acclamés ».
- **Titres variés** : 3 à 5 tournures en français par rangée, choisies selon le jour. Le titre est stable dans la journée et change d'un jour à l'autre. Les rangées les plus proches des goûts reçoivent un titre personnel (« Vous avez un faible pour les thrillers »).
- **Rangées personnelles** :
  - « Parce que vous avez regardé… » ×3, avec trois formulations ;
  - « Finissez la saga X ».
- **Classement** : affinité avec le profil, plus une petite part d'aléa du jour. Deux rangées voisines n'ont jamais le même genre principal, et une œuvre apparaît au plus deux fois.
- **Chargement progressif** : les 8 premières rangées, puis des lots de 6 quand le focus approche de la fin de la page. Vérifié sur la box : de nouvelles rangées arrivent encore après plus de 50 rangées descendues.
- **Correctif** : la rangée séries « À voir en famille » acceptait des animés pour adultes (Chainsaw Man, Hazbin Hotel), parce que le genre « Animation » suffisait. Elle exige maintenant les genres Familial ou Enfants, et exclut Action & Aventure.

### Sagas
- **Hub** :
  - une bannière sur la saga focalisée : fond, logo ou nom, faits (« 26 films · 1962 → 2021 · ★ 6,6 de moyenne »), part déjà vue, résumé ;
  - des tuiles avec logo, « SAGA · 11 FILMS », un fond qui zoome au focus et 3 mini-affiches qui s'ouvrent en éventail ;
  - une barre sous la tuile qui montre la part de la saga déjà vue.
- **Page « musée »** :
  - entrée animée une seule fois : le fond apparaît en dézoomant, puis le logo, les faits et les chapitres arrivent l'un après l'autre ;
  - le fond suit le film focalisé ;
  - un panneau au-dessus de la frise décrit le film focalisé ;
  - une vraie frise : chapitres datés (« 04 1999 ») reliés par une ligne, point plein pour un film vu, anneau pour un film à voir, gris pour un film hors catalogue ;
  - le bouton **« Commencer la saga »** ou **« Continuer la saga »** ouvre le prochain film non vu, dans l'ordre choisi ;
  - les visages zooment au focus.
- **Logo de la saga** : celui du film qui porte exactement son nom (« Alien », « Matrix », « X-Men »). Sinon, le nom en toutes lettres : le logo d'un épisode tromperait sur le nom de la saga.
- **26 sagas éditorialisées**, au lieu de 8 : Univers Marvel (9 collections), X-Men, Mission : Impossible, James Bond 007, Pirates des Caraïbes, Matrix, Indiana Jones, Retour vers le futur, Terminator, Rocky & Creed, John Wick, Toy Story, Shrek, Avatar, La Planète des singes (2 collections), Mad Max, Die Hard et Jason Bourne. Les 28 identifiants de collection ont été vérifiés sur le site themoviedb.org (nom de la collection et liste des films), sans clé d'API.

### Tests
- **Unitaires : 215**, tous verts (1 ignoré, comme avant). Nouveaux tests :
  - `TasteTest` (9) : poids, décroissance, abandon, démarrage à froid, catalogue, titres du jour, classement, diversité ;
  - `SagaLogicTest` (4) : logo, faits, prochain film, nombre de sagas ;
  - `SearchKeyboardTest` (+3) : AZERTY, « 123 », recherches récentes ;
  - `SettingsUiTest` (4, Robolectric) : rubrique qui suit le focus, ▶ et OK entrent dans le volet, ◀ et Retour reviennent sur la rubrique.
- **Tests mis à jour** : `ShellUiTest` (ordre de la barre), `DesignTokensTest` (coins, polices) et `SagaTest` (de 20 à 40 sagas).
- **`lintRelease` : 0 erreur** (18 avertissements).

### Fluidité mesurée sur la box (`dumpsys gfxinfo`, part d'images saccadées)
| Écran | Mesure | Commentaire |
|---|---|---|
| Accueil, déplacements latéraux | **2,3 %** | — |
| Accueil, descente une fois les rangées chargées | **4,8 % / 5,0 %** (médiane 31 ms) | Les rangées s'affichent avec leurs nouveaux effets de focus |
| Accueil, première descente | 8,5 % / 9,2 % | Les rangées et leurs images arrivent pendant le défilement |
| Hub des Sagas | **2,8 %** | Contre 6,1 % en phase 9 |
| Page saga, frise | 7,7 % → **5,7 %** | Le fond qui suit le film passe en 780 px de large |
| Recherche (saisie, résultats, repli du clavier) | **1,0 %** (médiane 10 ms) | — |
| Réglages | **4,75 %** (médiane 8 ms) | — |

### Limites connues
- La première descente sur l'accueil dépasse encore 5 %, le temps que les rangées arrivent.
- Le logo d'une saga éditorialisée dont aucun film ne porte le nom (« Le monde des sorciers ») reste en texte.
- Le nombre de films d'une saga compte ceux sortis jusqu'à l'année en cours, y compris ceux annoncés pour la fin de l'année.

## 13. Phase 11 — Expérience : TV en direct fiable et intelligente, style cinéma (26/10/2026)

Branche `phase11-experience` (créée depuis `phase10-finitions`). Onze commits locaux, de `23a5604` à `7f26180`. Rien n'est poussé sur GitHub.

### Retours de l'utilisateur et réponses
| Retour | Réponse |
|---|---|
| TV : l'image se fige, le son continue | Cause : l'option d'extraction `FLAG_DETECT_ACCESS_UNITS`, retirée. En plus, un **chien de garde vidéo** : si le son avance sans nouvelle image pendant 3 s, la piste vidéo est relancée sans nouvelle connexion. |
| « 7 versions » faux, qualité inconnue | Le nombre de versions disparaît. La **qualité réelle** est mesurée (`get_vod_info`, une fiche à la fois, cache 30 jours, table Room v7 `vod_probe`) et complétée pendant la lecture. Badge 4K, HDR ou Dolby Vision seulement quand c'est vrai. Versions regroupées par langue, avec « Meilleure image » et « Meilleur son ». |
| Polices sans caractère | **DM Serif Display** pour les titres (20 sp au moins), Manrope pour le reste. Titres de rangées avec une étiquette (« POUR VOUS », « TENDANCE »…), une partie en italique et un filet dégradé. |
| Titres sans logo, logos de sagas | **TitleArt** : un titre stylé selon le genre, avec 9 styles (métal, or, néon, horreur, tampon, élégant, ludique, western, documentaire). La taille s'adapte, le texte n'est jamais coupé. Sagas : logo anglais propre, sinon nom en or. |
| Barre de focus sur le texte des épisodes | Le liseré passe à l'intérieur de la carte. |
| Acteurs non sélectionnables | **Page acteur et réalisateur** : photo, dates, biographie dépliable, sa filmographie présente dans l'abonnement (films, séries) et ses rôles marquants hors catalogue, grisés. On y accède depuis la distribution d'une fiche et depuis les visages d'une saga. |
| Recherche : clavier, légendes, Top 10 coupé | Touches en relief, blanches au focus (×1,1). Légendes des touches fixées en bas. Clavier ouvert : un aperçu « Meilleurs résultats » taillé pour la place qui reste. Clavier replié : grandes cartes paysage avec le logo dans l'image. Top 10 en cartes paysage avec un **grand rang à empattements**. Suggestions raccourcies : les pastilles de type ne sortent plus de l'écran. ▲ en haut des résultats rouvre toujours le clavier (avant, le champ ne prenait pas le focus). |
| L'accueil ne s'adapte pas | Voir « Accueil vivant » ci-dessous. |
| Panneau audio et sous-titres trop grand | **Carte compacte** en bas à droite : hauteur de son contenu (60 % au plus), montée en 180 ms. Une seule piste audio : un simple rappel au lieu d'une colonne. Les noms de sous-titres en ligne tiennent sur deux lignes. |
| TV : bulles flottantes, pas de légendes | Tiroirs pleine hauteur collés au bord. Les catégories s'ouvrent en seconde colonne. Les légendes des touches sont dessinées. |
| TV : touches | OK : **détails de la chaîne** (programme en cours et suivant, santé du flux, actions). ◀ ouvre les chaînes, puis les catégories. ▶ ouvre les programmes. ▲▼ zappent seulement quand aucun panneau n'est ouvert. Menu ouvre les réglages du lecteur. |
| Programmes : ▼ change de chaîne | ▼ fait défiler la journée entière. |
| Réglages du lecteur en direct | Réglages du lecteur : audio, sous-titres, format d'image et **décodage pour la séance**. Le décodage passe en Auto, Matériel ou Logiciel tout de suite : le flux est rouvert, l'ancien lecteur étant fermé avant (une seule connexion). |
| Lecteur de direct intelligent | **Pause du direct sur disque**, jusqu'à 30 min. Voir ci-dessous. |
| « Commencer la saga » ignore l'ordre choisi | L'ordre est transmis et retenu pour chaque saga. Le bouton indique l'ordre suivi. |

### Pause du direct (timeshift)
- **Une seule connexion.** La lecture en direct normale ne change pas. À la pause, le lecteur réseau s'arrête et un **écrivain** prend l'unique connexion. C'est le même moteur que l'enregistrement, factorisé dans `StreamPump`.
- **Tampon tournant** :
  - fichiers d'une minute dans le cache, avec un index temps → octet (une marque par seconde, alignée sur les paquets TS) ;
  - plafond de 30 min et 2,5 Go ;
  - désactivé sous 3 Go libres ;
  - réglage « Pause du direct » dans Télévision.
- **Lecture locale.** Le lecteur lit le tampon et attend quand il rattrape l'écriture. Reculer ou avancer, c'est relancer la lecture à l'octet trouvé dans l'index, sans réseau.
- **Commandes** :
  - ⏯ ou OK : pause ou reprise ;
  - ◀◀ / ▶▶ (ou ◀ / ▶ quand la barre est affichée) : −10 s / +30 s ;
  - ▲ : retour au direct ;
  - ▼ : masquer la barre.
  - La barre montre la fenêtre enregistrée, la position et le retard (« −00:38 »). En différé, un repère discret « DIFFÉRÉ −00:25 » reste affiché.
- **Repli.** Si l'écriture s'arrête (refus du fournisseur, coupures répétées, disque), le lecteur revient au flux en direct, sans écran noir. Un refus (4xx) ne déclenche jamais de nouvel essai.
- **Vérifié sur la box (TF1, une seule connexion)** :
  - pause de 40 s : −00:38 affiché ;
  - reprise en différé : l'image avance (−00:39) ;
  - −10 s : −00:35 ;
  - ▲ : retour au direct, et le repère disparaît ;
  - lecteur fermé à la fin.

### Accueil vivant
- **Recalcul au retour sur l'écran** (fiche, lecteur, application rouverte) quand la page n'est plus juste :
  - le jour a changé ;
  - une lecture a eu lieu ;
  - Ma liste a changé (le profil de goûts en découle) ;
  - la page a plus de 30 min.
- Le calcul se fait en arrière-plan. La page reste affichée, puis elle est remplacée. Les rangées gardent leur clé, donc le focus reste en place.
- **Nouvelles rangées** :
  - « Nouveaux épisodes de vos séries » : séries regardées mises à jour par le fournisseur depuis le dernier visionnage ;
  - « Ce qui buzze aujourd'hui » : tendances TMDB du jour, cache de 3 h ;
  - « Arrivés cette semaine » : date d'ajout réelle, moins de 7 jours ; sinon « Nouveaux films ».
- L'ancienne rangée « Nouveaux épisodes », qui montrait en réalité les séries récemment mises à jour au catalogue, devient « Séries mises à jour ».
- **Héros** : tendances du jour, réordonnées selon les goûts. Le rang dans la tendance compte pour moitié.

### Tests
- **Unitaires : 261**, tous verts (1 ignoré, comme avant). Nouveaux tests :
  - `TimeshiftTest` (9) : segments, plafonds, index temps → octet, lecture qui attend l'écriture, refus sans nouvel essai, reconnexion ;
  - `LiveKeysTest` (7) ;
  - `TypographyTest` et `TitleArtTest` (taille adaptée sans coupure, Robolectric) ;
  - `TracksPanelTest`, `SearchStyleTest` (meilleurs résultats, pastilles) et `PeopleTest` (métier accordé, naissance, âge, décès) ;
  - `HomeFreshnessTest` (5) : recalcul avec horloge et état simulés, ajouts de la semaine, nouveaux épisodes, héros ;
  - `ParcoursDataTest` : page gardée en mémoire, puis recalculée après une lecture.
  - Test de migration Room v7.
- **`lintRelease` : 0 erreur.**

### Fluidité mesurée sur la box (`dumpsys gfxinfo`, part d'images saccadées)
| Écran | Mesure | Commentaire |
|---|---|---|
| Accueil, déplacements latéraux | **2,3 %** | — |
| Accueil, descente avec les images en cache | **2,8 %** (remontée 4,8 %) | — |
| Accueil, deux premières descentes | 6,1 % à 7,2 % | Les rangées et leurs images arrivent pendant le défilement |
| Recherche (saisie, résultats, repli du clavier) | **1,85 %** | — |
| Hub des Sagas | **3,4 %** | — |

### Limites connues
- **Image d'une version, son d'une autre** : impossible avec une seule connexion. La ligne est grisée dans « Plus », avec son explication. Les VF à 25 images/s (sources PAL) imposeraient aussi un vrai recalage.
- **Pause du direct** : flux HLS chiffrés et fMP4 (`EXT-X-MAP`) refusés par l'écrivain. Dans ce cas, la pause reste une pause simple.
- Les premières descentes sur l'accueil dépassent 5 % tant que les images ne sont pas en cache.
- La version installée sur la box est une version de production, sans journaux. Le recalcul de l'accueil après une lecture est donc prouvé par les tests, pas par les journaux de la box.
