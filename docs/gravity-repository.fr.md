# Dépôt de gravité

[English](gravity-repository.md) · [Français](gravity-repository.fr.md)

[`gravity-changer`](https://github.com/N4TH4NOT/gravity-changer/blob/main/README.fr.md) possède l’implémentation de gravité. `dependencies/gravity` épingle sa révision ; le composite Gradle fournit les bibliothèques sans publication Maven distante.

```sh
git submodule update --init --recursive
# Développement avec un clone voisin :
./gradlew -PgravitySourceDir=../gravity-changer build validateAccessWidener
```

Publier les commits de gravité avant d’avancer le sous-module. Le build des portails exécute aussi les contrôles autonomes de gravité et compile `compat-tests/` contre le SDK.

Les noms publics et le pont v2 restent compatibles. Les façades et enveloppes de messages restent ici ; implémentation et tests intégrés appartiennent au dépôt de gravité. Voir son [guide d’intégration](https://github.com/N4TH4NOT/gravity-changer/blob/main/docs/gravity-26.2.fr.md) et son [utilisation Fabric](https://github.com/N4TH4NOT/gravity-changer/blob/main/README.fr.md#installation-et-utilisation-fabric).
