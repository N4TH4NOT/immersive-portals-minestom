# Rapport : gravité changeante avec Minestom

État de l’implémentation au 21 octobre 2026, pour Minecraft 26.2.

Le scénario est réalisable : un joueur peut traverser un portail depuis un sol
horizontal et ressortir avec une gravité qui le fait tenir sur un mur. Ce parcours
a été exécuté avec un véritable serveur Minestom et un client Vulkan. Il nécessite
Immersive Portals et Gravity Changer Unofficial Port sur le client concerné.

## Les trois composants

| Composant | Responsabilité |
| --- | --- |
| Minestom et le SDK serveur | Conserver l’état autoritaire de gravité, synchroniser les entités, valider les traversées et interactions, transférer les instances et appliquer la physique passive. |
| Immersive Portals côté client | Afficher les autres mondes, détecter et prédire les traversées, appliquer les transformations du portail et recevoir les corrections du serveur. |
| Gravity Changer Unofficial Port côté client | Adapter la caméra, le corps, les yeux, les collisions et les déplacements du joueur aux six directions de gravité. |

Minestom n’exécute pas le mod Fabric. Le SDK utilise ses propres états et son
protocole pour piloter les attributs du mod client. Les transformations communes
sont adaptées des sources MIT du port Gravity Changer 26.2 et comparées à son JAR
publié par les tests.

## Ce que signifie « changer la gravité »

Un `GravityState` contient une direction et une intensité. Les directions possibles
sont `DOWN`, `UP`, `NORTH`, `SOUTH`, `WEST` et `EAST`. La gravité libre, selon un
angle arbitraire ou vers un point, ne fait pas partie de cette intégration.

L’intensité varie de `5` à `15`, avec `9.8` par défaut. Il ne s’agit pas d’une
simulation exprimée directement en mètres par seconde carrée : le rapport à
`9.8` multiplie l’accélération du jeu.

Les axes locaux suivent le corps : le haut local est opposé à la gravité.
Le SDK transforme donc aussi le décalage des yeux et la direction du regard.
Modifier seulement la force qui tire un joueur vers le bas laisserait ses yeux,
ses collisions et ses interactions dans un référentiel incohérent.

## Déroulement d’une traversée

1. Le client détecte le franchissement de l’ouverture et prédit sa nouvelle vue.
2. Il envoie l’identifiant du portail et le segment parcouru par ses yeux.
3. Le SDK vérifie le monde source, la proximité, le sens de traversée, la forme
   réelle de l’ouverture, les obstacles, le premier portail rencontré et la
   capacité de gravité du client.
4. Si `.changesGravity(true)` est activé, la rotation du portail transforme la
   direction de gravité. La direction cardinale la plus proche est choisie.
   Sinon, la direction actuelle est conservée. L’intensité reste inchangée.
5. Le SDK transforme les yeux, le regard et la vitesse. Il calcule les pieds à
   partir des yeux transformés, de la nouvelle gravité et de l’échelle du corps,
   puis applique le transfert d’instance.
6. Le nouvel état serveur est synchronisé avec le joueur et ses spectateurs
   disposant de la capacité nécessaire, y compris à travers les vues distantes.

La vitesse reste exprimée dans les coordonnées du monde. Elle est tournée et
mise à l’échelle par le portail ; elle ne reçoit pas une seconde conversion de
référentiel de gravité.

Si une demande est refusée, une correction rétablit ensemble le monde, les pieds,
le regard, la vitesse, l’échelle et la gravité du joueur. Si un transfert d’entité
échoue ou est annulé côté serveur, les changements d’échelle et de gravité déjà
préparés sont restaurés.

## Physique : joueurs et entités passives

**Pour un joueur, les mouvements sont simulés par le client.** Minestom conserve
la direction et l’intensité autoritaires et valide les demandes liées aux portails.
Le SDK ne lui envoie pas une seconde accélération à chaque tick : celle-ci
s’ajouterait à la gravité déjà appliquée par son client.

Cela correspond au fonctionnement habituel de Minestom, où le jeu serveur doit
définir ses règles de mouvement et, s’il en a besoin, son anti-triche. La présence
du mod est déclarée lors de la négociation ; elle ne constitue pas une preuve
cryptographique qu’un client respecte la physique. Le SDK ne fournit pas une
validation complète de tous les déplacements ordinaires d’un joueur modifié.

**Pour une entité vivante passive, le SDK applique aussi une physique serveur.**
Il conserve les collisions balayées de Minestom, remplace temporairement son
accélération selon Y par une accélération dans le référentiel choisi et adapte
les frottements. Les boîtes debout sont transformées ; les calculs de portée
utilisent les boîtes transformées des poses. Les paramètres aérodynamiques et
la boîte d’origine sont restaurés lors de la réinitialisation.

Cette physique permet, par exemple, de faire tomber une entité contre un mur.
Elle n’ajoute ni objectifs ni pathfinding mural. Les contrôleurs qui gèrent des
poses particulières ou leurs propres déplacements doivent être adaptés au même
référentiel. Pour tester le support, utiliser `PortalGravity.isOnGround` ;
`Entity.isOnGround` conserve sa signification native liée à l’axe vertical.
Les dégâts, règles de saut, véhicules et groupes de passagers restent gérés par
l’application Minestom. Les entités non vivantes ne disposent pas des attributs
du mod et conservent une gravité normale dans cette API.

## Clients sans les mods requis

| Client | Connexion et rendu | Changement de gravité |
| --- | --- | --- |
| Immersive Portals + Gravity Changer | Portails, vues distantes et négociation complète. | Six directions et intensité prises en charge. |
| Immersive Portals seul | Connexion et portails ordinaires disponibles. | Aucune gravité non standard autorisée pour ce joueur. |
| Minecraft sans Immersive Portals | Peut rejoindre le serveur si l’application l’autorise ; ne négocie pas le pont et ne voit pas les portails du SDK. | Le SDK ne lui attribue aucune gravité modifiée. |

Deux protections existent déjà pour un client Immersive Portals sans Gravity Changer :

- `portals.gravity().set(player, étatNonStandard)` lève une exception si la
  capacité n’a pas été négociée. Le serveur ne suppose donc pas que tous les
  joueurs disposent du mod.
- Une demande de traversée qui aboutirait à un état non standard est refusée
  côté serveur. Le client reçoit l’état autoritaire normal et sa correction.

Une rotation de portail qui laisse effectivement le joueur en gravité normale
reste autorisée ; la présence de l’option `changesGravity` ne suffit pas à elle
seule à interdire le passage.

**Le refus intervient après une éventuelle prédiction cliente.** Le test réel a
montré un bref passage visuel dans la destination, un message indiquant l’absence
de Gravity Changer, puis un retour dans le monde source par correction serveur.
La caméra et le corps ne peuvent pas acquérir durablement une gravité absente
du client. Le test se termine par `PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK`.

Ce refus ne crée pas un mur physique à la place du portail. Un client vanilla
ne voit pas l’ouverture et peut continuer à marcher dans le monde source selon
la géométrie normale. L’intégration ne télécharge pas automatiquement de mods
et ne déconnecte pas automatiquement ces joueurs.
De même, `PortalService.authorization(...)` contrôle l’accès au pont ; ce n’est
pas une règle de déconnexion de Minecraft.

Une autre limite concerne les spectateurs : un client sans Gravity Changer ne
reproduit pas correctement la posture ou la boîte d’une autre entité soumise à
une gravité non standard. Le simple fait de conserver sa propre gravité normale
ne rend donc pas toute une scène de gravité compatible avec un client vanilla.

Pour un serveur entièrement fondé sur cette mécanique, l’application doit
réserver l’accès aux mondes concernés aux clients compatibles, ou refuser la
connexion après un délai de négociation. Pour un serveur mixte, prévoir un lobby
normal et des accès conditionnels. Le contrôle doit aussi couvrir les autres
téléportations et commandes du jeu serveur ; le SDK protège les traversées qu’il
gère, pas tous les moyens de changer de monde de l’application.

## API et synchronisation

```java
if (portals.supportsGravity(player)) {
    portals.gravity().set(player, new GravityState(GravityDirection.NORTH, 12));
}

PortalOptions options = PortalOptions.builder().changesGravity(true).build();
handle.update(builder -> builder.extraNbt(options.encode()));
```

`supportsGravity` devient significatif après la négociation du pont, pas dès
l’événement initial de connexion. Un client sans Immersive Portals ne négocie
jamais cette capacité. Les mutations d’entités doivent s’effectuer sur leur
thread Minestom.

Le protocole du pont est passé en **version 2**. Le mod Immersive Portals et le
SDK doivent être mis à jour ensemble. Les messages de gravité identifient le
monde, l’identifiant numérique et l’UUID de l’entité pour éviter d’appliquer un
état à une entité dont l’identifiant a été réutilisé. Les caches sont bornés et
tolèrent l’arrivée de l’état avant l’apparition de l’entité.

La désactivation d’une session remet la gravité de son joueur à l’état normal.
Les états reçus du pont sont nettoyés côté client. Fermer le `PortalService`
libère ses tâches et restaure les paramètres des entités qu’il gérait.

## Vérifications et portée du résultat

Les vérifications exécutées comprennent les six directions, leur intensité,
les yeux, la caméra, la traversée, les boîtes du corps, la conservation de la
vitesse et de l’échelle, l’annulation d’un transfert, une chute contre un mur,
l’entité distante, la correction après une demande invalide et l’ouverture d’un
coffre à travers un portail depuis un mur. Le client sans Gravity Changer a aussi
été testé contre le serveur Minestom.

Les essais utilisent de véritables clients OpenGL/Vulkan dans le cloud, avec
Mesa llvmpipe. Ils confirment ces scénarios, sans constituer une garantie pour
tous les GPU, shaderpacks, contrôleurs d’entités ou modes de jeu.

La [documentation de l’intégration](gravity-26.2.md) fournit les versions exactes,
les sources du mod étudiées, les commandes de reproduction et les exemples.

## Repères dans le code

| Fichier | Rôle |
| --- | --- |
| [`PortalGravity`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalGravity.java) | État, yeux, regard, boîtes et physique passive. |
| [`PortalService`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalService.java) | Négociation, synchronisation, validation et corrections. |
| [`PortalEntityTeleportation`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalEntityTeleportation.java) | Transformation et transfert d’entités, restauration en cas d’échec. |
| [`BridgeClient`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeClient.java) et [`BridgeGravity`](../src/main/java/qouteall/imm_ptl/core/network/bridge/BridgeGravity.java) | Réception du protocole et application des états sur le client. |
| [`OnGravityChangerPresent`](../src/main/java/qouteall/imm_ptl/core/compat/gravity/OnGravityChangerPresent.java) | Adaptateur vers les attributs et transformations du mod de gravité. |
