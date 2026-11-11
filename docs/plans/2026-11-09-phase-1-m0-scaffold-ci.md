# M0: ModDevGradle scaffold and CI

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m0-scaffold-ci`.

**Milestone goal:** an empty mod that builds, passes its metadata and jar checks, loads beside Cobblemon,
Create and AE2 in a GameTest server, and runs in CI with CodeQL, dependency review and Dependabot.

---

### Task 1: Gradle scaffold, mod metadata, entry point and build guards

**Rows:** CD-PLAT-01, CD-PLAT-02, CD-PLAT-03, CD-PLAT-04, CD-PLAT-05, CD-PLAT-06, CD-PLAT-07, CD-PLAT-08,
CD-REL-05, CD-REL-06, CD-REL-07, CD-REL-11, CD-CRY-05 (guard only). CD-ART-14 is a Phase 2 row, and this
task sets its key early.

**Files:**
- Create: `settings.gradle`, `gradle.properties`, `build.gradle`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
  (copied from the NeoForge 1.21.1 MDK, Gradle 9.2.1)
- Create: `src/main/templates/META-INF/neoforge.mods.toml`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/Ids.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
- Create: `src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java`
- Create: `src/main/resources/cobblemon_dynamics.mixins.json`
- Create: `src/main/resources/META-INF/accesstransformer.cfg`
- Create: `src/main/resources/logo.png` (byte copy of `art/logo/logo-256.png`)
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/build/ModMetadataTest.kt`
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/build/RepositoryFilesTest.kt`
- Modify: `.gitignore` (add `run/`, `.kotlin/`, `*.iml`, `.idea/`, `repo/`)

**Interfaces:**
- Produces: `const val MOD_ID = "cobblemon_dynamics"` and `fun id(path: String): ResourceLocation` in
  package `codes.honor.cobblemondynamics` (JVM class `IdsKt`). `CobblemonDynamics.LOGGER: Logger`.
- Produces: Gradle tasks `verifyJar` and `verifyNoAe2`, both wired into `check`.
- Produces: Gradle property `release_version` (optional). The version is
  `${release_version ?: mod_version}+mc${minecraft_version}`.

- [ ] **Step 1: Copy the Gradle wrapper from the MDK**

```bash
for f in gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties; do
  mkdir -p "$(dirname "$f")"
  gh api "repos/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/contents/$f" --jq .content | base64 -d > "$f"
done
git update-index --add --chmod=+x gradlew
grep distributionUrl gradle/wrapper/gradle-wrapper.properties   # expect gradle-9.2.1-bin.zip
```

- [ ] **Step 2: Write `settings.gradle` and `gradle.properties`**

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'cobblemon-dynamics'
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false
kotlin.code.style=official

minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.21.2)
neo_version=21.1.265
neo_version_range=[21.1.219,)
parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

kotlin_version=2.2.20
kff_version=5.10.0
kff_version_range=[5.10,)
cobblemon_version=1.8.4+1.21.1
cobblemon_version_range=[1.8.4,1.9)
create_version=6.0.10-280
create_version_range=[6.0.10,6.1)
ponder_version=1.0.82+mc1.21.1
flywheel_version=1.0.6
registrate_version=MC1.21-1.3.0+67
ae2_version=19.2.19
ae2_version_range=[19.2,20)

mod_id=cobblemon_dynamics
mod_name=Cobblemon Dynamics
mod_license=MPL-2.0
mod_version=1.0.0-alpha.1
mod_group_id=codes.honor
mod_artifact_id=cobblemon-dynamics
mod_authors=HonorCodes
mod_description=Pastured Pokemon power and run the machines of your base.
```

- [ ] **Step 3: Write `build.gradle`**

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'idea'
    id 'net.neoforged.moddev' version '2.0.148'
    id 'org.jetbrains.kotlin.jvm' version '2.2.20'
}

version = "${project.findProperty('release_version') ?: mod_version}+mc${minecraft_version}"
group = mod_group_id

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)
kotlin {
    jvmToolchain(21)
}

sourceSets {
    gametest {
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    localRuntime
    runtimeClasspath.extendsFrom localRuntime
    gametestImplementation.extendsFrom implementation
    gametestCompileOnly.extendsFrom compileOnly
    gametestRuntimeOnly.extendsFrom runtimeOnly
    gametestRuntimeClasspath.extendsFrom localRuntime
}

repositories {
    maven {
        name = 'Kotlin for Forge'
        url = 'https://thedarkcolour.github.io/KotlinForForge/'
        content { includeGroup 'thedarkcolour' }
    }
    maven {
        name = 'Cobblemon'
        url = 'https://maven.impactdev.net/repository/development/'
        content { includeGroup 'com.cobblemon' }
    }
    maven {
        name = 'Create'
        url = 'https://maven.createmod.net'
        content {
            includeGroup 'com.simibubi.create'
            includeGroup 'net.createmod.ponder'
            includeGroup 'dev.engine-room.flywheel'
        }
    }
    maven {
        name = 'Registrate'
        url = 'https://maven.ithundxr.dev/snapshots'
        content { includeGroup 'com.tterrag.registrate' }
    }
    mavenCentral()
}

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    validateAccessTransformers = true

    addModdingDependenciesTo sourceSets.test
    addModdingDependenciesTo sourceSets.gametest

    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
        cobblemon_dynamics_test {
            sourceSet sourceSets.gametest
        }
    }

    runs {
        client {
            client()
            gameDirectory = project.file('run/client')
            loadedMods = [mods."${mod_id}"]
        }
        server {
            server()
            programArgument '--nogui'
            gameDirectory = project.file('run/server')
            loadedMods = [mods."${mod_id}"]
        }
        gameTestServer {
            type = 'gameTestServer'
            sourceSet = sourceSets.gametest
            gameDirectory = project.file('run/gametest')
            loadedMods = [mods."${mod_id}", mods.cobblemon_dynamics_test]
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
        }
        configureEach {
            systemProperty 'forge.logging.markers', 'REGISTRIES'
            logLevel = org.slf4j.event.Level.INFO
        }
    }
}

dependencies {
    implementation "thedarkcolour:kotlinforforge-neoforge:${kff_version}"
    implementation "com.cobblemon:neoforge:${cobblemon_version}"

    compileOnly("com.simibubi.create:create-1.21.1:${create_version}:slim") { transitive = false }
    compileOnly "net.createmod.ponder:ponder-neoforge:${ponder_version}"
    compileOnly "dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:${flywheel_version}"
    compileOnly "com.tterrag.registrate:Registrate:${registrate_version}"

    localRuntime("com.simibubi.create:create-1.21.1:${create_version}:slim") { transitive = false }
    localRuntime "net.createmod.ponder:ponder-neoforge:${ponder_version}"
    localRuntime "dev.engine-room.flywheel:flywheel-neoforge-1.21.1:${flywheel_version}"
    localRuntime "com.tterrag.registrate:Registrate:${registrate_version}"
    localRuntime "org.appliedenergistics:appliedenergistics2:${ae2_version}"

    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.tomlj:tomlj:1.1.1'
}

var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
            minecraft_version_range: minecraft_version_range,
            neo_version_range      : neo_version_range,
            kff_version_range      : kff_version_range,
            cobblemon_version_range: cobblemon_version_range,
            create_version_range   : create_version_range,
            ae2_version_range      : ae2_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : project.version.toString(),
            mod_authors            : mod_authors,
            mod_description        : mod_description,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}
sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

tasks.named('test', Test) {
    useJUnitPlatform()
    dependsOn generateModMetadata
    systemProperty 'cd.modsToml', file('build/generated/sources/modMetadata/META-INF/neoforge.mods.toml').absolutePath
    systemProperty 'cd.projectDir', projectDir.absolutePath
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}

// CD-PLAT-04, CD-REL-07: every class is Java 21 bytecode, no Cobblemon asset ships, no test class ships.
tasks.register('verifyJar') {
    group = 'verification'
    description = 'Checks the mod jar: class versions, no Cobblemon assets, no GameTest classes, the logo.'
    dependsOn tasks.named('jar')
    def jarFile = tasks.named('jar').flatMap { it.archiveFile }
    def logoSource = file('art/logo/logo-256.png')
    inputs.file jarFile
    inputs.file logoSource
    doLast {
        def problems = []
        new java.util.zip.ZipFile(jarFile.get().asFile).withCloseable { zip ->
            zip.entries().each { e ->
                if (e.name.endsWith('.class')) {
                    zip.getInputStream(e).withCloseable { s ->
                        def b = s.readNBytes(8)
                        int major = ((b[6] & 0xff) << 8) | (b[7] & 0xff)
                        if (major != 65) problems << "${e.name}: class file major version ${major}, expected 65"
                    }
                }
                if (e.name.startsWith('assets/cobblemon/')) problems << "${e.name}: copies a Cobblemon asset"
                if (e.name.startsWith('codes/honor/cobblemondynamics/test/')) problems << "${e.name}: GameTest class in the jar"
            }
            def logo = zip.getEntry('logo.png')
            if (logo == null) {
                problems << 'logo.png: missing'
            } else if (zip.getInputStream(logo).bytes != logoSource.bytes) {
                problems << 'logo.png: differs from art/logo/logo-256.png'
            }
        }
        if (!problems.isEmpty()) throw new GradleException("verifyJar failed:\n" + problems.join('\n'))
    }
}

// CD-CRY-05: the main source set never imports AE2 and has no AE2 compile dependency.
tasks.register('verifyNoAe2') {
    group = 'verification'
    description = 'Fails if the main source set imports appeng or compiles against AE2.'
    def mainSources = fileTree('src/main') { include '**/*.kt', '**/*.java' }
    inputs.files mainSources
    doLast {
        def bad = mainSources.files.findAll { it.text =~ /(?m)^\s*import\s+appeng\./ }
        if (!bad.isEmpty()) throw new GradleException("AE2 imports in src/main: ${bad*.path}")
        def ae2 = configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts.findAll {
            it.moduleVersion.id.group == 'org.appliedenergistics'
        }
        if (!ae2.isEmpty()) throw new GradleException("AE2 on the main compile classpath: ${ae2*.name}")
    }
}

tasks.named('check') {
    dependsOn 'verifyJar', 'verifyNoAe2'
}

publishing {
    publications {
        register('mavenJava', MavenPublication) {
            groupId = mod_group_id
            artifactId = mod_artifact_id
            from components.java
        }
    }
    repositories {
        maven { url = "file://${project.projectDir}/repo" }
    }
}

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

- [ ] **Step 4: Write the mod metadata template**

```toml
# src/main/templates/META-INF/neoforge.mods.toml
modLoader="kotlinforforge"
loaderVersion="${kff_version_range}"
license="${mod_license}"
issueTrackerURL="https://github.com/HonorCodes/cobblemon-dynamics/issues"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
displayURL="https://github.com/HonorCodes/cobblemon-dynamics"
logoFile="logo.png"
logoBlur=false
authors="${mod_authors}"
description='''${mod_description}'''

[[mixins]]
config="${mod_id}.mixins.json"

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="${neo_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="kotlinforforge"
type="required"
versionRange="${kff_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="cobblemon"
type="required"
versionRange="${cobblemon_version_range}"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="create"
type="optional"
versionRange="${create_version_range}"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="ae2"
type="optional"
versionRange="${ae2_version_range}"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="cobbleworkers"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"

[[dependencies.${mod_id}]]
modId="cobblebase"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"
```

- [ ] **Step 5: Write the failing metadata and repository tests**

```kotlin
// src/test/kotlin/codes/honor/cobblemondynamics/build/ModMetadataTest.kt
package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Path

class ModMetadataTest {
    private val toml = Toml.parse(Path.of(System.getProperty("cd.modsToml"))).also {
        check(!it.hasErrors()) { it.errors().joinToString() }
    }

    private fun mod(): TomlTable = toml.getArray("mods")!!.getTable(0)

    private fun dependency(modId: String): TomlTable? {
        val list = toml.getArray(listOf("dependencies", "cobblemon_dynamics")) ?: return null
        return (0 until list.size()).map { list.getTable(it) }.firstOrNull { it.getString("modId") == modId }
    }

    private fun requireDependency(modId: String): TomlTable =
        dependency(modId) ?: error("neoforge.mods.toml has no dependency entry for $modId")

    @Test
    fun `CD-PLAT-01 NeoForge is required from 21_1_219`() {
        val d = requireDependency("neoforge")
        assertEquals("required", d.getString("type"))
        assertEquals("[21.1.219,)", d.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-02 Cobblemon is required in the 1_8 line from 1_8_4`() {
        val d = requireDependency("cobblemon")
        assertEquals("required", d.getString("type"))
        assertEquals("[1.8.4,1.9)", d.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-03 the language loader is KotlinForForge from 5_10`() {
        assertEquals("kotlinforforge", toml.getString("modLoader"))
        assertEquals("[5.10,)", toml.getString("loaderVersion"))
        assertEquals("required", requireDependency("kotlinforforge").getString("type"))
    }

    @Test
    fun `CD-PLAT-05 Create and AE2 are optional with pinned ranges`() {
        val create = requireDependency("create")
        assertEquals("optional", create.getString("type"))
        assertEquals("[6.0.10,6.1)", create.getString("versionRange"))
        val ae2 = requireDependency("ae2")
        assertEquals("optional", ae2.getString("type"))
        assertEquals("[19.2,20)", ae2.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-06 every dependency applies on both sides`() {
        for (id in listOf("neoforge", "minecraft", "kotlinforforge", "cobblemon", "create", "ae2")) {
            assertEquals("BOTH", requireDependency(id).getString("side"), id)
        }
    }

    @Test
    fun `CD-PLAT-07 the mod id is cobblemon_dynamics`() {
        assertEquals("cobblemon_dynamics", mod().getString("modId"))
    }

    @Test
    fun `CD-PLAT-08 Cobbleworkers and Cobblebase are never required`() {
        for (id in listOf("cobbleworkers", "cobblebase")) {
            val d = dependency(id)
            if (d != null) assertNotEquals("required", d.getString("type"), id)
        }
    }

    @Test
    fun `CD-REL-05 the version reads MAJOR_MINOR_PATCH plus mc1_21_1`() {
        val version = mod().getString("version")!!
        assertTrue(Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?\+mc1\.21\.1$""").matches(version), version)
    }

    @Test
    fun `CD-REL-06 the declared license is MPL-2_0`() {
        assertEquals("MPL-2.0", toml.getString("license"))
    }

    @Test
    fun `CD-ART-14 logo blur is off`() {
        assertFalse(mod().getBoolean("logoBlur")!!)
        assertEquals("logo.png", mod().getString("logoFile"))
    }
}
```

```kotlin
// src/test/kotlin/codes/honor/cobblemondynamics/build/RepositoryFilesTest.kt
package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RepositoryFilesTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-REL-06 LICENSE holds the MPL-2_0 text`() {
        assertTrue(File(root, "LICENSE").readText().contains("Mozilla Public License Version 2.0"))
    }

    @Test
    fun `CD-REL-11 README states that the mod is not affiliated`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("not affiliated"))
        for (name in listOf("Cobblemon", "The Pokémon Company", "Create", "Applied Energistics 2")) {
            assertTrue(readme.contains(name), name)
        }
    }
}
```

- [ ] **Step 6: Run the tests and see them fail**

Run: `./gradlew test`
Expected: FAIL at configuration or compile time, because the template and the entry point do not exist
yet (`generateModMetadata` has no input), or with `NoSuchFileException` on the TOML path.

- [ ] **Step 7: Write the entry point, the mixin skeleton and the resources**

```kotlin
// src/main/kotlin/codes/honor/cobblemondynamics/Ids.kt
package codes.honor.cobblemondynamics

import net.minecraft.resources.ResourceLocation

const val MOD_ID = "cobblemon_dynamics"

fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
```

```kotlin
// src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt
package codes.honor.cobblemondynamics

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger

@Mod(MOD_ID)
class CobblemonDynamics(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Cobblemon Dynamics {} loading", container.modInfo.version)
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
```

```java
// src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java
package codes.honor.cobblemondynamics.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Skips each integration mixin whose target mod is absent, so a missing mod never crashes the game. */
public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String BASE = "codes.honor.cobblemondynamics.mixin.";
    private static final Map<String, String> REQUIRED_MOD_BY_PACKAGE = Map.of(
            BASE + "create.", "create",
            BASE + "compat.cobblebase.", "cobblebase");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> entry : REQUIRED_MOD_BY_PACKAGE.entrySet()) {
            if (mixinClassName.startsWith(entry.getKey())) {
                return FMLLoader.getLoadingModList().getModFileById(entry.getValue()) != null;
            }
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
```

```json
// src/main/resources/cobblemon_dynamics.mixins.json
{
  "required": true,
  "minVersion": "0.8",
  "package": "codes.honor.cobblemondynamics.mixin",
  "plugin": "codes.honor.cobblemondynamics.mixin.MixinPlugin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [],
  "injectors": {
    "defaultRequire": 0
  }
}
```

```text
# src/main/resources/META-INF/accesstransformer.cfg
# Access transformers for Cobblemon Dynamics. Each entry names the spec rows that need it.
```

```bash
cp art/logo/logo-256.png src/main/resources/logo.png
printf '\nrun/\n.kotlin/\n.idea/\n*.iml\nrepo/\n' >> .gitignore
```

- [ ] **Step 8: Run the build and see it pass**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. The test report lists the 12 tests of `ModMetadataTest` and
`RepositoryFilesTest` as passed, and `verifyJar` and `verifyNoAe2` run.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle gradle.properties build.gradle gradlew gradlew.bat gradle/wrapper \
  src/main .gitignore src/test
git commit -m "Scaffold the ModDevGradle build, mod metadata and jar checks"
```

---

### Task 2: GameTest test mod, structure templates and integration smoke tests

**Rows:** CD-REL-10, CD-PLAT-05 (the mod loads beside Create and AE2), CD-WMD-01 groundwork (a pasture
placed through `setPlacedBy`).

**Files:**
- Create: `scripts/gen_test_structures.py`
- Create: `src/gametest/resources/data/cobblemon_dynamics/structure/floor_9x5x9.nbt` and the four other
  templates from the index table (generated)
- Create: `src/gametest/resources/META-INF/neoforge.mods.toml`
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/DynamicsTestMod.kt`
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestKit.kt`
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/SmokeTests.kt`

**Interfaces:**
- Produces: `TEST_MOD_ID = "cobblemon_dynamics_test"`.
- Produces (`object TestKit`):
  - `fun owner(helper: GameTestHelper, name: String = "owner"): ServerPlayer`, a NeoForge `FakePlayer` with a
    unique profile. Vanilla's `makeMockServerPlayerInLevel` joins the player list, and its connection rejects
    Cobblemon's login payloads (`Payload cobblemon:script_registry_sync may not be sent to the client`).
  - `fun pasturedEntity(helper, pasture, pokemonId): PokemonEntity?`. `Pokemon.entity` is set only for sent-out
    Pokémon, so pastured entities are found with an AABB query by Pokémon UUID.
  - `fun placePasture(helper: GameTestHelper, rel: BlockPos, owner: ServerPlayer?): PokemonPastureBlockEntity`
  - `fun pokemon(spec: String): Pokemon` where `spec` is a Cobblemon properties string, for example
    `"charmander level=10"`.
  - `fun tether(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, owner: ServerPlayer, spec: String): PokemonEntity`
  - `fun wild(helper: GameTestHelper, rel: BlockPos, spec: String): PokemonEntity`

- [ ] **Step 1: Write the structure generator and generate the templates**

```python
# scripts/gen_test_structures.py
"""Writes empty GameTest structure templates with a stone floor at y = 0.

Usage: py scripts/gen_test_structures.py
Output: src/gametest/resources/data/cobblemon_dynamics/structure/floor_<x>x<y>x<z>.nbt
"""
import gzip
import pathlib
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1
SIZES = [(9, 5, 9), (15, 6, 15), (23, 10, 23), (23, 17, 23), (41, 8, 41)]
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/gametest/resources/data/cobblemon_dynamics/structure"


def name(tag_type: int, key: str) -> bytes:
    raw = key.encode("utf-8")
    return struct.pack(">bH", tag_type, len(raw)) + raw


def string_payload(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def int_list(key: str, values) -> bytes:
    return name(9, key) + struct.pack(">bi", 3, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def structure(x: int, y: int, z: int) -> bytes:
    palette = name(9, "palette") + struct.pack(">bi", 10, 1) + name(8, "Name") + string_payload("minecraft:stone") + b"\x00"
    blocks = [(bx, 0, bz) for bx in range(x) for bz in range(z)]
    block_list = name(9, "blocks") + struct.pack(">bi", 10, len(blocks))
    for pos in blocks:
        block_list += int_list("pos", pos) + name(3, "state") + struct.pack(">i", 0) + b"\x00"
    entities = name(9, "entities") + struct.pack(">bi", 10, 0)
    body = (
        name(3, "DataVersion") + struct.pack(">i", DATA_VERSION)
        + int_list("size", (x, y, z))
        + palette + block_list + entities
    )
    return name(10, "") + body + b"\x00"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for x, y, z in SIZES:
        path = OUT / f"floor_{x}x{y}x{z}.nbt"
        path.write_bytes(gzip.compress(structure(x, y, z), mtime=0))
        print(path.relative_to(OUT.parents[4]))


if __name__ == "__main__":
    main()
```

Run: `py scripts/gen_test_structures.py`
Expected: five `.nbt` paths printed.

- [ ] **Step 2: Write the test mod metadata and entry point**

```toml
# src/gametest/resources/META-INF/neoforge.mods.toml
modLoader="kotlinforforge"
loaderVersion="[5.10,)"
license="MPL-2.0"

[[mods]]
modId="cobblemon_dynamics_test"
version="0.0.0"
displayName="Cobblemon Dynamics GameTests"
description='''GameTests for Cobblemon Dynamics. Never shipped.'''

[[dependencies.cobblemon_dynamics_test]]
modId="cobblemon_dynamics"
type="required"
versionRange="*"
ordering="AFTER"
side="BOTH"
```

```kotlin
// src/gametest/kotlin/codes/honor/cobblemondynamics/test/DynamicsTestMod.kt
package codes.honor.cobblemondynamics.test

import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.world.level.GameRules
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent

const val TEST_MOD_ID = "cobblemon_dynamics_test"

@Mod(TEST_MOD_ID)
class DynamicsTestMod(modBus: IEventBus) {
    init {
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { event ->
            val server = event.server
            val rules = server.gameRules
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server)
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
            rules.getRule(CobblemonGameRules.DO_POKEMON_SPAWNING).set(false, server)
            server.allLevels.forEach { it.dayTime = 6000 }
        }
    }
}
```

- [ ] **Step 3: Write `TestKit`**

```kotlin
// src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestKit.kt
package codes.honor.cobblemondynamics.test

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object TestKit {
    fun owner(helper: GameTestHelper, name: String = "owner"): ServerPlayer {
        val uuid = UUID.randomUUID()
        return FakePlayerFactory.get(helper.level, GameProfile(uuid, "${name}_${uuid.toString().take(6)}"))
    }

    fun placePasture(helper: GameTestHelper, rel: BlockPos, owner: ServerPlayer?): PokemonPastureBlockEntity {
        val level = helper.level
        val pos = helper.absolutePos(rel)
        val state = CobblemonBlocks.PASTURE.defaultBlockState()
        level.setBlock(pos, state, 3)
        CobblemonBlocks.PASTURE.setPlacedBy(level, pos, state, owner, ItemStack.EMPTY)
        return level.getBlockEntity(pos) as? PokemonPastureBlockEntity
            ?: throw GameTestAssertException("no pasture block entity at $pos")
    }

    fun pokemon(spec: String): Pokemon = PokemonProperties.parse(spec).create()

    fun tether(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, owner: ServerPlayer, spec: String): PokemonEntity {
        val pokemon = pokemon(spec)
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for $spec")
        return pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no entity after tether for $spec")
    }

    fun wild(helper: GameTestHelper, rel: BlockPos, spec: String): PokemonEntity {
        val entity = PokemonEntity(helper.level, pokemon(spec))
        val pos = helper.absoluteVec(rel.bottomCenter)
        entity.moveTo(pos.x, pos.y, pos.z)
        helper.level.addFreshEntity(entity)
        return entity
    }
}
```

- [ ] **Step 4: Write the smoke GameTests**

```kotlin
// src/gametest/kotlin/codes/honor/cobblemondynamics/test/SmokeTests.kt
package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.fml.ModList
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SmokeTests {
    @GameTest(template = "floor_9x5x9")
    fun `cd_rel_10_pinned_mods_load`(helper: GameTestHelper) {
        val expected = mapOf("cobblemon" to "1.8.4", "create" to "6.0.10", "ae2" to "19.2.19", MOD_ID to "1.0.0")
        for ((modId, prefix) in expected) {
            val container = ModList.get().getModContainerById(modId).orElse(null)
            helper.assertTrue(container != null, "$modId is not loaded")
            val version = container!!.modInfo.version.toString()
            CobblemonDynamics.LOGGER.info("GameTest run loads {} {}", modId, version)
            helper.assertTrue(version.startsWith(prefix), "$modId is $version, expected $prefix")
        }
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun `smoke_pasture_tethers_a_pokemon`(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        helper.assertTrue(pasture.ownerId == owner.uuid, "ownerId is not the placer")
        val entity = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.runAfterDelay(20) {
            helper.assertTrue(entity.tethering?.pasturePos == pasture.blockPos, "entity is not tethered to the pasture")
            helper.assertTrue(pasture.tetheredPokemon.any { it.pokemonId == entity.pokemon.uuid }, "pasture lacks the Pokémon")
            helper.succeed()
        }
    }
}
```

Kotlin backtick names keep the GameTest ids readable. Vanilla lowercases them into the test id
`cobblemon_dynamics:smoketests.cd_rel_10_pinned_mods_load` only when the class prefix applies. With
`@PrefixGameTestTemplate(false)` only the template id loses its prefix. If the runner rejects the backtick
names, rename them to plain camel case and keep the row id in the name.

- [ ] **Step 5: Run the GameTests**

Run: `./gradlew runGameTestServer`
Expected: exit code 0. `run/gametest/logs/latest.log` holds `GameTest run loads cobblemon 1.8.4+1.21.1`,
`GameTest run loads create 6.0.10`, `GameTest run loads ae2 19.2.19` and
`All 2 required tests passed :)`.

If the server stops before the tests, read the first `ERROR` in the log. Likely causes and fixes:
Ponder needs Fuzss's maven (`https://maven.fuzs.net/`, add to `repositories`); GuideME is missing (add
`localRuntime "org.appliedenergistics:guideme:21.1.1"`); a backtick test name is rejected (rename).

- [ ] **Step 6: Commit**

```bash
git add scripts/gen_test_structures.py src/gametest
git commit -m "Add the GameTest test mod, structure templates and integration smoke tests"
```

---

### Task 3: CI workflow

**Rows:** CD-REL-01, CD-REL-10 (the CI log lists the three mods).

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Check the current major versions of the actions**

```bash
for a in actions/checkout actions/setup-java gradle/actions actions/upload-artifact; do
  echo "$a $(gh api repos/$a/releases/latest --jq .tag_name)"
done
```

Use the major of each result below (`@vN`). Dependabot keeps them current (Task 4).

- [ ] **Step 2: Write the workflow**

```yaml
# .github/workflows/build.yml
name: build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v6
      - name: Build, unit tests and jar checks
        run: ./gradlew build --stacktrace
      - name: GameTests
        run: ./gradlew runGameTestServer --stacktrace
      - name: Upload test reports and GameTest logs
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: test-reports
          path: |
            build/reports/tests/
            run/gametest/logs/
          if-no-files-found: ignore
          retention-days: 14
```

- [ ] **Step 3: Validate the workflow syntax locally**

Run: `py -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build.yml')); print('ok')"`
Expected: `ok`. (If PyYAML is missing, run `py -m pip install --user pyyaml` first.)

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "Run the build, unit tests and GameTests in CI on pushes and pull requests to main"
```

The CI proof (a failing unit test fails the check) runs in Task 6, once the branch is pushed.

---

### Task 4: Security scans and Dependabot

**Rows:** CD-REL-02, CD-REL-03.

**Files:**
- Create: `.github/workflows/security.yml`
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Write `security.yml`**

```yaml
# .github/workflows/security.yml
name: security

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '23 4 * * 1'

permissions:
  contents: read

jobs:
  codeql:
    name: CodeQL
    runs-on: ubuntu-latest
    timeout-minutes: 45
    permissions:
      contents: read
      security-events: write
      actions: read
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v6
      - uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin
          build-mode: manual
      - name: Compile for analysis
        run: ./gradlew compileJava compileKotlin --no-build-cache --rerun-tasks
      - uses: github/codeql-action/analyze@v4
        with:
          category: /language:java-kotlin

  dependency-review:
    name: Dependency review
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@v7
      - uses: actions/dependency-review-action@v5
        with:
          fail-on-severity: high
          comment-summary-in-pr: on-failure
```

Check the CodeQL and dependency-review majors the same way as in Task 3 Step 1, with
`github/codeql-action` and `actions/dependency-review-action`.

- [ ] **Step 2: Write `dependabot.yml`**

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
    open-pull-requests-limit: 5
    groups:
      gradle-minor-patch:
        update-types: [minor, patch]
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    groups:
      actions:
        patterns: ['*']
```

- [ ] **Step 3: Write a repository test for the two files**

```kotlin
// add to src/test/kotlin/codes/honor/cobblemondynamics/build/RepositoryFilesTest.kt
@Test
fun `CD-REL-02 security workflow runs CodeQL and dependency review`() {
    val text = File(root, ".github/workflows/security.yml").readText()
    assertTrue(text.contains("github/codeql-action/analyze"))
    assertTrue(text.contains("actions/dependency-review-action"))
}

@Test
fun `CD-REL-03 dependabot covers gradle and github-actions`() {
    val text = File(root, ".github/dependabot.yml").readText()
    assertTrue(text.contains("package-ecosystem: gradle"))
    assertTrue(text.contains("package-ecosystem: github-actions"))
}

@Test
fun `CD-REL-01 build workflow runs build and GameTests on main pushes and pull requests`() {
    val text = File(root, ".github/workflows/build.yml").readText()
    assertTrue(text.contains("pull_request:") && text.contains("push:") && text.contains("branches: [main]"))
    assertTrue(text.contains("./gradlew build") && text.contains("./gradlew runGameTestServer"))
}
```

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.build.RepositoryFilesTest"`
Expected: PASS (the three files exist from Steps 1 and 2 and Task 3).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/security.yml .github/dependabot.yml src/test
git commit -m "Add CodeQL, dependency review and Dependabot for Gradle and GitHub Actions"
```

---

### Task 5: Isolated test pack

**Rows:** demos of CD-PLAT-01, CD-PLAT-02, CD-PLAT-03 and CD-PLAT-05. CD-PLAT-06 stays a manual check
(a client without the mod joins), recorded in the manual checklist in M5.

**Files:**
- Create: `scripts/testpack/testpack.py`
- Create: `scripts/testpack/mods.json`
- Create: `scripts/testpack/README.md`

**Interfaces:**
- Produces: `py scripts/testpack/testpack.py smoke` (starts a clean server with the built jar, checks the
  mod loads, stops it) and `py scripts/testpack/testpack.py deps` (the dependency-range demos). Later
  milestones add scenarios to the same script with `def scenario_<name>(server)` functions.
- Produces: `class Server` with `start()`, `rcon(command) -> str`, `wait_log(pattern, timeout)`,
  `stop()`; and `def install(profile, overrides) -> Path`.

- [ ] **Step 1: Pin the mod set**

`scripts/testpack/mods.json` maps a mod to its Modrinth project id and exact version number. Resolve each
one once with the Modrinth API (`GET https://api.modrinth.com/v2/project/<slug>/version`), pick the
NeoForge 1.21.1 file whose `version_number` matches, and store the file name, URL and SHA-512:

```json
{
  "neoforge": "21.1.265",
  "mods": {
    "kotlinforforge": {"project": "kotlin-for-forge", "version": "5.10.0", "url": "", "sha512": ""},
    "cobblemon": {"project": "cobblemon", "version": "1.8.4+1.21.1", "url": "", "sha512": ""},
    "create": {"project": "create", "version": "6.0.10", "url": "", "sha512": ""},
    "ae2": {"project": "ae2", "version": "19.2.19", "url": "", "sha512": ""},
    "guideme": {"project": "guideme", "version": "21.1.1", "url": "", "sha512": ""}
  },
  "demo_versions": {
    "neoforge_below_floor": "21.1.218",
    "cobblemon_below_floor": "1.8.3+1.21.1",
    "create_below_floor": "6.0.9",
    "ae2_below_floor": "19.1.x (latest 19.1 release)"
  }
}
```

Fill `url` and `sha512` from the API response with the `resolve` subcommand (Step 2), commit the filled
file, and never download a jar whose hash differs.

- [ ] **Step 2: Write the script**

`scripts/testpack/testpack.py` uses only the Python 3.12 standard library. It sends
`User-Agent: testpack-script/1.0` to Modrinth. Structure:

```python
#!/usr/bin/env python3
"""Clean NeoForge 1.21.1 test server with only the mods a scenario needs. Never the COHO pack."""
import argparse, hashlib, json, os, pathlib, secrets, shutil, socket, struct, subprocess, sys, time, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = ROOT / "run" / "testpack"
PINS = json.loads((pathlib.Path(__file__).with_name("mods.json")).read_text())
UA = {"User-Agent": "testpack-script/1.0"}

def java() -> str:
    home = os.environ.get("JAVA_HOME")
    return str(pathlib.Path(home, "bin", "java")) if home else "java"

def fetch(url: str, dest: pathlib.Path, sha512: str | None = None) -> pathlib.Path: ...
    # download to dest.part, verify sha512 when given, rename; skip when dest exists with the right hash

def resolve() -> None: ...
    # for each mod in PINS["mods"]: GET /v2/project/<project>/version?loaders=["neoforge"]&game_versions=["1.21.1"],
    # pick version_number == pin, take files[primary].url and hashes.sha512, write mods.json back

def install(profile: str, overrides: dict[str, str] | None = None, neoforge: str | None = None) -> pathlib.Path: ...
    # PACK/<profile>/: run the NeoForge installer (--installServer), write eula.txt and server.properties:
    # online-mode=false, enable-rcon=true, rcon.port=<free port>, rcon.password=<random>, level-type=minecraft:flat,
    # spawn-protection=0, max-players=4, motd=testpack; copy pinned mods (minus any override) into mods/;
    # copy build/libs/cobblemon_dynamics-*.jar that is not -sources into mods/

class Rcon: ...      # Minecraft RCON over TCP: login packet type 3, command type 2, little-endian framing

class Server:
    def __init__(self, folder: pathlib.Path): ...
    def start(self) -> None: ...   # java @user_jvm_args.txt @libraries/net/neoforged/neoforge/<v>/{win,unix}_args.txt nogui
    def wait_log(self, pattern: str, timeout: float) -> str: ...   # tails logs/latest.log
    def rcon(self, command: str) -> str: ...
    def stop(self) -> int: ...      # "stop" over RCON, then wait for the process

def scenario_smoke(server: Server) -> None:
    server.wait_log(r"Cobblemon Dynamics .* loading", 240)
    server.wait_log(r"Done \(", 240)
    assert "players online" in server.rcon("list")

def deps_demo() -> None:
    # Each case expects the server to stop at FML's dependency check and log the mod id and range:
    #   neoforge 21.1.218            -> "neoforge" and "[21.1.219,)"
    #   cobblemon 1.8.3              -> "cobblemon" and "[1.8.4,1.9)"
    #   create 6.0.9                 -> "create" and "[6.0.10,6.1)"
    #   ae2 latest 19.1              -> "ae2" and "[19.2,20)"
    #   without create and ae2       -> loads ("Done (")
    #   kotlinforforge 5.10.0        -> loads (the pinned set already uses 5.10.0)
    ...

def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=["resolve", "smoke", "deps", "client"])
    ...
    print("SMOKE PASS")   # or "DEPS PASS", or a FAIL line naming the case, then exit 1
```

Write each `...` body in full. The `client` command assembles `run/testpack/client/mods/` with the same
pinned jars and the built jar, and prints the folder path for the approver's play-test launcher.

As built: every pin in `mods.json` is an object (version, Modrinth `version_number`, file name, URL,
SHA-512), because Modrinth's version numbers differ from the mod versions (Cobblemon `1.8.4NF`, Create
`6.0.10+mc1.21.1`). `fetch()` refuses a download without a SHA-512. FML logs a rejected dependency under
`Missing or unsupported mandatory dependencies:` (required) or `Unsupported installed optional
dependencies:` (optional), on a line `Mod ID: '<id>', Requested by: 'cobblemon_dynamics', Expected range:
'<range>', Actual version: '<v>'`, and the server still exits with code 0, so each case is judged from the
log. AE2 has no 19.1 release, so its below-floor case uses `19.1.3-beta`. GuideME is pinned to `21.1.1`,
the version AE2 19.2.19 builds against.

- [ ] **Step 3: Run the smoke scenario**

Run: `./gradlew build && py scripts/testpack/testpack.py resolve && py scripts/testpack/testpack.py smoke`
Expected: `SMOKE PASS`, and `run/testpack/smoke/logs/latest.log` lists `cobblemon_dynamics`.

- [ ] **Step 4: Run the dependency demos**

Run: `py scripts/testpack/testpack.py deps`
Expected: `DEPS PASS`. Copy the six case lines from the output into the pull request body as the demo
record for CD-PLAT-01, CD-PLAT-02, CD-PLAT-03 and CD-PLAT-05.

- [ ] **Step 5: Commit**

```bash
git add scripts/testpack
git commit -m "Add the isolated test pack with smoke and dependency-range scenarios"
```

---

### Task 6: Repository docs, pull request and CI proof

**Rows:** CD-REL-01 (demo), CD-REL-02, CD-REL-03 (live on GitHub).

**Files:**
- Modify: `README.md` (Status section: "Phase 1 in progress", and a "Building" section)
- Create: a local notes file (repo gotchas only: JDK 21 through `JAVA_HOME` because `java` on the PATH may be 8,
  `gradlew.bat` on Windows, GameTest logs live in `run/gametest/logs/`, the test pack never uses the COHO
  pack)

- [ ] **Step 1: Write the README additions**

```markdown
## Building

You need JDK 21. Set `JAVA_HOME` to it.

| Task | Command |
| --- | --- |
| Build the jar, run the unit tests and the jar checks | `./gradlew build` |
| Run the GameTests | `./gradlew runGameTestServer` |
| Start a development client | `./gradlew runClient` |
| Start a clean test server with the built jar | `py scripts/testpack/testpack.py smoke` |

The jar is written to `build/libs/`.
```

- [ ] **Step 2: Run everything once more**

Run: `./gradlew build runGameTestServer && py scripts/check_plan_coverage.py`
Expected: `BUILD SUCCESSFUL`, `All 2 required tests passed`, `missing: 0`.

- [ ] **Step 3: Commit, push and open the pull request**

Commit with `/commit`. Dispatch `pr-runner` with base `main`, the branch `m0-scaffold-ci`, and a body that
lists the rows of Tasks 1 to 5 and the `deps` demo record.

- [ ] **Step 4: Prove that CI fails on a failing unit test (CD-REL-01 demo)**

On a throwaway branch from `m0-scaffold-ci`, change one assertion in `ModMetadataTest` to a wrong value,
push it, open a draft pull request, and confirm that the `build` check fails. Close the draft pull request
and delete the branch. Record the run URL in the M0 pull request body.

- [ ] **Step 5: Merge**

When `build`, `CodeQL` and `Dependency review` pass and every review thread is resolved, merge the pull
request.
