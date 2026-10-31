# Tasks

## 1. Réglages modifiables (socle des autres groupes)

- [ ] 1.1 Clés de réglages et `DesktopPrefs` (lecture et écriture typées dans `settings`). Vérification : test JVM `DesktopTest.reglages typés`.
- [ ] 1.2 Rubriques Lecture et Télévision et guide modifiables (`OptionsDialog`, `ToggleRow`, « Avancé » repliable), avec les libellés de la tablette. Vérification : captures `settings:playback` et `settings:tv`, comparées à `ref/reg-lecture.png` et `ref/reg-tv.png`.
- [ ] 1.3 Application au lecteur : langue préférée dans `AudioChoice`, apparence des sous-titres (fabrique libVLC recréée hors lecture), mise en mémoire, décalage du guide (`GuideOffset`, pur). Vérification : tests JVM `AudioChoice` (langue préférée) et `GuideOffset`.

## 2. Fiche, sources d'une version et pages de bibliothèque

- [ ] 2.1 Fiche : « Finit à », « Aussi sur » (`TmdbClient.providers` dans `:core`) et section « Saga : *nom* ». Vérification : capture `detail:movie:140607`, comparée à `ref/fiche-sw.png` ; `:core:test` et `testDebugUnitTest` au vert.
- [ ] 2.2 Fenêtre des sources d'une version (« +n sources »). Vérification : capture comparée à `ref/sources-fiche.png`.
- [ ] 2.3 `curated_sagas.json` en ressource, `CuratedSagas` et `SagaOrder` (purs), page d'une saga façon tablette, accroche de la page Sagas. Vérification : tests JVM `SagaOrder` (récit et prochain film) ; capture `saga:10` comparée à `ref/saga-page.png`.
- [ ] 2.4 « À venir » (TVmaze par IMDb, avec cache) et « Historique » (groupé par jour), boutons dans Ma liste. Vérification : tests JVM du tri d'« À venir » et du groupement par jour ; captures comparées à `ref/maliste-avenir.png` et `ref/maliste-historique.png`.

## 3. Lecteur de films

- [ ] 3.1 Pastille « langue · définition · origine » et panneau latéral « Langue et qualité » (pistes, versions par langue, autres sources, échecs, changement de version à la même position). Vérification : capture `play:movie:27205` avec le panneau ouvert, comparée à `ref/lecteur-langue.png`.
- [ ] 3.2 Panneau « Sous-titres » et menu « … » (vitesse, format d'image). Vérification : capture et test réel de la vitesse.
- [ ] 3.3 Fenêtre réduite (480×270, toujours au premier plan, retour à la taille d'avant). Vérification : essai réel et capture.
- [ ] 3.4 `NextEpisodeRule` (pur) et carte « Épisode suivant » avec compte à rebours. Vérification : test JVM `NextEpisodeRule` ; capture en fin d'épisode (route d'essai qui se place à 30 s de la fin).

## 4. Direct et lecteur du direct

- [ ] 4.1 Panneau de la chaîne avec aperçu muet (lecteur unique), « EN CE MOMENT », « ENSUITE », « Regarder » sans relancer le flux, arrêt en quittant le Direct. Vérification : capture comparée à `ref/lecteur-direct-sortie.png`, et compte des ouvertures de flux en journal d'essai. La tablette ne doit rien lire pendant l'essai.
- [ ] 4.2 Bandeau du direct de la tablette : boutons du haut, actions (Pause du direct, Programmes, Chaînes, Enregistrer, Favori, Réglages), santé du flux. Vérification : capture comparée à `ref/lecteur-direct-barre.png`.
- [ ] 4.3 Pause du direct (différé libVLC) et « Retour au direct ». Vérification : essai réel (pause 30 s, reprise, retour au direct).

## 5. Enregistrements

- [ ] 5.1 Table `recordings`, `RecordingRules` PC (marges, chevauchement, doublon, manqué). Vérification : tests JVM `RecordingRules`.
- [ ] 5.2 `DesktopRecorder` (flux TS sur disque, refus 4xx sans relance, priorité sur la lecture) et planificateur. Vérification : essai réel d'un enregistrement de 3 minutes, fichier lu ensuite, la tablette sans lecture.
- [ ] 5.3 Entrée « Enreg. » du rail et page Enregistrements ; « Enregistrer » depuis le guide et depuis le lecteur du direct. Vérification : capture comparée à `ref/enreg.png` ; test d'interface du rail mis à jour.

## 6. Clôture

- [ ] 6.1 `:desktop:test`, `:core:test` et `testDebugUnitTest` au vert ; relecture par les agents `android-reviewer` et `desktop-designer`, avec les captures.
- [ ] 6.2 Réinstallation (`tools/install-desktop.ps1`), commit et push par groupe, compte rendu.
