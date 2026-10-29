# Design

## Context

Le Direct possède déjà un panneau de détails piloté par le focus, `StreamResolver` pour construire l'URL et `StreamPlayer` pour les reconnexions. Le fournisseur est protégé par `ProviderGate`, qui arrête le lecteur exclusif précédent.

## Goals / Non-Goals

**Goals:**

- Démarrer l'aperçu uniquement après 600 ms de focus stable.
- Réutiliser les abstractions Media3 et libérer le lecteur au changement d'écran.
- Stocker les règles avec une clé stable et programmer les occurrences du guide sans doublon.

**Non-Goals:**

- Ajouter un second moteur de lecture ou modifier le multi-écran.
- Synchroniser les règles entre appareils.

## Decisions

- Utiliser un `StreamPlayer` dédié au panneau Direct, créé paresseusement et contrôlé par le `ViewModel`, plutôt qu'un `ExoPlayer` directement dans Compose. Cela conserve la reconnexion, les refus fournisseur et le partage HTTP existants.
- Utiliser `debounce(600)` sur le flux de focus afin d'éviter un démarrage pendant le défilement D-pad.
- Ajouter `recording_rules` avec une clé unique `(playlistId, ruleKey)`, puis vérifier `(channelId, startAt, endAt)` avant chaque programmation.
- Parcourir la fenêtre EPG à venir lors du rafraîchissement des chaînes ; une occurrence déjà programmée est ignorée.

## Risks / Trade-offs

- [Le panneau d'aperçu peut consommer la connexion autorisée] → `ProviderGate` arrête tout lecteur exclusif concurrent et affiche l'état de refus/reconnexion du `StreamPlayer`.
- [Un grand nombre de chaînes peut provoquer plusieurs lectures EPG] → la fenêtre est limitée à sept jours et les appels réutilisent le cache EPG existant.
- [Le passage au plein écran recrée encore le lecteur de route] → la connexion d'aperçu est arrêtée lors de la navigation ; le transfert exact du même objet nécessite une session de lecture partagée ultérieure.

## Migration Plan

1. Exécuter la migration Room 8→9.
2. Déployer l'aperçu et les règles avec la version debug.
3. En cas de retour arrière, conserver la table inconnue par l'ancienne version et restaurer l'APK précédent ; aucune donnée existante n'est supprimée.
