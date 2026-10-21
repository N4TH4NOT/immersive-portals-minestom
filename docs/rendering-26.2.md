# Rendu et optimisations natives de 26.2

Le chemin principal utilise uniquement Blaze3D : les mêmes passes fonctionnent
avec les backends OpenGL et Vulkan de Minecraft. L’adaptateur Iris reste propre
au backend pris en charge par Iris et sert à accéder à ses cibles actives.

## Visibilité et masques

Chaque portail produit une couverture contre la profondeur opaque de la vue
parente, sans modifier cette profondeur. Le masque stocke la couverture et la
profondeur la plus proche ; un mélange `MAX` conserve la bonne face lorsque les
faces d’un portail en volume se superposent. Les fragments situés derrière le
plan de découpage parent sont rejetés, même avec le clamp de profondeur actif.

Une réduction GPU par groupes de 8 × 8 conserve tout pixel visible jusqu’à une
texture de 1 × 1. Seuls quatre octets sont copiés vers le CPU. Les résultats sont
séparés par portail, contexte de rendu et chemin de récursion. Un changement de
caméra, de maillage, de projection, de taille de cible ou un rechargement invalide
leur réutilisation. Une lecture absente ou trop ancienne laisse la vue visible.
Les vues proches de l’œil restent également conservatrices.

Avec `visibilityPrediction=true`, un résultat compatible des deux images
précédentes peut éviter l’extraction et le rendu du monde distant. Avec la
prédiction désactivée, une mesure de l’image courante est tentée, avec une attente
limitée à 5 ms ; si le GPU n’a pas terminé, la vue reste rendue. Les points de
synchronisation sont consultés sans attente pour récupérer les résultats prêts.
Au plus 128 lectures sont simultanément en attente. Les ressources sont libérées
au rechargement, à la déconnexion et après leur période d’inactivité.

## Modes distincts

| Mode | Composition |
| --- | --- |
| Normal / Iris normal | Un masque de stencil en texture conserve l’ouverture et sa profondeur ; la vue est composée par une passe couvrant l’écran et limitée par ce masque. |
| Compatibilité / Iris compatibilité | La vue est composée directement sur les triangles du portail, avec découpage explicite par le plan parent. |
| Debug | La première vue distante remplit l’écran pour examiner son contenu. |
| Aucun | Aucun monde de portail n’est rendu. |
| Iris expérimental | Les prépasses de visibilité sont regroupées avant le rendu des vues, avec au plus quatre masques conservés par groupe et par niveau. |

L’API native ne propose pas les opérations nécessaires à un stencil matériel
S8. Le mode normal conserve donc sa fonction d’isolation des fenêtres avec une
texture, sans appeler directement OpenGL ou Vulkan. Changer de stratégie ne
détruit pas les shaders Iris déjà extraits pour l’image courante. Un véritable
rechargement de pack nettoie aussi les contextes et les masques des vues distantes.

## Terrain et réglages

Le frustum natif conserve le filtre de portail lors des copies et déplacements.
Sodium reçoit un snapshot par viewport, utilisable depuis ses tâches de culling
asynchrones. Les branches d’octree sont rejetées uniquement lorsqu’elles sont
entièrement hors de la région visible ; leur centre ne suffit pas à les rejeter.
Les vues de portail actualisent leur frustum même lorsque les angles de caméra
ne changent pas, notamment pour les animations d’ouverture.

Les invalidations de sections, y compris les mises à jour de voisins et de
régions, sont propagées à chaque contexte du même monde avant une éventuelle
annulation par Sodium. Chaque contexte est activé pendant sa mise à jour.

| Réglage / commande de debug | Effet actuel |
| --- | --- |
| `visibilityPrediction` / `offset_occlusion_query` | Choisit la réutilisation asynchrone ou la mesure de l’image courante. |
| `advanced_frustum_culling_enable` / `advanced_frustum_culling_disable`, `super_advanced_frustum_culling` | Contrôlent les filtres géométriques des vues et, lorsque disponible, le filtrage extérieur derrière une ouverture. |
| `cull_sections_behind` | Rejette les volumes entièrement derrière le plan de découpage. |
| `enableClippingMechanism`, `front_clipping` | Contrôlent le découpage oblique et explicite des fragments des portails. |
| `useDepthClampForPortalRendering` / `depth_clamp_for_portal_rendering` | Active le clamp des sommets des ouvertures ; le plan parent reste respecté. |
| `portal_rendering_cave_culling` | Autorise le graphe de grottes pour les vues physiques compatibles. Les caméras tournées, mises à l’échelle, réfléchies, en volume ou placées dans un bloc opaque conservent un repli sans ce graphe, tout en gardant le frustum de portail et l’occlusion GPU. |
| `always_override_terrain_setup` | Force l’invalidation du graphe et l’actualisation du frustum. |
| `maxIdleChunkRendererNum` | Limite les contextes inactifs, évincés du plus ancien au plus récent ; l’éviction après 120 images reste appliquée. |
| `experimental_iris_portal_renderer`, `iris_stencil` | Sélectionnent les prépasses groupées ou forcent le mode à masques avec Iris. |

Les commandes `cache_gl_buffer`, `secondary_vertex_consumer`,
`cloud_optimization`, `shared_block_mesh_builder`, `another_stencil` ainsi que
les champs de configuration `sharedBlockMeshBufferOptimization` et
`saveMemoryInBufferPack` ont été retirés. Ils contrôlaient des mécanismes anciens
remplacés par la gestion native des tampons, des nuages et des cibles. Les clés
JSON anciennes sont ignorées à la lecture de la configuration.

## Vérification en jeu

Préparer l’environnement graphique décrit dans `port-26.2.md`, puis lancer le
serveur Fabric de validation dans un premier terminal :

```sh
./gradlew -PportalSmoke runServer
```

Dans un second terminal :

```sh
./gradlew -PportalSmoke -PportalRenderSmoke=true runClient \
  --args='--graphicsBackend vulkan --username OcclusionTest'
```

Utiliser `opengl` pour l’autre backend, ajouter `-PportalSmokeSodium` pour Sodium,
ou `-PportalSmokeIris` pour Sodium avec le shaderpack de validation actif sous
OpenGL. Les étapes attendues sont `PORTAL_RENDER_OCCLUDED_OK`,
`PORTAL_RENDER_REVEALED_OK`, `PORTAL_RENDER_NO_PREDICTION_OK`,
`PORTAL_RENDER_MODES_OK` et `PORTAL_RENDER_COMPLETE`.

Les captures `portal-occluded.png`, `portal-revealed.png`,
`portal-compatibility.png`, `portal-debug.png`, `portal-iris-batched.png` et
`portal-after-reload.png` se trouvent dans `run/smoke-client/screenshots/`.
Le scénario vérifie aussi l’occlusion avec prédiction puis avec une mesure de
l’image courante, les compteurs de frustum, les viewports Sodium et la limite des
lectures en attente. Il est exclu du JAR distribué.

Ces essais utilisent Mesa llvmpipe dans l’environnement cloud. La version reste
une alpha ; ils ne couvrent pas toutes les cartes graphiques et tous les packs.
