# Proposition

## Pourquoi

L'écran Direct ouvre aujourd'hui le lecteur plein écran après sélection d'une chaîne. Il manque un aperçu vidéo après stabilisation du focus et une règle permettant d'enregistrer automatiquement les prochaines diffusions d'un même programme sur une chaîne.

## Proposition

- Ajouter un aperçu contrôlé par le même `StreamPlayer` et `ProviderGate` que le lecteur principal.
- Déclencher l'aperçu après 600 ms de focus stable et transférer le lecteur au plein écran sans ouvrir une seconde connexion.
- Ajouter une clé de règle basée sur le titre normalisé et la chaîne normalisée.
- Rechercher les programmes correspondants à chaque rafraîchissement du guide et programmer les occurrences de façon idempotente.

## Hors périmètre

Le multi-écran, Chromecast et la synchronisation en ligne entre appareils ne sont pas modifiés.
