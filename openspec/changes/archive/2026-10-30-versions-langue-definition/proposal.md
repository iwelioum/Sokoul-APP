# Proposal

## Why

Sur le PC, Inception a démarré en turc. La version classée en tête, « MULTI 4K », était `Inception.2010.UHD.Bluray.1080p.DUAL.x264-HDM.mkv`. Elle contient deux pistes, une turque et une anglaise, et son image est en 1080p.

`ReleaseName` (dans `:core`, partagé par la tablette, Fire TV et le PC) la lit mal :
- **« DUAL » sans marque de français est compté comme MULTI.** Dans la scène française, MULTi veut dire français + VO. « DUAL » est surtout employé par des groupes turcs, latino-américains ou brésiliens : langue locale + anglais.
- **« UHD » l'emporte sur une résolution explicite.** `UHD.Bluray.1080p` est étiqueté 4K. Le fichier passe donc devant de vraies 4K et de vraies MULTI.
- **Les codes « FRE » et « FRA » ne sont pas reconnus comme du français** (`ENG.LATINO.CASTELLANO.HINDI.ITA.GER.FRE…`).

La tablette classe les versions avec le même code, donc elle peut lancer la même mauvaise version.

## What Changes

- « DUAL » ne vaut MULTI que si une marque de français figure aussi dans le nom. Sinon, la langue reste inconnue : la version passe derrière les MULTI, VF et VOSTFR.
- La définition vient d'abord de la résolution écrite (2160p, 1080p, 720p…). « UHD » et « 4K » ne décident que s'il n'y en a aucune.
- « FRE » et « FRA » comptent comme du français, au même titre que « FRENCH » ou « VFF ».
- Des tests JVM figent ces cas, dont le nom réel ci-dessus.

## Capabilities

### New Capabilities
- `release-name-parsing` : langue et définition lues dans le nom d'une version Real-Debrid.

## Impact

- `app-android/core/.../data/debrid/ReleaseName.kt`, avec de nouveaux tests dans `:core`.
- Classement des versions sur la tablette, sur Fire TV et sur le PC, sans autre changement de code.
- Vérification : `:core:test`, les 420 tests Android (`testDebugUnitTest`), `:desktop:test`, puis un essai réel sur PC. La fiche d'Inception ne doit plus proposer ce fichier en « MULTI 4K », et la lecture doit démarrer en français. L'essai sur tablette se fera quand elle répondra à adb.
