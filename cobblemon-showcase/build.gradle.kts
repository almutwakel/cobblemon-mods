import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    kotlin("jvm") version "2.2.20"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    maven("https://maven.nucleoid.xyz")
    maven("https://artefacts.cobblemon.com/releases")
    mavenCentral()
}

dependencies {
    minecraft("net.minecraft:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation(fabricApi.module("fabric-command-api-v2", project.property("fabric_version") as String))
    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", project.property("fabric_version") as String))

    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.6+kotlin.2.2.20")

    modImplementation("com.cobblemon:mod:1.7.3+1.21.1") { isTransitive = false }
    modImplementation("com.cobblemon:fabric:1.7.3+1.21.1")

    modImplementation(include("eu.pb4:sgui:1.6.1+1.21.1")!!)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(project.properties)
        }
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    compileJava {
        options.release = 21
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
