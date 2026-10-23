# Dépôt indépendant de gravité

La gravité est développée dans
[`N4TH4NOT/gravity-changer`](https://github.com/N4TH4NOT/gravity-changer).
Le sous-module `dependencies/gravity` épingle un commit précis. Le composite
Gradle fournit `gravity-api`, `gravity-minestom` et `gravity-fabric` sans
dépendre d’une publication Maven externe.

## Récupérer et compiler

```sh
git clone --recurse-submodules --branch port-26.2 https://github.com/N4TH4NOT/immersive-portals-minestom.git
cd immersive-portals-minestom
./gradlew build validateAccessWidener -PportalSmoke compileSmokeTestJava
```

Pour une copie existante : `git submodule update --init --recursive`.
Pour modifier les deux projets dans des répertoires voisins :

```sh
./gradlew -PgravitySourceDir=../gravity-changer build validateAccessWidener
```

La CI initialise le sous-module, compile les deux projets et exécute aussi les
tests autonomes de gravité. Les tests de compatibilité, les scénarios en jeu et
l’exemple de gravité sont maintenus dans `compat-tests/` du dépôt séparé et
compilés contre les portails actuels.

## Contrat conservé

- Les types publics `GravityDirection`, `GravityState`, `Vec3`, `Quaternion` et
  `PortalGravity` gardent leurs noms complets et leurs méthodes existantes.
- `PortalService.gravity()` et `supportsGravity(player)` gardent leur API.
- Le protocole de pont reste en version 2 sur `imm_ptl:bridge` ; le bit de
  capacité, les identifiants, l’ordre des champs et les octets restent identiques.
- L’option `teleportChangesGravity` garde sa représentation NBT et son sens.
- Le mod de portails embarque l’adaptateur et son API. Gravity Changer reste
  optionnel, installé séparément lorsque la gravité directionnelle est voulue.

Le dépôt des portails conserve les façades publiques et les appels nécessaires
à ses collisions, caméras, commandes et téléportations. Les calculs de gravité,
la physique Minestom, l’accès à l’API du mod amont et les caches de synchronisation
sont possédés par le dépôt indépendant. Les enveloppes `BridgeMessage` restent
dans le protocole des portails pour préserver leur API publique et éviter toute
dépendance circulaire.

Pour changer la version intégrée, avancer explicitement le commit du sous-module
et vérifier les deux builds avant de publier le pointeur. Les dépendances Maven
publiées réexportent les modules de gravité ; une distribution du SDK doit aussi
publier ces modules dans le dépôt Maven choisi.
