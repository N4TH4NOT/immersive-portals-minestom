# Comparaison du fork 1.20.6 et de l’amont 1.21

Audit effectué le 20 octobre 2026.

Ce document décrit la comparaison initiale. Les adaptations désormais réalisées
et leurs validations sont détaillées dans [le bilan du portage 26.2](port-26.2.md).

## Périmètre vérifié

- Fork : `origin/1.20.6`, commit `0dbdc1d493c3906943147668092a41fc67a49527`.
- Amont : `upstream/1.21`, commit `2b1d3546aa60c7d866adfcb7236fba136a8433cb`.
- Branche locale de travail : `port-26.2`, créée exactement depuis cet amont.
- La base 1.20.6 est un ancêtre direct de la cible : 69 commits supplémentaires,
  dont trois merges, et 167 fichiers modifiés ; 2 432 lignes ajoutées et 2 458 supprimées.

Le commit `2b1d3546` du 21 avril 2026 ajoute uniquement l’annonce d’arrêt de
maintenance dans le README. Les changements applicatifs de cette branche s’arrêtent
en novembre 2024. Sa configuration cible Minecraft **1.21.1**, pas 26.2.

Le dossier `modern/` du portage présenté dans l’ancien environnement n’est pas
présent dans ce checkout. Les conséquences pour cette architecture sont donc
identifiées à partir du travail décrit précédemment ; ce document ne prétend pas
avoir modifié ou recompilé ces sources absentes. Les correctifs amont décrits
ci-dessous sont déjà contenus dans `port-26.2` : aucun cherry-pick supplémentaire
n’est nécessaire pour les obtenir dans la base actuelle.

## Changements à préserver ou à adapter

| Sujet | Commits représentatifs | Changement constaté | Conséquence pour 26.2 et Minestom |
| --- | --- | --- | --- |
| Taille physique des entités | `874588b8`, `c148926d`, `cabd8b69` | Suppression de la dépendance Pehkui ; attribut Minecraft `SCALE`, modificateur permanent propre au portail et recalcul des dimensions. | Intégrer la taille des corps, la hauteur des yeux et les collisions via les attributs natifs. Préserver les autres modificateurs et la position des yeux lors de la traversée. |
| Temps d’interpolation | `89c35c2b` | Le calcul avant rendu utilise `getGameTimeDeltaPartialTick(true)` plutôt que la durée écoulée en ticks. Les animations sont mises à jour avant la téléportation. | Séparer temps de simulation et fraction d’interpolation. Tester les traversées et les portails animés sous plusieurs fréquences de rendu. |
| Matrice de projection | `f1e09897` | Copie défensive de la projection mise en cache et de celle rendue aux vues récursives. | Chaque vue doit posséder sa matrice avant découpage oblique ; ne pas modifier la projection partagée d’une autre vue. Conserver les conventions de profondeur du moteur 26.2. |
| Caméra et rendu dans une interface | `ec60bedc`, `5127ad56`, `6bbc5bfa` | Conservation de la transformation de caméra en troisième personne ; correction des coordonnées et dimensions du viewport ; réintroduction de signatures publiques. | Tester les vues à la troisième personne et les rendus dans une sous-zone d’interface, avec redimensionnement et échelle GUI. Voir aussi le problème des anciennes surcharges ci-dessous. |
| Conteneurs distants | `6bb58c52` | L’extension de portée intercepte uniquement `Player.canInteractWithBlock`, au lieu de remplacer un résultat final invalide de `Container.stillValidBlockEntity`. | La portée à travers un portail ne doit jamais réautoriser un conteneur détruit ou remplacé. Dans Minestom, vérifier aussi l’instance, le bloc et la validité du conteneur au moment de l’action. |
| Maillages et concurrence | `a9bd638e` | `Mesh2D.toTag()` ne compacte plus le maillage ; la compaction est faite pendant le calcul, avant publication. La boucle de resynchronisation du cache du portail est supprimée. | Sérialiser un état déjà publié sans le modifier. Construire les maillages hors de l’état partagé puis publier sur le fil propriétaire. |
| Chunks et données Fabric | `b7d6ac94` | Remplacement d’un overwrite par une injection annulable ; redirection couvrant le chunk et les données d’attachement Fabric associées. | Pour un serveur Fabric, vérifier la synchronisation complète des données du chunk avec les API 26.2. Ne pas recopier les classes `fabric.impl` 1.21.1 dans la bibliothèque Minestom. |
| Protocoles et identifiants | `c7ecf525` et adaptations 1.21 | Types de payload construits avec un identifiant explicite ; codec lié à `GameProtocols.CLIENTBOUND_TEMPLATE` ; adaptation des paquets de création d’entité. | Garder les identifiants avec leur namespace et des codecs liés aux registres de la connexion. Les identifiants de paquets 1.21.1 ne doivent pas être réutilisés pour le flux 26.2. |
| Génération et recherche des cadres | `3a5de6b9`, `253d77dc`, `1f292a9e`, `e1b75bf4` | Remplacement de la région de chunks tolérante par `FastBlockAccess` ; parcours proche-vers-lointain et adaptation du matching. | Préserver le fonctionnement des générateurs et leur validation sur le serveur. Tester les limites de région et les hauteurs négatives ; une erreur de borne subsiste dans cette base. |
| Portails cassables | `632789a8`, `83105dca` | Recherche différée du portail inverse quand son chunk n’est pas chargé ; essais limités et arrêt si le portail source est supprimé. | Ne pas conclure à une absence du portail inverse à partir d’un chunk indisponible ; nettoyer les deux côtés lorsque la destination devient accessible. |
| Anciens objets sauvegardés | `672fca43` | Conversion des tags de baguette et de bâton de commande vers leurs composants de données. | Conserver une migration dédiée aux anciens objets. Cela ne constitue pas à lui seul un import des portails, des mondes ou de toute leur NBT. |
| Ressources de rendu | `7fa7ab17` et suppression des groupes | `PortalRenderInfo` devient fermable ; son nettoyage de secours programme la libération sur le fil de rendu. | Libérer explicitement les ressources GPU des vues fermées. Un éventuel nettoyage de secours doit respecter le fil propriétaire et les durées de vie du moteur natif. |

### Décision révisée sur Pehkui

L’absence éventuelle de Pehkui pour Minecraft 26.2 **ne justifie pas de retirer la
mise à l’échelle des corps**. L’amont effectue déjà cette fonction sans Pehkui.
Le binaire Minestom `2026.10.07-26.2` conservé dans le cache expose également
`Attribute.SCALE`, ainsi que les attributs de portée d’interaction.

L’approche à reprendre est un modificateur propre au portail, multiplié à chaque
traversée, sans écraser la taille de base ni les modificateurs d’autres systèmes.
Le serveur reste l’autorité ; les dimensions, la position des yeux, les passagers
et la synchronisation client doivent être traités ensemble. Les méthodes natives
exactes et le rendu du corps restent à adapter et à tester en 26.2.

Source : [ScaleUtils.java](../src/main/java/qouteall/imm_ptl/core/ScaleUtils.java).

## Adaptations à ne pas transposer telles quelles

### Sodium, Iris et OpenGL

Les commits `1630b917`, `219db635`, `78643317`, `b50cbfbd`, `31473e9b`,
`e91964df` et `d3ab5417` constituent une suite d’essais, de revert puis de
réapplication. Leur résultat final vise Sodium 0.6.0 et Iris 1.8.0 pour 1.21.1.
Les premiers essais ne doivent pas être réintroduits en plus de l’état final.

Ces mixins dépendent de classes, de shaders et d’un rendu OpenGL spécifiques à
ces versions. Ils n’apportent pas un backend Vulkan natif. Pour 26.2, conserver
l’objectif de clipping et de rendu correct, puis adapter aux API du moteur et aux
versions réellement disponibles des mods.

Conformément à la demande, une intégration optionnelle peut être écartée si son
mod requis n’est pas disponible en 26.2. Les pins 1.21.1 de cet amont ne prouvent
ni cette disponibilité ni son absence. La fonction intrinsèque d’un portail ne
doit pas être écartée lorsqu’une API native la couvre, comme pour la taille.

### Groupement de portails

La série `0e34101a`, `7fa7ab17`, `4facf06f`, `a3291b1f` retire le groupement de
rendu et supprime `PortalGroup`. Ne pas reconstruire cette ancienne optimisation
pour obtenir la parité avec la branche cible. Les formes de portail 3D, y compris
`BoxPortalShape`, existaient déjà dans la base 1.20.6 : leur présence ne constitue
pas une nouvelle fonction ajoutée par ces commits.

Les fichiers des pilotes d’animation ont surtout reçu des adaptations
d’identifiants dans cette comparaison. Leur portage reste nécessaire pour la
parité, mais ces modifications ne constituent pas un nouveau moteur d’animation.

### Versions de dépendances

Loom 1.8-SNAPSHOT, Fabric API 0.109.0+1.21.1, Loader 0.16.2, les mappings et
DimLib `v1.1.0-mc1.21.1` servent à cette branche 1.21.1. Les recopier dans une
configuration 26.2 ne produirait pas un portage. Leurs rôles et les comportements
à conserver doivent être distingués de leurs versions et points d’injection.

## Deux problèmes supplémentaires constatés dans l’état final

### Région de recherche : borne supérieure incohérente

Dans [FastBlockAccess.java](../src/main/java/qouteall/imm_ptl/core/portal/nether_portal/FastBlockAccess.java),
la surcharge centrée calcule `upperCX = centerX + radius` et la même borne en Z,
puis les passe à une surcharge qui les traite comme **exclusives**.

Conséquences déterminées à partir des bornes et des boucles du code :

- un rayon de zéro produit zéro colonne au lieu de la colonne centrale ;
- un rayon de un produit un carré de 2×2 colonnes au lieu de 3×3 ;
- les bords positifs X et Z ne correspondent pas à la région parcourue par
  `ChunkLoader.foreachChunkPos`, qui inclut les deux extrémités.

Il faudra utiliser `center + radius + 1` pour les bornes exclusives horizontales,
et corriger le commentaire qui annonce des bornes inclusives. La borne verticale
`world.getMaxSection()` est déjà exclusive et ne doit pas recevoir ce `+1`.
Préserver également l’indexation `cy - minSectionY`, nécessaire sous Y = 0.

Tests requis avant validation d’un correctif : rayon zéro, rayon un, centre
négatif, présence de blocs sur les bords positifs et monde commençant à Y = -64.
Ce constat statique n’est pas présenté comme une exécution du jeu.

### Anciennes surcharges de rendu GUI : arguments de position ignorés

Le commit `6bbc5bfa` réintroduit `drawFramebuffer(...)` et
`drawFramebufferWithViewport(...)` dans
[MyRenderHelper.java](../src/main/java/qouteall/imm_ptl/core/render/MyRenderHelper.java).
Ces surcharges acceptent encore des coordonnées mais transmettent `(0, 0)` au
nouvel utilitaire ; la première utilise en outre la taille de toute la fenêtre.

Les signatures existent pour la continuité de l’API, mais cela ne garantit pas
le respect des bornes fournies par les anciens appelants. Pour le portage,
définir explicitement les coordonnées et dimensions attendues et tester un rendu
GUI décentré. Ne pas copier ces wrappers en supposant que le correctif de
positionnement s’applique à toutes les anciennes signatures.

## Ordre d’adaptation du portage 26.2

1. Préserver les invariants de sécurité et de concurrence : conteneurs,
   autorisations, codecs et registres, sérialisation sans mutation.
2. Porter l’échelle native avec ses effets physiques et réseau ; conserver
   l’interpolation correcte et les copies des matrices.
3. Rétablir les fonctions historiques nécessaires : formes, animations,
   génération, portails cassables, commandes et migrations de sauvegardes.
4. Adapter le rendu au moteur natif 26.2, en vérifiant troisième personne,
   vues imbriquées, clipping, sous-vues GUI et libération des ressources.
5. Ajouter les intégrations optionnelles dont une version 26.2 existe et
   documenter précisément celles qui sont omises pour indisponibilité.

Les changements d’architecture de la bibliothèque Minestom doivent conserver
les mêmes invariants, avec des adaptateurs propres au serveur. Les références
Minecraft/Fabric internes ne doivent pas pénétrer dans le protocole partagé.

## Vérification de cet audit

Les conclusions viennent de `git log`, `git show`, du diff net entre les deux
références, de l’inspection des sources finales et d’une inspection `javap` des
attributs du binaire Minestom conservé en cache. Les commits de revert ont été
examinés comme une histoire ; c’est l’état final qui sert de référence.

Cet audit ajoute seulement ce document. Aucun correctif applicatif ni changement
de dépendance n’a été appliqué, et aucun build ou scénario 26.2 n’a été exécuté
dans ce tour. Les deux problèmes supplémentaires sont signalés pour correction
et tests ; les correctifs amont déjà hérités ne sont pas réappliqués.

Commandes permettant de reproduire les limites de la comparaison :

```sh
git merge-base origin/1.20.6 upstream/1.21
git rev-list --count origin/1.20.6..upstream/1.21
git log --format='%h %cs %s' origin/1.20.6..upstream/1.21
git diff --stat origin/1.20.6 upstream/1.21
```
