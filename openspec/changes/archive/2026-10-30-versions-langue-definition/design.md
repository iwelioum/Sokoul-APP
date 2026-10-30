# Design

## Context

`ReleaseName.parse` lit un nom de fichier ou de torrent. `language()` décide MULTI, VF, VOSTFR ou VO, et `quality()` décide la définition. `DesktopRanker` et le classement Android en dépendent.

## Decisions

- **DUAL** : `multi` = jeton `MULTI` ou « MULTI AUDIO ». `dual` est calculé à part : `dual && french` donne MULTI ; `dual` seul donne null (langue inconnue). On ne le classe pas en VO, car sa langue principale n'est pas forcément l'anglais.
- **Définition** : on lit d'abord la résolution explicite (`2160P`, `2160`, `3840X2160`, puis `1080P`, `1080I`, `1080`, `1920X1080`, puis `720P`…). On ne se replie sur `UHD`, `4K` ou `FHD` que si aucune résolution n'est écrite.
- **Français** : on ajoute `FRE` et `FRA` à l'ensemble des marques de français. Ce sont des jetons entiers, comme les autres : « FREE » ou « FRANCE » ne sont pas concernés.

## Risks

- Une vraie MULTI française marquée seulement « DUAL », sans autre indice, passera en langue inconnue, donc derrière les MULTI explicites. C'est acceptable : le lecteur choisit quand même la piste française si elle existe (`AudioChoice` sur PC, préférence de langue sur Android).
