# release-name-parsing Specification

## Purpose

Langue et définition d'une version Real-Debrid lues dans son nom, pour la classer correctement sur la tablette, Fire TV et le PC.

## Requirements

### Requirement: DUAL sans français
Un nom portant « DUAL » sans aucune marque de français (FRENCH, TRUEFRENCH, VFF, VFQ, VFI, VF, FR, FRE, FRA, 🇫🇷) MUST NOT être classé MULTI. Sa langue SHALL rester inconnue.

#### Scenario: Version turque et anglaise
- **WHEN** le nom est `Inception.2010.UHD.Bluray.1080p.DUAL.x264-HDM.mkv`
- **THEN** la langue lue est inconnue (ni MULTI, ni VF), vérifié par un test JVM de `:core`

#### Scenario: DUAL avec le français
- **WHEN** le nom est `Film.2020.1080p.DUAL.TRUEFRENCH.x264.mkv`
- **THEN** la langue lue est MULTI

### Requirement: Résolution écrite d'abord
La définition SHALL venir de la résolution écrite dans le nom (2160p, 1080p, 720p…). « UHD », « 4K » et « FHD » ne décident que si aucune résolution n'est écrite.

#### Scenario: UHD avec 1080p
- **WHEN** le nom contient à la fois « UHD » et « 1080p »
- **THEN** la définition lue est 1080p, vérifié par un test JVM de `:core`

#### Scenario: UHD seul
- **WHEN** le nom contient « UHD » sans résolution écrite
- **THEN** la définition lue est 4K

### Requirement: Pistes vérifiées à l'ouverture (PC)
Sur PC, une version lancée automatiquement et annoncée MULTI ou VF SHALL être remplacée par la suivante si son fichier ne contient aucune piste française. Le nombre d'essais reste limité à 3. La version reste lue si aucune autre ne passe, et une version choisie à la main MUST NOT être remplacée.

#### Scenario: MULTI sans français
- **WHEN** la version lancée a pour pistes English, Spanish, Italian, Ukrainian…, sans français
- **THEN** la version suivante est lancée, et la lecture démarre sur une piste française (essai réel : « VFF AC3 5.1 - [French] »)

### Requirement: Codes FRE et FRA
Les jetons « FRE » et « FRA » SHALL compter comme une marque de français.

#### Scenario: Liste de langues
- **WHEN** le nom est `Inception.2010.1080p.BluRay.ENG.LATINO.CASTELLANO.HINDI.ITA.GER.FRE.x265.mkv`
- **THEN** la langue lue est MULTI, vérifié par un test JVM de `:core`
