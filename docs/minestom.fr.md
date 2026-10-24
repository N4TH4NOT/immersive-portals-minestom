# Intégration Minestom

[English](minestom.md) · [Français](minestom.fr.md)

## Dépendance et cycle de vie

Utiliser un composite de ce dépôt, initialisé avec son sous-module de gravité :

```groovy
// settings.gradle
includeBuild('../immersive-portals-minestom')
// build.gradle
repositories { mavenCentral() }
dependencies {
    implementation 'io.github.n4th4not:minestom:7.0.0-alpha.1'
}
```

Le SDK réexporte ses bibliothèques de protocole et de gravité. Utiliser la version Minestom du [build de l’exemple](../minestom-example/build.gradle). Pour Maven local, publier les modules de gravité avant `:portal-protocol:publishToMavenLocal :minestom:publishToMavenLocal`.

Créer `PortalService` après `MinecraftServer.init()`. Les mondes doivent avoir des clés distinctes et leurs types de dimension doivent figurer dans les registres de configuration du client.

```java
PortalService portals = new PortalService();
PortalHandle handle = portals.create(lobby, gallery, p -> p
    .origin(new Vec3(0, 72, 0)).destination(new Vec3(20, 72, 0)).size(4, 4));
handle.update(p -> p.size(6, 4));
```

Les origines désignent le centre des ouvertures. `axisW × axisH` définit la normale avant ; la traversée va de l’avant vers l’arrière. Créer explicitement le portail inverse. Fermer les handles supprimés et le service à l’arrêt. Voir [ExampleServer](../minestom-example/src/main/java/io/github/n4th4not/immersiveportals/example/ExampleServer.java) pour l’initialisation complète.

## Options et traversées

[`PortalOptions`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalOptions.java) encode formes et comportements dans `extraNbt`. La géométrie de `PortalDefinition` prévaut sur les champs NBT natifs équivalents. Le client peut afficher les pilotes d’animation natifs ; le SDK ne les exécute pas : actualiser la géométrie autoritaire des portails mobiles.

```java
if (handle.crosses(previousEye, currentEye)) {
    handle.teleport(entity);
}
```

Les transferts explicites ne nécessitent pas de contact avec l’ouverture. Ils transforment yeux, regard, vitesse et échelle en préservant les modificateurs externes. Détacher les passagers avant les transferts individuels ; un échec restaure l’état d’échelle/gravité pris en charge.

## Interactions et paquets distants

```java
portals.onRemoteInteraction(event -> {
    // Appliquer les règles via event.instance(), blockPosition(), entity() et packet().
});
portals.sendWorldPacket(gallery, soundPacket);
```

Le service valide chemin, portée, visibilité et obstacles. Le callback gère le gameplay et la validité des conteneurs. L’état du bloc et l’acquittement de séquence résolvent la prédiction même si le callback ne fait rien.

`sendWorldPacket` transmet les paquets locaux à un monde pris en charge aux vues distantes uniquement ; les spectateurs du monde courant nécessitent les envois Minestom habituels de l’application. Les particules personnalisées exigent un enregistrement et un encodage client correspondants.

Utiliser `authorization` et `visibility` pour restreindre le pont. Les limites et formats figurent dans [`PortalService`](../minestom/src/main/java/io/github/n4th4not/immersiveportals/minestom/PortalService.java) et [`BridgeCodec`](../portal-protocol/src/main/java/io/github/n4th4not/immersiveportals/protocol/BridgeCodec.java). Les pairs du pont v2 doivent cibler le même protocole Minecraft.

Voir l’[intégration de gravité](gravity-repository.fr.md), le [comportement réseau](network-audit-26.2.fr.md) et les [essais en jeu](port-26.2.fr.md).
