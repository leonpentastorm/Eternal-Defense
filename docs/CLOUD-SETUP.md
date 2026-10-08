# Claude cloud setup

The source is in `custom-mods`, with a root Gradle wrapper. **The owner authorized Mess Hall v4 builds, testing and a local test bundle on 2026-10-08.** Include TaCZ Attributes 1.4 as a separate JAR in that bundle. Use JDK 17 and run:

```sh
./gradlew --no-daemon build releaseJars
```

In Claude's environment selector, edit the environment, select **Custom** network access, keep **Also include default list of common package managers** enabled, and paste the contents of [NETWORK-ALLOWLIST.txt](NETWORK-ALLOWLIST.txt), one domain per line. Save and retry the build after about a minute. Current Anthropic-hosted sessions receive network changes without requiring a fresh session, according to the [official environment documentation](https://code.claude.com/docs/en/cloud-environments#network-access), checked on 2026-10-05.

The default package-manager list covers Maven Central and Gradle plugin repositories. The additional list covers Forge, Mojang, JEI, Gradle distribution delivery, Curse Maven and MixinGradle. V4 compiles against pinned Curse Maven TaCZ 1.1.8-hotfix2 (1028108:9037989) and TaCZ Attributes 1.4 (1113285:8470731), plus Mixin 0.8.5 annotation processing/MixinGradle 0.7-SNAPSHOT for production refmaps. First resolution needs network access; existing offline caches may not contain these new dependencies. Dependency JARs are not shaded into either edition. Modrinth hosts remain useful for optional author-supplied downloads.

Gradle's official 8.8 distribution currently redirects to a GitHub release asset. If the very first wrapper download fails with a GitHub proxy 403, also attach the public `gradle/gradle-distributions` repository to the Claude session, or provision Gradle 8.8 through the environment's setup process. Adding more Maven domains will not fix a repository-scoped GitHub proxy rejection. The source handoff's build verification used an existing verified Gradle 8.8 installation; a fresh Claude VM bootstrap has not been tested.

If a host still fails, capture the failing URL and Gradle error. Network allowlist errors are different from an unavailable Java 17 toolchain or a nonexistent dependency version. Do not replace pinned dependencies with arbitrary newer versions just to avoid a download failure.

Read [UI-DEVELOPMENT.md](UI-DEVELOPMENT.md) and the [artist brief](../ARTIST-BRIEF.md) before editing. Keep both flavors: common UI and guide content may be shared, while instructions and recipes retain their edition-specific behavior.
