# Gravity repository

[English](gravity-repository.md) · [Français](gravity-repository.fr.md)

[`gravity-changer`](https://github.com/N4TH4NOT/gravity-changer) owns the gravity implementation. `dependencies/gravity` pins its revision; the Gradle composite supplies the libraries without a remote Maven publication.

```sh
git submodule update --init --recursive
# Develop against a sibling checkout:
./gradlew -PgravitySourceDir=../gravity-changer build validateAccessWidener
```

Publish gravity commits before advancing the submodule. The portal build also runs standalone gravity checks and compiles `compat-tests/` against the SDK.

Public FQNs and bridge v2 remain compatible. Portal facades and message envelopes stay here; implementation and integration tests live in the gravity repository. See its [integration guide](https://github.com/N4TH4NOT/gravity-changer/blob/main/docs/gravity-26.2.md) and [Fabric usage](https://github.com/N4TH4NOT/gravity-changer#fabric-installation-and-use).
