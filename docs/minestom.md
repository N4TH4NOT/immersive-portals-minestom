# API Minestom 26.2

Java 25 et Minestom `2026.10.07-26.2` sont requis. Les artefacts du projet sont
`io.github.n4th4not:minestom:7.0.0-alpha.1` et
`io.github.n4th4not:portal-protocol:7.0.0-alpha.1`. Ils ne sont pas publiés sur un
registre distant par cette modification. Pour une consommation locale :

```sh
./gradlew :portal-protocol:publishToMavenLocal :minestom:publishToMavenLocal
```

```groovy
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation 'net.minestom:minestom:2026.10.07-26.2'
    implementation 'io.github.n4th4not:minestom:7.0.0-alpha.1'
}
```

Le module Minestom expose le protocole transitivement. Il n’embarque pas une
seconde copie du serveur Minestom.

## Créer et modifier un portail

Créer un `PortalService` après `MinecraftServer.init()`. Chaque instance doit
posséder une clé de dimension unique, par exemple `demo:lobby` et `demo:gallery`.
Le type de dimension doit être présent dans les registres envoyés au client
pendant la configuration de la connexion.

```java
import io.github.n4th4not.immersiveportals.minestom.*;
import io.github.n4th4not.immersiveportals.protocol.*;

PortalService portals = new PortalService();
PortalHandle handle = portals.create(lobby, gallery, builder -> builder
    .origin(new Vec3(0, 72, 0))
    .destination(new Vec3(20, 72, 0))
    .size(4, 4)
    .scale(0.5)
    .rotation(Quaternion.axisAngle(new Vec3(0, 1, 0), Math.PI / 2)));

handle.update(builder -> builder.size(6, 4));
PortalDefinition current = handle.definition();
handle.close(); // supprime le portail et le synchronise aux clients
```

Les coordonnées `origin` et `destination` désignent le centre de l’ouverture.
Les axes unitaires orthogonaux `axisW` et `axisH` définissent sa largeur et sa
hauteur ; leur produit vectoriel définit la face avant. La traversée s’effectue
de la face avant vers l’arrière. La rotation est une rotation globale des
vecteurs ; elle ne crée pas implicitement le portail inverse.

Créer le côté inverse séparément, avec les axes, destination, rotation inverse
et échelle réciproque adaptés. L’exemple exécutable montre deux portails droits
réciproques. Utiliser `registerWorld(instance, seaLevel)` pour renseigner un
niveau de mer différent de 63.

## Formes et options natives

```java
PortalOptions options = PortalOptions.builder()
    .triangles(-1, -1, 1, -1, 0, 1)
    .interactable(true)
    .changesScale(true)
    .crossPortalCollision(true)
    .build();
handle.update(builder -> builder.extraNbt(options.encode()));
```

Les triangles utilisent des coordonnées normalisées dans `[-1, 1]`, six nombres
par triangle. Les triangles dégénérés sont rejetés. `box(thickness, outward)`
définit un portail en volume, avec entrée par l’extérieur ou sortie depuis
l’intérieur. Les intersections serveur utilisent la forme réelle.

`visible`, `teleportable`, `renderPlayer` et `fuseView` règlent les autres
comportements courants. `nativeOptions(CompoundBinaryTag)` permet d’ajouter la
NBT native, notamment les animations. La géométrie de `PortalDefinition` garde
priorité sur les champs géométriques homonymes de cette NBT. Les données sont
copiées ; la sérialisation ne modifie pas les objets publiés.

## Connexions, vues et traversées

Le service négocie automatiquement le protocole avec le mod à la première
apparition du joueur. Un client Minecraft sans le mod peut continuer à jouer,
mais ne reçoit pas les vues de portail. `isConnected(player)` indique si la
négociation a abouti. `authorization(predicate)` et `visibility(predicate)`
contrôlent respectivement l’accès au service et les portails visibles.

Les vues distantes synchronisent les chunks, les blocs, les entités, leurs
positions, métadonnées, attributs et équipements. Utiliser
`sendWorldPacket(instance, packet)` pour les événements produits par le jeu du
serveur, par exemple sons, particules et changements spécifiques de block entity.
Le paquet est encodé avec les registres du serveur et redirigé dans le bon monde
client ; seuls les paquets locaux à un monde sont acceptés par le mod.

Les demandes du client sont traitées sur les ticks du service. Le serveur vérifie
la position, le sens et l’ouverture traversée, les obstacles et la destination.
Il applique rotation, vitesse et attribut `SCALE`, en préservant la valeur de base
et les modificateurs des autres systèmes. Une demande de traversée refusée
entraîne une correction de la prédiction cliente.

La bibliothèque gère les traversées demandées par les joueurs. Les règles de jeu
Minestom concernant les autres entités, leurs déplacements autonomes, les
véhicules ou les inventaires restent sous le contrôle de l’application serveur.
Le serveur Fabric conserve son gestionnaire complet de téléportation d’entités.

## Interactions distantes

```java
portals.onRemoteInteraction(event -> {
    // Joueur, instance destination, chemin de portails et paquet Minestom natif.
    event.blockPosition().ifPresent(pos -> {
        var block = event.instance().getBlock(pos);
        // Appliquer les règles de jeu : protection, casse, placement, conteneur…
    });
});
```

Le service vérifie les portails du chemin, leur visibilité, la portée, les
obstacles et l’intersection avec le bloc ou l’entité désigné. Il ne remplace pas
les règles de jeu de Minestom : le callback décide de l’action. Pour un conteneur,
l’application conserve aussi la responsabilité de sa validité pendant son
ouverture et de sa fermeture si le bloc disparaît ou devient inaccessible.

L’exemple traite la casse en créatif et l’ouverture d’un coffre distant. Le mod
transmet les interactions de blocs à travers les portails ; le validateur du SDK
accepte aussi les paquets d’interaction d’entités fournis par un client compatible.

## Limites et nettoyage

Le protocole version 1 cible exclusivement Minecraft 26.2 / protocole 776.
Limites actuelles : 64 mondes enregistrés, 4 096 portails, huit vues distantes par
joueur, trois niveaux de recherche, 49 chunks et 256 entités par vue. La taille
maximale d’un paquet reconstitué est 2 Mio ; les fragments et la NBT sont bornés.
Les fragments incohérents, les index invalides et les coordonnées non finies sont
rejetés.

Les définitions et collections retournées sont immuables. Appeler `close()` sur
le service à l’arrêt du serveur pour annuler sa tâche, désinscrire ses événements
et nettoyer les sessions. Fermer les `PortalHandle` pour retirer les portails
qui ne sont plus nécessaires. Voir le serveur complet dans
[`minestom-example`](../minestom-example/src/main/java/io/github/n4th4not/immersiveportals/example/ExampleServer.java).
