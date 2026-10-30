# Tasks

## 1. Lecture des noms

- [ ] 1.1 Tests JVM dans `:core` pour les trois exigences : DUAL turc, DUAL TRUEFRENCH, UHD + 1080p, UHD seul, liste avec FRE. Ils doivent échouer avant la correction.
- [ ] 1.2 Corriger `ReleaseName.language()` (DUAL, FRE, FRA) et `ReleaseName.quality()` (résolution écrite d'abord). `:core:test` au vert.

## 2. Vérification

- [ ] 2.1 `testDebugUnitTest` Android (420 tests) et `:desktop:test` au vert.
- [ ] 2.2 Essai réel sur PC : fiche d'Inception sans « MULTI 4K » pour le fichier HDM, lecture qui démarre en français, son coupé.
- [ ] 2.3 APK debug installé et essayé sur la tablette, dès qu'elle répond à adb.
- [ ] 2.4 Réinstallation sur PC, commit et push.
