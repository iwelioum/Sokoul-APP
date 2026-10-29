# Tasks

## 1. Aperçu dans l'écran Direct

- [ ] 1.1 Ajouter `livePreview` aux réglages (AppSettings, SettingsStore, sauvegarde) et l'interrupteur dans Réglages › Télévision ; vérifier par un test de `sanitized()` et de l'aller-retour sauvegarde
- [ ] 1.2 Créer l'aperçu dans le LiveViewModel : StreamPlayer exclusif (forMultiview, volume 0), anti-rebond 800 ms sur la chaîne focalisée, rien si l'aperçu est désactivé, si un enregistrement occupe la connexion ou pendant la pause du fournisseur ; vérifier par un test de la règle de déclenchement (fonction pure)
- [ ] 1.3 Afficher l'aperçu 16:9 en haut du panneau d'informations (TV), logo et programme en repli quand rien ne joue ; vérifier sur le Fire TV par capture adb
- [ ] 1.4 Arrêter et libérer l'aperçu avant d'ouvrir le plein écran, à ON_STOP et en quittant l'écran ; vérifier sur le Fire TV que le plein écran démarre sans erreur de connexion et que `dumpsys media_session` ne montre plus de lecture après Accueil

## 2. Règles d'enregistrement récurrent

- [ ] 2.1 Ajouter la table `recording_rules`, son DAO et la migration 8 → 9 ; vérifier par le test de migration Room existant étendu à la version 9
- [ ] 2.2 Écrire la normalisation du titre et la recherche des diffusions correspondantes (fonctions pures dans RecordingRules) ; vérifier par des tests sur des titres réels (« Plus belle la vie - Épisode 4512 », « Le 20h », titres génériques)
- [ ] 2.3 Écrire `SeriesRecorder.run(pid)` : programme chaque diffusion à venir via `RecordingScheduler.schedule`, sans doublon (±5 min), compte les refus ; vérifier par un test avec une base en mémoire
- [ ] 2.4 Appeler `SeriesRecorder` après chaque synchronisation du guide, à la création d'une règle et dans `restoreAll` ; vérifier que les tests de SyncWorker et du planificateur passent
- [ ] 2.5 Ajouter les règles à la sauvegarde (export et import) ; vérifier par un test d'aller-retour de BackupManager

## 3. Interface des règles

- [ ] 3.1 Ajouter « Enregistrer tous les épisodes » au menu d'un programme du guide et du direct, avec message si la règle existe déjà ; vérifier sur le Fire TV qu'une règle crée les enregistrements attendus dans l'écran Enregistrements
- [ ] 3.2 Lister les règles dans Enregistrements (titre, chaîne, prochaine diffusion, diffusions ignorées) avec « Supprimer la règle », qui annule les programmations à venir et garde les enregistrements faits ; vérifier par un test du planificateur et une capture adb

## 4. Vérification d'ensemble

- [ ] 4.1 `gradlew testDebugUnitTest assembleDebug` au vert, installation sur le Fire TV et la tablette, essai complet : aperçu, plein écran, Accueil, règle créée depuis le guide, rafraîchissement du guide sans doublon

## Workflow follow-up

- Archiver le changement (`/opsx:archive`) une fois validé sur les appareils, pour verser les specs `live-preview` et `series-recording` dans `openspec/specs/`.
