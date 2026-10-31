# desktop-recordings Specification

## Purpose
Enregistrer des programmes du direct sur le PC, comme la tablette : programmation depuis le guide ou le lecteur, capture du flux sur disque, page des enregistrements et lecture.

## Requirements

### Requirement: Programmer un enregistrement
L'utilisateur SHALL pouvoir programmer l'enregistrement d'un programme depuis le guide (clic droit ou bouton « Enregistrer » du programme) et depuis le lecteur du direct (« Enregistrer » = programme en cours). Un programme déjà programmé MUST NOT l'être deux fois. Deux enregistrements qui se chevauchent sur des chaînes différentes MUST être refusés, avec un message, car l'abonnement n'autorise qu'une connexion.

#### Scenario: Depuis le guide
- **WHEN** l'utilisateur choisit « Enregistrer » sur « Télématin » dans le guide
- **THEN** le programme apparaît sous « Programmés » avec son heure, et une pastille rouge le marque dans le guide

#### Scenario: Chevauchement refusé
- **WHEN** un enregistrement est déjà programmé de 20:00 à 21:00 sur France 2 et l'utilisateur programme 20:30–21:30 sur TF1
- **THEN** la programmation est refusée avec « Un seul enregistrement à la fois (une connexion) », vérifié par un test JVM des règles

### Requirement: Capture du flux
À l'heure prévue, le PC SHALL lire le flux de la chaîne et l'écrire dans un fichier `.ts` du dossier de données, de 2 minutes avant le début à 5 minutes après la fin (marges de la tablette). L'enregistrement en cours MUST passer avant la lecture : une lecture d'une autre chaîne est arrêtée avec un message, et la même chaîne reste regardable. Un refus du fournisseur (4xx) MUST NOT être relancé tout de suite.

#### Scenario: Enregistrement terminé
- **WHEN** un enregistrement de 5 minutes se termine pendant que l'app est ouverte
- **THEN** il passe sous « Terminés » avec sa taille, et sa lecture démarre au début du fichier

#### Scenario: App fermée
- **WHEN** l'app est fermée à l'heure d'un enregistrement
- **THEN** il est marqué « Manqué (app fermée) » sous « Échecs et annulés » au prochain lancement

### Requirement: Page Enregistrements
La page SHALL reprendre celle de la tablette : titre, ligne « n terminés · x utilisés · y libres », rangées « PRÊTS À REGARDER · Terminés », « Programmés » et « À VÉRIFIER · Échecs et annulés », cartes avec logo de la chaîne, titre, chaîne, jour et heures, taille. Clic = lire (terminé) ; clic droit = annuler ou supprimer.

#### Scenario: Page
- **WHEN** l'utilisateur clique « Enreg. » dans le rail
- **THEN** la page s'affiche avec les rangées de la tablette (capture comparée à `ref/enreg.png`)
