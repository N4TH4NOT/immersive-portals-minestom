# Validation de compilation et en jeu

[English](port-26.2.md) · [Français](port-26.2.fr.md)

```sh
git submodule update --init --recursive
./gradlew build validateAccessWidener -PportalSmoke compileSmokeTestJava
```

La référence du 24 octobre passe 56 tests JUnit sur les deux dépôts. Les classes des scénarios optionnels sont exclues du mod distribué. Prévoir un affichage graphique et un pilote OpenGL/Vulkan fonctionnels ; mondes de développement et captures figurent sous `run/smoke-*`.

## Fabric

Lancer serveur et client dans deux terminaux ; configurer d’abord l’EULA du serveur local.

```sh
./gradlew -PportalSmoke runServer
./gradlew -PportalSmoke runClient --args='--graphicsBackend vulkan --username PortalValidation'
```

Pour les ressources/datapacks du serveur intégré, remplacer la commande cliente par `./gradlew -PportalSmoke -PportalSmokeSingleplayer runClient`. Utiliser le nom `ScaledTest` pour les vérifications de rotation/échelle sur le serveur dédié.

## Minestom

```sh
./gradlew :minestom-example:run
./gradlew -PportalSmoke -PportalSmokeAddress=127.0.0.1:25566 \
  -PportalSmokeTarget=demo:gallery -PportalSmokeInteraction=true \
  runClient --args='--graphicsBackend vulkan --username PortalValidation'
```

Attendre `PORTAL_SMOKE_COMPLETE`, `MINESTOM_REMOTE_CHEST_OK` et le changement de monde confirmé par le serveur. Ajouter `-PportalSmokeInteractions=true` aux deux commandes pour les callbacks de blocs/entités à travers plusieurs portails et les prédictions d’actions refusées.

## Gravité

```sh
./gradlew :minestom-example:run -PportalSmokeGravity
./gradlew -PportalSmoke -PportalSmokeGravity -PportalGravitySmoke=true \
  -PportalSmokeAddress=127.0.0.1:25566 -PportalSmokeTarget=demo:gallery \
  runClient --args='--graphicsBackend vulkan --username GravityTest'
```

Attendre `PORTAL_GRAVITY_COMPLETE` : six directions, traversée, entités et interaction distantes. Conserver le serveur, retirer `-PportalSmokeGravity` côté client et utiliser le nom `GravityNone` pour obtenir `PORTAL_GRAVITY_UNSUPPORTED_CLIENT_OK`.

## Rendu

Ajouter `-PportalRenderSmoke=true` à la commande cliente Fabric et utiliser le nom `OcclusionTest`. Le scénario vérifie mur masquant/révélé, politiques de prédiction, modes et rechargement ; attendre `PORTAL_RENDER_COMPLETE`.

Utiliser `opengl` avec `-PportalSmokeSodium` ou `-PportalSmokeIris` pour ces intégrations. Le drapeau Iris prépare le shaderpack de test. Les [choix de rendu](rendering-26.2.fr.md) expliquent le comportement attendu.

Les essais enregistrés utilisent Xvfb/Mesa llvmpipe, notamment Vulkan natif et OpenGL avec Sodium/Iris. Ils couvrent les scènes exercées, pas tous les GPU ou shaderpacks. Voir les [constats de migration](migration-audit-26.2.fr.md) et les [constats réseau](network-audit-26.2.fr.md).
