# Immersive Portals — Minecraft 26.2 / Minestom

Portage Fabric de la branche amont `1.21` (Minecraft 1.21.1, commit
`2b1d3546`), avec rendu par l’API GPU native de Minecraft, y compris son backend
Vulkan, et bibliothèque serveur Minestom. Version actuelle : **7.0.0-alpha.1**.

Les portails récursifs, formes personnalisées, miroirs, animations, collisions,
traversées, mise à l’échelle native, interactions distantes, génération de
portails, commandes, baguette, empilement des dimensions et vues GUI sont conservés.
Les anciennes implémentations OpenGL du moteur de rendu ont été remplacées.

| Composant | Version ciblée |
| --- | --- |
| Minecraft / protocole | 26.2 / 776 |
| Java | 25 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.161.0+26.2 |
| Cloth Config, inclus dans le JAR | 26.2.155 |
| Sodium, optionnel | 0.9.2 pour 26.2 |
| Iris, optionnel | 1.11.4 pour 26.2 |
| Gravity Changer Unofficial Port, optionnel | 1.5.2-beta.5-mc26.2 |
| Mod Menu, optionnel | 20.0.3 |
| Minestom | 2026.10.07-26.2 |

## Compiler et installer

Avec un JDK 25, après récupération du sous-module de gravité :

```sh
git submodule update --init --recursive
./gradlew build validateAccessWidener
```

Le mod est produit dans `build/libs/immersive-portals-7.0.0-alpha.1.jar`.
Installer ce JAR et Fabric API dans `mods/`, avec Fabric Loader 0.20.1.
Utiliser le JAR sans suffixe `-sources`. Le protocole commun et Cloth Config sont
embarqués, ainsi que l’adaptateur du [dépôt de gravité séparé](docs/gravity-repository.md)
et son API ; la bibliothèque Minestom n’est pas un mod Fabric.

La [gravité directionnelle](docs/gravity-26.2.md) s’intègre au client Fabric et
au SDK Minestom : six directions, intensité, traversées et interactions distantes.
Le JAR de Gravity Changer reste une installation optionnelle côté client ; un
serveur Fabric l’installe aussi, tandis que Minestom utilise l’API du SDK.

Pour préparer un environnement cloud Linux x86-64 avec téléchargement vérifié du
JDK, configuration du proxy Java et compilation :

```sh
./scripts/setup-cloud.sh
source .cloud/activate.sh
```

`--prepare-only` permet de préparer l’environnement sans lancer la compilation.
Le script réutilise `JAVA_HOME` lorsqu’il désigne Java 25. Les outils, caches et
paramètres de la machine restent hors du dépôt.

## Vulkan natif

Le rendu utilise les pipelines, textures, passes et commandes GPU de Minecraft.
Choisir Vulkan dans les options graphiques du jeu, ou utiliser
`--graphicsBackend vulkan` au lancement. Aucun VulkanMod n’est nécessaire.

Sodium et Iris ont leurs propres contraintes de backend. Les essais avec Iris
sont effectués sous OpenGL ; leur intégration utilise un adaptateur OpenGL isolé
pour les cibles de shaders appartenant à Iris. La compatibilité des shaders
complexes, notamment leurs effets temporels, dépend du pack.

## Bibliothèque Minestom

- `portal-protocol` : géométrie immuable, messages versionnés, codec borné et
  réassemblage des paquets ; aucune dépendance Minecraft ou Minestom.
- `minestom` : registre de portails, négociation client, vues distantes,
  synchronisation et validation des traversées et interactions.
- `minestom-example` : serveur exécutable avec deux instances, portails réciproques,
  entité équipée et coffre accessible à travers un portail.

```sh
./gradlew :minestom-example:run
```

L’exemple écoute sur **127.0.0.1:25566**, en authentification locale hors ligne.
Se connecter avec le client Fabric ci-dessus.

[Utiliser l’API Minestom](docs/minestom.md) ·
[État du portage et validation](docs/port-26.2.md) ·
[Comparaison des 69 commits amont](docs/upstream-1.21-review.md) ·
[Revérification de la migration](docs/migration-audit-26.2.md) ·
[Audit des paquets et doublons réseau](docs/network-audit-26.2.md)

## Datapack d’exemple

Les [sources du datapack](examples/custom_portal_gen/README.md) et l’archive
`example_custom_portal_gen.zip` ciblent le format de données 107.1 de Minecraft
26.2. `./gradlew examplePortalDatapack` reconstruit l’archive dans `build/examples/`.
Ce pack optionnel déclare ses dimensions par datapack, sans DimLib, et fournit
sept exemples de génération. L’installer à la création d’un monde.

## Intégrations exclues

Les intégrations dépendant de DimLib, Pehkui, Flywheel, Porting Lib et Requiem
restent écartées : aucune publication Fabric 26.2 n’a été trouvée sur Modrinth
lors de la revérification du 22 octobre 2026. Cela concerne notamment l’ajout
dynamique des anciennes dimensions alternatives. Cette absence de publication
ne signifie pas nécessairement que ces mods sont abandonnés.
Gravity Changer Unofficial Port dispose d’une version 26.2 et son intégration
Fabric/Minestom est disponible ; voir [la documentation de gravité](docs/gravity-26.2.md).
La taille des entités utilise l’attribut natif `SCALE` et reste disponible sans
Pehkui. Les générateurs de terrain et les dimensions déclarées par datapack sont
conservés, ainsi que les empilements entre dimensions existantes.

## Projet d’origine

Mod créé par qouteall et les contributeurs d’[Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod),
sous licence Apache-2.0. Ce dépôt maintient un portage distinct.
[Documentation historique](https://qouteall.fun/immptl/wiki/) — les API du moteur
et les versions des dépendances qui y sont décrites peuvent différer de 26.2.
