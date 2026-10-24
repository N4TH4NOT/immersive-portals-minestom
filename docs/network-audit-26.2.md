# Audit réseau Minecraft 26.2

Revue du 24 octobre 2026 sur `port-26.2`, après extraction de la gravité.
Périmètre : les dix types de payloads personnalisés du projet, les treize
messages du pont Minestom, les paquets natifs acceptés dans les vues distantes
et leurs sites d’envoi. Minecraft utilise ici le protocole **776**, le pont
conserve son format **version 2**. Aucun identifiant ni codec public ne change.

## Doublons corrigés

| Séquence observée | Comportement corrigé |
| --- | --- |
| Entité distante : position, tête, métadonnées, attributs et équipement renvoyés toutes les deux ticks | Historique par spectateur et par vue ; seuls les champs modifiés sont renvoyés. Les attributs, indices de métadonnées et emplacements d’équipement sont comparés individuellement dans leur représentation réseau. |
| Apparition immédiatement suivie des mêmes position et tête | Le paquet d’apparition initialise l’historique, y compris sa rotation de tête réelle. Une position précise reste envoyée si la quantification native ou l’état au sol apporte une information supplémentaire. |
| Mise à jour d’entité explicite puis même état au balayage automatique | L’historique intègre les paquets explicitement transmis. Les mises à jour partielles ne masquent pas les autres attributs, métadonnées ou équipements. |
| Reconstruction d’une définition de portail identique | Égalité par valeur, y compris le contenu de `extraNbt`, et suppression de l’`Upsert` inchangé. |
| Enregistrements répétés des mêmes mondes | `Welcome` n’est renvoyé que si la table publiée change. |
| Même état du même bloc plusieurs fois dans le tick | Un seul delta ordinaire. Une séquence A → B → A conserve ses trois transitions. |
| Delta de bloc hors des chunks transmis, ou avant leur remplacement complet | Le delta est omis ; le snapshot complet transmet l’état autoritaire du chunk. |
| Correction de téléportation puis même gravité | La correction contient déjà la gravité ; `GravitySync.recordSent` l’enregistre pour éviter un message `Gravity` immédiat. |
| Révocation de session : `Clear` puis fermeture de chaque vue | `Clear` suffit : le client ferme déjà toutes les vues, fragments et états de gravité. |
| Niveau de performance inchangé envoyé toutes les cinq secondes | Le calcul local continue ; le RPC Fabric est envoyé au changement de niveau ou de connexion, uniquement si le serveur a négocié Immersive Portals. Minestom ne reçoit plus ce RPC sans destinataire. |
| Portails globaux : diffusion immédiate puis diffusion du même état au tick suivant | La diffusion immédiate consomme également le drapeau de synchronisation en attente. |

Les caches d’entités sont supprimés à la sortie de vue. Une réapparition ou un
identifiant numérique recyclé déclenche une nouvelle apparition complète ; le
recyclage détruit d’abord l’ancienne entité. Les snapshots sont des octets copiés,
pas des références à des objets Minestom mutables.

## Inventaire des payloads personnalisés

S→C : serveur vers client ; C→S : client vers serveur.

| Canal | Sens et information | Contexte et répétitions |
| --- | --- | --- |
| `iportal:config_packet` | S→C, version et paramètres de compatibilité | Négociation Fabric pendant la configuration. |
| `iportal:configure_complete` | C→S, version et choix de compatibilité du client | Réponse au précédent ; elle valide une information provenant de l’autre extrémité. |
| `imm_ptl:dim_int_id_sync` | S→C, correspondance dimensions/entiers, types et niveaux de mer | Connexion et changements de dimensions. `Welcome` assure le rôle équivalent avec Minestom : les deux mécanismes correspondent à des serveurs différents. |
| `imm_ptl:teleport` | C→S, dimension source, yeux avant traversée et UUID du portail | Demande de validation Fabric. La téléportation native qui suit transporte la décision du serveur. |
| `imm_ptl:spawn_portal` | S→C, identité, dimension, position et NBT du portail | Remplace le paquet natif d’apparition du portail. Son tracker annule les mises à jour natives ordinaires ; il n’y a pas de seconde apparition vanilla. Les synchronisations explicitement demandées par l’application restent possibles. |
| `imm_ptl:upd_glb_ptl` | S→C, portails globaux d’un monde | État initial par nouveau joueur, puis état sale ; diffusion immédiate et différée coordonnées. |
| `i:r` | S→C, dimension et paquet natif | Enveloppe de redirection, avec protection contre une seconde enveloppe. Les bundles sont parcourus ; le paquet n’est pas livré une seconde fois sans redirection. |
| `iportal:remote_c2s` | C→S, méthode RPC et arguments | Chaque commande peut représenter une action distincte. Déduplication spécifique au rapport de performance, aucune suppression générique des commandes. |
| `iportal:remote_s2c` | S→C, méthode RPC et arguments | Même règle pour les événements et opérations côté client. |
| `imm_ptl:bridge` | Les deux sens, messages ci-dessous | Backend Minestom ; les paquets natifs d’un monde distant sont encapsulés. |

La baguette de déplacement vérifie déjà que la cible du curseur a bougé de plus
de `1e-6` avant `requestApplyDrag`. Ses commandes de fin et d’annulation restent
des opérations distinctes, même sans nouveau déplacement.

## Inventaire du pont Minestom

Chaque message commence par la signature du protocole et son type. Le rappel
de version dans `Hello` sert à négocier la compatibilité ; ce n’est pas une
copie d’un état de jeu.

| ID / message | Information utile | Politique d’envoi |
| --- | --- | --- |
| 0 `Hello` | Versions du pont/Minecraft et capacités, dont gravité | Amorçage et réponse ; un second Hello client n’ouvre pas une nouvelle session existante. |
| 1 `Welcome` | UUID de session, mondes, types et niveaux de mer | Premier état puis changement réel de table. |
| 2 `Upsert` | Définition complète du portail et options NBT | Nouveau portail ou définition différente. |
| 3 `Remove` | UUID du portail | Disparition ou perte de visibilité. |
| 4 `Clear` | Fin de l’état de session | Ferme toutes les vues en une seule opération. |
| 5 `Teleport` | UUID du portail et segment des yeux dans le monde source | Les deux extrémités servent au test de traversée balayée ; `toEye` n’est pas une destination à accepter du client. |
| 6 `Rejected` | Motif du refus | Erreur de protocole/requête ; les requêtes distinctes ne sont pas fusionnées. |
| 7 `OpenView` | UUID de vue et monde ; centre et rayon hérités | Une ouverture par vue. UUID et monde sélectionnent le contexte du contenu suivant. Voir la réserve ci-dessous. |
| 8 `CloseView` | UUID de vue | Fermeture individuelle quand une vue n’est plus nécessaire ; pas après `Clear`. |
| 9 `WorldFragment` | Vue, séquence, index, nombre, taille totale et octets | Réassemblage borné des paquets natifs. Les en-têtes permettent d’isoler les vues et de reconstituer les gros chunks. |
| 10 `Interaction` | Chemin de portails et paquet natif client | Remplace l’envoi natif de l’interaction distante ; le serveur valide chemin, portée et obstacles. |
| 11 `Correction` | Monde, pieds, vitesse, échelle, regard et gravité | Correction atomique d’une prédiction ; chaque refus doit pouvoir corriger le client. |
| 12 `Gravity` | Monde, ID numérique, UUID, direction et intensité | Changement, apparition forcée ou rappel à 100 ticks. L’UUID évite d’appliquer l’état à un ID recyclé. La correction tient lieu d’envoi de cet état. |

**Champs hérités identifiés :** le centre et le rayon d’`OpenView` coûtent
28 octets à l’ouverture. Le cache de chunks actuel n’est pas circulaire et ses
méthodes `updateViewCenter`/`updateViewRadius` sont vides : ces deux indications
ne pilotent donc pas son stockage. Elles sont conservées dans le codec v2 pour
les clients existants. Leur retrait demanderait une évolution négociée du
protocole ; il ne justifie pas de casser la compatibilité pour un envoi ponctuel.

## Paquets natifs dans les vues distantes

Les noms ci-dessous sont ceux du client Minecraft. L’encodage serveur utilise
les registres Minestom de la version ciblée, sans recopier d’ID numérique 1.21.
Une vue distante n’est pas créée pour le monde courant : Minestom transmet
déjà ce monde par son flux natif.

| Famille / classes `Clientbound…Packet` | Contexte et informations complémentaires |
| --- | --- |
| `LevelChunkWithLight`, `LightUpdate`, `ForgetLevelChunk`, `ChunksBiomes` | Snapshot initial/invalidation, éclairage ultérieur, sortie de vue et biomes. Un chunk invalidé ne peut pas être comparé seulement par ses coordonnées. |
| `BlockUpdate`, `SectionBlocksUpdate`, `BlockEntityData` | État d’un bloc, changements groupés et données propres à une block entity. Le delta d’un bloc est coalescé dans le tick, sauf réponse à une prédiction. |
| `BlockChangedAck` | Acquittement de séquence de prédiction. Il suit l’état autoritaire du bloc ; deux refus conservent chacun leur couple état/acquittement. |
| `BlockEvent`, `LevelEvent`, `LevelParticles`, `Sound`, `SoundEntity` | Événements : deux sons ou animations identiques peuvent être deux occurrences voulues. Aucune déduplication générique par octets. |
| `SetTime`, `GameEvent` | Horloge/transition ou événement propre au monde ; les envois explicites de l’application restent transmis. |
| `AddEntity`, `RemoveEntities` | Cycle de vie. Nouvelle vue, réapparition et nouvel UUID imposent un nouvel état initial. |
| `SetEntityData`, `UpdateAttributes`, `SetEquipment` | Données indépendantes ; publication automatique seulement des indices/attributs/emplacements différents, y compris remise à AIR de l’équipement. |
| `EntityPositionSync`, `RotateHead` | Position/vitesse/orientation/sol et orientation de tête indépendante. Une tête déjà portée par l’apparition n’est pas renvoyée. Les positions absolues respectent aussi `Entity.getSynchronizationTicks()` : 20 ticks par défaut. |
| `SetEntityMotion`, `MoveEntity` et variantes, `TeleportEntity` | Vitesse, deltas relatifs et téléportation explicites. Une ancre absolue ultérieure reste utile contre la dérive et la quantification ; ces opérations ne sont pas fusionnées aveuglément. |
| `UpdateMobEffect`, `RemoveMobEffect` | Ajout/actualisation et fin d’un effet ; données indépendantes des attributs et métadonnées. |
| `EntityEvent`, `HurtAnimation`, `DamageEvent`, `TakeItemEntity` | Statuts, animation, contexte des dégâts et collecte : événements préservés, même si leurs octets sont identiques. |
| `SetEntityLink`, `SetPassengers` | Relations d’entités ; distinctes de leurs apparitions et mouvements. |

Après un changement de monde du joueur, les snapshots natifs du nouveau monde
restent nécessaires au cycle de connexion vanilla, même si une ancienne vue
distante avait chargé une partie du terrain. Le rappel de gravité est également
conservé pour réappliquer l’état autoritaire au client. Les attributs gravitationnels
du mod ne sont pas enregistrés dans Minestom : le message `Gravity` ne duplique
donc pas ses paquets d’attributs natifs.

## Mesures et validation

Le test `RemoteEntityTrackerTest.anIdleEntitySendsOnlyItsConfiguredAbsolutePositionRefreshes`
mesure une entité vivante immobile après son apparition : ticks 2 à 198, un
balayage tous les deux ticks, soit **99 balayages**. L’ancien chemin envoyait
**495 paquets natifs** (cinq par balayage) ; le nouveau en envoie **9**, soit
**531 octets natifs encodés**, correspondant aux ancres de position. Cela fait
**98,2 % de paquets en moins dans ce scénario**, hors chunks, gravité, enveloppes
et connexion initiale ; ce chiffre n’est pas un gain global de bande passante.

Les tests couvrent aussi les changements partiels, la remise à zéro de
l’équipement, la précision de l’apparition, la sortie/réentrée de vue, les IDs
recyclés, les états explicitement transmis, les transitions de blocs, le
remplacement de chunk, les acquittements et les événements identiques conservés.
Les fixtures du protocole gravitationnel v2 restent inchangées.

Validation exécutée pendant cette revue :

- `./gradlew build validateAccessWidener -PportalSmoke compileSmokeTestJava` :
  **56 tests JUnit**, mod Fabric, SDK, exemple, modules de gravité et scénarios
  de validation compilés avec le sous-module épinglé, sans substitution locale.
- Client Vulkan et Minestom avec Gravity Changer : six directions, traversée
  et interaction distante, marqueur `PORTAL_GRAVITY_COMPLETE` et confirmations
  serveur. La capture compte un seul `Welcome`, deux `Upsert` pour deux portails,
  et aucun `iportal:remote_c2s` reçu par Minestom.
- Client Vulkan sans Gravity Changer : interactions de blocs à travers deux
  portails, actions dont le callback ne modifie pas le monde, interaction et
  attaque d’entité, puis traversée ; marqueur `PORTAL_SMOKE_COMPLETE`.
- Serveur Fabric intégré sous Vulkan : configuration négociée, création du
  monde, datapack, dimensions et scène de portails ; marqueurs
  `PORTAL_SMOKE_DATAPACK_WORLDS_AND_NBT_OK` et `PORTAL_RENDER_COMPLETE`.

La revue concerne les envois du projet et les séquences exercées. Un appelant de
`sendWorldPacket` ou un mod tiers peut encore produire volontairement des
événements ou resynchronisations supplémentaires ; leur intention ne se déduit
pas de la seule égalité des octets.
