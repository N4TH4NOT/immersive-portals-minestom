# Portage 26.2 : périmètre et validation

Vérification du 20 octobre 2026. La base est la branche amont `1.21` au commit
`2b1d3546` : les 69 commits qui la séparent du fork 1.20.6 sont déjà intégrés dans
la branche de travail. Leur revue figure dans `upstream-1.21-review.md`.
Complément du 21 octobre : API de traversée explicite des entités Minestom et
interactions distantes de blocs/entités, y compris à travers plusieurs portails.

## Fonctions conservées et adaptations

| Domaine | Adaptation 26.2 |
| --- | --- |
| Portails, miroirs, formes planes et en volume | Maillages natifs et composition GPU avec profondeur inversée ; formes triangulées conservées. |
| Récursion, vues fusionnées, caméra | Contextes natifs isolés pour chaque vue ; découpage oblique, réflexion, rotation, changement d’échelle et troisième personne. |
| Entités visibles pendant la traversée | Extraction des états natifs et passes de corps découpées de part et d’autre du portail. |
| Ressources GPU | Projections copiées, restitution des états après chaque vue, rotation des tampons en fin d’image et éviction des contextes inutilisés. |
| Vulkan | API Blaze3D native de 26.2, sans appels OpenGL dans le chemin de rendu principal. |
| Sodium / Iris | Contextes de terrain et pipelines isolés ; cibles de shaders Iris adaptées et invalidées au rechargement. |
| Mondes distants | Chunks, lumières, entités, particules, attributs d’environnement, cycle de vie et rechargement des ressources. |
| Téléportation et collisions | Gestion native des joueurs, autres entités, véhicules, interpolation et collisions à travers les ouvertures. |
| Taille physique | Attribut Minecraft `SCALE`, modificateur `iportal:scaling`, préservation des autres modificateurs et de la position des yeux. |
| Réseau Fabric | Payloads et codecs 26.2 ; redirection par dimension et synchronisation des attachements Fabric. |
| Interactions et conteneurs | Portée à travers les portails, préservation des contrôles d’identité et de validité du bloc. |
| Génération, cadres, portails cassables | API de chunks et hauteur de monde 26.2 ; correction de la borne inclusive de `FastBlockAccess`. |
| Commandes, baguette et bâtons | Permissions, composants d’objets, NBT, rendu des guides et interfaces natives. |
| Empilement des dimensions | Mondes existants ou déclarés par datapack ; widgets et listes de sélection adaptés à 26.2. |
| Animations, sauvegardes et API GUI | Code d’origine conservé et adapté ; vues GUI avec options de ciel, brouillard et caméra respectées. |
| Minestom | Modules séparés de protocole et de service, négociation, streaming borné, validation serveur et exemple exécutable. |

Une partie des anciens fichiers de mixins OpenGL a été supprimée : leurs
responsabilités sont reprises par les contextes et passes GPU natifs. Les
anciennes variantes de renderer conservées dans l’API délèguent au moteur natif.

La parité des anciennes optimisations n'est pas complète : les requêtes
d'occlusion GPU et leur prédiction de visibilité n'ont pas d'équivalent dans ce
portage. Les anciens branchements du culling avancé propre au mod ont été
retirés. Les options historiques correspondantes peuvent encore être présentes
sans effet. Les variantes stencil et Iris ne constituent plus des algorithmes
de rendu distincts. Ces limites sont indépendantes des exclusions de mods
optionnels ci-dessous.

## Compatibilité optionnelle

| Mod | Version 26.2 examinée | Traitement |
| --- | --- | --- |
| Sodium | 0.9.2 | Rendu vérifié en jeu, également avec Iris. |
| Iris | 1.11.4 | Rendu vérifié sans pack puis avec le pack de validation fourni. |
| Cloth Config | 26.2.155 | Inclus ; ouverture de l’écran de configuration vérifiée. |
| Mod Menu | 20.0.3 | Chargé pendant les essais d’interface. |
| Cardinal Components API | 8.0.1 | Adaptateur de synchronisation conservé ; chargement client et serveur vérifié. |
| Lithium | 0.25.3 | Ancienne option inexistante supprimée ; exclusion `combined_heightmap_update` conservée. |
| DimLib | Aucune publication 26.2 trouvée | Enregistrement dynamique des dimensions alternatives exclu ; générateurs conservés pour les datapacks. |
| Gravity Changer / Gravity API Fork | Aucune publication 26.2 trouvée | Transformation de gravité exclue. Les rotations géométriques restent disponibles. |
| Pehkui | Aucune publication 26.2 trouvée | Aucune dépendance ; la taille physique fonctionne par l’attribut natif. |
| Flywheel / Porting Lib / Requiem | Aucune publication 26.2 trouvée | Intégrations historiques exclues. |

Les versions ont été vérifiées dans les métadonnées Fabric et Modrinth. Une
absence de publication 26.2 n’est pas une affirmation d’abandon définitif du mod.
CCA 8.0.1 émet lui-même un avertissement de cible `Bucketable` absente ; cela
n’empêche pas le démarrage observé. Les composants applicatifs propres aux mods
utilisateurs ne font pas partie du scénario de validation.

## Vérifications exécutées

- `./gradlew build validateAccessWidener` : compilation, JAR du mod, sources,
  modules Minestom/protocole, distribution de l’exemple et tests JUnit.
- Contrôle des cibles, champs et signatures des mixins contre les classes 26.2,
  complété par le chargement réel du client et du serveur.
- Serveur Fabric dédié + client OpenGL et Vulkan : monde distant, traversée,
  miroir, boîte, triangle animé, rotation/échelle et vue GUI.
- Serveur Minestom réel + client Vulkan : négociation de deux mondes, chunks,
  entité équipée, coffre distant, ouverture/fermeture d’inventaire, traversée
  confirmée côté serveur et reconnexion avec le même compte local.
- Scénario Minestom à trois mondes : clics gauche/droit sur une pierre à travers
  deux portails, callback sans action et restauration de la prédiction cliente ;
  interactions d'une entité avec décalage de coordonnées entre les mondes,
  confirmées par les callbacks serveur.
- Transferts explicites d'entités Minestom : position des yeux, rotation,
  vitesse, échelle, modificateurs externes, même instance et refus de traversée
  couverts par quatre tests supplémentaires.
- Sodium + Iris avec pack GLSL actif : scénario de formes avancées, GUI,
  traversée et arrêt complet, sans réutilisation invalide des tampons GPU.
- Ouverture des interfaces Cloth Config et d’empilement des dimensions.
- Intersections géométriques, quaternion et échelle, limites du codec, NBT,
  fragments réordonnés/dupliqués, ouvertures triangulaires et portails en volume
  couverts par les tests unitaires.

Les tests graphiques utilisent Xvfb et Mesa llvmpipe, dont le véritable backend
Vulkan `1.4.305` de Mesa `25.0.7`, sur un environnement cloud sans GPU physique.
Les captures sont inspectées en plus des marqueurs du scénario. Ces essais ne
constituent pas une validation de toutes les cartes graphiques, de tous les
shaderpacks ni de toutes les combinaisons de mods. Le coût des scènes récursives
augmente avec le nombre de vues ; les limites de profondeur et de rendu restent
configurables. Cette livraison conserve donc la version alpha.

## Reproduire les essais en jeu

Les sources `src/smokeTest` sont activées uniquement avec `-PportalSmoke` et
n’entrent pas dans le JAR du mod distribué. Elles créent une scène locale et
pilotent un client de développement jusqu’à sa fermeture.

Préparer un affichage graphique et un pilote OpenGL/Vulkan utilisables. Le
serveur de test est limité à l’adresse de boucle locale, port 25567. Après avoir
accepté l’EULA Minecraft dans `run/smoke-server/eula.txt`, lancer :

```sh
./gradlew -PportalSmoke runServer
```

Dans un second terminal, avec la même configuration Java et graphique :

```sh
./gradlew -PportalSmoke runClient --args='--graphicsBackend vulkan --username PortalValidation'
# Rotation de 90° et taille physique divisée par deux :
./gradlew -PportalSmoke runClient --args='--graphicsBackend vulkan --username ScaledTest'
# Sodium + Iris ; le pack de validation est généré automatiquement :
./gradlew -PportalSmoke -PportalSmokeIris runClient --args='--graphicsBackend opengl --username PortalValidation'
```

Les marqueurs attendus incluent `PORTAL_SMOKE_CONFIG_GUI_OK`,
`PORTAL_SMOKE_DIM_STACK_GUI_OK`, `PORTAL_SMOKE_GUI_OK`, `PORTAL_SMOKE_RENDER_OK`,
`PORTAL_SMOKE_TELEPORT_OK` et `PORTAL_SMOKE_COMPLETE`. Le serveur confirme
`PORTAL_SMOKE_SERVER_TELEPORT_OK` ou `PORTAL_SMOKE_SERVER_SCALED_TELEPORT_OK`.
Les captures sont écrites dans `run/smoke-client/screenshots/`.

Pour Minestom, lancer `./gradlew :minestom-example:run`, puis :

```sh
./gradlew -PportalSmoke \
  -PportalSmokeAddress=127.0.0.1:25566 \
  -PportalSmokeTarget=demo:gallery \
  -PportalSmokeInteraction=true \
  runClient --args='--graphicsBackend vulkan --username PortalValidation'
```

Le client doit afficher `PORTAL_SMOKE_INTERACTION_OK`. Le serveur doit afficher
`MINESTOM_REMOTE_CHEST_OK`, puis `MINESTOM_PLAYER_WORLD demo:gallery`.
Répéter cette commande teste aussi la reconnexion.

Pour inclure CCA, Lithium ou Mod Menu dans ces essais, placer leurs JAR 26.2 dans
les dossiers `mods` des répertoires de développement concernés. Ne pas démarrer
deux serveurs de test sur le même port ni plusieurs clients dans le même
répertoire simultanément. Les tentatives d’authentification Mojang hors ligne
peuvent échouer dans cet environnement ; les serveurs de validation locaux
n’utilisent pas cette authentification.
