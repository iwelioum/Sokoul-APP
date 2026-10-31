# desktop-tablet-parity Specification

## Purpose

L'app Windows reprend l'interface de l'app tablette et Fire TV de l'utilisateur, écran par écran, au lieu d'une interface PC différente.

## Requirements

### Requirement: Même code d'interface que la tablette
L'interface PC SHALL être construite à partir des composants Compose de l'app Android (thème, jetons, cartes, héros, repères, badges, boutons, focus), copiés puis adaptés. Seules les dépendances Android sont remplacées : images, polices, touches et taille d'écran.

#### Scenario: Composant repris
- **WHEN** une affiche, un héros ou une ligne de repères s'affiche sur PC
- **THEN** ses tailles, couleurs, polices et textes sont ceux de la tablette en paysage

### Requirement: Survol de la souris
Le survol SHALL produire le même effet que le focus de la télécommande : agrandissement, contour blanc, halo et liseré d'accent. La carte survolée passe devant ses voisines.

#### Scenario: Survol d'une affiche
- **WHEN** la souris passe sur une affiche d'une rangée
- **THEN** la carte s'agrandit, passe devant ses voisines et prend le contour blanc et le liseré violet de la tablette

### Requirement: Coque de la tablette
La fenêtre SHALL afficher le rail latéral de la tablette : logo S, Recherche, Accueil, Films, Séries, Ma liste, Sagas, Direct, Guide TV, Enreg. et Réglages, avec la pastille d'accent sur l'entrée active. Les pages de navigation ont la barre du haut de la tablette : titre et recherche, transparente sur le héros puis opaque.

#### Scenario: Navigation
- **WHEN** l'utilisateur clique « Films » dans le rail
- **THEN** la page Films s'ouvre avec son héros, et « Films » est marqué dans le rail

#### Scenario: Enregistrements
- **WHEN** l'utilisateur clique « Enreg. » dans le rail
- **THEN** la page Enregistrements s'ouvre et « Enreg. » est marqué dans le rail

### Requirement: Héros des pages
Les pages Accueil, Films et Séries SHALL commencer par le héros animé de la tablette : pastille « TOP N° n », logo, repères, résumé, boutons « Lecture » et « Plus d'infos », points de pagination.

#### Scenario: Héros
- **WHEN** l'accueil s'ouvre
- **THEN** une œuvre en tendance occupe le haut, avec la pastille « TOP N° 1 des films cette semaine »

### Requirement: Rangées des pages
Sous le héros, les rangées SHALL reprendre l'en-tête de la tablette : kicker, titre en DM Serif et filet d'accent. On y trouve notamment Reprendre la lecture, le Top 10, « Parce que vous avez regardé *titre* », les plateformes et les sagas. La molette fait défiler une rangée horizontalement.

#### Scenario: Rangées de l'accueil
- **WHEN** l'utilisateur descend sous le héros
- **THEN** il voit « Reprendre la lecture », le Top 10 aux grands chiffres détourés, puis « POUR VOUS · Parce que vous avez regardé … »

#### Scenario: Molette
- **WHEN** la molette tourne au-dessus d'une rangée
- **THEN** la rangée défile horizontalement

### Requirement: En-tête de la fiche
La fiche SHALL être celle de la tablette en paysage, plein cadre avec fond Ken Burns. Elle affiche le logo, l'accroche, une ligne de repères avec les notes TMDB, IMDb, RT et MC, le résumé et les boutons « Lecture · langue · définition », « Ma liste », « Bande-annonce » et « Plus ».

#### Scenario: Fiche d'un film
- **WHEN** l'utilisateur ouvre Inception
- **THEN** il voit le logo, « IMDb 8,8 · RT 86 % · MC 74 » et le bouton « Reprendre · MULTI · 4K »

### Requirement: Sections de la fiche
Sous le premier écran, la fiche SHALL présenter, dans cet ordre :
- « Versions disponibles » (par langue, une carte par définition et HDR) ;
- les épisodes par saison (séries) ;
- la distribution ;
- « Vous aimerez aussi ».

#### Scenario: Versions
- **WHEN** des versions Real-Debrid existent
- **THEN** elles sont rangées sous « MULTI », « VF », « VOSTFR » et « VO », et un clic sur une carte la lance

### Requirement: Ma liste et Sagas
« Ma liste » SHALL regrouper les œuvres ajoutées depuis leur fiche, qu'un nouveau clic retire. « Sagas » SHALL présenter les grandes collections TMDB en tuiles de la tablette, et une saga s'ouvre sur ses films dans l'ordre de sortie.

#### Scenario: Ajout à Ma liste
- **WHEN** l'utilisateur clique « Ma liste » sur une fiche
- **THEN** le bouton devient « Dans ma liste » et l'œuvre apparaît dans l'écran Ma liste
