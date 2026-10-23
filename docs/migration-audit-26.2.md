# Revérification de la migration 1.21 → 26.2

Audit du **22 octobre 2026**, depuis `port-26.2` au commit
`ab71cf2c5cca43425c0bbbbc7785d3df2414eeb5`. La branche par défaut du dépôt,
`1.20.6`, n’est pas la branche du portage. La référence fonctionnelle est l’amont
`1.21`, commit `2b1d3546aa60c7d866adfcb7236fba136a8433cb`, qui cible Minecraft
1.21.1. Ses 69 commits depuis le fork 1.20.6 sont bien des ancêtres du portage.

## Corrections de cette revue

1. **Création de monde** : le callback du constructeur de `CreateWorldScreen`
   capturait encore les arguments 1.21. Le chargement de cet écran échouait avec
   `InvalidInjectionException` en 26.2. Le callback ne capture désormais que
   `CallbackInfo`, puisqu’il n’utilise aucun argument du constructeur.
2. **Objets** : ajout des définitions `assets/immersive_portals/items/*.json`
   pour la baguette, le bâton de commande et le bloc assistant. Les anciens
   modèles `models/item` sont conservés et référencés par le nouveau format.
3. **Génération réversible** : le gestionnaire validait la règle inverse sans
   l’enregistrer. Ce défaut existait déjà dans la base 1.21. La règle inverse
   valide est désormais chargée pour les trois familles de déclencheurs.
   Un test échoue avant le correctif et réussit après ; les règles à sens unique
   et les destinations inverses invalides restent exclues.
   Le hook de téléportation distingue aussi les changements de dimension des
   déplacements dans le même monde : `teleport(TeleportTransition)` couvre les
   deux cas en 26.2, contrairement à l’ancien `changeDimension`. Une téléportation
   locale ne lance plus une conversion de portail ni le retrait des trackers.
   Le chargement de chunks attend aussi la promotion de la sentinelle native
   `UNLOADED_LEVEL_CHUNK`, au lieu de la traiter comme un échec de chargement
   terminé et de libérer prématurément sa place dans la file limitée.
4. **Terrain et vues récursives** : les deltas de chunks de 26.2 utilisaient les
   buffers réutilisables du cache natif. Une autre extraction pouvait les vider
   avant le rendu de la vue parente ; les chunks déjà reçus avant l’initialisation
   du monde courant n’étaient pas tous réannoncés. Les notifications sont copiées
   par vue et les chunks initiaux sont repris. Le cache du mod transmet aussi les
   transitions vide/non vide sans la limite du stockage circulaire vanilla, ainsi
   que les déchargements et les remplacements de chunks. Deux tests couvrent
   l’indépendance des snapshots et les transitions de sections à coordonnées
   éloignées et négatives. Le scénario d’occlusion vérifie le résultat en jeu.
5. **Datapack d’exemple** : remplacement du format 6 par le format 107.1, chemin
   actuel du registre personnalisé, tags explicites avec `#`, remplacement de
   `bamboo_plantable_on` par `supports_bamboo`. Les sept règles ont des sources
   lisibles dans `examples/custom_portal_gen` et une archive reproductible.
   Le pack déclare lui-même ses trois dimensions avec les générateurs conservés,
   sans dépendre de l’enregistrement dynamique de DimLib.
6. **Documentation et CI** : retrait de l’affirmation erronée excluant Gravity
   Changer, compilation des scénarios de validation dans la CI et publication
   du datapack d’exemple parmi les artefacts.

## Périmètre technique contrôlé

L’inventaire couvre les sources et ressources suivies, les quatre modules,
les dépendances, les configurations de mixins, l’access widener, les shaders,
les exemples et la CI. La compilation seule ne vérifie ni les ressources
d’objets ni toutes les signatures des callbacks Mixin : les essais en jeu et
les vérifications de bytecode complètent donc le build.

| Domaine | État vérifié |
| --- | --- |
| Versions et outils | Minecraft 26.2, protocole 776, Java 25, Loom 1.19.2, Loader 0.20.1 et Fabric API 0.161.0+26.2 cohérents entre build, métadonnées et CI. |
| Mixins et accès | 145 mixins configurés ; vérification ASM de 186 sélecteurs de méthodes, 154 shadows, 48 accesseurs/invokers et 62 points d’injection sur appels/champs contre les binaires ciblés. Contrôle complémentaire des arguments capturés par les callbacks `@Inject`. |
| Portails, formes, animations et NBT | Implémentations 1.21 conservées et adaptées aux identifiants et à `ValueInput`/`ValueOutput` ; géométrie et codec partagé testés, formes et animations présentes dans la scène Fabric. |
| Rendu | API Blaze3D 26.2, scènes et caméras isolées, matrices copiées, profondeur inversée, masques GPU, visibilité et libération des ressources ; les anciens fichiers OpenGL retirés ont des remplaçants natifs. |
| Mondes distants et réseau Fabric | Codecs liés aux registres de connexion, redirection par dimension, chunks/lumières/entités et attachements Fabric ; pas d’identifiants de paquet 1.21 réutilisés dans le pont Minestom. |
| Physique et traversées | Collisions, interpolation, téléportation et attribut natif `SCALE` ; tests Minestom des yeux, du regard, de la vitesse, de l’échelle, de la gravité et de la restauration après annulation. |
| Interactions et conteneurs | Contrôles de portée et de validité conservés ; coffre distant ouvert et fermé en jeu, traversée confirmée par le serveur Minestom. |
| Génération et dimensions | Générateurs et cadres adaptés aux API 26.2, borne positive de `FastBlockAccess` corrigée dans le portage antérieur, dimensions définies par datapack et empilement conservés. |
| Commandes, objets et interfaces | Permissions, composants d’objets, conversions DFU historiques, baguette, Cloth Config et empilement présents ; modèles d’objets effectivement chargés par le client. |
| Projectiles | Les anciens mixins de recherche du propriétaire et de perles interdimensionnelles sont remplacés par les mécanismes natifs : `EntityReference` recherche dans toutes les dimensions et la perle utilise `TeleportTransition`. |
| Minestom | Modules `portal-protocol`, `minestom` et exemple séparés ; protocole partagé indépendant du moteur Minecraft, service compilé contre `2026.10.07-26.2` et exécuté avec un client réel. |

Les callbacks n’ont pas tous besoin de capturer tous les arguments natifs :
Mixin accepte également une capture initiale plus courte. Le contrôle recherche
les types incompatibles, sans considérer ces préfixes valides comme des erreurs.

## Mods optionnels et exceptions

Les métadonnées Modrinth ont été interrogées pour **Minecraft 26.2 + Fabric**.
Les branches et configurations GitHub des intégrations indisponibles ont aussi
été examinées. Une absence de port 26.2 n’est pas une déclaration d’abandon global.

| Dépendance | Résultat au 22 octobre 2026 |
| --- | --- |
| Sodium | `mc26.2-0.9.2-fabric`, intégré. |
| Iris | `1.11.4+26.2-fabric`, intégré, shaderpack de validation actif sous OpenGL. |
| Gravity Changer Unofficial Port | `1.5.2-beta.5-mc26.2`, intégré côté Fabric et Minestom ; il ne fait plus partie des exceptions. |
| Cloth Config / Mod Menu | `26.2.155+fabric` / `20.0.3`, versions 26.2 disponibles. |
| Cardinal Components / Lithium | `8.0.1` / `mc26.2-0.25.3-fabric`, chargés pendant les essais. |
| [DimLib](https://github.com/iPortalTeam/DimLib/tree/1.21.3) | Pas de publication Fabric 26.2 ; branche examinée ciblant 1.21.3. L’ajout/suppression dynamique de dimensions reste exclu. Les dimensions de datapack fonctionnent sans cette intégration. |
| [Pehkui](https://github.com/Virtuoel/Pehkui) | Pas de publication Fabric 26.2 ; configuration Fabric examinée ciblant 1.21. La taille physique reste assurée par l’attribut natif, comme dans l’amont 1.21. |
| [Flywheel](https://github.com/Engine-Room/Flywheel) | Pas de publication Fabric 26.2 ; branche récente `26.1.2/dev`, sans branche 26.2 trouvée. L’ancien pont de rendu reste exclu. |
| [Porting Lib](https://github.com/Fabricators-of-Create/Porting-Lib/tree/1.21.11) | Pas de publication Fabric 26.2 ; configuration récente examinée ciblant 1.21.11. Ancienne intégration exclue. |
| [Requiem](https://github.com/Ladysnake/Requiem) | Pas de publication Fabric 26.2 ; configuration par défaut examinée ciblant 1.19.3. Ancienne intégration exclue. |

CCA 8.0.1 émet un avertissement sur sa propre cible `Bucketable` absente ; le
chargement observé continue. Les composants applicatifs de mods tiers n’ont pas
fait l’objet d’un scénario dédié. Les classes de compatibilité optionnelles
restent conditionnées à la présence de leur mod.

## Vérifications exécutées pendant cette revue

- `./gradlew build validateAccessWidener -PportalSmoke compileSmokeTestJava` :
  mod, sources, protocole, SDK, exemple exécutable, datapack et **36 tests JUnit**.
- Le test du gestionnaire reproduit d’abord l’absence de règle inverse ; après
  correction, les deux tests supplémentaires réussissent.
- Client Vulkan + serveur Minestom : chargement des trois modèles, mondes et
  entité distante équipée, écran Cloth Config, empilement, vue GUI, coffre
  distant et traversée. Marqueurs `PORTAL_SMOKE_COMPLETE`,
  `MINESTOM_REMOTE_CHEST_OK` et `MINESTOM_PLAYER_WORLD demo:gallery` observés.
- Client OpenGL avec Sodium, Iris, shaderpack `PortalSmoke`, Gravity Changer,
  CCA, Lithium et Mod Menu + Minestom : six directions, traversée avec changement
  de gravité et interaction distante. Marqueurs `PORTAL_GRAVITY_COMPLETE` et
  confirmations serveur observés.
- Client Vulkan sans Gravity Changer + Minestom : prédiction refusée et retour
  dans le monde source, marqueur `PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK`.
- Fabric intégré sous Vulkan : écran réel de création/empilement, chargement des
  trois dimensions et des sept règles, génération de chunks, sauvegarde/relecture
  des six formes de la scène et téléportation locale sans déclencheur de dimension.
  Les marqueurs `PORTAL_SMOKE_CREATE_WORLD_GUI_OK`,
  `PORTAL_SMOKE_DATAPACK_WORLDS_AND_NBT_OK` et
  `PORTAL_SMOKE_SAME_WORLD_TELEPORT_OK` sont observés.
- Rendu Fabric sous Vulkan : mur opaque, prédiction activée puis désactivée,
  retrait du mur, modes normal/compatibilité/debug/aucun et rechargement.
  `PORTAL_RENDER_COMPLETE` confirme 765 sondes, 94 vues masquées et 14 décisions
  d’occlusion synchrones ; les captures du mur puis du portail révélé sont examinées.
- Même parcours Fabric sous OpenGL avec Sodium, Iris, le shaderpack `PortalSmoke`,
  Gravity Changer, CCA, Lithium et Mod Menu : modes Iris normal, compatibilité,
  expérimental, masques forcés et rechargement réussis. Les compteurs confirment
  749 viewports Sodium adaptés, 130 vues masquées et 50 décisions synchrones.
  Iris signale l’absence d’un programme de remplacement pour une variante du
  compositeur personnalisé ; son chemin de repli reste utilisable dans ce pack,
  comme le montrent les captures et la fin du scénario.
- Codecs natifs 26.2 : métadonnées du datapack, deux types de dimension, trois
  générateurs et sept règles personnalisées décodés dans le runtime Fabric.
  Les tags de bloc utilisés par les exemples sont aussi vérifiés.
- Inspection du JAR distribué : trois définitions d’objets présentes ; aucun
  scénario `validation` ni ressource `migration-example` embarqué. L’archive
  d’exemple est comparée aux sources lisibles.

## Reproduire le parcours Fabric isolé

Avec Java 25, un affichage et un pilote GPU utilisables :

```sh
./gradlew -PportalSmoke -PportalSmokeSingleplayer -PportalRenderSmoke=true \
  runClient --args='--graphicsBackend vulkan --username OcclusionTest'
```

Ce parcours ouvre le véritable écran de création de monde et son interface
d’empilement, crée un monde neuf `PortalMigration-<horodatage>` sous
`run/smoke-client/saves`, avec le datapack construit depuis les sources, puis
exécute les contrôles Fabric. Il n’utilise aucun monde utilisateur existant.
Les commandes multijoueurs et de gravité sont décrites dans
[`port-26.2.md`](port-26.2.md) et [`gravity-26.2.md`](gravity-26.2.md).
Pour réutiliser uniquement un monde de vérification déjà créé, ajouter
`-PportalSmokeSingleplayerWorld=PortalMigration-<horodatage>` ; le scénario
effectue une sauvegarde si Minecraft la propose. Les autres noms sont refusés.

## Limites de la conclusion

Après les correctifs ci-dessus, aucun autre oubli de migration n’a été identifié
dans le périmètre contrôlé. Les intégrations sans port Fabric 26.2 restent les
exceptions décrites dans le tableau.

Ces vérifications distinguent le code porté, les comportements exécutés et les
intégrations optionnelles indisponibles. Elles ne constituent pas une preuve de
tous les états possibles du jeu, de tous les anciens mondes, des véhicules sous
gravité modifiée ou de toutes les combinaisons de mods. Les tests graphiques
utilisent Xvfb et Mesa llvmpipe : Vulkan 1.4.305 et OpenGL 4.5, Mesa 25.0.7.
La compatibilité de chaque carte physique et de chaque shaderpack reste à tester.
La version reste `7.0.0-alpha.1`.
