# Immersive Portals — Minecraft 26.2 / Minestom

[English](README.md) · [Français](README.fr.md)

Portage Fabric avec rendu OpenGL/Vulkan natif et SDK serveur Minestom. Le développement continue sur `port-26.2` ; les versions des dépendances figurent dans [gradle.properties](gradle.properties). Java 25 requis.

## Compiler et lancer

```sh
git submodule update --init --recursive
./gradlew build validateAccessWidener
./gradlew :minestom-example:run
```

Installer le JAR du mod produit dans `build/libs/` avec Fabric API. Cloth Config et l’adaptateur/API de gravité sont embarqués. La gravité directionnelle du joueur nécessite aussi [Gravity Changer](https://github.com/N4TH4NOT/gravity-changer/blob/main/README.fr.md#installation-et-utilisation-fabric). L’exemple Minestom écoute sur `127.0.0.1:25566` en mode hors ligne.

Utiliser `--graphicsBackend vulkan` de Minecraft pour Vulkan natif. Iris est testé sous OpenGL ; la compatibilité dépend du shaderpack. La préparation cloud est fournie par [scripts/setup-cloud.sh](scripts/setup-cloud.sh).

## Guides développeurs

- [API Minestom](docs/minestom.fr.md) et [intégration de la gravité](docs/gravity-repository.fr.md).
- [Validation de compilation et en jeu](docs/port-26.2.fr.md) et [choix de rendu](docs/rendering-26.2.fr.md).
- [Revue de l’amont 1.21](docs/upstream-1.21-review.fr.md), [constats de migration](docs/migration-audit-26.2.fr.md) et [audit réseau](docs/network-audit-26.2.fr.md).
- [Datapack de génération optionnel](examples/custom_portal_gen/README.fr.md).

Les intégrations DimLib, Pehkui, Flywheel, Porting Lib et Requiem restent exclues : aucune publication Fabric 26.2 correspondante n’a été trouvée lors de la revue d’octobre 2026. L’échelle native et les dimensions de datapack restent disponibles.

Basé sur [Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod) de qouteall et contributeurs, sous [Apache-2.0](LICENSE). Le [wiki amont](https://qouteall.fun/immptl/wiki/) décrit les versions antérieures du moteur.
