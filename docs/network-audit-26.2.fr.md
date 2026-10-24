# Audit réseau

[English](network-audit-26.2.md) · [Français](network-audit-26.2.fr.md)

Revue du 24 octobre 2026 : payloads personnalisés, messages du pont, paquets natifs distants et séquences d’envoi. Le format reste en v2. Définitions et bornes dans [`BridgeMessage`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeMessage.java) / [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java) ; types natifs acceptés dans [`BridgeClient`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeClient.java).

## Redondances supprimées

| Séquence | Décision |
| --- | --- |
| Entité distante inchangée à chaque balayage | Comparer les snapshots réseau par spectateur/vue ; ne publier que les indices de métadonnées, attributs et équipements modifiés. |
| Apparition puis mêmes position/tête | Initialiser l’historique depuis l’apparition encodée ; conserver les précisions et l’état au sol supplémentaires. |
| Mise à jour explicite puis balayage automatique | Enregistrer l’état partiel déjà publié avant comparaison. |
| Définitions de portails ou mondes équivalents | Égalité par valeur, y compris les octets NBT ; ne publier que les définitions/tables modifiées. |
| Même delta de bloc dans un tick du service | Fusionner les états ordinaires identiques ; conserver A → B → A et les barrières de prédiction. |
| Delta précédant un chunk initial/actualisé | Laisser le snapshot autoritaire du chunk fournir cet état. |
| Correction puis même gravité ; Clear puis fermetures individuelles | Enregistrer la gravité portée par la correction ; Clear ferme déjà les vues de la session. |
| Performance et portails globaux Fabric | Envoyer les performances au changement vers les serveurs Fabric négociés ; une diffusion immédiate consomme la synchronisation globale en attente. |

## Répétitions nécessaires

Les ancres absolues d’entités et rappels de gravité réaffirment l’autorité. Une apparition ou un ID recyclé exige un nouvel état. Chaque prédiction de bloc refusée conserve son couple état/acquittement. Sons, dégâts, particules, effets et RPC peuvent être des événements distincts malgré des octets identiques.

Le monde courant utilise le flux natif Minestom ; les vues distantes transportent les autres mondes. Les paquets redirigés remplacent leur livraison d’origine. Les en-têtes de fragments identifient les contextes de réassemblage bornés ; ils ne dupliquent pas l’état du jeu.

`OpenView` conserve 28 octets hérités de centre/rayon, inutilisés par le cache client actuel. Leur retrait exige une évolution négociée du protocole. Les resynchronisations demandées par l’application sont conservées ; les événements explicites ne sont pas dédupliqués par leurs seuls octets.

## Preuves

Pour une entité vivante immobile après apparition, 99 balayages émettaient 495 paquets natifs. Le tracker émet neuf ancres absolues, soit 531 octets natifs : 98,2 % de paquets en moins dans ce scénario, hors gravité, chunks, enveloppes et connexion initiale.

[`RemoteEntityTrackerTest`](../minestom/src/test/java/io/github/n4th4not/immersiveportals/minestom/RemoteEntityTrackerTest.java) et [`PortalNetworkTest`](../minestom/src/test/java/io/github/n4th4not/immersiveportals/minestom/PortalNetworkTest.java) couvrent mises à jour partielles, remises à zéro, IDs recyclés, retour de vue, précision, transitions de blocs, acquittements et événements identiques. La capture Minestom confirme trois états de bloc pour trois acquittements et aucun RPC de performance sans destinataire. Les [commandes de validation](port-26.2.fr.md) reproduisent les parcours exercés.
