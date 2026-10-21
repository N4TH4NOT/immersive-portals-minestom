# Gravité : Fabric et Minestom 26.2

L’intégration optionnelle cible **Gravity Changer Unofficial Port
1.5.2-beta.5-mc26.2**, de NightEpiphany :

- [Publication Modrinth](https://modrinth.com/mod/gravity-changer-unofficial-port/version/vpktEkWn).
- [Sources examinées, branche 26.2](https://github.com/NightEpiphany/gravity-changer-1.21.1/tree/de77cc3bf140343273c9de76d3fa18375949d0df).

Ce port utilise les attributs `gravity_changer:gravity_direction` et
`gravity_changer:gravity_strength`. La direction possède six valeurs : bas,
haut, nord, sud, ouest et est. L’intensité par défaut est `9.8`, dans l’intervalle
`[5, 15]`. Ce nombre multiplie l’accélération native par `intensité / 9.8`.

## Client et serveur Fabric

Installer le JAR de Gravity Changer en plus d’Immersive Portals. Le mod de
gravité conserve ses dépendances Fabric API et Cloth Config. Sans ce JAR,
Immersive Portals reste utilisable avec la gravité normale.

L’adaptateur utilise les attributs et transformations de ce port : position des
yeux, direction locale, caméra, collisions et traversées qui changent la gravité.
Les vitesses de cette version de Gravity Changer sont déjà exprimées dans les
coordonnées du monde et ne reçoivent aucune seconde rotation de référentiel.
L’animation de gravité est remise immédiatement dans son nouvel état pendant
une traversée, pour laisser l’interpolation du portail gérer la continuité.
Les changements ordinaires de gravité conservent l’animation du mod.

Le contrôle de pose du joueur conserve la méthode native, permettant aux deux
mods d’appliquer leurs opérations de collision. Les caméras des vues distantes
reconstruisent leurs axes après la composition de toutes les transformations.

Activer `teleportChangesGravity` sur le portail pour que sa rotation transforme
la direction de gravité. La direction cardinale la plus proche est retenue ;
l’intensité reste inchangée. Sur Fabric, cela s’applique aux joueurs et aux
entités vivantes téléportées par le gestionnaire du mod.

## Bibliothèque Minestom

Le serveur Minestom n’exécute pas de JAR Fabric. Le SDK négocie la capacité du
client et transmet des états par son propre protocole. Il ne dépend d’aucun
numéro d’attribut ou de bloc ajouté au registre du client.

```java
import io.github.n4th4not.immersiveportals.protocol.*;

// Pour un joueur : attendre que la négociation ait abouti.
if (portals.supportsGravity(player)) {
    portals.gravity().set(player, new GravityState(GravityDirection.NORTH, 12));
}

portals.gravity().set(armorStand, GravityDirection.EAST);
GravityState state = portals.gravity().get(armorStand);
Vec3 eyes = portals.gravity().eyePosition(armorStand);
Vec3 look = portals.gravity().lookDirection(armorStand);
boolean grounded = portals.gravity().isOnGround(armorStand);
portals.gravity().reset(armorStand);
```

Modifier ces états depuis le thread de l’entité, comme les autres mutations
Minestom. L’API accepte les `LivingEntity`, correspondant aux entités disposant
des attributs du mod de gravité. Les objets non vivants conservent leur gravité
normale. Une affectation non standard à un joueur sans capacité négociée est
refusée explicitement.

```java
byte[] options = PortalOptions.builder().changesGravity(true).build().encode();
handle.update(builder -> builder.extraNbt(options));
handle.teleport(armorStand);
```

Les traversées et interactions utilisent les yeux et le regard dans le bon
référentiel. La position finale des pieds découle de la position transformée des
yeux, de la nouvelle gravité et de l’échelle. Une traversée refusée rétablit
ensemble position, orientation, vitesse, échelle et gravité du joueur. Une
annulation du transfert serveur restaure aussi l’échelle et la gravité de
l’entité. Sans `.changesGravity(true)`, le portail conserve sa direction de
gravité actuelle. L’option absente vaut `false`, indépendamment des préférences
locales du client.

Les entités visibles dans le monde courant et dans les vues distantes reçoivent
leur direction et leur intensité. Les messages comportent le monde, l’identifiant
numérique et l’UUID de l’entité ; ils peuvent attendre son apparition sans
s’appliquer à un identifiant recyclé. Les caches et les messages restent bornés.
Les états sont rafraîchis après un changement, une apparition ou périodiquement.

Pour les entités passives, le service conserve les collisions balayées de
Minestom et applique accélération et frottement dans le référentiel de gravité.
Il prend temporairement en charge les paramètres aérodynamiques et les boîtes
debout des entités concernées, puis les restaure à la réinitialisation ou à la
fermeture du service. Les validations de portée utilisent aussi les boîtes des
poses transformées. `PortalGravity.isOnGround` teste le support dans la direction
de gravité ; `Entity.isOnGround` reste l’indicateur vertical natif de Minestom.
Les poses non debout d’entités passives peuvent nécessiter une adaptation de leur
contrôleur physique, car Minestom substitue lui-même certaines boîtes de pose.

Les déplacements des joueurs restent simulés par leur client, comme avec
Minestom habituellement. Le service ne leur applique pas une seconde accélération
par des paquets de vitesse. Les objectifs, le pathfinding sur les murs, les règles
de dégâts et les groupes de véhicules/passagers restent sous le contrôle du jeu
serveur. Le SDK fournit la gravité et la physique passive ; il ne fournit pas une
copie du moteur de jeu Fabric.

## Validation reproductible

Utiliser Java 25 et l’environnement graphique décrit dans `port-26.2.md`.
Dans deux terminaux, pour Minestom :

```sh
./gradlew :minestom-example:run -PportalSmokeGravity

./gradlew -PportalSmoke -PportalSmokeGravity -PportalGravitySmoke=true \
  -PportalSmokeAddress=127.0.0.1:25566 -PportalSmokeTarget=demo:gallery \
  runClient --args='--graphicsBackend vulkan --username GravityTest'
```

Pour Fabric, utiliser `./gradlew -PportalSmoke -PportalSmokeGravity runServer`
puis la commande cliente sans les deux propriétés d’adresse et de destination.
Remplacer `vulkan` par `opengl` pour l’autre backend.

Le scénario contrôle les six directions, l’intensité, les yeux, la caméra, une
traversée qui fait pivoter la gravité et les boîtes du corps. Avec Minestom, il
contrôle également l’entité distante avant la traversée, la correction après
refus et l’ouverture d’un coffre à travers le portail depuis un mur. Les captures
`gravity-*.png` sont écrites dans `run/smoke-client/screenshots/`.

Pour tester un client dépourvu de Gravity Changer, conserver le serveur Minestom
et retirer `-PportalSmokeGravity` de la commande cliente, avec le nom
`GravityNone`. Le serveur doit refuser la traversée qui exige une autre gravité,
et le client doit afficher `PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK`.

Les tests unitaires comparent les six transformations au JAR publié, contrôlent
le protocole, les traversées, les annulations et la chute contre un mur latéral.
Le scénario graphique a aussi été exécuté avec Sodium et Iris sous OpenGL, avec
le shaderpack de validation actif.
Les essais graphiques cloud utilisent Mesa llvmpipe ; ils ne remplacent pas une
validation sur cartes graphiques physiques et sur tous les packs de shaders.

## Sources adaptées et licence

Les bases de transformation du module indépendant sont adaptées de
`RotationUtil.java`, sous licence MIT de NightEpiphany. La notice est conservée
dans [`licenses/GravityChanger-MIT.txt`](../licenses/GravityChanger-MIT.txt) et
incluse dans les JAR concernés. Le reste de l’intégration appelle l’API du mod,
qui reste une dépendance optionnelle distribuée séparément.
