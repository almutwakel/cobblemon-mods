import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.78"
    kotlin("jvm") version "2.2.20"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

neoForge {
    version = project.property("neoforge_version") as String

    runs {
        register("server") {
            server()
            programArguments.add("--nogui")
        }
        register("client") {
            client()
        }
    }

    mods {
        register("cobblemon_bridge") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${project.property("cobblemon_version")}")

    // Mixin annotation API — runtime is provided by NeoForge. We deliberately DO NOT use the
    // Mixin AP (annotation processor) because our DataPackManagerMixin targets a class from
    // RCTmod (not on our compile classpath). The AP can't verify the target and refuses to
    // build. With remap=false the Mixin is applied directly at runtime; AP-generated refmaps
    // aren't needed for cross-mod NeoForge targets that already use Mojmap names.
    compileOnly("org.spongepowered:mixin:0.8.7")
    // MixinExtras — bundled by NeoForge at runtime. We need it at compile-time for the
    // `@ModifyExpressionValue` annotation, which lets us modify a method-invoke return value
    // without declaring the receiver type. Useful when the target class (TrainerManager) isn't
    // on our compile classpath — plain `@Redirect` requires the exact type at index 0.
    compileOnly("io.github.llamalad7:mixinextras-common:0.4.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(project.properties)
        }
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
