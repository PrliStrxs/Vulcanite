# Publishing and Artifacts

Vulcanite 0.3.0 Alpha 1 publishes two local Maven coordinates:

```text
dev.mgf:mgf-api:0.3.0-alpha.1
dev.mgf:mgf-fabric-26.2:0.3.0-alpha.1
```

Build and stage all publications with:

```text
./gradlew clean build publishAllPublicationsToStagingRepository
```

The local Maven repository is `build/repository`. Each publication includes a
POM, Gradle module metadata, binary JAR, sources JAR, and Javadoc JAR.

The player artifact is produced separately at:

```text
mgf-impl-26.2/build/libs/mgf-0.3.0-alpha.1+mc26.2.jar
```

This is the JAR installed by players and modpacks. It embeds `mgf-api`; provider
mods should not bundle the Fabric implementation. The API artifact uses the MIT
license. The implementation/player artifact includes the PolyForm Shield terms.

## Verification

Run these checks before publishing a provider against the Alpha API:

```text
./gradlew :mgf-api:test :mgf-api:javadoc
./gradlew :samples:sample-provider:compileJava
./gradlew check
```

The API dependency-boundary test rejects Minecraft, Mojang, LWJGL, Fabric, and
implementation references in stable API class files. `apiCompatibilityCheck`
compares public/protected signatures with the checked Alpha baseline once the
baseline task is enabled.

Remote repository credentials are intentionally outside this project. Publishing
to the local staging repository requires no secrets.
