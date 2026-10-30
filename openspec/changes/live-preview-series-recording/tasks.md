# Tasks

## 1. Aperçu dans l'écran Direct

- [x] 1.1 Ajouter `livePreview` aux réglages (AppSettings, SettingsStore, sauvegarde) et l'interrupteur dans Réglages › Télévision ; vérifier par un test de `sanitized()` et de l'aller-retour sauvegarde — *booléen sans bornes, donc rien à assainir : seul l'aller-retour des règles est testé*
- [x] 1.2 Créer l'aperçu dans le LiveViewModel : StreamPlayer exclusif (forMultiview, volume 0), anti-rebond 800 ms sur la chaîne focalisée, rien si l'aperçu est désactivé, si un enregistrement occupe la connexion ou pendant la pause du fournisseur ; vérifier par un test de la règle de déclenchement (fonction pure)
- [ ] 1.3 Afficher l'aperçu 16:9 en haut du panneau d'informations (TV), logo et programme en repli quand rien ne joue ; vérifier sur le Fire TV par capture adb — *code fait, essai Fire TV à faire*
- [ ] 1.4 Arrêter et libérer l'aperçu avant d'ouvrir le plein écran, à ON_STOP et en quittant l'écran ; vérifier sur le Fire TV que le plein écran démarre sans erreur de connexion et que `dumpsys media_session` ne montre plus de lecture après Accueil — *code fait, essai Fire TV à faire*

## 2. Règles d'enregistrement récurrent

- [x] 2.1 Ajouter la table `recording_rules`, son DAO et la migration 8 → 9 ; vérifier par le test de migration Room existant étendu à la version 9 — *plus une migration 9 → 10 (`skipped`), voir les notes*
- [x] 2.2 Écrire la normalisation du titre et la recherche des diffusions correspondantes (fonctions pures dans RecordingRules) ; vérifier par des tests sur des titres réels (« Plus belle la vie - Épisode 4512 », « Le 20h », titres génériques)
- [x] 2.3 Écrire `SeriesRecorder.run(pid)` : programme chaque diffusion à venir via `RecordingScheduler.schedule`, sans doublon (±5 min), compte les refus ; vérifier par un test avec une base en mémoire — *le choix des diffusions est testé en fonction pure (`seriesAirings`), la suppression d'une règle sur une base en mémoire*
- [x] 2.4 Appeler `SeriesRecorder` après chaque synchronisation du guide, à la création d'une règle et dans `restoreAll` ; vérifier que les tests de SyncWorker et du planificateur passent — *au démarrage via `SokoulApp` (5 s après `restoreAll`), ce qui couvre aussi le redémarrage de l'appareil*
- [x] 2.5 Ajouter les règles à la sauvegarde (export et import) ; vérifier par un test d'aller-retour de BackupManager

## 3. Interface des règles

- [x] 3.1 Ajouter « Enregistrer tous les épisodes » au menu d'un programme du guide et du direct, avec message si la règle existe déjà ; vérifier sur le Fire TV qu'une règle crée les enregistrements attendus dans l'écran Enregistrements — *vérifié sur la tablette le 30/10 (Télématin, N'oubliez pas les paroles) ; Fire TV à refaire avec l'aperçu*
- [x] 3.2 Lister les règles dans Enregistrements (titre, chaîne, prochaine diffusion, diffusions ignorées) avec « Supprimer la règle », qui annule les programmations à venir et garde les enregistrements faits ; vérifier par un test du planificateur et une capture adb — *capture tablette : « 1 diffusion ignorée (conflit) », suppression → programmation annulée, enregistrement en cours conservé*

## 4. Vérification d'ensemble

- [ ] 4.1 `gradlew testDebugUnitTest assembleDebug` au vert, installation sur le Fire TV et la tablette, essai complet : aperçu, plein écran, Accueil, règle créée depuis le guide, rafraîchissement du guide sans doublon — *compilation et 411 tests au vert ; tablette OK le 30/10 (migration 8 → 10 sur la vraie base, règles, rafraîchissement du guide sans doublon) ; reste le Fire TV : aperçu, plein écran, Accueil*

## Notes de mise en œuvre

- Le code de départ vient de la branche `phase20-implementation`, écrite par une autre session : entité, DAO, migration 8 → 9, schéma 9 et clé de titre. Il a été corrigé sur plusieurs points :
  - l'aperçu ne démarrait jamais (lancé après un `collect` sans fin) ;
  - un ExoPlayer était créé à chaque chaîne sans libérer le précédent, avec le son actif ;
  - les règles étaient programmées depuis l'écran Direct, avec `epg.range` sur toutes les chaînes ;
  - les marges et le numéro d'épisode n'étaient pas gérés.
- Le schéma v9 a pu être installé par cette autre session. Il n'est donc pas modifié : la colonne `skipped` arrive par une migration 9 → 10.
- `skipped` compte les diffusions refusées au dernier passage : un conflit levé disparaît au passage suivant.
- Une diffusion annulée à la main après la création de la règle n'est pas reprogrammée.
- Une diffusion déjà commencée est enregistrée tout de suite à la création de la règle.
- Limite constatée sur la tablette : deux diffusions consécutives sur la même chaîne (« N'oubliez pas les paroles » 18:35 et 19:10) se chevauchent à cause des marges, donc la seconde est ignorée pour conflit.

## Workflow follow-up

- Archiver le changement (`/opsx:archive`) une fois validé sur les appareils, pour verser les specs `live-preview` et `series-recording` dans `openspec/specs/`.
