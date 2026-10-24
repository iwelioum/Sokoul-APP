# Feuille de route

## Fait (v2.0.0)
- Playlists multiples Xtream Codes + M3U (URL ou fichier), conversion automatique des liens Xtream
- Guide TV XMLTV complet + EPG court Xtream de secours, rapprochement des chaînes par nom, décalage horaire
- Grille EPG plein écran, replay (Xtream timeshift et catch-up M3U), rappels, enregistrements programmés
- Lecteur : zapping, liste des chaînes, saisie de numéro, chaîne précédente, pistes audio/sous-titres,
  formats d'image, reconnexion automatique, FFmpeg, MediaSession, image dans l'image
- Films/séries : catégories, tri, fiches TMDB (affiches, résumé, casting, bande-annonce), reprise, épisode suivant
- Multi-écran 4 chaînes, favoris, récents, recherche globale (y compris le guide)
- Contrôle parental (code, groupes verrouillés), groupes masqués, sauvegarde/restauration JSON

## À tester sur l'appareil réel
- Synchronisation et lecture avec l'abonnement réel (Xtream et/ou M3U)
- Nombre de décodeurs simultanés pour le multi-écran (dépend de la box)
- Fluidité de la grille EPG sur la box la plus modeste

## Idées pour la suite
- Aperçu vidéo de la chaîne sélectionnée dans l'écran Direct (attention à la limite de connexions)
- Timeshift local (pause du direct) via un tampon disque
- Profils utilisateurs distincts (favoris et reprises par profil)
- Chaînes Android TV « Watch Next » sur l'écran d'accueil de la box
- Traduction anglaise de l'interface
- Signature release dédiée (actuellement signée avec la clé debug)
