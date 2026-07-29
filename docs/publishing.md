# Publishing and Artifacts

Vulcanite 1.0.0 publishes two local Maven coordinates:

```text
dev.mgf:mgf-api:1.0.0
dev.mgf:mgf-fabric-26.2:1.0.0
```

Build and stage all publications with:

```text
./gradlew clean build apiCompatibilityCheck publishAllPublicationsToStagingRepository
```

The local Maven repository is `build/repository`. Each publication includes a
POM, Gradle module metadata, binary JAR, sources JAR, and Javadoc JAR.

The player artifact is produced separately at:

```text
mgf-impl-26.2/build/libs/mgf-1.0.0+mc26.2.jar
```

This is the JAR installed by players and modpacks. It embeds `mgf-api`; provider
mods should not bundle the Fabric implementation. The API artifact uses the MIT
license. The implementation/player artifact includes the PolyForm Shield terms.

## Verification

Run these checks before publishing a provider against the stable API:

```text
./gradlew :mgf-api:test :mgf-api:javadoc
./gradlew :samples:sample-provider:compileJava
./gradlew check apiCompatibilityCheck
```

The API dependency-boundary test rejects Minecraft, Mojang, LWJGL, Fabric, and
implementation references in stable API class files. `apiCompatibilityCheck`
compares public/protected signatures with the checked 1.0 baseline and is part
of the normal `check` lifecycle.

Remote repository credentials are intentionally outside this project. Publishing
to the local staging repository requires no secrets.
