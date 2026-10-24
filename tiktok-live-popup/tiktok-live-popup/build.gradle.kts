plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    id("maven-publish")
}

fun prop(name: String): String = findProperty(name) as String

version = "${prop("mod.version")}+${prop("minecraft_version")}"
group = prop("mod.group")
base.archivesName = prop("mod.archives_name")

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
}

loom {
    enableModProvidedJavadoc = false
    splitEnvironmentSourceSets()

    mods {
        create("tiktok-live-popup") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDir = "run"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

tasks {
    processResources {
        val props = mapOf(
            "version" to prop("mod.version"),
            "minecraft" to prop("mod.mc_compat"),
            "fabric_loader_min" to prop("deps.fabric_loader_min"),
            "java_min" to prop("deps.java_min"),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
}
